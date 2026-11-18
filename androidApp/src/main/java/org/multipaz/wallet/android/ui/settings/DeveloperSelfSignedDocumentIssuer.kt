package org.multipaz.wallet.android.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.toDataItem
import org.multipaz.cbor.toDataItemDateTimeString
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
import org.multipaz.documenttype.DocumentAttribute
import org.multipaz.documenttype.DocumentAttributeSensitivity
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.ISO_23220_5_CHV_1_DATA_ELEMENT
import org.multipaz.documenttype.ISO_23220_5_CHV_1_NAMESPACE
import org.multipaz.documenttype.JsonDocumentType
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.mdoc.credential.KeyBoundMdocCredential
import org.multipaz.mdoc.credential.KeylessMdocCredential
import org.multipaz.mdoc.issuersigned.buildIssuerNamespaces
import org.multipaz.mdoc.mso.MobileSecurityObject
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.sdjwt.credential.KeylessSdJwtVcCredential
import org.multipaz.securearea.AndroidKeystoreCreateKeySettings
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.UserAuthenticationType
import org.multipaz.trustmanagement.TrustEntryAlreadyExistsException
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import org.multipaz.util.truncateToWholeSeconds
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.shared.Domains
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Helper for minting developer mode self-signed documents (ISO mdoc and/or IETF SD-JWT VC)
 * with a hard-coded IACA certificate and a dynamic DS certificate generated at runtime.
 */
object DeveloperSelfSignedDocumentIssuer {
    private const val TAG = "DeveloperSelfSignedDocumentIssuer"

    const val DEFAULT_ISSUING_AUTHORITY = "Multipaz Wallet TEST In-App Issuing Authority"
    const val DEFAULT_ISSUING_COUNTRY = "ZZ"
    const val DEFAULT_SD_JWT_ISSUER = "https://example-issuer.com"
    const val DEFAULT_HOLDER_AGE_YEARS = 20

    private val AGE_OVER_REGEX = Regex("^age_(?:over_|above)(\\d+)$")

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
     * Returns the top-level JSON claim name used for the holder portrait image, if any.
     */
    fun findJsonPortraitClaimName(jsonDocumentType: JsonDocumentType?): String? {
        if (jsonDocumentType == null) return null
        return jsonDocumentType.claims.entries.firstOrNull { (claimName, attr) ->
            !claimName.contains('.') &&
                attr.type == DocumentAttributeType.Picture &&
                (attr.sensitivity == DocumentAttributeSensitivity.PORTRAIT_IMAGE ||
                    attr.identifier == "picture" ||
                    attr.identifier == "portrait")
        }?.key
    }

    /**
     * Finds a fallback JSON sample value from the corresponding ISO mdoc attribute when
     * [DocumentAttribute.sampleValueJson] is not populated on [DocumentType].
     */
    fun findFallbackSampleJson(
        documentType: DocumentType,
        identifier: String
    ): JsonElement? {
        val mdocDocType = documentType.mdocDocumentType ?: return null
        for (ns in mdocDocType.namespaces.values) {
            val sampleMdoc = ns.dataElements[identifier]?.attribute?.sampleValueMdoc
            if (sampleMdoc != null && sampleMdoc != Simple.NULL) {
                return sampleMdoc.toJson()
            }
        }
        return null
    }

    /**
     * Returns `true` if [documentType] is mDL, PhotoID, or EU PID, where the default holder age
     * should be initialized to [DEFAULT_HOLDER_AGE_YEARS] (20 years old).
     */
    fun shouldDefaultToAge20(documentType: DocumentType): Boolean {
        val docType = documentType.mdocDocumentType?.docType
        val vct = documentType.jsonDocumentType?.vct
        return docType == DrivingLicense.MDL_DOCTYPE ||
            docType == PhotoID.PHOTO_ID_DOCTYPE ||
            docType == EUPersonalID.EUPID_DOCTYPE ||
            vct == EUPersonalID.EUPID_VCT
    }

    /**
     * Builds the default map of ISO mdoc data elements for [documentType].
     *
     * Mandatory elements and elements with non-null sample values are included by default
     * (excluding the portrait equivalence target element, which is populated from `portraitBytes`).
     */
    fun buildDefaultMdocDataElements(
        documentType: DocumentType,
        today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ): Map<Pair<String, String>, DataItem> {
        val mdocDocType = documentType.mdocDocumentType ?: return emptyMap()
        val peData = mdocDocType.supportsPortraitImageEquivalence
        val defaultExpiry = today.plus(5, DateTimeUnit.YEAR)
        val defaultDob = today.minus(DEFAULT_HOLDER_AGE_YEARS, DateTimeUnit.YEAR)
        val defaultToAge20 = shouldDefaultToAge20(documentType)
        val result = mutableMapOf<Pair<String, String>, DataItem>()

        for ((nsName, ns) in mdocDocType.namespaces) {
            for ((deName, de) in ns.dataElements) {
                if (peData != null && peData.namespace == nsName && peData.dataElementName == deName) {
                    continue
                }
                val sample = de.attribute.sampleValueMdoc
                val includeByDefault = de.mandatory || (sample != null && sample != Simple.NULL)
                if (!includeByDefault) continue

                val ageOverThreshold = if (defaultToAge20 && de.attribute.type == DocumentAttributeType.Boolean) {
                    AGE_OVER_REGEX.matchEntire(deName)?.groupValues?.get(1)?.toIntOrNull()
                } else {
                    null
                }

                val value: DataItem = when {
                    deName == "issuing_authority" && de.attribute.type == DocumentAttributeType.String ->
                        DEFAULT_ISSUING_AUTHORITY.toDataItem()
                    deName == "issuing_country" &&
                        (de.attribute.type == DocumentAttributeType.String ||
                            de.attribute.type is DocumentAttributeType.StringOptions) ->
                        DEFAULT_ISSUING_COUNTRY.toDataItem()
                    (deName == "issue_date" || deName == "issuance_date" || deName == "portrait_capture_date") &&
                        de.attribute.type == DocumentAttributeType.Date ->
                        today.toDataItemFullDate()
                    deName == "expiry_date" && de.attribute.type == DocumentAttributeType.Date ->
                        defaultExpiry.toDataItemFullDate()
                    defaultToAge20 && deName == "birth_date" && de.attribute.type == DocumentAttributeType.Date -> {
                        if (sample is CborMap) {
                            buildCborMap { put("birth_date", defaultDob.toDataItemFullDate()) }
                        } else {
                            defaultDob.toDataItemFullDate()
                        }
                    }
                    defaultToAge20 && deName == "age_in_years" && de.attribute.type == DocumentAttributeType.Number ->
                        DEFAULT_HOLDER_AGE_YEARS.toLong().toDataItem()
                    defaultToAge20 && deName == "age_birth_year" && de.attribute.type == DocumentAttributeType.Number ->
                        defaultDob.year.toLong().toDataItem()
                    ageOverThreshold != null ->
                        (DEFAULT_HOLDER_AGE_YEARS >= ageOverThreshold).toDataItem()
                    sample != null && sample != Simple.NULL -> sample
                    else -> defaultDataItemForAttribute(de.attribute, today, defaultDob)
                }
                result[Pair(nsName, deName)] = value
            }
        }
        return result
    }

    private fun defaultDataItemForAttribute(
        attribute: DocumentAttribute,
        today: LocalDate,
        defaultDob: LocalDate
    ): DataItem {
        return when (val type = attribute.type) {
            is DocumentAttributeType.String -> "".toDataItem()
            is DocumentAttributeType.Number -> 0L.toDataItem()
            is DocumentAttributeType.Boolean -> false.toDataItem()
            is DocumentAttributeType.Date -> {
                if (attribute.sampleValueMdoc is CborMap) {
                    buildCborMap { put("birth_date", defaultDob.toDataItemFullDate()) }
                } else {
                    today.toDataItemFullDate()
                }
            }
            is DocumentAttributeType.DateTime ->
                Clock.System.now().truncateToWholeSeconds().toDataItemDateTimeString()
            is DocumentAttributeType.IntegerOptions ->
                (type.options.firstOrNull { it.value != null }?.value ?: 0).toLong().toDataItem()
            is DocumentAttributeType.StringOptions ->
                (type.options.firstOrNull { it.value != null }?.value ?: "").toDataItem()
            is DocumentAttributeType.Blob,
            is DocumentAttributeType.Picture -> ByteArray(0).toDataItem()
            is DocumentAttributeType.ComplexType -> buildCborMap {}
        }
    }

    /**
     * Builds the default [JsonObject] of SD-JWT VC claims for [documentType].
     */
    fun buildDefaultJsonClaims(
        documentType: DocumentType,
        today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ): JsonObject {
        val jsonDocType = documentType.jsonDocumentType ?: return buildJsonObject {}
        val portraitClaimName = findJsonPortraitClaimName(jsonDocType)
        val defaultExpiry = today.plus(5, DateTimeUnit.YEAR)
        val defaultDob = today.minus(DEFAULT_HOLDER_AGE_YEARS, DateTimeUnit.YEAR)
        val defaultToAge20 = shouldDefaultToAge20(documentType)

        return buildJsonObject {
            for ((claimName, attribute) in jsonDocType.claims) {
                if (claimName.contains('.') || claimName == portraitClaimName) {
                    continue
                }
                if (attribute.embeddedAttributes.isNotEmpty()) {
                    val parentSampleObj = attribute.sampleValueJson as? JsonObject
                    val subObj = buildJsonObject {
                        for (subAttr in attribute.embeddedAttributes) {
                            val ageOverThreshold = if (
                                defaultToAge20 &&
                                claimName == "age_equal_or_over" &&
                                subAttr.type == DocumentAttributeType.Boolean
                            ) {
                                subAttr.identifier.toIntOrNull()
                            } else {
                                null
                            }
                            if (ageOverThreshold != null) {
                                put(
                                    subAttr.identifier,
                                    JsonPrimitive(DEFAULT_HOLDER_AGE_YEARS >= ageOverThreshold)
                                )
                            } else {
                                val subSample = parentSampleObj?.get(subAttr.identifier)
                                    ?: subAttr.sampleValueJson
                                    ?: findFallbackSampleJson(documentType, subAttr.identifier)
                                if (subSample != null && subSample !is JsonNull) {
                                    put(subAttr.identifier, subSample)
                                }
                            }
                        }
                    }
                    if (subObj.isNotEmpty()) {
                        put(claimName, subObj)
                    }
                } else {
                    val rawSample = attribute.sampleValueJson
                        ?: findFallbackSampleJson(documentType, claimName)
                    val ageOverThreshold = if (defaultToAge20 && attribute.type == DocumentAttributeType.Boolean) {
                        AGE_OVER_REGEX.matchEntire(claimName)?.groupValues?.get(1)?.toIntOrNull()
                    } else {
                        null
                    }
                    val value: JsonElement? = when {
                        claimName == "issuing_authority" && attribute.type == DocumentAttributeType.String ->
                            JsonPrimitive(DEFAULT_ISSUING_AUTHORITY)
                        claimName == "issuing_country" &&
                            (attribute.type == DocumentAttributeType.String ||
                                attribute.type is DocumentAttributeType.StringOptions) ->
                            JsonPrimitive(DEFAULT_ISSUING_COUNTRY)
                        (claimName == "issue_date" || claimName == "date_of_issuance") &&
                            attribute.type == DocumentAttributeType.Date ->
                            JsonPrimitive(today.toString())
                        (claimName == "expiry_date" || claimName == "date_of_expiry") &&
                            attribute.type == DocumentAttributeType.Date ->
                            JsonPrimitive(defaultExpiry.toString())
                        defaultToAge20 &&
                            (claimName == "birth_date" || claimName == "birthdate") &&
                            attribute.type == DocumentAttributeType.Date ->
                            JsonPrimitive(defaultDob.toString())
                        defaultToAge20 &&
                            claimName == "age_in_years" &&
                            attribute.type == DocumentAttributeType.Number ->
                            JsonPrimitive(DEFAULT_HOLDER_AGE_YEARS)
                        defaultToAge20 &&
                            claimName == "age_birth_year" &&
                            attribute.type == DocumentAttributeType.Number ->
                            JsonPrimitive(defaultDob.year)
                        ageOverThreshold != null ->
                            JsonPrimitive(DEFAULT_HOLDER_AGE_YEARS >= ageOverThreshold)
                        rawSample != null && rawSample !is JsonNull -> rawSample
                        else -> null
                    }
                    if (value != null) {
                        put(claimName, value)
                    }
                }
            }
        }
    }

    /**
     * Mints a new self-signed document for [documentType] and adds it to [documentStore].
     *
     * Supports any [DocumentType] with a non-null [DocumentType.mdocDocumentType] and/or
     * [DocumentType.jsonDocumentType].
     *
     * @param documentStore the [DocumentStore] to store the document in.
     * @param secureArea the [SecureArea] to bind credential keys to.
     * @param userIssuerTrustManager the trust manager to register the IACA certificate in.
     * @param settingsModel current application developer settings.
     * @param documentType the [DocumentType] to issue.
     * @param portraitBytes JPEG-encoded portrait image captured from the front camera, or `null`
     *   for document types that do not use a portrait image.
     * @param mdocDataElements map of `(namespace, dataElementName)` to [DataItem] for ISO mdoc credentials.
     * @param jsonClaims [JsonObject] of claims for SD-JWT VC credentials.
     * @param authorizedNamespaces MSO device-key authorized namespaces.
     * @param authorizedDataElements MSO device-key authorized data elements per namespace.
     * @return the created [Document].
     */
    suspend fun issueSelfSignedDocument(
        documentStore: DocumentStore,
        secureArea: SecureArea,
        userIssuerTrustManager: TrustManager,
        settingsModel: SettingsModel,
        documentType: DocumentType,
        portraitBytes: ByteArray? = null,
        mdocDataElements: Map<Pair<String, String>, DataItem> = buildDefaultMdocDataElements(documentType),
        jsonClaims: JsonObject = buildDefaultJsonClaims(documentType),
        authorizedNamespaces: List<String> = emptyList(),
        authorizedDataElements: Map<String, List<String>> =
            if (documentType.mdocDocumentType?.keyBound == true &&
                documentType.mdocDocumentType?.supportsPortraitImageEquivalence != null
            ) {
                mapOf(ISO_23220_5_CHV_1_NAMESPACE to listOf(ISO_23220_5_CHV_1_DATA_ELEMENT))
            } else {
                emptyMap()
            }
    ): Document {
        val mdocDocType = documentType.mdocDocumentType
        val jsonDocType = documentType.jsonDocumentType
        require(mdocDocType != null || jsonDocType != null) {
            "DocumentType '${documentType.displayName}' must have an mdocDocumentType or jsonDocumentType"
        }

        val conflict = authorizedNamespaces.toSet().intersect(authorizedDataElements.keys)
        require(conflict.isEmpty()) {
            "Namespace(s) ${conflict.joinToString()} cannot appear in both authorized namespaces and authorized data elements"
        }

        val peData = mdocDocType?.supportsPortraitImageEquivalence
        if (peData != null) {
            requireNotNull(portraitBytes) {
                "portraitBytes must be provided when supportsPortraitImageEquivalence is non-null"
            }
        }

        val finalMdocDataElements = mdocDataElements.toMutableMap()
        if (peData != null && portraitBytes != null) {
            finalMdocDataElements[Pair(peData.namespace, peData.dataElementName)] = portraitBytes.toDataItem()
        }

        val jsonPortraitClaim = findJsonPortraitClaimName(jsonDocType)
        val finalJsonClaims = buildJsonObject {
            for ((k, v) in jsonClaims) {
                put(k, v)
            }
            if (jsonPortraitClaim != null && portraitBytes != null) {
                put(jsonPortraitClaim, JsonPrimitive(portraitBytes.toBase64Url()))
            }
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

        val docDisplayName = deriveDocumentDisplayName(
            documentType = documentType,
            mdocDataElements = finalMdocDataElements,
            jsonClaims = finalJsonClaims
        )

        val document = documentStore.createDocument(
            displayName = docDisplayName,
            typeDisplayName = documentType.displayName
        )

        try {
            val cardArtBytes = if (portraitBytes != null) {
                renderCardArtWithPortrait(
                    document = document,
                    portraitBytes = portraitBytes
                )
            } else {
                encodeImageToPng(Branding.Current.value.renderFallbackCardArt(document))
            }
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

        if (mdocDocType != null) {
            val issuerNamespaces = buildIssuerNamespaces {
                val byNamespace = finalMdocDataElements.entries.groupBy { it.key.first }
                for ((nsName, entries) in byNamespace) {
                    addNamespace(nsName) {
                        for (entry in entries) {
                            addDataElement(entry.key.second, entry.value)
                        }
                    }
                }
            }

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

            if (mdocDocType.keyBound) {
                val mdocDomains = mutableListOf(Domains.DOMAIN_MDOC_USER_AUTH)
                if (!settingsModel.disableNoUserAuth.value) {
                    mdocDomains.add(Domains.DOMAIN_MDOC_NO_USER_AUTH)
                }

                for (domain in mdocDomains) {
                    val userAuthRequired = (domain == Domains.DOMAIN_MDOC_USER_AUTH)
                    val createKeySettings = buildCreateKeySettings(userAuthRequired)

                    val mdocCredential = try {
                        KeyBoundMdocCredential.create(
                            document = document,
                            asReplacementForIdentifier = null,
                            domain = domain,
                            secureArea = secureArea,
                            docType = mdocDocType.docType,
                            createKeySettings = createKeySettings
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.w(
                            TAG,
                            "Failed creating mdoc key with userAuth=$userAuthRequired, falling back to no-auth",
                            e
                        )
                        KeyBoundMdocCredential.create(
                            document = document,
                            asReplacementForIdentifier = null,
                            domain = domain,
                            secureArea = secureArea,
                            docType = mdocDocType.docType,
                            createKeySettings = CreateKeySettings(userAuthenticationRequired = false)
                        )
                    }

                    val mso = MobileSecurityObject(
                        version = "1.0",
                        docType = mdocDocType.docType,
                        signedAt = validFrom,
                        validFrom = validFrom,
                        validUntil = validUntil,
                        expectedUpdate = null,
                        digestAlgorithm = Algorithm.SHA256,
                        valueDigests = issuerNamespaces.getValueDigests(Algorithm.SHA256),
                        deviceKey = mdocCredential.getAttestation().publicKey,
                        deviceKeyAuthorizedNamespaces = authorizedNamespaces,
                        deviceKeyAuthorizedDataElements = authorizedDataElements,
                    )
                    val taggedEncodedMso = Cbor.encode(
                        Tagged(
                            Tagged.ENCODED_CBOR,
                            Bstr(Cbor.encode(mso.toDataItem()))
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
            } else {
                val mdocCredential = KeylessMdocCredential.create(
                    document = document,
                    asReplacementForIdentifier = null,
                    domain = Domains.DOMAIN_MDOC_KEYLESS,
                    docType = mdocDocType.docType,
                )

                val mso = MobileSecurityObject(
                    version = "1.0",
                    docType = mdocDocType.docType,
                    signedAt = validFrom,
                    validFrom = validFrom,
                    validUntil = validUntil,
                    expectedUpdate = null,
                    digestAlgorithm = Algorithm.SHA256,
                    valueDigests = issuerNamespaces.getValueDigests(Algorithm.SHA256),
                    deviceKey = null,
                    deviceKeyAuthorizedNamespaces = emptyList(),
                    deviceKeyAuthorizedDataElements = emptyMap(),
                )
                val taggedEncodedMso = Cbor.encode(
                    Tagged(
                        Tagged.ENCODED_CBOR,
                        Bstr(Cbor.encode(mso.toDataItem()))
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
        }

        if (jsonDocType != null) {
            if (jsonDocType.keyBound) {
                val sdJwtDomains = mutableListOf(Domains.DOMAIN_SDJWT_USER_AUTH)
                if (!settingsModel.disableNoUserAuth.value) {
                    sdJwtDomains.add(Domains.DOMAIN_SDJWT_NO_USER_AUTH)
                }

                for (domain in sdJwtDomains) {
                    val userAuthRequired = (domain == Domains.DOMAIN_SDJWT_USER_AUTH)
                    val createKeySettings = buildCreateKeySettings(userAuthRequired)

                    val sdJwtCredential = try {
                        KeyBoundSdJwtVcCredential.create(
                            document = document,
                            asReplacementForIdentifier = null,
                            domain = domain,
                            secureArea = secureArea,
                            vct = jsonDocType.vct,
                            createKeySettings = createKeySettings
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.w(TAG, "Failed creating SD-JWT key with userAuth=$userAuthRequired, falling back to no-auth", e)
                        KeyBoundSdJwtVcCredential.create(
                            document = document,
                            asReplacementForIdentifier = null,
                            domain = domain,
                            secureArea = secureArea,
                            vct = jsonDocType.vct,
                            createKeySettings = CreateKeySettings(userAuthenticationRequired = false)
                        )
                    }

                    val sdJwt = SdJwt.create(
                        issuerKey = dsKey,
                        kbKey = sdJwtCredential.getAttestation().publicKey,
                        claims = finalJsonClaims,
                        nonSdClaims = buildJsonObject {
                            put("iss", DEFAULT_SD_JWT_ISSUER)
                            put("vct", sdJwtCredential.vct)
                            put("iat", validFrom.epochSeconds)
                            put("nbf", validFrom.epochSeconds)
                            put("exp", validUntil.epochSeconds)
                        }
                    )
                    sdJwtCredential.certify(sdJwt.compactSerialization.encodeToByteString())
                }
            } else {
                val sdJwtCredential = KeylessSdJwtVcCredential.create(
                    document = document,
                    asReplacementForIdentifier = null,
                    domain = Domains.DOMAIN_SDJWT_KEYLESS,
                    vct = jsonDocType.vct
                )
                val sdJwt = SdJwt.create(
                    issuerKey = dsKey,
                    kbKey = null,
                    claims = finalJsonClaims,
                    nonSdClaims = buildJsonObject {
                        put("iss", DEFAULT_SD_JWT_ISSUER)
                        put("vct", sdJwtCredential.vct)
                        put("iat", validFrom.epochSeconds)
                        put("nbf", validFrom.epochSeconds)
                        put("exp", validUntil.epochSeconds)
                    }
                )
                sdJwtCredential.certify(sdJwt.compactSerialization.encodeToByteString())
            }
        }

        document.edit {
            provisioned = true
        }

        return document
    }

    private fun buildCreateKeySettings(userAuthRequired: Boolean): CreateKeySettings {
        return try {
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
    }

    private fun deriveDocumentDisplayName(
        documentType: DocumentType,
        mdocDataElements: Map<Pair<String, String>, DataItem>,
        jsonClaims: JsonObject
    ): String {
        val givenName = mdocDataElements.entries
            .firstOrNull { it.key.second == "given_name" && it.value is Tstr }
            ?.value?.asTstr?.trim()
            ?: (jsonClaims["given_name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        val familyName = mdocDataElements.entries
            .firstOrNull { it.key.second == "family_name" && it.value is Tstr }
            ?.value?.asTstr?.trim()
            ?: (jsonClaims["family_name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        val residentName = mdocDataElements.entries
            .firstOrNull { it.key.second == "resident_name" && it.value is Tstr }
            ?.value?.asTstr?.trim()

        val fullName = listOfNotNull(
            givenName?.takeIf { it.isNotEmpty() },
            familyName?.takeIf { it.isNotEmpty() }
        ).joinToString(" ")

        return when {
            fullName.isNotEmpty() -> fullName
            !residentName.isNullOrEmpty() -> residentName
            else -> documentType.displayName
        }
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
