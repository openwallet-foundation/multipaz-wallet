package org.multipaz.wallet.android.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.bytestring.ByteString
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.toDataItem
import org.multipaz.cbor.toDataItemFullDate
import org.multipaz.compose.branding.Branding
import org.multipaz.compose.encodeImageToPng
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.mdoc.issuersigned.buildIssuerNamespaces
import org.multipaz.mdoc.mso.MobileSecurityObject
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.securearea.AndroidKeystoreCreateKeySettings
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.UserAuthenticationType
import org.multipaz.trustmanagement.TrustEntryAlreadyExistsException
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.util.Logger
import org.multipaz.util.truncateToWholeSeconds
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.shared.Domains
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Helper for minting developer mode PhotoID documents with a hard-coded IACA certificate
 * and a dynamic DS certificate generated at runtime.
 */
object DeveloperPhotoIdIssuer {
    private const val TAG = "DeveloperPhotoIdIssuer"

    const val IACA_CERT_PEM = """-----BEGIN CERTIFICATE-----
MIICcDCCAhagAwIBAgIRAJWn0Akgz0sEZ1WuGOnkn0UwCgYIKoZIzj0EAwIwUzELMAkGA1UEBgwC
VVMxCzAJBgNVBAgMAk1BMREwDwYDVQQKDAhNdWx0aXBhejEkMCIGA1UEAwwbTXVsdGlwYXogV2Fs
bGV0IEluLUFwcCBJQUNBMB4XDTI2MDkxNjIwNTAxNFoXDTI5MTIyOTIwNTAxNFowUzELMAkGA1UE
BgwCVVMxCzAJBgNVBAgMAk1BMREwDwYDVQQKDAhNdWx0aXBhejEkMCIGA1UEAwwbTXVsdGlwYXog
V2FsbGV0IEluLUFwcCBJQUNBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfr/M6FefKFmcgoTB
qKeiuO0qw5IOjP+9g5eZoSzSCZuyftOQb0yCQGErW0PTCrgOWEmPWGKETaC5Gzmr3SxbAaOByjCB
xzAOBgNVHQ8BAf8EBAMCAQYwEgYDVR0TAQH/BAgwBgEB/wIBADArBgNVHRIEJDAihiBodHRwczov
L2Rldi53YWxsZXQubXVsdGlwYXoub3JnLzA0BgNVHR8ELTArMCmgJ6AlhiNodHRwczovL2Rldi53
YWxsZXQubXVsdGlwYXoub3JnL2NybDAdBgNVHQ4EFgQUqnhVfyeCgE3Gh+mAXemY3i+hAkEwHwYD
VR0jBBgwFoAUqnhVfyeCgE3Gh+mAXemY3i+hAkEwCgYIKoZIzj0EAwIDSAAwRQIgS/t+icKw8kUC
tlwgFx2pGccSnjLEkMICsFz185yRotICIQDfIw+HJ/CXq/INi1NlGXKYo0xY2f7bxAJJBFNfM4fF
yA==
-----END CERTIFICATE-----"""

    const val IACA_PRIVATE_KEY_PEM = """-----BEGIN PRIVATE KEY-----
ME0CAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEMzAxAgEBBCBIqVhMuVMO7Aw0nUejFAOnDplYCUNz
YM2TzKkXtYaiiKAKBggqhkjOPQMBBw==
-----END PRIVATE KEY-----"""

    /**
     * Obtains the hard-coded IACA certified key.
     */
    fun getIacaKey(): AsymmetricKey.X509Certified {
        val iacaCert = X509Cert.fromPem(IACA_CERT_PEM)
        val iacaPrivateKey = EcPrivateKey.fromPem(IACA_PRIVATE_KEY_PEM, iacaCert.ecPublicKey)
        return AsymmetricKey.X509CertifiedExplicit(
            certChain = X509CertChain(listOf(iacaCert)),
            privateKey = iacaPrivateKey
        )
    }

    /**
     * Mints a new PhotoID document and adds it to [documentStore].
     *
     * @param documentStore the [DocumentStore] to store the document in.
     * @param secureArea the [SecureArea] to bind the credential keys to.
     * @param userIssuerTrustManager the trust manager to register the IACA certificate in.
     * @param settingsModel current application developer settings.
     * @param givenName given name entered by the user.
     * @param familyName family name entered by the user.
     * @param dateOfBirth date of birth entered by the user.
     * @param portraitBytes JPEG-encoded portrait image captured from front camera.
     * @return the created [Document].
     */
    suspend fun issuePhotoId(
        documentStore: DocumentStore,
        secureArea: SecureArea,
        userIssuerTrustManager: TrustManager,
        settingsModel: SettingsModel,
        givenName: String,
        familyName: String,
        dateOfBirth: LocalDate,
        portraitBytes: ByteArray,
        sex: Long = 1L,
        nationality: String = "US",
        documentNumber: String = "987654321",
        issueDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        expiryDate: LocalDate = LocalDate(
            issueDate.year + 5,
            issueDate.month,
            issueDate.day
        ),
        issuingAuthority: String = "Multipaz Wallet TEST In-App Issuing Authority",
        issuingCountry: String = "ZZ",
        residentStreet: String = "Main Street",
        residentHouseNumber: String = "123",
        residentCity: String = "Sample City",
        residentState: String = "CA",
        residentPostalCode: String = "12345",
        residentCountry: String = "US",
        residentAddress: String = "$residentHouseNumber $residentStreet, $residentCity, $residentState $residentPostalCode",
        personId: String = "24601",
        administrativeNumber: String = "123456789",
        authorizedNamespaces: List<String> = emptyList(),
        authorizedDataElements: Map<String, List<String>> = mapOf("org.iso.23220.5.1" to listOf("CHV_1"))
    ): Document {
        val conflict = authorizedNamespaces.toSet().intersect(authorizedDataElements.keys)
        require(conflict.isEmpty()) {
            "Namespace(s) ${conflict.joinToString()} cannot appear in both authorized namespaces and authorized data elements"
        }

        val iacaKey = getIacaKey()
        val iacaCert = iacaKey.certChain.certificates.first()

        // Register IACA certificate in user issuer trust manager so wallet verifier trusts it
        try {
            userIssuerTrustManager.addX509Cert(
                certificate = iacaCert,
                metadata = TrustMetadata(displayName = "Multipaz Wallet In-App IACA")
            )
        } catch (_: TrustEntryAlreadyExistsException) {
            // Already registered
        }

        // Create document in DocumentStore
        val document = documentStore.createDocument(
            displayName = "$givenName $familyName",
            typeDisplayName = "Photo ID"
        )
        try {
            val cardArtBytes = renderCardArtWithPortrait(
                document = document,
                portraitBytes = portraitBytes
            )
            document.edit {
                cardArt = cardArtBytes
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Failed to render card art: ${e.message}")
        }

        val now = Clock.System.now().truncateToWholeSeconds()
        val validFrom = now - 1.days
        val validUntil = now + 365.days

        // Generate dynamic DS key pair and certificate signed by hard-coded IACA
        val dsPrivateKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val dsCert = MdocUtil.generateDsCertificate(
            iacaKey = iacaKey,
            dsKey = dsPrivateKey.publicKey,
            subject = X500Name.fromName("C=US,ST=MA,O=Multipaz,CN=Multipaz Wallet Dynamic DS"),
            serial = ASN1Integer.fromRandom(128),
            validFrom = validFrom,
            validUntil = validUntil
        )
        val dsKey = AsymmetricKey.X509CertifiedExplicit(
            certChain = X509CertChain(listOf(dsCert)),
            privateKey = dsPrivateKey
        )

        // Calculate age attributes
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        var ageInYears = today.year - dateOfBirth.year
        if (today.month < dateOfBirth.month ||
            (today.month == dateOfBirth.month && today.day < dateOfBirth.day)
        ) {
            ageInYears--
        }
        ageInYears = maxOf(0, ageInYears)

        val overrides = mutableMapOf<Pair<String, String>, DataItem>()
        val nsIso = PhotoID.ISO_23220_2_NAMESPACE
        val nsPhotoId = PhotoID.PHOTO_ID_NAMESPACE

        // All 9 mandatory PhotoID data elements as per ISO/IEC 23220-4 and PhotoID specification:
        // 1. family_name (mandatory)
        overrides[Pair(nsIso, "family_name")] = familyName.toDataItem()
        // 2. given_name (mandatory)
        overrides[Pair(nsIso, "given_name")] = givenName.toDataItem()
        // 3. birth_date (mandatory) - map with "birth_date" full-date
        overrides[Pair(nsIso, "birth_date")] = buildCborMap {
            put("birth_date", dateOfBirth.toDataItemFullDate())
        }
        // 4. portrait (mandatory) - JPEG bytes
        overrides[Pair(nsIso, "portrait")] = portraitBytes.toDataItem()
        // 5. issue_date (mandatory)
        overrides[Pair(nsIso, "issue_date")] = issueDate.toDataItemFullDate()
        // 6. expiry_date (mandatory)
        overrides[Pair(nsIso, "expiry_date")] = expiryDate.toDataItemFullDate()
        // 7. issuing_authority (mandatory)
        overrides[Pair(nsIso, "issuing_authority")] = issuingAuthority.toDataItem()
        // 8. issuing_country (mandatory)
        overrides[Pair(nsIso, "issuing_country")] = issuingCountry.toDataItem()
        // 9. age_over_18 (mandatory)
        overrides[Pair(nsIso, "age_over_18")] = (ageInYears >= 18).toDataItem()

        // Additional standard PhotoID data elements
        overrides[Pair(nsIso, "age_in_years")] = ageInYears.toDataItem()
        overrides[Pair(nsIso, "age_birth_year")] = dateOfBirth.year.toDataItem()
        overrides[Pair(nsIso, "portrait_capture_date")] = issueDate.toDataItemFullDate()

        val ageThresholds = listOf(13, 15, 16, 18, 21, 23, 25, 27, 28, 40, 60, 65, 67)
        for (age in ageThresholds) {
            val id = "age_over_${if (age < 10) "0$age" else "$age"}"
            overrides[Pair(nsIso, id)] = (ageInYears >= age).toDataItem()
        }

        // Additional user-provided / editable PhotoID data elements in ISO_23220_2_NAMESPACE
        overrides[Pair(nsIso, "sex")] = sex.toDataItem()
        overrides[Pair(nsIso, "nationality")] = nationality.toDataItem()
        overrides[Pair(nsIso, "document_number")] = documentNumber.toDataItem()
        overrides[Pair(nsIso, "resident_address")] = residentAddress.toDataItem()
        overrides[Pair(nsIso, "resident_city")] = residentCity.toDataItem()
        overrides[Pair(nsIso, "resident_postal_code")] = residentPostalCode.toDataItem()
        overrides[Pair(nsIso, "resident_country")] = residentCountry.toDataItem()

        // Additional user-provided / editable PhotoID data elements in PHOTO_ID_NAMESPACE
        overrides[Pair(nsPhotoId, "resident_street")] = residentStreet.toDataItem()
        overrides[Pair(nsPhotoId, "resident_house_number")] = residentHouseNumber.toDataItem()
        overrides[Pair(nsPhotoId, "resident_state")] = residentState.toDataItem()
        overrides[Pair(nsPhotoId, "person_id")] = personId.toDataItem()
        overrides[Pair(nsPhotoId, "administrative_number")] = administrativeNumber.toDataItem()

        // Build IssuerNamespaces from the provided overrides without any sample data
        val issuerNamespaces = buildIssuerNamespaces {
            val byNamespace = overrides.entries.groupBy { it.key.first }
            for ((nsName, entries) in byNamespace) {
                addNamespace(nsName) {
                    for (entry in entries) {
                        addDataElement(entry.key.second, entry.value)
                    }
                }
            }
        }

        val domainsToProvision = mutableListOf(Domains.DOMAIN_MDOC_USER_AUTH)
        if (!settingsModel.disableNoUserAuth.value) {
            domainsToProvision.add(Domains.DOMAIN_MDOC_NO_USER_AUTH)
        }

        for (domain in domainsToProvision) {
            val userAuthRequired = (domain == Domains.DOMAIN_MDOC_USER_AUTH)
            val createKeySettings = try {
                AndroidKeystoreCreateKeySettings.Builder(ByteString())
                    .setAlgorithm(Algorithm.ESP256)
                    .setUserAuthenticationRequired(
                        userAuthRequired,
                        0.seconds,
                        setOf(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC)
                    )
                    .build()
            } catch (_: Exception) {
                CreateKeySettings(userAuthenticationRequired = false)
            }

            val mdocCredential = try {
                MdocCredential.create(
                    document = document,
                    asReplacementForIdentifier = null,
                    domain = domain,
                    secureArea = secureArea,
                    docType = PhotoID.PHOTO_ID_DOCTYPE,
                    createKeySettings = createKeySettings
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.w(TAG, "Failed creating key with userAuth=$userAuthRequired, falling back to no-auth", e)
                MdocCredential.create(
                    document = document,
                    asReplacementForIdentifier = null,
                    domain = domain,
                    secureArea = secureArea,
                    docType = PhotoID.PHOTO_ID_DOCTYPE,
                    createKeySettings = CreateKeySettings(userAuthenticationRequired = false)
                )
            }

            val mso = MobileSecurityObject(
                version = "1.0",
                docType = PhotoID.PHOTO_ID_DOCTYPE,
                signedAt = now,
                validFrom = validFrom,
                validUntil = validUntil,
                expectedUpdate = null,
                digestAlgorithm = Algorithm.SHA256,
                valueDigests = issuerNamespaces.getValueDigests(Algorithm.SHA256),
                deviceKey = mdocCredential.getAttestation().ecPublicKey,
                deviceKeyAuthorizedNamespaces = authorizedNamespaces,
                deviceKeyAuthorizedDataElements = authorizedDataElements,
            )
            val taggedEncodedMso = Cbor.encode(
                Tagged(
                    Tagged.ENCODED_CBOR,
                    Bstr(Cbor.encode(mso.toDataItem()))
                )
            )

            val protectedHeaders = mapOf<CoseLabel, DataItem>(
                Pair(
                    CoseNumberLabel(Cose.COSE_LABEL_ALG),
                    Algorithm.ES256.coseAlgorithmIdentifier!!.toDataItem()
                )
            )
            val unprotectedHeaders = mapOf<CoseLabel, DataItem>(
                Pair(
                    CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
                    dsKey.certChain.toDataItem()
                )
            )
            val encodedIssuerAuth = Cbor.encode(
                Cose.coseSign1Sign(
                    dsKey,
                    taggedEncodedMso,
                    true,
                    protectedHeaders,
                    unprotectedHeaders
                ).toDataItem()
            )
            val issuerProvidedAuthenticationData = Cbor.encode(
                buildCborMap {
                    put("nameSpaces", issuerNamespaces.toDataItem())
                    put("issuerAuth", RawCbor(encodedIssuerAuth))
                }
            )

            mdocCredential.certify(ByteString(issuerProvidedAuthenticationData))
        }

        document.edit {
            provisioned = true
        }

        return document
    }

    private suspend fun renderCardArtWithPortrait(
        document: Document,
        portraitBytes: ByteArray
    ): ByteString {
        val baseCardArt = Branding.Current.value.renderFallbackCardArt(document)
        val baseBitmap = baseCardArt.asAndroidBitmap()
        val resultBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(resultBitmap)

        val portraitBitmap = BitmapFactory.decodeByteArray(portraitBytes, 0, portraitBytes.size)
        if (portraitBitmap != null) {
            val w = resultBitmap.width.toFloat()
            val h = resultBitmap.height.toFloat()
            val portraitHeight = h * 0.62f
            val portraitWidth = portraitHeight * (3f / 4f)
            val rightMargin = w * 0.06f
            val left = w - rightMargin - portraitWidth
            val top = (h - portraitHeight) / 2f
            val rect = RectF(left, top, left + portraitWidth, top + portraitHeight)

            val cornerRadius = h * 0.04f
            val path = Path().apply {
                addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            }

            canvas.save()
            canvas.clipPath(path)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(portraitBitmap, null, rect, paint)
            canvas.restore()

            // Subtle border around portrait
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = android.graphics.Color.WHITE
                strokeWidth = h * 0.006f
                alpha = 180
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        }

        return encodeImageToPng(resultBitmap.asImageBitmap())
    }
}
