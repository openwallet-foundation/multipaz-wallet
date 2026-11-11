package org.multipaz.wallet.android.settings

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.Logger
import org.multipaz.wallet.client.verification.IdentificationQuery
import org.multipaz.wallet.client.verification.UserDefinedQuery

class SettingsModelTest {
    @Test
    fun testLoggingDebugEnabledDefaultsToFalse() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel = SettingsModel.create(storage)

        assertFalse(settingsModel.loggingDebugEnabled.value)
        assertFalse(Logger.isDebugEnabled)
    }

    @Test
    fun testLoggingDebugEnabledUpdatesLogger() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel = SettingsModel.create(storage)

        settingsModel.loggingDebugEnabled.value = true
        // Allow flow collection to run
        delay(100)
        assertTrue(settingsModel.loggingDebugEnabled.value)
        assertTrue(Logger.isDebugEnabled)

        settingsModel.loggingDebugEnabled.value = false
        delay(100)
        assertFalse(settingsModel.loggingDebugEnabled.value)
        assertFalse(Logger.isDebugEnabled)
    }

    @Test
    fun testLoggingDebugEnabledPersistsAcrossInstances() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel1 = SettingsModel.create(storage)

        settingsModel1.loggingDebugEnabled.value = true
        delay(100)
        assertTrue(Logger.isDebugEnabled)

        val settingsModel2 = SettingsModel.create(storage)
        assertTrue(settingsModel2.loggingDebugEnabled.value)
        assertTrue(Logger.isDebugEnabled)
    }

    @Test
    fun testPreconsentForNewDocumentsDefaultsToTrue() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel = SettingsModel.create(storage)

        assertTrue(settingsModel.preconsentForNewDocuments.value)
    }

    @Test
    fun testPreconsentForNewDocumentsPersistsAcrossInstances() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel1 = SettingsModel.create(storage)

        settingsModel1.preconsentForNewDocuments.value = false
        delay(100)
        assertFalse(settingsModel1.preconsentForNewDocuments.value)

        val settingsModel2 = SettingsModel.create(storage)
        assertFalse(settingsModel2.preconsentForNewDocuments.value)
    }

    @Test
    fun testHasAdvancedVerificationSettings() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel = SettingsModel.create(storage)

        assertFalse(settingsModel.hasAdvancedVerificationSettings())

        // Test issuer identifiers
        settingsModel.verificationIssuerIdentifiers.value = listOf(kotlinx.io.bytestring.ByteString(1, 2, 3))
        assertTrue(settingsModel.hasAdvancedVerificationSettings())

        settingsModel.verificationIssuerIdentifiers.value = emptyList()
        assertFalse(settingsModel.hasAdvancedVerificationSettings())

        // Test reader authentication key/cert
        val ecPrivateKey = org.multipaz.crypto.Crypto.createEcPrivateKey(org.multipaz.crypto.EcCurve.P256)
        val cert = org.multipaz.crypto.buildX509Cert(
            publicKey = ecPrivateKey.publicKey,
            signingKey = org.multipaz.crypto.AsymmetricKey.anonymous(ecPrivateKey),
            serialNumber = org.multipaz.asn1.ASN1Integer(1),
            subject = org.multipaz.crypto.X500Name.fromName("CN=Test"),
            issuer = org.multipaz.crypto.X500Name.fromName("CN=Test"),
            validFrom = kotlin.time.Instant.fromEpochMilliseconds(0),
            validUntil = kotlin.time.Instant.fromEpochMilliseconds(1000000000000)
        ) {}
        val certChain = org.multipaz.crypto.X509CertChain(listOf(cert))

        settingsModel.customVerificationReaderKey.value = ecPrivateKey
        settingsModel.customVerificationReaderCertChain.value = certChain
        assertTrue(settingsModel.hasAdvancedVerificationSettings())

        settingsModel.customVerificationReaderKey.value = null
        assertFalse(settingsModel.hasAdvancedVerificationSettings())

        // Test useNfcV2
        settingsModel.useNfcV2.value = false
        assertTrue(settingsModel.hasAdvancedVerificationSettings())

        settingsModel.useNfcV2.value = true
        assertFalse(settingsModel.hasAdvancedVerificationSettings())
    }

    @Test
    fun testReaderAuthenticationPersistence() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel1 = SettingsModel.create(storage)

        val ecPrivateKey = org.multipaz.crypto.Crypto.createEcPrivateKey(org.multipaz.crypto.EcCurve.P256)
        val cert = org.multipaz.crypto.buildX509Cert(
            publicKey = ecPrivateKey.publicKey,
            signingKey = org.multipaz.crypto.AsymmetricKey.anonymous(ecPrivateKey),
            serialNumber = org.multipaz.asn1.ASN1Integer(1),
            subject = org.multipaz.crypto.X500Name.fromName("CN=Test"),
            issuer = org.multipaz.crypto.X500Name.fromName("CN=Test"),
            validFrom = kotlin.time.Instant.fromEpochMilliseconds(0),
            validUntil = kotlin.time.Instant.fromEpochMilliseconds(1000000000000)
        ) {}
        val certChain = org.multipaz.crypto.X509CertChain(listOf(cert))

        settingsModel1.customVerificationReaderKey.value = ecPrivateKey
        settingsModel1.customVerificationReaderCertChain.value = certChain
        delay(100)

        val settingsModel2 = SettingsModel.create(storage)
        assertEquals(ecPrivateKey.curve, settingsModel2.customVerificationReaderKey.value?.curve)
        assertEquals(certChain.certificates.size, settingsModel2.customVerificationReaderCertChain.value?.certificates?.size)
        assertEquals(certChain.certificates[0].subject.name, settingsModel2.customVerificationReaderCertChain.value?.certificates?.get(0)?.subject?.name)
    }

    @Test
    fun testPresentmentFaceMatcherDefaultsToMobileFaceNet() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel = SettingsModel.create(storage)

        assertEquals("MobileFaceNet", settingsModel.presentmentFaceMatcher.value)
    }

    @Test
    fun testPresentmentFaceMatcherPersistsAcrossInstances() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel1 = SettingsModel.create(storage)

        settingsModel1.presentmentFaceMatcher.value = "MobileFaceNet (debug)"
        delay(100)
        assertEquals("MobileFaceNet (debug)", settingsModel1.presentmentFaceMatcher.value)

        val settingsModel2 = SettingsModel.create(storage)
        assertEquals("MobileFaceNet (debug)", settingsModel2.presentmentFaceMatcher.value)
    }

    private val residenceQuery = UserDefinedQuery(
        docType = "jp.go.juminhyo.1",
        namespaces = mapOf("jp.go.juminhyo.1" to listOf("family_name", "given_name")),
        name = "Residence record"
    )
    private val disasterQuery = UserDefinedQuery(
        docType = "jp.go.disaster.1",
        namespaces = mapOf("jp.go.disaster.1" to listOf("damage_level")),
        name = "Disaster victim certificate"
    )

    @Test
    fun testUserDefinedQueriesPersistAcrossInstances() = runBlocking {
        val storage = EphemeralStorage()
        val settingsModel1 = SettingsModel.create(storage)
        assertEquals(emptyList<UserDefinedQuery>(), settingsModel1.userDefinedQueries.value)
        // User defined queries can only be selected in developer mode.
        settingsModel1.devMode.value = true

        settingsModel1.saveUserDefinedQuery(null, residenceQuery)
        settingsModel1.saveUserDefinedQuery(null, disasterQuery)
        delay(100)

        val settingsModel2 = SettingsModel.create(storage)
        assertEquals(listOf(residenceQuery, disasterQuery), settingsModel2.userDefinedQueries.value)
        assertEquals(disasterQuery, settingsModel2.readerQuery.value)
    }

    @Test
    fun testSaveUserDefinedQueryReplacesAndSelects() = runBlocking {
        val settingsModel = SettingsModel.create(EphemeralStorage())
        settingsModel.devMode.value = true
        settingsModel.saveUserDefinedQuery(null, residenceQuery)
        settingsModel.saveUserDefinedQuery(null, disasterQuery)

        val edited = residenceQuery.copy(name = "Edited")
        settingsModel.saveUserDefinedQuery(0, edited)
        assertEquals(listOf(edited, disasterQuery), settingsModel.userDefinedQueries.value)
        assertEquals(edited, settingsModel.readerQuery.value)
    }

    @Test
    fun testDeleteUserDefinedQuery() = runBlocking {
        val settingsModel = SettingsModel.create(EphemeralStorage())
        settingsModel.devMode.value = true
        settingsModel.saveUserDefinedQuery(null, residenceQuery)
        settingsModel.saveUserDefinedQuery(null, disasterQuery)

        // Deleting a query that is not selected keeps the selection.
        settingsModel.deleteUserDefinedQuery(0)
        assertEquals(listOf(disasterQuery), settingsModel.userDefinedQueries.value)
        assertEquals(disasterQuery, settingsModel.readerQuery.value)

        // Deleting the selected query falls back to identification.
        settingsModel.deleteUserDefinedQuery(0)
        assertEquals(emptyList<UserDefinedQuery>(), settingsModel.userDefinedQueries.value)
        assertEquals(IdentificationQuery(false), settingsModel.readerQuery.value)
    }

    @Test
    fun testSelectedUserDefinedQueryIsMigrated() = runBlocking {
        // Before several queries could be saved, the only one was stored as the selected query.
        val storage = EphemeralStorage()
        val settingsModel1 = SettingsModel.create(storage)
        val oldQuery = UserDefinedQuery(
            docType = "jp.go.juminhyo.1",
            namespaces = mapOf("jp.go.juminhyo.1" to listOf("family_name"))
        )
        settingsModel1.devMode.value = true
        settingsModel1.readerQuery.value = oldQuery
        delay(100)

        val settingsModel2 = SettingsModel.create(storage)
        assertEquals(listOf(oldQuery), settingsModel2.userDefinedQueries.value)
        assertEquals(oldQuery, settingsModel2.readerQuery.value)
    }
}
