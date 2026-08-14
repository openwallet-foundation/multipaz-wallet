package org.multipaz.wallet.shared

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.X509CertChain
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.securearea.KeyAttestation
import org.multipaz.wallet.backend.WalletBackendBase
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialIssuerSettingsTest {

    @Test
    fun testCredentialIssuerSettingsCbor() {
        val original = CredentialIssuerSettings(
            secureAreaToUse = CredentialIssuerSecureAreaType.CLOUD_SECURE_AREA,
            androidKeySettings = CredentialIssuerSettingsAndroidKeySettings(
                algorithm = Algorithm.ED25519,
                useStrongBox = true,
                userAuthenticationTimeoutMillis = 5000L,
                userAuthenticationLskf = false,
                userAuthenticationBiometric = true
            )
        )
        val cbor = original.toCbor()
        val decoded = CredentialIssuerSettings.fromCbor(cbor)
        assertEquals(CredentialIssuerSecureAreaType.CLOUD_SECURE_AREA, decoded.secureAreaToUse)
        val decodedAks = decoded.androidKeySettings
        assertNotNull(decodedAks)
        assertEquals(Algorithm.ED25519, decodedAks.algorithm)
        assertTrue(decodedAks.useStrongBox)
        assertEquals(5000L, decodedAks.userAuthenticationTimeoutMillis)
        assertFalse(decodedAks.userAuthenticationLskf)
        assertTrue(decodedAks.userAuthenticationBiometric)
    }

    @Test
    fun testCredentialIssuerOpenID4VCIWithSettingsCbor() {
        val issuer = CredentialIssuerOpenID4VCI(
            name = "Utopia PID (StrongBox)",
            iconUrl = "https://issuer.multipaz.org/issuer/card-pid.png",
            url = "https://issuer.multipaz.org/issuer",
            id = "pid_mdoc",
            credentialIssuerSettings = CredentialIssuerSettings(
                secureAreaToUse = CredentialIssuerSecureAreaType.PLATFORM_SECURE_AREA,
                androidKeySettings = CredentialIssuerSettingsAndroidKeySettings(
                    useStrongBox = true
                )
            )
        )
        val cbor = issuer.toCbor()
        val decoded = CredentialIssuer.fromCbor(cbor)
        assertIs<CredentialIssuerOpenID4VCI>(decoded)
        assertEquals("Utopia PID (StrongBox)", decoded.name)
        assertEquals("https://issuer.multipaz.org/issuer/card-pid.png", decoded.iconUrl)
        assertEquals("https://issuer.multipaz.org/issuer", decoded.url)
        assertEquals("pid_mdoc", decoded.id)
        val decodedSettings = decoded.credentialIssuerSettings
        assertNotNull(decodedSettings)
        assertEquals(CredentialIssuerSecureAreaType.PLATFORM_SECURE_AREA, decodedSettings.secureAreaToUse)
        val decodedSettingsAks = decodedSettings.androidKeySettings
        assertNotNull(decodedSettingsAks)
        assertTrue(decodedSettingsAks.useStrongBox)
    }

    @Test
    fun testCredentialIssuerOpenID4VCIWithoutSettingsCbor() {
        val issuer = CredentialIssuerOpenID4VCI(
            name = "Utopia PID",
            iconUrl = "https://issuer.multipaz.org/issuer/card-pid.png",
            url = "https://issuer.multipaz.org/issuer",
            id = "pid_mdoc",
            credentialIssuerSettings = null
        )
        val cbor = issuer.toCbor()
        val decoded = CredentialIssuer.fromCbor(cbor)
        assertIs<CredentialIssuerOpenID4VCI>(decoded)
        assertEquals("Utopia PID", decoded.name)
        assertNull(decoded.credentialIssuerSettings)
    }

    @Test
    fun testConfigurationParser() = runTest {
        val provisioningJson = """
            {
                "credential_issuers": [
                    {
                        "name": "Utopia PID",
                        "icon_url": "https://issuer.multipaz.org/issuer/card-pid.png",
                        "type": "openid4vci",
                        "url": "https://issuer.multipaz.org/issuer",
                        "id": "pid_mdoc"
                    },
                    {
                        "name": "Utopia PID (Legacy StrongBox)",
                        "icon_url": "https://issuer.multipaz.org/issuer/card-pid.png",
                        "type": "openid4vci",
                        "url": "https://issuer.multipaz.org/issuer",
                        "id": "pid_mdoc",
                        "strongBox": true
                    },
                    {
                        "name": "Utopia PID (Full Settings)",
                        "icon_url": "https://issuer.multipaz.org/issuer/card-pid.png",
                        "type": "openid4vci",
                        "url": "https://issuer.multipaz.org/issuer",
                        "id": "pid_mdoc",
                        "settings": {
                            "secure_area": "cloud",
                            "android_key_settings": {
                                "use_strongbox": true,
                                "algorithm": "ED25519",
                                "user_authentication_timeout_millis": 5000,
                                "user_authentication_lskf": false,
                                "user_authentication_biometric": true
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        val serverConfig = object : Configuration {
            override fun getValue(key: String): String? {
                return if (key == "provisioning") provisioningJson else null
            }
        }

        val testEnv = object : BackendEnvironment {
            override fun <T : Any> getInterface(clazz: KClass<T>): T =
                clazz.cast(when (clazz) {
                    Configuration::class -> serverConfig
                    else -> throw IllegalArgumentException("No impl for $clazz")
                })
        }

        val backend = object : WalletBackendBase() {
            override suspend fun googleIdTokenVerifier(googleIdTokenString: String, expectedNonce: String): String = ""
            override suspend fun googleCodeExchanger(authorizationCode: String, redirectUri: String, expectedNonce: String): Pair<String, String> = Pair("", "")
            override suspend fun exchangeCodeForTokensInternal(authorizationCode: String, redirectUri: String): GoogleTokens = GoogleTokens("", "")
            override suspend fun getClientId(): String = ""
            override suspend fun getIpAddress(): String? = null
            override suspend fun certifyReaderKeys(readerKeys: List<KeyAttestation>): List<X509CertChain> = emptyList()
        }

        val issuers = withContext(testEnv) {
            backend.getCredentialIssuers()
        }

        assertEquals(3, issuers.size)

        // 1. Normal issuer without settings
        val normalIssuer = issuers[0]
        assertIs<CredentialIssuerOpenID4VCI>(normalIssuer)
        assertEquals("Utopia PID", normalIssuer.name)
        assertNull(normalIssuer.credentialIssuerSettings)

        // 2. Legacy strongBox shorthand
        val legacyStrongBoxIssuer = issuers[1]
        assertIs<CredentialIssuerOpenID4VCI>(legacyStrongBoxIssuer)
        assertEquals("Utopia PID (Legacy StrongBox)", legacyStrongBoxIssuer.name)
        val legacySettings = legacyStrongBoxIssuer.credentialIssuerSettings
        assertNotNull(legacySettings)
        assertEquals(CredentialIssuerSecureAreaType.PLATFORM_SECURE_AREA, legacySettings.secureAreaToUse)
        val legacyAks = legacySettings.androidKeySettings
        assertNotNull(legacyAks)
        assertTrue(legacyAks.useStrongBox)

        // 3. Full structured settings
        val fullSettingsIssuer = issuers[2]
        assertIs<CredentialIssuerOpenID4VCI>(fullSettingsIssuer)
        assertEquals("Utopia PID (Full Settings)", fullSettingsIssuer.name)
        val settings = fullSettingsIssuer.credentialIssuerSettings
        assertNotNull(settings)
        assertEquals(CredentialIssuerSecureAreaType.CLOUD_SECURE_AREA, settings.secureAreaToUse)
        val aks = settings.androidKeySettings
        assertNotNull(aks)
        assertTrue(aks.useStrongBox)
        assertEquals(Algorithm.ED25519, aks.algorithm)
        assertEquals(5000L, aks.userAuthenticationTimeoutMillis)
        assertFalse(aks.userAuthenticationLskf)
        assertTrue(aks.userAuthenticationBiometric)
    }
}
