package org.multipaz.wallet.android.ui.settings

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.toDataItem
import org.multipaz.cbor.toDataItemFullDate
import org.multipaz.cose.Cose
import org.multipaz.crypto.Algorithm
import org.multipaz.document.DocumentStore
import org.multipaz.document.buildDocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.ISO_23220_5_CHV_1_DATA_ELEMENT
import org.multipaz.documenttype.ISO_23220_5_CHV_1_NAMESPACE
import org.multipaz.documenttype.knowntypes.Aadhaar
import org.multipaz.documenttype.knowntypes.AgeVerification
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.documenttype.knowntypes.addKnownTypes
import org.multipaz.mdoc.credential.KeyBoundMdocCredential
import org.multipaz.mdoc.credential.KeylessMdocCredential
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.sdjwt.credential.KeylessSdJwtVcCredential
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.toBase64Url
import org.multipaz.utopia.knowntypes.UtopiaMovieTicket
import org.multipaz.utopia.knowntypes.UtopiaNaturalization
import org.multipaz.utopia.knowntypes.addUtopiaTypes
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.shared.Domains

class DeveloperSelfSignedDocumentIssuerTest {

    private data class TestEnv(
        val documentStore: DocumentStore,
        val softwareSecureArea: SoftwareSecureArea,
        val userIssuerTrustManager: TrustManager,
        val settingsModel: SettingsModel,
        val documentTypeRepository: DocumentTypeRepository
    )

    private suspend fun createTestEnv(): TestEnv {
        val storage = EphemeralStorage()
        val softwareSecureArea = SoftwareSecureArea.create(storage)
        val secureAreaRepository = SecureAreaRepository.Builder()
            .add(softwareSecureArea)
            .build()
        val documentStore = buildDocumentStore(
            storage = storage,
            secureAreaRepository = secureAreaRepository
        ) {}
        val userIssuerTrustManager = TrustManager(
            storage = storage,
            identifier = "userIssuerTrustManager"
        )
        val settingsModel = SettingsModel.create(storage)
        val documentTypeRepository = DocumentTypeRepository().apply {
            addKnownTypes()
            addUtopiaTypes()
        }
        return TestEnv(
            documentStore = documentStore,
            softwareSecureArea = softwareSecureArea,
            userIssuerTrustManager = userIssuerTrustManager,
            settingsModel = settingsModel,
            documentTypeRepository = documentTypeRepository
        )
    }

    @Test
    fun testIssuePhotoIdWithCustomMdocDataElements(): Unit = runBlocking {
        val env = createTestEnv()
        val photoIdDocType = PhotoID.getDocumentType()

        val testPortraitBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val testDob = LocalDate(1995, 5, 20)
        val testIssueDate = LocalDate(2025, 6, 1)
        val testExpiryDate = LocalDate(2030, 6, 1)

        val customElements = DeveloperSelfSignedDocumentIssuer
            .buildDefaultMdocDataElements(photoIdDocType, testIssueDate)
            .toMutableMap()
        val nsIso = PhotoID.ISO_23220_2_NAMESPACE
        val nsPhotoId = PhotoID.PHOTO_ID_NAMESPACE
        // Default holder age for PhotoID is 20 years old
        assertEquals(20L, customElements[Pair(nsIso, "age_in_years")]?.asNumber)
        assertEquals(true, customElements[Pair(nsIso, "age_over_18")]?.asBoolean)
        assertEquals(false, customElements[Pair(nsIso, "age_over_21")]?.asBoolean)
        assertEquals(
            LocalDate(testIssueDate.year - 20, testIssueDate.month, testIssueDate.day),
            customElements[Pair(nsIso, "birth_date")]!!["birth_date"].asDateString
        )

        customElements[Pair(nsIso, "given_name")] = "Jane".toDataItem()
        customElements[Pair(nsIso, "family_name")] = "Doe".toDataItem()
        customElements[Pair(nsIso, "birth_date")] = buildCborMap {
            put("birth_date", testDob.toDataItemFullDate())
        }
        customElements[Pair(nsIso, "sex")] = 2L.toDataItem()
        customElements[Pair(nsIso, "nationality")] = "CA".toDataItem()
        customElements[Pair(nsIso, "document_number")] = "D12345678".toDataItem()
        customElements[Pair(nsIso, "issuing_authority")] = "Custom Issuing Authority".toDataItem()
        customElements[Pair(nsIso, "issuing_country")] = "CA".toDataItem()
        customElements[Pair(nsPhotoId, "person_id")] = "P-98765".toDataItem()

        val document = DeveloperSelfSignedDocumentIssuer.issueSelfSignedDocument(
            documentStore = env.documentStore,
            secureArea = env.softwareSecureArea,
            userIssuerTrustManager = env.userIssuerTrustManager,
            settingsModel = env.settingsModel,
            documentType = photoIdDocType,
            portraitBytes = testPortraitBytes,
            mdocDataElements = customElements
        )

        assertEquals("Jane Doe", document.displayName)
        assertEquals(photoIdDocType.displayName, document.typeDisplayName)
        assertTrue(document.provisioned)

        val trustPoints = env.userIssuerTrustManager.getTrustPoints()
        val iacaCert = DeveloperSelfSignedDocumentIssuer.getIacaKey().certChain.certificates.first()
        assertTrue(trustPoints.any { it.certificate == iacaCert })

        val mdocCredential = document
            .getCertifiedCredentialsForDomain(Domains.DOMAIN_MDOC_USER_AUTH)
            .first() as KeyBoundMdocCredential

        val nsData = mdocCredential.issuerNamespaces.data[nsIso]!!
        val dataElementMap = nsData.mapValues { it.value.dataElementValue }

        assertEquals("Jane", dataElementMap["given_name"]?.asTstr)
        assertEquals("Doe", dataElementMap["family_name"]?.asTstr)
        assertEquals(testDob, dataElementMap["birth_date"]!!["birth_date"].asDateString)
        assertEquals(testPortraitBytes.toList(), dataElementMap["portrait"]?.asBstr?.toList())
        assertEquals(testIssueDate, dataElementMap["issue_date"]?.asDateString)
        assertEquals(testExpiryDate, dataElementMap["expiry_date"]?.asDateString)
        assertEquals("Custom Issuing Authority", dataElementMap["issuing_authority"]?.asTstr)
        assertEquals("CA", dataElementMap["issuing_country"]?.asTstr)

        val photoIdDataMap = mdocCredential.issuerNamespaces.data[nsPhotoId]!!
            .mapValues { it.value.dataElementValue }
        assertEquals("P-98765", photoIdDataMap["person_id"]?.asTstr)

        // Verify un-sampled dg4..dg16 and extra age_over_NN are excluded by default
        assertNull(dataElementMap["age_over_19"])

        val dsCert = mdocCredential.issuerCertChain.certificates.first()
        dsCert.verify(iacaCert.ecPublicKey)

        val mso = mdocCredential.mso
        assertEquals(PhotoID.PHOTO_ID_DOCTYPE, mso.docType)
        assertEquals(emptyList<String>(), mso.deviceKeyAuthorizedNamespaces)
        assertEquals(
            mapOf(ISO_23220_5_CHV_1_NAMESPACE to listOf(ISO_23220_5_CHV_1_DATA_ELEMENT)),
            mso.deviceKeyAuthorizedDataElements
        )

        Cose.coseSign1Check(
            publicKey = dsCert.ecPublicKey,
            detachedData = null,
            signature = mdocCredential.issuerAuth,
            signatureAlgorithm = Algorithm.ES256
        )

        val claims = mdocCredential.getClaims(env.documentTypeRepository)
        assertTrue(claims.isNotEmpty())
    }

    @Test
    fun testIssueDrivingLicenseAndAadhaarWithPortraitEquivalence(): Unit = runBlocking {
        val env = createTestEnv()
        val testPortraitBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        // DrivingLicense (mDL)
        val mdlDocType = DrivingLicense.getDocumentType()
        val mdlDoc = DeveloperSelfSignedDocumentIssuer.issueSelfSignedDocument(
            documentStore = env.documentStore,
            secureArea = env.softwareSecureArea,
            userIssuerTrustManager = env.userIssuerTrustManager,
            settingsModel = env.settingsModel,
            documentType = mdlDocType,
            portraitBytes = testPortraitBytes
        )
        val mdlCred = mdlDoc
            .getCertifiedCredentialsForDomain(Domains.DOMAIN_MDOC_USER_AUTH)
            .first() as MdocCredential
        assertEquals(DrivingLicense.MDL_DOCTYPE, mdlCred.mso.docType)
        val mdlNsData = mdlCred.issuerNamespaces.data[DrivingLicense.MDL_NAMESPACE]!!
            .mapValues { it.value.dataElementValue }
        assertEquals(testPortraitBytes.toList(), mdlNsData["portrait"]?.asBstr?.toList())
        assertEquals(20L, mdlNsData["age_in_years"]?.asNumber)
        assertEquals(true, mdlNsData["age_over_18"]?.asBoolean)
        assertEquals(false, mdlNsData["age_over_21"]?.asBoolean)
        // Verify Simple.NULL biometric templates were excluded by default
        assertNull(mdlNsData["biometric_template_face"])

        // Aadhaar (uses resident_image as supportsPortraitImageEquivalence target and resident_name)
        val aadhaarDocType = Aadhaar.getDocumentType()
        val aadhaarDoc = DeveloperSelfSignedDocumentIssuer.issueSelfSignedDocument(
            documentStore = env.documentStore,
            secureArea = env.softwareSecureArea,
            userIssuerTrustManager = env.userIssuerTrustManager,
            settingsModel = env.settingsModel,
            documentType = aadhaarDocType,
            portraitBytes = testPortraitBytes
        )
        assertEquals("Erika", aadhaarDoc.displayName)
        val aadhaarCred = aadhaarDoc
            .getCertifiedCredentialsForDomain(Domains.DOMAIN_MDOC_USER_AUTH)
            .first() as MdocCredential
        val aadhaarNsData = aadhaarCred.issuerNamespaces.data[Aadhaar.AADHAAR_NAMESPACE]!!
            .mapValues { it.value.dataElementValue }
        assertEquals(testPortraitBytes.toList(), aadhaarNsData["resident_image"]?.asBstr?.toList())
    }

    @Test
    fun testIssueMdocWithoutPortraitEquivalence(): Unit = runBlocking {
        val env = createTestEnv()
        val ageVerificationDocType = AgeVerification.getDocumentType()
        assertNull(ageVerificationDocType.mdocDocumentType?.supportsPortraitImageEquivalence)

        val document = DeveloperSelfSignedDocumentIssuer.issueSelfSignedDocument(
            documentStore = env.documentStore,
            secureArea = env.softwareSecureArea,
            userIssuerTrustManager = env.userIssuerTrustManager,
            settingsModel = env.settingsModel,
            documentType = ageVerificationDocType,
            portraitBytes = null
        )

        assertEquals(ageVerificationDocType.displayName, document.displayName)
        val mdocCred = document
            .getCertifiedCredentialsForDomain(Domains.DOMAIN_MDOC_USER_AUTH)
            .first() as MdocCredential
        assertEquals(AgeVerification.AV_DOCTYPE, mdocCred.mso.docType)
        assertEquals(emptyMap<String, List<String>>(), mdocCred.mso.deviceKeyAuthorizedDataElements)
        val avData = mdocCred.issuerNamespaces.data[AgeVerification.AV_NAMESPACE]!!
            .mapValues { it.value.dataElementValue }
        assertEquals(true, avData["age_over_18"]?.asBoolean)
    }

    @Test
    fun testIssueDualMdocAndSdJwtVcDocument(): Unit = runBlocking {
        val env = createTestEnv()
        val euPidDocType = EUPersonalID.getDocumentType()
        val testPortraitBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03)

        val document = DeveloperSelfSignedDocumentIssuer.issueSelfSignedDocument(
            documentStore = env.documentStore,
            secureArea = env.softwareSecureArea,
            userIssuerTrustManager = env.userIssuerTrustManager,
            settingsModel = env.settingsModel,
            documentType = euPidDocType,
            portraitBytes = testPortraitBytes
        )

        // Verify mdoc credential was minted
        val mdocCred = document
            .getCertifiedCredentialsForDomain(Domains.DOMAIN_MDOC_USER_AUTH)
            .firstOrNull() as? MdocCredential
        assertNotNull(mdocCred)
        assertEquals(EUPersonalID.EUPID_DOCTYPE, mdocCred!!.mso.docType)
        val euMdocData = mdocCred.issuerNamespaces.data[EUPersonalID.EUPID_NAMESPACE]!!
            .mapValues { it.value.dataElementValue }
        assertEquals("Erika", euMdocData["given_name"]?.asTstr)
        assertEquals("Mustermann", euMdocData["family_name"]?.asTstr)
        assertEquals(testPortraitBytes.toList(), euMdocData["portrait"]?.asBstr?.toList())
        assertEquals("ZZ", euMdocData["place_of_birth"]?.get("country")?.asTstr)
        assertEquals("Sample State", euMdocData["place_of_birth"]?.get("region")?.asTstr)
        assertEquals("Sample City", euMdocData["place_of_birth"]?.get("locality")?.asTstr)

        // Verify KeyBoundSdJwtVcCredential was also minted
        val sdJwtCred = document
            .getCertifiedCredentialsForDomain(Domains.DOMAIN_SDJWT_USER_AUTH)
            .firstOrNull() as? KeyBoundSdJwtVcCredential
        assertNotNull(sdJwtCred)
        assertEquals(EUPersonalID.EUPID_VCT, sdJwtCred!!.vct)

        val sdJwt = SdJwt.fromCompactSerialization(sdJwtCred.issuerProvidedData.decodeToString())
        val dsCert = sdJwt.x5c!!.certificates.first()
        val iacaCert = DeveloperSelfSignedDocumentIssuer.getIacaKey().certChain.certificates.first()
        dsCert.verify(iacaCert.ecPublicKey)

        val processedPayload = sdJwt.verify(dsCert.ecPublicKey)
        assertEquals("Erika", processedPayload["given_name"]?.jsonPrimitive?.content)
        assertEquals("Mustermann", processedPayload["family_name"]?.jsonPrimitive?.content)
        assertEquals(
            testPortraitBytes.toBase64Url(),
            processedPayload["picture"]?.jsonPrimitive?.content
        )
        assertEquals(
            "ZZ",
            processedPayload["place_of_birth"]?.jsonObject?.get("country")?.jsonPrimitive?.content
        )
        assertEquals(
            "Sample State",
            processedPayload["place_of_birth"]?.jsonObject?.get("region")?.jsonPrimitive?.content
        )
        assertEquals(
            "Sample City",
            processedPayload["place_of_birth"]?.jsonObject?.get("locality")?.jsonPrimitive?.content
        )
        val validation = sdJwtCred.validate(sdJwtCred.issuerProvidedData)
        assertFalse("Validation errors: ${validation.errors}", validation.hasErrors)
    }

    @Test
    fun testIssueStandaloneKeyBoundAndKeylessSdJwtVc(): Unit = runBlocking {
        val env = createTestEnv()

        // 1. Key-bound SD-JWT VC (UtopiaNaturalization)
        val natDocType = UtopiaNaturalization.getDocumentType()
        val customNatClaims = buildJsonObject {
            put("given_name", JsonPrimitive("Alice"))
            put("family_name", JsonPrimitive("Wonderland"))
            put("birth_date", JsonPrimitive("1990-01-15"))
            put("naturalization_date", JsonPrimitive("2020-07-04"))
        }
        val natDoc = DeveloperSelfSignedDocumentIssuer.issueSelfSignedDocument(
            documentStore = env.documentStore,
            secureArea = env.softwareSecureArea,
            userIssuerTrustManager = env.userIssuerTrustManager,
            settingsModel = env.settingsModel,
            documentType = natDocType,
            jsonClaims = customNatClaims
        )
        assertEquals("Alice Wonderland", natDoc.displayName)
        val natCred = natDoc
            .getCertifiedCredentialsForDomain(Domains.DOMAIN_SDJWT_USER_AUTH)
            .first() as KeyBoundSdJwtVcCredential
        assertEquals(UtopiaNaturalization.VCT, natCred.vct)
        val natClaims = natCred.getClaims(env.documentTypeRepository)
        assertTrue(natClaims.any { it.attribute?.identifier == "given_name" })

        // 2. Keyless mdoc and SD-JWT VC (UtopiaMovieTicket)
        val movieDocType = UtopiaMovieTicket.getDocumentType()
        val movieDoc = DeveloperSelfSignedDocumentIssuer.issueSelfSignedDocument(
            documentStore = env.documentStore,
            secureArea = env.softwareSecureArea,
            userIssuerTrustManager = env.userIssuerTrustManager,
            settingsModel = env.settingsModel,
            documentType = movieDocType
        )
        assertEquals(movieDocType.displayName, movieDoc.displayName)
        val movieMdocCred = movieDoc
            .getCertifiedCredentialsForDomain(Domains.DOMAIN_MDOC_KEYLESS)
            .first() as KeylessMdocCredential
        assertEquals(UtopiaMovieTicket.MOVIE_TICKET_DOCTYPE, movieMdocCred.docType)
        assertNull(movieMdocCred.mso.deviceKey)
        val movieMdocValidation = movieMdocCred.validate(movieMdocCred.issuerProvidedData)
        assertFalse("Mdoc validation errors: ${movieMdocValidation.errors}", movieMdocValidation.hasErrors)

        val movieCred = movieDoc
            .getCertifiedCredentialsForDomain(Domains.DOMAIN_SDJWT_KEYLESS)
            .first() as KeylessSdJwtVcCredential
        assertEquals(UtopiaMovieTicket.MOVIE_TICKET_VCT, movieCred.vct)
        val movieSdJwt = SdJwt.fromCompactSerialization(movieCred.issuerProvidedData.decodeToString())
        assertNull(movieSdJwt.kbKey)
        val movieValidation = movieCred.validate(movieCred.issuerProvidedData)
        assertFalse("Validation errors: ${movieValidation.errors}", movieValidation.hasErrors)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConflictingKeyAuthorizationsThrows(): Unit = runBlocking {
        val env = createTestEnv()
        val photoIdDocType = PhotoID.getDocumentType()
        val testPortraitBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())

        DeveloperSelfSignedDocumentIssuer.issueSelfSignedDocument(
            documentStore = env.documentStore,
            secureArea = env.softwareSecureArea,
            userIssuerTrustManager = env.userIssuerTrustManager,
            settingsModel = env.settingsModel,
            documentType = photoIdDocType,
            portraitBytes = testPortraitBytes,
            authorizedNamespaces = listOf("conflict.namespace"),
            authorizedDataElements = mapOf("conflict.namespace" to listOf("some_elem"))
        )
    }
}
