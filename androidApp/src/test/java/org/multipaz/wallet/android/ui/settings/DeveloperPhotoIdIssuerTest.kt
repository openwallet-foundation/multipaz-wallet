package org.multipaz.wallet.android.ui.settings

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.multipaz.cose.Cose
import org.multipaz.crypto.Algorithm
import org.multipaz.document.buildDocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.documenttype.knowntypes.addKnownTypes
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.shared.Domains

class DeveloperPhotoIdIssuerTest {

    @Test
    fun testIssuePhotoIdContainsMandatoryDataElementsAndValidCertificates(): Unit = runBlocking {
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

        val testPortraitBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val testDob = LocalDate(1995, 5, 20)
        val testGivenName = "Jane"
        val testFamilyName = "Doe"
        val testSex = 2L
        val testNationality = "CA"
        val testDocNumber = "D12345678"
        val testIssueDate = LocalDate(2025, 6, 1)
        val testExpiryDate = LocalDate(2030, 6, 1)
        val testIssuingAuthority = "Custom Issuing Authority"
        val testIssuingCountry = "CA"
        val testResidentStreet = "742 Evergreen Terrace"
        val testResidentHouseNumber = "742"
        val testResidentCity = "Springfield"
        val testResidentState = "OR"
        val testResidentPostalCode = "97477"
        val testResidentCountry = "US"
        val testResidentAddress = "$testResidentHouseNumber $testResidentStreet, $testResidentCity, $testResidentState $testResidentPostalCode"
        val testPersonId = "P-98765"
        val testAdminNumber = "ADM-4321"

        val document = DeveloperPhotoIdIssuer.issuePhotoId(
            documentStore = documentStore,
            secureArea = softwareSecureArea,
            userIssuerTrustManager = userIssuerTrustManager,
            settingsModel = settingsModel,
            givenName = testGivenName,
            familyName = testFamilyName,
            dateOfBirth = testDob,
            portraitBytes = testPortraitBytes,
            sex = testSex,
            nationality = testNationality,
            documentNumber = testDocNumber,
            issueDate = testIssueDate,
            expiryDate = testExpiryDate,
            issuingAuthority = testIssuingAuthority,
            issuingCountry = testIssuingCountry,
            residentStreet = testResidentStreet,
            residentHouseNumber = testResidentHouseNumber,
            residentCity = testResidentCity,
            residentState = testResidentState,
            residentPostalCode = testResidentPostalCode,
            residentCountry = testResidentCountry,
            residentAddress = testResidentAddress,
            personId = testPersonId,
            administrativeNumber = testAdminNumber
        )

        assertEquals("Jane Doe", document.displayName)
        assertEquals("Photo ID", document.typeDisplayName)
        assertTrue(document.provisioned)

        // Verify document is in document store
        val storedDoc = documentStore.lookupDocument(document.identifier)
        assertNotNull(storedDoc)

        // Verify userIssuerTrustManager has the IACA certificate registered
        val trustPoints = userIssuerTrustManager.getTrustPoints()
        val iacaCert = DeveloperPhotoIdIssuer.getIacaKey().certChain.certificates.first()
        assertTrue(trustPoints.any { it.certificate == iacaCert })

        // Find the user_auth credential
        val credential = document.getCertifiedCredentialsForDomain(Domains.DOMAIN_MDOC_USER_AUTH).firstOrNull()
        assertNotNull(credential)
        assertTrue(credential is MdocCredential)
        val mdocCredential = credential as MdocCredential

        val ns = PhotoID.ISO_23220_2_NAMESPACE
        val nsData = mdocCredential.issuerNamespaces.data[ns]
        assertNotNull(nsData)

        // Verify all mandatory and requested data elements
        val dataElementMap = nsData!!.mapValues { it.value.dataElementValue }

        // 1. family_name (mandatory)
        assertEquals(testFamilyName, dataElementMap["family_name"]?.asTstr)
        // 2. given_name (mandatory)
        assertEquals(testGivenName, dataElementMap["given_name"]?.asTstr)
        // 3. birth_date (mandatory) - map containing "birth_date"
        val birthDateItem = dataElementMap["birth_date"]
        assertNotNull(birthDateItem)
        assertEquals(testDob, birthDateItem!!["birth_date"].asDateString)
        // 4. portrait (mandatory)
        assertEquals(testPortraitBytes.toList(), dataElementMap["portrait"]?.asBstr?.toList())
        // 5. issue_date (mandatory)
        assertEquals(testIssueDate, dataElementMap["issue_date"]?.asDateString)
        // 6. expiry_date (mandatory)
        assertEquals(testExpiryDate, dataElementMap["expiry_date"]?.asDateString)
        // 7. issuing_authority (mandatory)
        assertEquals(testIssuingAuthority, dataElementMap["issuing_authority"]?.asTstr)
        // 8. issuing_country (mandatory)
        assertEquals(testIssuingCountry, dataElementMap["issuing_country"]?.asTstr)
        // 9. age_over_18 (mandatory)
        assertEquals(true, dataElementMap["age_over_18"]?.asBoolean)

        // Additional requested elements in ISO_23220_2_NAMESPACE
        assertEquals(testSex, dataElementMap["sex"]?.asNumber)
        assertEquals(testNationality, dataElementMap["nationality"]?.asTstr)
        assertEquals(testDocNumber, dataElementMap["document_number"]?.asTstr)
        assertEquals(testResidentAddress, dataElementMap["resident_address"]?.asTstr)
        assertEquals(testResidentCity, dataElementMap["resident_city"]?.asTstr)
        assertEquals(testResidentPostalCode, dataElementMap["resident_postal_code"]?.asTstr)
        assertEquals(testResidentCountry, dataElementMap["resident_country"]?.asTstr)

        // Additional attributes
        assertNotNull(dataElementMap["age_in_years"]?.asNumber)
        assertEquals(1995L, dataElementMap["age_birth_year"]?.asNumber)
        assertEquals(testIssueDate, dataElementMap["portrait_capture_date"]?.asDateString)

        // Verify PHOTO_ID_NAMESPACE data elements
        val photoIdNs = PhotoID.PHOTO_ID_NAMESPACE
        val photoIdNsData = mdocCredential.issuerNamespaces.data[photoIdNs]
        assertNotNull(photoIdNsData)
        val photoIdDataMap = photoIdNsData!!.mapValues { it.value.dataElementValue }
        assertEquals(testResidentStreet, photoIdDataMap["resident_street"]?.asTstr)
        assertEquals(testResidentHouseNumber, photoIdDataMap["resident_house_number"]?.asTstr)
        assertEquals(testResidentState, photoIdDataMap["resident_state"]?.asTstr)
        assertEquals(testPersonId, photoIdDataMap["person_id"]?.asTstr)
        assertEquals(testAdminNumber, photoIdDataMap["administrative_number"]?.asTstr)

        // Verify unrequested sample data was NOT included
        assertNull(dataElementMap["birthplace"])
        assertNull(dataElementMap["resident_city_latin1"])
        assertNull(photoIdDataMap["travel_document_type"])
        assertNull(photoIdDataMap["birth_country"])

        // Verify DS Certificate and chain
        val dsCert = mdocCredential.issuerCertChain.certificates.first()
        assertNotNull(dsCert)

        // Verify DS certificate is signed by the hard-coded IACA
        dsCert.verify(iacaCert.ecPublicKey)

        // Verify MSO
        val mso = mdocCredential.mso
        assertEquals(PhotoID.PHOTO_ID_DOCTYPE, mso.docType)

        // Verify default Key Authorizations
        assertEquals(emptyList<String>(), mso.deviceKeyAuthorizedNamespaces)
        assertEquals(
            mapOf("org.iso.23220.5.1" to listOf("CHV_1")),
            mso.deviceKeyAuthorizedDataElements
        )

        // Verify MSO signature
        Cose.coseSign1Check(
            publicKey = dsCert.ecPublicKey,
            detachedData = null,
            signature = mdocCredential.issuerAuth,
            signatureAlgorithm = Algorithm.ES256
        )

        val docTypeRepo = DocumentTypeRepository().apply {
            addKnownTypes()
        }
        val claims = mdocCredential.getClaims(docTypeRepo)
        println("CLAIMS_COUNT: ${claims.size}")
        for (c in claims) {
            println("CLAIM: ${c.dataElementName} -> ${c.displayName}: ${c.render(kotlinx.datetime.TimeZone.currentSystemDefault())}")
        }
        assertTrue(claims.isNotEmpty())
    }

    @Test
    fun testIssuePhotoIdCustomKeyAuthorizations(): Unit = runBlocking {
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

        val testPortraitBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val testDob = LocalDate(2000, 1, 1)

        val customNamespaces = listOf("org.custom.namespace.a", "org.custom.namespace.b")
        val customDataElements = mapOf(
            "org.iso.23220.5.1" to listOf("CHV_1", "CHV_2"),
            "org.custom.other" to listOf("elem1")
        )

        val document = DeveloperPhotoIdIssuer.issuePhotoId(
            documentStore = documentStore,
            secureArea = softwareSecureArea,
            userIssuerTrustManager = userIssuerTrustManager,
            settingsModel = settingsModel,
            givenName = "Alice",
            familyName = "Smith",
            dateOfBirth = testDob,
            portraitBytes = testPortraitBytes,
            authorizedNamespaces = customNamespaces,
            authorizedDataElements = customDataElements
        )

        val credential = document.getCertifiedCredentialsForDomain(Domains.DOMAIN_MDOC_USER_AUTH).first() as MdocCredential
        val mso = credential.mso
        assertEquals(customNamespaces, mso.deviceKeyAuthorizedNamespaces)
        assertEquals(customDataElements, mso.deviceKeyAuthorizedDataElements)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIssuePhotoIdConflictingKeyAuthorizationsThrows(): Unit = runBlocking {
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

        val testPortraitBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val testDob = LocalDate(2000, 1, 1)

        DeveloperPhotoIdIssuer.issuePhotoId(
            documentStore = documentStore,
            secureArea = softwareSecureArea,
            userIssuerTrustManager = userIssuerTrustManager,
            settingsModel = settingsModel,
            givenName = "Alice",
            familyName = "Smith",
            dateOfBirth = testDob,
            portraitBytes = testPortraitBytes,
            authorizedNamespaces = listOf("conflict.namespace"),
            authorizedDataElements = mapOf("conflict.namespace" to listOf("some_elem"))
        )
    }

    @Test
    fun testIssuePhotoIdDefaultValues(): Unit = runBlocking {
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

        val testPortraitBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val testDob = LocalDate(2000, 1, 1)

        val document = DeveloperPhotoIdIssuer.issuePhotoId(
            documentStore = documentStore,
            secureArea = softwareSecureArea,
            userIssuerTrustManager = userIssuerTrustManager,
            settingsModel = settingsModel,
            givenName = "Bob",
            familyName = "Smith",
            dateOfBirth = testDob,
            portraitBytes = testPortraitBytes
        )

        val credential = document.getCertifiedCredentialsForDomain(Domains.DOMAIN_MDOC_USER_AUTH).first() as MdocCredential
        val ns = PhotoID.ISO_23220_2_NAMESPACE
        val nsData = credential.issuerNamespaces.data[ns]!!
        val dataElementMap = nsData.mapValues { it.value.dataElementValue }
        assertEquals("Multipaz Wallet TEST In-App Issuing Authority", dataElementMap["issuing_authority"]?.asTstr)
        assertEquals("ZZ", dataElementMap["issuing_country"]?.asTstr)
    }
}
