package org.multipaz.wallet.android.settings

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.Logger

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
}
