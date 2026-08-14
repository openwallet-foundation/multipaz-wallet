package org.multipaz.wallet.android

import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.multipaz.crypto.Algorithm
import org.multipaz.document.buildDocumentStore
import org.multipaz.provisioning.DocumentProvisioningHandler
import org.multipaz.provisioning.DocumentProvisioningSettings
import org.multipaz.securearea.AndroidKeystoreCreateKeySettings
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.UserAuthenticationType
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.wallet.shared.CredentialIssuerSecureAreaType
import org.multipaz.wallet.shared.CredentialIssuerSettings
import org.multipaz.wallet.shared.CredentialIssuerSettingsAndroidKeySettings
import org.multipaz.wallet.shared.fromCbor
import org.multipaz.wallet.shared.toCbor
import kotlin.time.Duration.Companion.seconds

class ProvisioningSecureAreaSelectionTest {

    @Test
    fun testSelectSecureAreaWithStrongBoxSettings() = runBlocking {
        val storage = EphemeralStorage()
        val secureArea = SoftwareSecureArea.create(storage)
        val secureAreaRepository = SecureAreaRepository.Builder()
            .add(secureArea)
            .build()
        val documentStore = buildDocumentStore(storage = storage, secureAreaRepository = secureAreaRepository) {}

        val handler = DocumentProvisioningHandler(
            documentStore = documentStore,
            secureArea = secureArea,
            defaultDocumentProvisioningSettings = DocumentProvisioningSettings(),
            selectSecureArea = { appData, createKeySettings ->
                val settings = appData?.let {
                    CredentialIssuerSettings.fromCbor(it.toByteArray())
                }
                settings?.androidKeySettings?.let { aks ->
                    val builder = AndroidKeystoreCreateKeySettings.Builder(createKeySettings.nonce)
                        .setAlgorithm(createKeySettings.algorithm)
                        .setUseStrongBox(aks.useStrongBox)
                    if (createKeySettings.userAuthenticationRequired) {
                        val authTypes = mutableSetOf<UserAuthenticationType>()
                        if (aks.userAuthenticationLskf) {
                            authTypes.add(UserAuthenticationType.LSKF)
                        }
                        if (aks.userAuthenticationBiometric) {
                            authTypes.add(UserAuthenticationType.BIOMETRIC)
                        }
                        builder.setUserAuthenticationRequired(
                            true,
                            createKeySettings.userAuthenticationTimeout,
                            authTypes
                        )
                    }
                    if (createKeySettings.validFrom != null && createKeySettings.validUntil != null) {
                        builder.setValidityPeriod(createKeySettings.validFrom!!, createKeySettings.validUntil!!)
                    }
                    Pair(secureArea, builder.build())
                } ?: Pair(secureArea, createKeySettings)
            }
        )

        val settings = CredentialIssuerSettings(
            secureAreaToUse = CredentialIssuerSecureAreaType.PLATFORM_SECURE_AREA,
            androidKeySettings = CredentialIssuerSettingsAndroidKeySettings(
                useStrongBox = true
            )
        )
        val strongBoxAppData = ByteString(settings.toCbor())
        val docWithStrongBox = documentStore.createDocument(
            displayName = "Utopia PID (StrongBox)",
            typeDisplayName = "PID",
            cardArt = null,
            issuerLogo = null,
            authorizationData = null,
            appData = strongBoxAppData,
            metadata = null
        )

        val initialSettings = CreateKeySettings(
            algorithm = Algorithm.ESP256,
            nonce = ByteString(byteArrayOf(1, 2, 3)),
            userAuthenticationRequired = true,
            userAuthenticationTimeout = 30.seconds,
            validFrom = null,
            validUntil = null
        )

        val (_, selectedSettings) = handler.selectSecureArea(docWithStrongBox, initialSettings)
        assertTrue(selectedSettings is AndroidKeystoreCreateKeySettings)
        val androidKeystoreSettings = selectedSettings as AndroidKeystoreCreateKeySettings
        assertTrue(androidKeystoreSettings.useStrongBox)
        assertEquals(Algorithm.ESP256, androidKeystoreSettings.algorithm)
        assertTrue(androidKeystoreSettings.userAuthenticationRequired)
        assertEquals(30.seconds, androidKeystoreSettings.userAuthenticationTimeout)
        assertTrue(androidKeystoreSettings.userAuthenticationTypes.contains(UserAuthenticationType.LSKF))
        assertTrue(androidKeystoreSettings.userAuthenticationTypes.contains(UserAuthenticationType.BIOMETRIC))

        // Normal document without StrongBox appData
        val normalDoc = documentStore.createDocument(
            displayName = "Utopia PID",
            typeDisplayName = "PID",
            cardArt = null,
            issuerLogo = null,
            authorizationData = null,
            appData = null,
            metadata = null
        )

        val (_, normalSettings) = handler.selectSecureArea(normalDoc, initialSettings)
        assertFalse(normalSettings is AndroidKeystoreCreateKeySettings)
        assertEquals(initialSettings, normalSettings)
    }
}
