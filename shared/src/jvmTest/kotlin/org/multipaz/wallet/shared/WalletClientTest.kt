package org.multipaz.wallet.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.isEmpty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.cbor.buildCborMap
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.crypto.buildX509Cert
import org.multipaz.document.buildDocumentStore
import org.multipaz.mpzpass.MpzPass
import org.multipaz.mpzpass.MpzPassSdJwtVc
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.handler.AesGcmCipher
import org.multipaz.rpc.handler.RpcDispatcherLocal
import org.multipaz.rpc.handler.RpcExceptionMap
import org.multipaz.rpc.handler.RpcNotifications
import org.multipaz.rpc.handler.RpcNotificationsLocal
import org.multipaz.rpc.handler.RpcNotifier
import org.multipaz.rpc.handler.RpcPoll
import org.multipaz.sdjwt.SdJwt
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.Storage
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.TrustEntryX509Cert
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import org.multipaz.util.truncateToWholeSeconds
import org.multipaz.wallet.backend.WalletBackendBase
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientSharedData
import org.multipaz.wallet.client.WalletClientSignedInUser
import org.multipaz.wallet.client.syncWithSharedData
import org.multipaz.wallet.client.toCbor
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@RpcState(
    endpoint = "wallet_backend",
    creatable = true
)
@CborSerializable
class TestWalletBackendImpl: WalletBackendBase(), WalletBackend {

    override suspend fun googleIdTokenVerifier(googleIdTokenString: String): Pair<String, String> {
        val map = Cbor.decode(googleIdTokenString.fromBase64Url())
        return Pair(
            map["nonce"].asTstr,
            map["id"].asTstr
        )
    }

    override suspend fun getClientId(): String {
        val testClientData = BackendEnvironment.getInterface(TestClientData::class)
        return testClientData!!.clientId
    }

    companion object {

        fun buildTestGoogleIdTokenString(
            nonce: String,
            id: String
        ): String {
            return Cbor.encode(
                buildCborMap {
                    put("nonce", nonce)
                    put("id", id)
                }
            ).toBase64Url()
        }
    }
}

data class TestClientData(
    val clientId: String
)

class TestBackendEnvironment(
    private val notifications: RpcNotifications,
    private val notifier: RpcNotifier,
    private val storage: Storage,
    private val testClientData: TestClientData,
    private val serverConfiguration: Configuration?,
    private var poll: RpcPoll? = null
): BackendEnvironment {
    override fun <T : Any> getInterface(clazz: KClass<T>): T =
        clazz.cast(when (clazz) {
            RpcNotifications::class -> notifications
            RpcNotifier::class -> notifier
            RpcPoll::class -> poll ?: RpcPoll.SILENT
            Storage::class -> storage
            TestClientData::class -> testClientData
            Configuration::class -> serverConfiguration
            else -> throw IllegalArgumentException("No implementation for $clazz")
        })
}

// RpcHandlerLocal handles RPC calls that executed locally.
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.buildLocalDispatcher(
    clientId: String,
    backendStorage: Storage,
    serverConfiguration: Configuration?,
    exceptionMap: RpcExceptionMap,
): RpcDispatcherLocal {
    val builder = RpcDispatcherLocal.Builder()
    TestWalletBackendImpl.register(builder)
    val cipher = AesGcmCipher(Random.nextBytes(16))
    val local = RpcNotificationsLocal(cipher)
    val environment = TestBackendEnvironment(
        notifications = local,
        notifier = local,
        storage = backendStorage,
        testClientData = TestClientData(
            clientId = clientId
        ),
        serverConfiguration = serverConfiguration
    )
    return builder.build(environment, cipher, exceptionMap)
}

private fun buildExceptionMap(): RpcExceptionMap {
    val builder = RpcExceptionMap.Builder()
    WalletBackendException.register(builder)
    return builder.build()
}


class WalletClientTest {
    private val exceptionMap = buildExceptionMap()

    private var clientCounter = 0
    private val backendStorage = EphemeralStorage()

    private suspend fun createPass(uniqueId: String, version: Long): MpzPass {
        val issuerKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val kbKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val sdJwt = SdJwt.create(
            issuerKey = AsymmetricKey.anonymous(issuerKey, Algorithm.ESP256),
            kbKey = kbKey.publicKey,
            random = Random(0),
            claims = Json.parseToJsonElement(
                """
                    {
                      "given_name": "Erika",
                      "family_name": "Mustermann",
                      "age_birth_year": 1963,
                      "age_equal_or_over": {
                        "12": true,
                        "14": true,
                        "16": true,
                        "18": true,
                        "21": true,
                        "65": false
                      },
                      "nationalities": [
                        "DE",
                        "US",
                        "DK"
                      ]
                    }                    
                """.trimIndent().trim()
            ).jsonObject,
            nonSdClaims = Json.parseToJsonElement(
                """
                   {
                     "vct": "urn:eudi:pid:de:1",
                     "iss": "https://pid-issuer.bund.de.example"
                   }
                """.trimIndent().trim()
            ).jsonObject,
        )
        return MpzPass(
            uniqueId = uniqueId,
            version = version,
            updateUrl = null,
            name = null,
            typeName = null,
            cardArt = null,
            isoMdoc = emptyList(),
            sdJwtVc = listOf(
                MpzPassSdJwtVc(
                    vct = sdJwt.credentialType!!,
                    deviceKeyPrivate = kbKey,
                    compactSerialization = sdJwt.compactSerialization
                )
            )
        )
    }

    private suspend fun getPass1() = createPass("pass1", 1L)
    private suspend fun getPass1v2() = createPass("pass1", 2L)
    private suspend fun getPass2() = createPass("pass2", 1L)
    private suspend fun getPass3() = createPass("pass3", 1L)

    private suspend fun TestScope.createWalletClientBase(
        clientStorage: Storage,
        serverConfiguration: Configuration? = null
    ): WalletClient {
        val localDispatcher = buildLocalDispatcher(
            "client${clientCounter++}",
            backendStorage = backendStorage,
            serverConfiguration = serverConfiguration,
            exceptionMap = exceptionMap
        )
        val client = WalletClient.create(
            dispatcher = localDispatcher,
            notifier = localDispatcher.environment.getInterface(RpcNotifier::class)!!,
            storage = clientStorage
        )
        return client
    }

    @Test
    fun notSignedIn() = runTest {
        val clientStorage = EphemeralStorage()
        val client = createWalletClientBase(clientStorage)
        assertNull(client.signedInUser.value)

        // Signing out fails if not signed in
        assertFailsWith(WalletBackendNotSignedInException::class) {
            client.signOut()
        }

        // Check operations on the shared data fail, both reading and writing
        assertFailsWith(WalletBackendNotSignedInException::class) {
            assertNull(client.getSharedData())
        }
        assertFailsWith(WalletBackendNotSignedInException::class) {
            client.putSharedData(ByteString(1, 2, 3))
        }
    }

    @Test
    fun signedInDataPersists() = runTest {
        val fooUser = WalletClientSignedInUser(
            id = "foo@gmail.com",
            displayName = "Foo Bar",
            profilePicture = ByteString(4, 5, 6)
        )
        val fooEncryptionKey = ByteString(Random.nextBytes(32))

        val clientStorage = EphemeralStorage()
        val client = createWalletClientBase(clientStorage)
        val clientNonce = client.getNonce()
        client.signInWithGoogle(
            nonce = clientNonce,
            googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                nonce = clientNonce,
                id = fooUser.id
            ),
            signedInUser = fooUser,
            walletBackendEncryptionKey = fooEncryptionKey,
            resetSharedData = false
        )
        assertEquals(fooUser, client.signedInUser.value)
        assertNotNull(client.getSharedData())
        assertTrue(client.getSharedData().isEmpty())
        val sharedData = ByteString(WalletClientSharedData(emptyList()).toCbor())
        client.putSharedData(sharedData)
        assertEquals(sharedData, client.getSharedData())

        // Simulate restarting the wallet app (that is, using same storage) and check
        // that the data at the end of the other run is the same.
        val clientNextRun = createWalletClientBase(clientStorage)
        assertEquals(fooUser, clientNextRun.signedInUser.value)
        assertEquals(sharedData, clientNextRun.getSharedData())
    }

    @Test
    fun signedInDifferentDevicesSameAccount() = runTest {
        val fooUser = WalletClientSignedInUser(
            id = "foo@gmail.com",
            displayName = "Foo Bar",
            profilePicture = ByteString(4, 5, 6)
        )
        val fooEncryptionKey = ByteString(Random.nextBytes(32))

        val client1Storage = EphemeralStorage()
        val client1 = createWalletClientBase(client1Storage)
        val client1Nonce = client1.getNonce()
        client1.signInWithGoogle(
            nonce = client1Nonce,
            googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                nonce = client1Nonce,
                id = fooUser.id
            ),
            signedInUser = fooUser,
            walletBackendEncryptionKey = fooEncryptionKey,
            resetSharedData = false
        )
        val sharedData = ByteString(WalletClientSharedData(emptyList()).toCbor())
        client1.putSharedData(sharedData)
        assertFalse(client1.refreshSharedData())

        // Now simulate signing in from another device and check we get the same data
        val client2Storage = EphemeralStorage()
        val client2 = createWalletClientBase(client2Storage)
        val client2Nonce = client2.getNonce()
        client2.signInWithGoogle(
            nonce = client2Nonce,
            googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                nonce = client2Nonce,
                id = fooUser.id
            ),
            signedInUser = fooUser,
            walletBackendEncryptionKey = fooEncryptionKey,
            resetSharedData = false
        )
        assertEquals(fooUser, client2.signedInUser.value)
        assertEquals(sharedData, client2.getSharedData())
        assertFalse(client2.refreshSharedData())

        // Have client2 write new shared data and check that it's only updated on
        // client1 after refresh().
        val sharedData2 = ByteString(
            WalletClientSharedData().addMpzPass(getPass1()).toCbor()
        )
        client2.putSharedData(sharedData2)
        assertEquals(sharedData, client1.getSharedData())
        assertTrue(client1.refreshSharedData())
        assertEquals(sharedData2, client1.getSharedData())
        assertFalse(client1.refreshSharedData())
        assertFalse(client2.refreshSharedData())
    }

    @Test
    fun signedInDifferentAccounts() = runTest {
        val fooUser = WalletClientSignedInUser(
            id = "foo@gmail.com",
            displayName = "Foo Bar",
            profilePicture = ByteString(4, 5, 6)
        )
        val fooEncryptionKey = ByteString(Random.nextBytes(32))

        val barUser = WalletClientSignedInUser(
            id = "bar@gmail.com",
            displayName = "Bar Foo",
            profilePicture = ByteString(7, 8, 9)
        )
        val barEncryptionKey = ByteString(Random.nextBytes(32))

        val client1Storage = EphemeralStorage()
        val client1 = createWalletClientBase(client1Storage)
        val client1Nonce = client1.getNonce()
        client1.signInWithGoogle(
            nonce = client1Nonce,
            googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                nonce = client1Nonce,
                id = fooUser.id
            ),
            signedInUser = fooUser,
            walletBackendEncryptionKey = fooEncryptionKey,
            resetSharedData = false
        )
        assertEquals(ByteString(), client1.getSharedData())
        client1.putSharedData(ByteString(1, 2, 3))

        // Now simulate signing in with another account (bar) and check that we don't
        // get the data from the first account (foo)
        val client2Storage = EphemeralStorage()
        val client2 = createWalletClientBase(client2Storage)
        val client2Nonce = client2.getNonce()
        client2.signInWithGoogle(
            nonce = client2Nonce,
            googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                nonce = client2Nonce,
                id = barUser.id
            ),
            signedInUser = barUser,
            walletBackendEncryptionKey = barEncryptionKey,
            resetSharedData = false
        )
        assertEquals(barUser, client2.signedInUser.value)
        assertEquals(ByteString(), client2.getSharedData())
    }

    @Test
    fun signedInTwoDevicesEncryptionKeyDoesNotMatch() = runTest {
        val fooUser = WalletClientSignedInUser(
            id = "foo@gmail.com",
            displayName = "Foo Bar",
            profilePicture = ByteString(4, 5, 6)
        )
        val fooEncryptionKey = ByteString(Random.nextBytes(32))

        val client1Storage = EphemeralStorage()
        val client1 = createWalletClientBase(client1Storage)
        val client1Nonce = client1.getNonce()
        client1.signInWithGoogle(
            nonce = client1Nonce,
            googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                nonce = client1Nonce,
                id = fooUser.id
            ),
            signedInUser = fooUser,
            walletBackendEncryptionKey = fooEncryptionKey,
            resetSharedData = false
        )
        client1.putSharedData(ByteString(1, 2, 3))
        assertFalse(client1.refreshSharedData())

        // Now simulate signing in from another device but present a different encryption
        // key and check that it fails with WalletBackendEncryptionKeyMismatchException
        val client2Storage = EphemeralStorage()
        val client2 = createWalletClientBase(client2Storage)
        val client2Nonce = client2.getNonce()
        val fooEncryptionKeyDifferent = ByteString(Random.nextBytes(32))
        assertFailsWith(WalletBackendEncryptionKeyMismatchException::class) {
            client2.signInWithGoogle(
                nonce = client2Nonce,
                googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                    nonce = client2Nonce,
                    id = fooUser.id
                ),
                signedInUser = fooUser,
                walletBackendEncryptionKey = fooEncryptionKeyDifferent,
                resetSharedData = false
            )
        }

        // This is an unusual case but can happen if the shared encryption key stored in Google
        // Drive (for signing in with Google) gets corrupted. There is no good way to recover
        // here except to tell the user "E2EE key isn't working. Would you like to clear the
        // data and start over fresh? All passes synced to other devices will be deleted" and
        // if the user does this, other clients will fail to decrypt the new data and thus
        // end up signing themselves out...
        //
        // Test this.
        //
        // Also see the "Corrupt E2EE key in Google Drive" button in Developer Settings which
        // does the same for testing the UI side of this in the Wallet App.
        //
        client2.signInWithGoogle(
            nonce = client2Nonce,
            googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                nonce = client2Nonce,
                id = fooUser.id
            ),
            signedInUser = fooUser,
            walletBackendEncryptionKey = fooEncryptionKeyDifferent,
            resetSharedData = true
        )

        assertFailsWith(
            exceptionClass = WalletBackendNotSignedInException::class,
            message = "User has been signed out because decryption failed"
        ) {
            client1.refreshSharedData()
        }
        assertFailsWith(WalletBackendNotSignedInException::class) {
            assertNull(client1.getSharedData())
        }
        assertNull(client1.signedInUser.value)
    }

    private suspend fun generateTestCert(): X509Cert {
        val now = Clock.System.now()

        val testCertKey = AsymmetricKey.ephemeral()
        val testCertValidFrom = (now - 1.days).truncateToWholeSeconds()
        val testCertValidUntil = (now + 2.days).truncateToWholeSeconds()
        val testCert = buildX509Cert(
            publicKey = testCertKey.publicKey,
            signingKey = testCertKey,
            serialNumber = ASN1Integer.fromRandom(128),
            subject = X500Name.fromName("CN=Root"),
            issuer = X500Name.fromName("CN=Root"),
            validFrom = testCertValidFrom,
            validUntil = testCertValidUntil,
        ) {
            setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
            setBasicConstraints(ca = true, pathLenConstraint = 1)
            includeSubjectKeyIdentifier()
            includeAuthorityKeyIdentifierAsSubjectKeyIdentifier()
        }
        return testCert
    }

    @Test
    fun testPublicData() = runTest {
        val testCert1 = generateTestCert()
        val testCert2 = generateTestCert()
        val testCert3 = generateTestCert()

        val serverConfiguration = TestConfiguration(buildJsonObject {
            putJsonObject("public_data") {
                put("version", "2026_0402_1229")
                putJsonArray("trusted_issuers") {
                    addJsonObject {
                        put("display_name", "Test Issuer")
                        put("test_only", false)
                        put("certificate", testCert1.toPem())
                    }
                }
                putJsonArray("trusted_readers") {
                    addJsonObject {
                        put("display_name", "Test Reader")
                        put("test_only", true)
                        put("certificate", testCert2.toPem())
                    }
                }
            }
        })

        val clientStorage = EphemeralStorage()
        val client = createWalletClientBase(
            clientStorage = clientStorage,
            serverConfiguration = serverConfiguration
        )

        val (publicData, newDataRetrieved) = client.getPublicData(
            checkWithWalletBackend = false
        )
        assertFalse(newDataRetrieved)

        // Check the data
        assertEquals(1, publicData.trustedIssuers.size)
        assertEquals("Test Issuer", publicData.trustedIssuers[0].metadata.displayName)
        assertEquals(false, publicData.trustedIssuers[0].metadata.testOnly)
        assertTrue(publicData.trustedIssuers[0] is TrustEntryX509Cert)
        assertEquals(testCert1, (publicData.trustedIssuers[0] as TrustEntryX509Cert).certificate)
        assertEquals(1, publicData.trustedReaders.size)
        assertEquals("Test Reader", publicData.trustedReaders[0].metadata.displayName)
        assertEquals(true, publicData.trustedReaders[0].metadata.testOnly)
        assertTrue(publicData.trustedReaders[0] is TrustEntryX509Cert)
        assertEquals(testCert2, (publicData.trustedReaders[0] as TrustEntryX509Cert).certificate)

        // Getting new data without pinging the server
        val (publicData2, newDataRetrieved2) = client.getPublicData(
            checkWithWalletBackend = false
        )
        assertFalse(newDataRetrieved2)
        assertEquals(publicData2, publicData)

        // Getting new data with _explicitly_ pinging the server .. since we didn't change the server
        // on the backend nothing changed
        val (publicData3, newDataRetrieved3) = client.getPublicData(
            checkWithWalletBackend = true
        )
        assertFalse(newDataRetrieved3)
        assertEquals(publicData3, publicData)

        // Change the data on the server...
        serverConfiguration.update(buildJsonObject {
            putJsonObject("public_data") {
                put("version", "2026_0402_1230")
                putJsonArray("trusted_issuers") {
                    addJsonObject {
                        put("display_name", "Test Issuer")
                        put("test_only", false)
                        put("certificate", testCert1.toPem())
                    }
                }
                putJsonArray("trusted_readers") {
                    addJsonObject {
                        put("display_name", "Test Reader")
                        put("test_only", true)
                        put("certificate", testCert2.toPem())
                    }
                    addJsonObject {
                        put("display_name", "Test Reader 2")
                        put("test_only", true)
                        put("display_icon_url", "https://www.multipaz.org/multipaz-logo-200x200.png")
                        put("certificate", testCert3.toPem())
                    }
                }
            }
        })

        // Getting new data without _explicitly_ pinging the server .. we should see not see the new
        // version even if it's already on the server (b/c we didn't ping it)
        val (publicData4, newDataRetrieved4) = client.getPublicData(
            checkWithWalletBackend = false
        )
        assertFalse(newDataRetrieved4)
        assertEquals(publicData4, publicData)

        // Getting new data with _explicitly_ pinging the server .. we should see the new version
        val (publicData5, newDataRetrieved5) = client.getPublicData(
            checkWithWalletBackend = true
        )
        assertTrue(newDataRetrieved5)
        assertNotEquals(publicData5, publicData)

        // Check the new data
        assertEquals(2, publicData5.trustedReaders.size)
        assertEquals("Test Reader 2", publicData5.trustedReaders[1].metadata.displayName)
        assertEquals(true, publicData5.trustedReaders[1].metadata.testOnly)
        assertEquals("https://www.multipaz.org/multipaz-logo-200x200.png", publicData5.trustedReaders[1].metadata.displayIconUrl)
        assertTrue(publicData5.trustedReaders[1] is TrustEntryX509Cert)
        assertEquals(testCert3, (publicData5.trustedReaders[1] as TrustEntryX509Cert).certificate)
    }

    @Test
    fun sharedDataMpzPass() = runTest {
        val pass1 = getPass1()
        val pass2 = getPass2()
        val pass3 = getPass3()

        val data0 = WalletClientSharedData()
        assertNull(data0.encodedMpzPasses)
        assertTrue(data0.getMpzPasses().isEmpty())

        val data1 = data0.addMpzPass(pass1)
        assertNotNull(data1.encodedMpzPasses)
        assertEquals(listOf(pass1), data1.getMpzPasses())

        val data2 = data1.addMpzPass(pass2)
        assertEquals(listOf(pass1, pass2), data2.getMpzPasses())

        val data3 = data2.addMpzPass(pass3)
        assertEquals(listOf(pass1, pass2, pass3), data3.getMpzPasses())

        val data4 = data3.addMpzPass(pass3)
        assertEquals(listOf(pass1, pass2, pass3, pass3), data4.getMpzPasses())

        val data5 = data4.removeMpzPass(pass2)
        assertEquals(listOf(pass1, pass3, pass3), data5.getMpzPasses())

        val data6 = data5.removeMpzPass(pass3)
        assertEquals(listOf(pass1), data6.getMpzPasses())

        val data7 = data6.removeMpzPass(pass2)
        assertEquals(listOf(pass1), data7.getMpzPasses())

        val data8 = data6.removeMpzPass(pass2)
        assertEquals(listOf(pass1), data8.getMpzPasses())

        val data9 = data8.removeMpzPass(pass1)
        assertNull(data9.encodedMpzPasses)
        assertEquals(listOf(), data9.getMpzPasses())
    }

    @Test
    fun testDocumentStoreSyncMpzPass() = runTest {
        val pass1 = getPass1()
        val pass2 = getPass2()
        val pass1v2 = getPass1v2()

        val storage = EphemeralStorage()
        val softwareSecureArea = SoftwareSecureArea.create(storage)
        val secureAreaRepository = SecureAreaRepository.Builder()
            .add(softwareSecureArea)
            .build()

        val fooUser = WalletClientSignedInUser(
            id = "foo@gmail.com",
            displayName = "Foo Bar",
            profilePicture = ByteString(4, 5, 6)
        )
        val fooEncryptionKey = ByteString(Random.nextBytes(32))

        // Sign in on the first device ...
        val client1Storage = EphemeralStorage()
        val client1 = createWalletClientBase(client1Storage)
        val client1Nonce = client1.getNonce()
        client1.signInWithGoogle(
            nonce = client1Nonce,
            googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                nonce = client1Nonce,
                id = fooUser.id
            ),
            signedInUser = fooUser,
            walletBackendEncryptionKey = fooEncryptionKey,
            resetSharedData = false
        )
        val client1DocumentStore = buildDocumentStore(
            storage = client1Storage,
            secureAreaRepository = secureAreaRepository,
        ) {}
        assertEquals(fooUser, client1.signedInUser.value)
        assertEquals(0, client1DocumentStore.listDocuments().size)
        // ... and import a pass and add that pass to SharedData (source of truth)
        client1.setSharedData(client1.sharedData.value!!.addMpzPass(pass1))
        client1DocumentStore.syncWithSharedData(
            sharedData = client1.sharedData.value!!,
            mpzPassIsoMdocDomain = "mdoc_software",
            mpzPassSdJwtVcDomain = "sdjwt_software",
            mpzPassKeylessSdJwtVcDomain = "sdjwt_keyless"
        )
        assertNotNull(client1DocumentStore.listDocuments().find { it.mpzPassId == pass1.uniqueId })

        // Now simulate signing in from another device and check we get the same data
        val client2Storage = EphemeralStorage()
        val client2 = createWalletClientBase(client2Storage)
        val client2Nonce = client2.getNonce()
        client2.signInWithGoogle(
            nonce = client2Nonce,
            googleIdTokenString = TestWalletBackendImpl.buildTestGoogleIdTokenString(
                nonce = client2Nonce,
                id = fooUser.id
            ),
            signedInUser = fooUser,
            walletBackendEncryptionKey = fooEncryptionKey,
            resetSharedData = false
        )
        val client2DocumentStore = buildDocumentStore(
            storage = client2Storage,
            secureAreaRepository = secureAreaRepository,
        ) {}
        assertEquals(fooUser, client2.signedInUser.value)
        assertEquals(0, client2DocumentStore.listDocuments().size)
        client2DocumentStore.syncWithSharedData(
            sharedData = client2.sharedData.value!!,
            mpzPassIsoMdocDomain = "mdoc_software",
            mpzPassSdJwtVcDomain = "sdjwt_software",
            mpzPassKeylessSdJwtVcDomain = "sdjwt_keyless"
        )
        assertNotNull(client2DocumentStore.listDocuments().find { it.mpzPassId == pass1.uniqueId })

        // Add pass2 to client2 ...
        client2.setSharedData(client2.sharedData.value!!.addMpzPass(pass2))
        client2DocumentStore.syncWithSharedData(
            sharedData = client2.sharedData.value!!,
            mpzPassIsoMdocDomain = "mdoc_software",
            mpzPassSdJwtVcDomain = "sdjwt_software",
            mpzPassKeylessSdJwtVcDomain = "sdjwt_keyless"
        )
        // ... check it's updated on client1
        assertEquals(1, client1DocumentStore.listDocuments().size)
        assertTrue(client1.refreshSharedData())
        client1DocumentStore.syncWithSharedData(
            sharedData = client1.sharedData.value!!,
            mpzPassIsoMdocDomain = "mdoc_software",
            mpzPassSdJwtVcDomain = "sdjwt_software",
            mpzPassKeylessSdJwtVcDomain = "sdjwt_keyless"
        )
        assertEquals(2, client1DocumentStore.listDocuments().size)
        assertNotNull(client1DocumentStore.listDocuments().find { it.mpzPassId == pass1.uniqueId })
        assertNotNull(client1DocumentStore.listDocuments().find { it.mpzPassId == pass2.uniqueId })

        // Update pass1 on client1 to v2 ...
        client1.setSharedData(client1.sharedData.value!!.removeMpzPass(pass1).addMpzPass(pass1v2))
        client1DocumentStore.syncWithSharedData(
            sharedData = client1.sharedData.value!!,
            mpzPassIsoMdocDomain = "mdoc_software",
            mpzPassSdJwtVcDomain = "sdjwt_software",
            mpzPassKeylessSdJwtVcDomain = "sdjwt_keyless"
        )
        assertNotNull(client1DocumentStore.listDocuments().find {
            it.mpzPassId == pass1v2.uniqueId && it.mpzPassVersion == pass1v2.version
        })
        // ... check it's updated on client2
        assertNotNull(client2DocumentStore.listDocuments().find {
            it.mpzPassId == pass1.uniqueId && it.mpzPassVersion == pass1.version
        })
        assertTrue(client2.refreshSharedData())
        client2DocumentStore.syncWithSharedData(
            sharedData = client2.sharedData.value!!,
            mpzPassIsoMdocDomain = "mdoc_software",
            mpzPassSdJwtVcDomain = "sdjwt_software",
            mpzPassKeylessSdJwtVcDomain = "sdjwt_keyless"
        )
        assertNotNull(client2DocumentStore.listDocuments().find {
            it.mpzPassId == pass1v2.uniqueId && it.mpzPassVersion == pass1v2.version
        })

        // Delete pass2 on client2 ...
        client2.setSharedData(client2.sharedData.value!!.removeMpzPass(pass2))
        assertNotNull(client2DocumentStore.listDocuments().find { it.mpzPassId == pass2.uniqueId })
        client2DocumentStore.syncWithSharedData(
            sharedData = client2.sharedData.value!!,
            mpzPassIsoMdocDomain = "mdoc_software",
            mpzPassSdJwtVcDomain = "sdjwt_software",
            mpzPassKeylessSdJwtVcDomain = "sdjwt_keyless"
        )
        assertNull(client2DocumentStore.listDocuments().find { it.mpzPassId == pass2.uniqueId })
        // ... check that it's deleted on client1
        assertTrue(client1.refreshSharedData())
        assertNotNull(client1DocumentStore.listDocuments().find { it.mpzPassId == pass2.uniqueId })
        client1DocumentStore.syncWithSharedData(
            sharedData = client1.sharedData.value!!,
            mpzPassIsoMdocDomain = "mdoc_software",
            mpzPassSdJwtVcDomain = "sdjwt_software",
            mpzPassKeylessSdJwtVcDomain = "sdjwt_keyless"
        )
        assertNull(client1DocumentStore.listDocuments().find { it.mpzPassId == pass2.uniqueId })
    }
}

private class TestConfiguration(
    initialJson: JsonObject
): Configuration {
    private var values = mutableMapOf<String, String>()

    init {
        applyJson(initialJson)
    }

    fun update(newJson: JsonObject) {
        applyJson(newJson)
    }

    private fun applyJson(json: JsonObject) {
        values.clear()
        for (entry in json) {
            val value = entry.value
            values[entry.key] = if (value is JsonPrimitive) {
                value.content
            } else {
                value.toString()
            }
        }
    }

    override fun getValue(key: String): String? = values[key]
}