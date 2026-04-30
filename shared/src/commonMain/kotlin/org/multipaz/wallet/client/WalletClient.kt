package org.multipaz.wallet.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.provisioning.openid4vci.OpenID4VCIBackend
import org.multipaz.provisioning.openid4vci.OpenID4VCIBackendStub
import org.multipaz.provisioning.openid4vci.OpenID4VCIClientPreferences
import org.multipaz.rpc.client.RpcAuthorizedDeviceClient
import org.multipaz.rpc.handler.RpcAuthClientSession
import org.multipaz.rpc.handler.RpcAuthException
import org.multipaz.rpc.handler.RpcDispatcher
import org.multipaz.rpc.handler.RpcExceptionMap
import org.multipaz.rpc.handler.RpcNotifier
import org.multipaz.rpc.transport.HttpTransport
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.NoRecordStorageException
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTableSpec
import org.multipaz.trustmanagement.ConfigurableTrustManager
import org.multipaz.util.Logger
import org.multipaz.util.deflate
import org.multipaz.util.inflate
import org.multipaz.wallet.client.WalletClient.Companion.create
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.CredentialIssuer
import org.multipaz.wallet.shared.WalletClientPublicData
import org.multipaz.wallet.shared.WalletBackend
import org.multipaz.wallet.shared.WalletBackendEncryptionKeyMismatchException
import org.multipaz.wallet.shared.WalletBackendException
import org.multipaz.wallet.shared.WalletBackendIdTokenException
import org.multipaz.wallet.shared.WalletBackendNonceException
import org.multipaz.wallet.shared.WalletBackendNotSignedInException
import org.multipaz.wallet.shared.WalletBackendStub
import org.multipaz.wallet.shared.GoogleTokens
import org.multipaz.wallet.shared.fromCbor
import org.multipaz.wallet.shared.register
import org.multipaz.wallet.shared.toCbor
import kotlin.collections.set
import kotlin.random.Random

private const val TAG = "WalletClient"

/**
 * Wallet backend client.
 *
 * The main purpose of [WalletClient] is to provide access to wallet backend services
 * as well as a way for multiple clients to share data between each other, and
 * encrypted so the wallet backend cannot read the data. The latter requires the user
 * being logged in to an account provider. Currently, this supports Google accounts but
 * other account providers may be added in the future.
 *
 * With a Google account, the app must do the following
 *
 * - ask the user to sign in with their Google account
 * - ask the user for authorization to use the [`https://www.googleapis.com/auth/drive.appdata`](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
 *   scope (View and manage the app's own configuration data in your Google Drive)
 * - get the shared encryption key from Google Drive, creating one if it isn't present
 *
 * and then call [signInWithGoogle] with a nonce obtained from [getNonce] and the shared encryption
 * key obtained from Google Drive. On successful sign-in the encryption key is stored in
 * [storage] and only a hash of the encryption key is ever shared with the backend.
 *
 * Once signed-in the app may observe shared data in [sharedData] and update this data
 * using [setSharedData]. These changes are propagated to the backend and onwards to other
 * clients.
 *
 * The wallet backend provides storage and versioning for the shared objects, but
 * it only sees an opaque blob. Each wallet client will decrypt, decompress, and decode
 * the payload into a [WalletClientSharedData]. Similarly, when this data is updated, the
 * client will encode, compress, and encrypt and send the encrypted payload to the
 * backend.
 *
 * When a client signs in it transmits the SHA-256 of the encryption key to the wallet
 * backend which will retain this along with the versioned and encrypted shared state
 * that it stores. If the SHA-256 of the encryption doesn't match what's stored in the
 * wallet backend, [org.multipaz.wallet.shared.WalletBackendEncryptionKeyMismatchException] is thrown and the client
 * can decide how to proceed.
 *
 * The client caches the last received state so no network requests are made to the
 * wallet backend except for when the application is signing the user in or out, when
 * wallet objects are mutated, and when a notification from the backend is received. In
 * particular no network calls are made at client instantiation time so the application may
 * want to call [refreshPublicData] and [refreshSharedData] at application start-up or when
 * the network is back, in case a notification was missed. Because payloads are versioned
 * and the [refreshPublicData] and [refreshSharedData] calls transmit the version currently
 * held by the client, payload is only downloaded if there's a newer version.
 *
 * The backend may at any time in the future decide that the user is no longer signed
 * in (for example if all backend data is cleared) which causes [org.multipaz.wallet.shared.WalletBackend] to return
 * [org.multipaz.wallet.shared.WalletBackendNotSignedInException] for operations, sets [sharedData] to `null`,
 * and clears local encryption data.
 *
 * Use [create] to create a new instance.
 */
class WalletClient private constructor(
    private var backendUrl: String,
    private val secret: String,
    private val storage: Storage,
    private val secureArea: SecureArea?,
    private val httpClientEngineFactory: HttpClientEngineFactory<*>?,
    private val dispatcherAndNotifier: Pair<RpcDispatcher, RpcNotifier>?
) {
    init {
        require(httpClientEngineFactory != null || dispatcherAndNotifier != null) {
            "Both httpClientEngineFactory and dispatcherAndNotifier cannot be null"
        }
    }

    /**
     * The URL of the wallet backend.
     */
    val url: String
        get() = backendUrl

    val _signedInUser = MutableStateFlow<WalletClientSignedInUser?>(null)

    /**
     * The currently signed-in user or `null` if no user is signed in.
     */
    val signedInUser: StateFlow<WalletClientSignedInUser?> = _signedInUser.asStateFlow()

    private var _walletBackend: WalletBackend? = null

    //private val _session by lazy { RpcAuthClientSession() }
    private var _session: RpcAuthClientSession? = null

    private val session: RpcAuthClientSession
        get() {
            // Ensure that caller is holding the lock
            check(lock.isLocked) {
                "Only use session while holding the lock"
            }
            if (_session != null) {
                return _session!!
            }
            _session = RpcAuthClientSession()
            return _session!!
        }

    private var lock = Mutex()

    private suspend fun getWalletBackend(): WalletBackend {
        check(lock.isLocked)
        _walletBackend?.let { return it }

        val exceptionMapBuilder = RpcExceptionMap.Builder()
        WalletBackendException.register(exceptionMapBuilder)
        val exceptionMap = exceptionMapBuilder.build()

        val (dispatcher, notifier) = if (dispatcherAndNotifier != null) {
            dispatcherAndNotifier
        } else {
            val rpcAuthorizedClient = try {
                RpcAuthorizedDeviceClient.connect(
                    exceptionMap = exceptionMap,
                    httpClientEngine = httpClientEngineFactory!!,
                    url = "$backendUrl/rpc",
                    secureArea = secureArea!!,
                    storage = storage,
                    secret = secret
                )
            } catch (e: HttpTransport.HttpClientException) {
                throw WalletClientBackendUnreachableException("Wallet backend is currently unreachable", e)
            } catch (e: RpcAuthException) {
                throw WalletClientAuthorizationException("Not authorized to access wallet backend", e)
            }
            Pair(rpcAuthorizedClient.dispatcher, rpcAuthorizedClient.notifier)
        }

        _walletBackend = withContext(session) {
            WalletBackendStub(
                endpoint = "wallet_backend",
                dispatcher = dispatcher,
                notifier = notifier
            )
        }
        return _walletBackend!!
    }

    private var _openID4VCIBackend: OpenID4VCIBackend? = null
    private var _openID4VCIClientPreferences: OpenID4VCIClientPreferences? = null

    suspend fun getOpenID4VCIBackend(): OpenID4VCIBackend {
        lock.withLock {
            return getOpenID4VCIBackendUnlocked()
        }
    }

    private suspend fun getOpenID4VCIBackendUnlocked(): OpenID4VCIBackend {
        check(lock.isLocked)
        _openID4VCIBackend?.let { return it }
        val (dispatcher, notifier) = if (dispatcherAndNotifier != null) {
            dispatcherAndNotifier
        } else {
            val rpcAuthorizedClient = RpcAuthorizedDeviceClient.connect(
                exceptionMap = RpcExceptionMap.Builder().build(),
                httpClientEngine = httpClientEngineFactory!!,
                url = "$backendUrl/rpc",
                secureArea = secureArea!!,
                storage = storage
            )
            Pair(rpcAuthorizedClient.dispatcher, rpcAuthorizedClient.notifier)
        }
        _openID4VCIBackend = OpenID4VCIBackendStub(
            endpoint = "openid4vci_backend",
            dispatcher = dispatcher,
            notifier = notifier
        )
        return _openID4VCIBackend!!
    }

    suspend fun getOpenID4VCIClientPreferences(): OpenID4VCIClientPreferences {
        lock.withLock {
            _openID4VCIClientPreferences?.let { return it }
            _openID4VCIClientPreferences = OpenID4VCIClientPreferences(
                clientId = withContext(RpcAuthClientSession()) {
                    getOpenID4VCIBackendUnlocked().getClientId()
                },
                // Note: always uses `BuildConfig.BACKEND_URL` instead of `backendUrl` since this
                // is what the AndroidManifest.xml file uses
                redirectUrl = "${BuildConfig.BACKEND_URL}/redirect",
                locales = listOf("en-US"),
                signingAlgorithms = listOf(Algorithm.ESP256, Algorithm.ESP384, Algorithm.ESP512)
            )
            return _openID4VCIClientPreferences!!
        }
    }

    private val pendingLinksByState = mutableMapOf<String, SendChannel<String>>()

    suspend fun processAppLinkInvocation(url: String) {
        val state = Url(url).parameters["state"] ?: ""
        lock.withLock {
            pendingLinksByState.remove(state)?.send(url)
        }
    }

    suspend fun waitForAppLinkInvocation(state: String): String {
        val channel = Channel<String>(Channel.RENDEZVOUS)
        lock.withLock {
            pendingLinksByState[state] = channel
        }
        return channel.receive()
    }

    val appLinkBaseUrl: String
        get() = "${BuildConfig.BACKEND_URL}/redirect"

    /**
     * Gets a list of credential issuers from the backend.
     *
     * @return a list of [CredentialIssuer]s.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        CancellationException::class
    )
    suspend fun getCredentialIssuers(): List<CredentialIssuer> {
        lock.withLock {
            val walletBackend = getWalletBackend()
            return withContext(session) {
                try {
                    walletBackend.getCredentialIssuers()
                } catch (e: HttpTransport.HttpClientException) {
                    throw WalletClientBackendUnreachableException(e.message, e)
                }
            }
        }
    }

    /**
     * Gets the EULA the user needs to accept to use the application.
     *
     * @param locale BCP-47 language tag for the user's language.
     * @return the EULA, as Markdown.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        CancellationException::class
    )
    suspend fun getEula(locale: String): String {
        lock.withLock {
            val walletBackend = getWalletBackend()
            return withContext(session) {
                try {
                    walletBackend.getEula(locale)
                } catch (e: HttpTransport.HttpClientException) {
                    throw WalletClientBackendUnreachableException(e.message, e)
                }
            }
        }
    }

    /**
     * Gets a nonce from the wallet backend to be used for signing in.
     *
     * @return the nonce.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        CancellationException::class
    )
    suspend fun getNonce(): String {
        lock.withLock {
            val walletBackend = getWalletBackend()
            return withContext(session) {
                try {
                    walletBackend.getNonce()
                } catch (e: HttpTransport.HttpClientException) {
                    throw WalletClientBackendUnreachableException(e.message, e)
                }
            }
        }
    }

    /**
     * Exchanges a Google authorization code for tokens.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        CancellationException::class
    )
    suspend fun exchangeCodeForTokens(
        nonce: String,
        authorizationCode: String,
        redirectUri: String
    ): GoogleTokens {
        return lock.withLock {
            val walletBackend = getWalletBackend()
            withContext(session) {
                try {
                    walletBackend.exchangeCodeForTokens(nonce, authorizationCode, redirectUri)
                } catch (e: HttpTransport.HttpClientException) {
                    throw WalletClientBackendUnreachableException(e.message, e)
                }
            }
        }
    }

    /**
     * Signs the user in with a Google authorization code.
     *
     * @param nonce the nonce from [getNonce].
     * @param authorizationCode the authorization code from Google.
     * @param redirectUri the redirect URI used in the authorization request.
     * @param signedInUser user information to store.
     * @param walletBackendEncryptionKey the shared encryption key to use for server-side storage.
     * @param resetSharedData if `true`, deletes any existing server-side shared data for the user.
     * @return the access token for Drive access.
     * @throws WalletBackendNonceException if the nonce didn't match.
     * @throws WalletBackendIdTokenException if the ID token didn't verify.
     * @throws WalletBackendEncryptionKeyMismatchException if the shared encryption key doesn't match
     *   what's stored in the wallet backend.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        WalletBackendNonceException::class,
        WalletBackendIdTokenException::class,
        WalletBackendEncryptionKeyMismatchException::class,
        CancellationException::class
    )
    suspend fun signInWithGoogleCode(
        nonce: String,
        authorizationCode: String,
        redirectUri: String,
        signedInUser: WalletClientSignedInUser,
        walletBackendEncryptionKey: ByteString,
        resetSharedData: Boolean
    ): String {
        return lock.withLock {
            val walletBackend = getWalletBackend()
            val accessToken = withContext(session) {
                try {
                    walletBackend.signInWithGoogleCode(
                        nonce = nonce,
                        authorizationCode = authorizationCode,
                        redirectUri = redirectUri,
                        walletServerEncryptionKeySha256 = ByteString(
                            Crypto.digest(Algorithm.SHA256, walletBackendEncryptionKey.toByteArray())
                        ),
                        resetSharedData = resetSharedData,
                        initialSharedData = createInitialSharedData(walletBackendEncryptionKey)
                    )
                } catch (e: HttpTransport.HttpClientException) {
                    throw WalletClientBackendUnreachableException(e.message, e)
                }
            }
            // OK, that worked. Stash the encryption key locally for future use.
            saveEncryptionKey(walletBackendEncryptionKey)
            // Initial load of data
            getSharedDataInternal(
                checkWithWalletBackend = true,
                signedInUser = signedInUser
            )
            _signedInUser.value = localSharedData?.signedInUser
            onSharedDataUpdated()
            accessToken
        }
    }

    /**
     * Signs in with Google.
     *
     * This should be called after the application has signed in the user using [nonce] previously
     * obtained with [getNonce] and retrieved (or created if missing) the shared encryption key
     * from Google Drive.
     *
     * In the unusual case where [org.multipaz.wallet.shared.WalletBackendEncryptionKeyMismatchException]
     * is thrown, one way to deal with this is to ask the user "That didn't work. Would you like to clear
     * the backend data and start over fresh? All passes synced to other devices will be deleted" and
     * then call this method again with [resetSharedData] set to `true. This will effectively delete
     * the shared data entirely and also cause all other signed-in clients to fail decryption causing
     * them to sign out of the account automatically. They can then sign in the account, and hopefully
     * they'll start using the same encryption key...
     *
     * @param nonce the nonce obtained from [getNonce].
     * @param googleIdTokenString the ID token string obtained from Google.
     * @param walletBackendEncryptionKey the shared encryption key from Google Drive.
     * @param resetSharedData normally set to `false`, if set to `true` will reset all shared data
     * effectively causing other clients signed in with the same account to be kicked out.
     * @throws org.multipaz.wallet.shared.WalletBackendNonceException if the nonce didn't match.
     * @throws org.multipaz.wallet.shared.WalletBackendIdTokenException if the ID token didn't verify.
     * @throws org.multipaz.wallet.shared.WalletBackendEncryptionKeyMismatchException if the shared encryption key doesn't match
     *   what's stored in the wallet backend.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        WalletBackendNonceException::class,
        WalletBackendIdTokenException::class,
        WalletBackendEncryptionKeyMismatchException::class,
        CancellationException::class
    )
    suspend fun signInWithGoogle(
        nonce: String,
        googleIdTokenString: String,
        signedInUser: WalletClientSignedInUser,
        walletBackendEncryptionKey: ByteString,
        resetSharedData: Boolean
    ) {
        lock.withLock {
            val walletBackend = getWalletBackend()
            withContext(session) {
                try {
                    walletBackend.signIn(
                        nonce = nonce,
                        googleIdTokenString = googleIdTokenString,
                        walletServerEncryptionKeySha256 = ByteString(
                            Crypto.digest(Algorithm.SHA256, walletBackendEncryptionKey.toByteArray())
                        ),
                        resetSharedData = resetSharedData,
                        initialSharedData = createInitialSharedData(walletBackendEncryptionKey)
                    )
                } catch (e: HttpTransport.HttpClientException) {
                    throw WalletClientBackendUnreachableException(e.message, e)
                }
            }
            // OK, that worked. Stash the encryption key locally for future use.
            saveEncryptionKey(walletBackendEncryptionKey)
            // Initial load of data
            getSharedDataInternal(
                checkWithWalletBackend = true,
                signedInUser = signedInUser
            )
            _signedInUser.value = localSharedData?.signedInUser
            onSharedDataUpdated()
        }
    }

    /**
     * Signs the user out.
     *
     * @throws org.multipaz.wallet.shared.WalletBackendNotSignedInException if not signed in.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        WalletBackendNotSignedInException::class,
        CancellationException::class
    )
    suspend fun signOut() {
        lock.withLock {
            // Clear encryption key and local shared data...
            saveEncryptionKey(null)
            clearLocalSharedData()
            _signedInUser.value = null
            onSharedDataUpdated()
            val walletBackend = getWalletBackend()
            withContext(session) {
                try {
                    walletBackend.signOut()
                } catch (e: HttpTransport.HttpClientException) {
                    throw WalletClientBackendUnreachableException(e.message, e)
                }
            }
        }
    }

    /**
     * Sets a new backend URL.
     *
     * This includes connecting the wallet backend at the given URL. This will fail if unable to
     * connect to the new backend.
     *
     * NOTE: If already signed in, this will sign the user out.
     *
     * @throws WalletClientBackendUnreachableException if unable to reach the new wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the new wallet backend.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        CancellationException::class
    )
    suspend fun setBackendUrl(url: String) {
        // Sign the user out
        try {
            signOut()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Ignoring error while signing user out", e)
        }
        lock.withLock {
            val oldUrl = backendUrl
            backendUrl = url
            _walletBackend = null
            _session = null
            _openID4VCIBackend = null
            try {
                val walletBackend = getWalletBackend()
                withContext(session) {
                    walletBackend.getNonce()
                }
            } catch (e: Exception) {
                Logger.w(TAG, "New backend didn't work, rolling back")
                backendUrl = oldUrl
                _walletBackend = null
                _openID4VCIBackend = null
                throw e
            }
        }
    }


    /**
     * Checks with the wallet backend to see if there's new public data.
     *
     * @return `true` if there's new data (shared or public), `false` otherwise.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        CancellationException::class
    )
    suspend fun refreshPublicData(): Boolean {
        lock.withLock {
            val (publicData, publicDataChanged) = getPublicDataInternal(checkWithWalletBackend = true)
            if (publicDataChanged) {
                onPublicDataUpdated(publicData)
            }
            return publicDataChanged
        }
    }

    /**
     * Checks with the wallet backend to see if there's new shared data.
     *
     * @return `true` if there's new data, `false` otherwise.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     * @throws WalletBackendNotSignedInException if not signed in.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        WalletBackendNotSignedInException::class,
        CancellationException::class
    )
    suspend fun refreshSharedData(): Boolean {
        lock.withLock {
            val (_, sharedDataChanged) = getSharedDataInternal(checkWithWalletBackend = true)
            if (sharedDataChanged) {
                onSharedDataUpdated()
            }
            return sharedDataChanged
        }
    }

    private var localSharedData: LocalSharedData? = null

    internal suspend fun getSharedData(): ByteString {
        lock.withLock {
            return getSharedDataInternal(
                checkWithWalletBackend = false,
                signedInUser = null
            ).first ?: throw WalletBackendNotSignedInException("Not signed in")
        }
    }

    internal suspend fun putSharedData(data: ByteString) {
        lock.withLock {
            if (localSharedData == null) {
                throw WalletBackendNotSignedInException("Not signed in")
            }
            putSharedDataInternal(data)
        }
    }

    internal suspend fun getPublicData(
        checkWithWalletBackend: Boolean,
    ): Pair<WalletClientPublicData, Boolean> {
        lock.withLock {
            return getPublicDataInternal(checkWithWalletBackend = checkWithWalletBackend)
        }
    }

    /**
     * Gets locally shared data.
     *
     * If decryption fails a warning is logged and the user is automatically signed out
     * and [WalletBackendNotSignedInException] is thrown.
     *
     * @param checkWithWalletBackend if `true` calls the wallet backend to see if there's an update.
     * @param signedInUser to set the user who signed, must be set only when first signing in.
     * @return The decrypted local shared data (null if not signed in) and whether a new version
     * was retrieved from the backend.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     * @throws WalletBackendNotSignedInException if not signed in.
     */
    private suspend fun getSharedDataInternal(
        checkWithWalletBackend: Boolean,
        signedInUser: WalletClientSignedInUser? = null
    ): Pair<ByteString?, Boolean> {
        check(lock.isLocked)
        if (!checkWithWalletBackend) {
            return Pair(getSharedDataCached()?.decryptedData, false)
        }
        val walletBackend = getWalletBackend()
        val currentData = getSharedDataCached()
        val getSharedDataResult = withContext(session) {
            val currentVersion = if (currentData != null) {
                Logger.i(TAG, "Checking if backend has newer SharedData, we have version ${currentData.dataVersion}")
                currentData.dataVersion
            } else {
                Logger.i(TAG, "Getting SharedData (we don't have any version)")
                Long.MIN_VALUE
            }
            try {
                walletBackend.getSharedData(currentVersion)
            } catch (e: WalletBackendNotSignedInException) {
                Logger.i(TAG, "Server reports we're not signed in, clearing local state")
                saveEncryptionKey(null)
                clearLocalSharedData()
                _signedInUser.value = null
                onSharedDataUpdated()
                throw e
            } catch (e: HttpTransport.HttpClientException) {
                throw WalletClientBackendUnreachableException(e.message, e)
            }
        }
        if (getSharedDataResult == null) {
            Logger.i(TAG, "Server reports we have the latest version")
            localSharedData = currentData
            return Pair(localSharedData!!.decryptedData, false)
        }
        Logger.i(TAG, "Server gave us version ${getSharedDataResult.version}")

        val decompressedData = if (getSharedDataResult.data.size == 0) {
            byteArrayOf()
        } else {
            val encryptionKey = getEncryptionKey()
            val decryptedData = try {
                Crypto.decrypt(
                    algorithm = Algorithm.A256GCM,
                    key = encryptionKey,
                    nonce = getSharedDataResult.data.substring(0, 12).toByteArray(),
                    messageCiphertext = getSharedDataResult.data.substring(12).toByteArray(),
                )
            } catch (e: IllegalStateException) {
                Logger.w(TAG, "Decryption failed", e)
                saveEncryptionKey(null)
                clearLocalSharedData()
                _signedInUser.value = null
                onSharedDataUpdated()
                throw WalletBackendNotSignedInException("User has been signed out because decryption failed")
            }
            decryptedData.inflate()
        }

        if (currentData == null && signedInUser == null) {
            throw IllegalStateException("currentData is null but signedInUser isn't set")
        }
        val newData = LocalSharedData(
            signedInUser = currentData?.signedInUser ?: signedInUser!!,
            dataVersion = getSharedDataResult.version,
            decryptedData = ByteString(decompressedData)
        )
        val localSharedDataTable = storage.getTable(localSharedDataTableSpec)
        try {
            localSharedDataTable.update(
                key = LOCAL_SHARED_DATA_KEY,
                data = ByteString(newData.toCbor())
            )
        } catch (_: NoRecordStorageException) {
            localSharedDataTable.insert(
                key = LOCAL_SHARED_DATA_KEY,
                data = ByteString(newData.toCbor())
            )
        }
        localSharedData = newData
        return Pair(localSharedData!!.decryptedData, true)
    }

    private suspend fun getSharedDataCached(): LocalSharedData? {
        check(lock.isLocked)
        localSharedData?.let {
            return it
        }
        val localSharedDataTable = storage.getTable(localSharedDataTableSpec)
        val encodedLocalSharedData = localSharedDataTable.get(LOCAL_SHARED_DATA_KEY)
        if (encodedLocalSharedData != null) {
            localSharedData = LocalSharedData.fromCbor(encodedLocalSharedData.toByteArray())
            return localSharedData
        }
        return null
    }

    private suspend fun createInitialSharedData(
        encryptionKey: ByteString
    ): ByteString {
        val data = ByteString()
        val compressedData = data.toByteArray().deflate()
        val nonce = Random.nextBytes(12)
        val encryptedData = Crypto.encrypt(
            algorithm = Algorithm.A256GCM,
            key = encryptionKey.toByteArray(),
            nonce = nonce,
            messagePlaintext = compressedData,
            aad = byteArrayOf()
        )
        return ByteString(nonce + encryptedData)
    }

    private suspend fun putSharedDataInternal(data: ByteString) {
        check(lock.isLocked)
        val walletBackend = getWalletBackend()

        val compressedData = data.toByteArray().deflate()
        val encryptionKey = getEncryptionKey()
        val nonce = Random.nextBytes(12)
        val encryptedData = ByteString(
            Crypto.encrypt(
                algorithm = Algorithm.A256GCM,
                key = encryptionKey,
                nonce = nonce,
                messagePlaintext = compressedData,
                aad = byteArrayOf()
            )
        )

        val newVersion = withContext(session) {
            try {
                walletBackend.putSharedData(ByteString(nonce + encryptedData.toByteArray()))
            } catch (e: HttpTransport.HttpClientException) {
                throw WalletClientBackendUnreachableException(e.message, e)
            }
        }
        val newData = LocalSharedData(
            signedInUser = localSharedData!!.signedInUser,
            dataVersion = newVersion,
            decryptedData = data
        )
        val localSharedDataTable = storage.getTable(localSharedDataTableSpec)
        try {
            localSharedDataTable.update(
                key = LOCAL_SHARED_DATA_KEY,
                data = ByteString(newData.toCbor())
            )
        } catch (_: NoRecordStorageException) {
            localSharedDataTable.insert(
                key = LOCAL_SHARED_DATA_KEY,
                data = ByteString(newData.toCbor())
            )
        }
        localSharedData = newData
    }

    private suspend fun clearLocalSharedData() {
        check(lock.isLocked)
        val localSharedDataTable = storage.getTable(localSharedDataTableSpec)
        localSharedDataTable.delete(
            key = LOCAL_SHARED_DATA_KEY,
        )
        localSharedData = null
    }

    private var _encryptionKey: ByteArray? = null

    private suspend fun saveEncryptionKey(newEncryptionKey: ByteString?) {
        check(lock.isLocked)
        val encryptionKeyTable = storage.getTable(encryptionKeyTableSpec)
        if (newEncryptionKey == null) {
            encryptionKeyTable.delete(
                key = ENCRYPTION_KEY_KEY,
            )
            _encryptionKey = null
            return
        }
        try {
            encryptionKeyTable.update(
                key = ENCRYPTION_KEY_KEY,
                data = newEncryptionKey
            )
        } catch (_: NoRecordStorageException) {
            encryptionKeyTable.insert(
                key = ENCRYPTION_KEY_KEY,
                data = newEncryptionKey
            )
        }
        _encryptionKey = newEncryptionKey.toByteArray()
    }

    private suspend fun getEncryptionKey(): ByteArray {
        check(lock.isLocked)
        _encryptionKey?.let {
            return it
        }

        val encryptionKeyTable = storage.getTable(encryptionKeyTableSpec)
        val encryptionKey = encryptionKeyTable.get(
            key = ENCRYPTION_KEY_KEY
        )
        if (encryptionKey != null) {
            _encryptionKey = encryptionKey.toByteArray()
            return _encryptionKey!!
        }

        throw IllegalStateException("No encryption key found")
    }

    // Returns true if *new* data was retrieved from the backend
    //
    private suspend fun getPublicDataInternal(
        checkWithWalletBackend: Boolean,
    ): Pair<WalletClientPublicData, Boolean> {
        check(lock.isLocked)
        val currentData = getPublicDataCached()
        if (!checkWithWalletBackend && currentData != null) {
            return Pair(currentData, false)
        }
        val walletBackend = getWalletBackend()
        val getPublicDataResult = withContext(session) {
            val currentVersion = if (currentData != null) {
                Logger.i(TAG, "Checking if backend has newer PublicData, we have version ${currentData.version}")
                currentData.version
            } else {
                Logger.i(TAG, "Getting PublicData (we don't have any version)")
                null
            }
            try {
                walletBackend.getPublicData(currentVersion)
            } catch (e: HttpTransport.HttpClientException) {
                throw WalletClientBackendUnreachableException(e.message, e)
            }
        }
        if (getPublicDataResult == null) {
            Logger.i(TAG, "Server reports we have the latest public data")
            return Pair(currentData!!, false)
        }
        Logger.i(TAG, "Server gave us new public data, version ${getPublicDataResult.version}")
        localPublicData = getPublicDataResult
        val localPublicDataTable = storage.getTable(localPublicDataTableSpec)
        try {
            localPublicDataTable.update(
                key = LOCAL_PUBLIC_DATA_KEY,
                data = ByteString(localPublicData!!.toCbor())
            )
        } catch (_: NoRecordStorageException) {
            localPublicDataTable.insert(
                key = LOCAL_PUBLIC_DATA_KEY,
                data = ByteString(localPublicData!!.toCbor())
            )
        }
        return Pair(localPublicData!!, true)
    }

    private var localPublicData: WalletClientPublicData? = null

    private suspend fun getPublicDataCached(): WalletClientPublicData? {
        check(lock.isLocked)
        localPublicData?.let {
            return it
        }
        val localPublicDataTable = storage.getTable(localPublicDataTableSpec)
        val encodedLocalPublicData = localPublicDataTable.get(LOCAL_PUBLIC_DATA_KEY)
        if (encodedLocalPublicData != null) {
            localPublicData = WalletClientPublicData.fromCbor(encodedLocalPublicData.toByteArray())
            return localPublicData
        }
        return null
    }

    private suspend fun clearLocalPublicData() {
        check(lock.isLocked)
        val localPublicDataTable = storage.getTable(localPublicDataTableSpec)
        localPublicDataTable.delete(
            key = LOCAL_PUBLIC_DATA_KEY,
        )
        localPublicData = null
    }

    companion object {
        private const val LOCAL_PUBLIC_DATA_KEY = "LocalPublicData"
        private val localPublicDataTableSpec = StorageTableSpec(
            name = "LocalPublicData",
            supportExpiration = false,
            supportPartitions = false
        )

        private const val LOCAL_SHARED_DATA_KEY = "LocalSharedData"
        private val localSharedDataTableSpec = StorageTableSpec(
            name = "LocalSharedData",
            supportExpiration = false,
            supportPartitions = false
        )

        private const val ENCRYPTION_KEY_KEY = "EncryptionKey"
        private val encryptionKeyTableSpec = StorageTableSpec(
            name = "EncryptionKey",
            supportExpiration = false,
            supportPartitions = false
        )

        /**
         * Creates a new [WalletClient] instance.
         *
         * @param url the URL for the wallet backend.
         * @param secret the secret for the wallet backend.
         * @param storage the storage to use for caching.
         * @param secureArea the [SecureArea] to use for device attestations.
         * @param httpClientEngineFactory a [HttpClientEngineFactory] to use for the HTTP client.
         */
        suspend fun create(
            url: String,
            secret: String,
            storage: Storage,
            secureArea: SecureArea,
            httpClientEngineFactory: HttpClientEngineFactory<*>,
        ): WalletClient {
            val client = WalletClient(
                backendUrl = url,
                secret = secret,
                storage = storage,
                secureArea = secureArea,
                httpClientEngineFactory = httpClientEngineFactory,
                dispatcherAndNotifier = null
            )
            client.initialize()
            return client
        }

        /**
         * Creates a new [WalletClient] instance (for testing only).
         *
         * @param dispatcher the [RpcDispatcher] to use.
         * @param notifier the [RpcNotifier] to use.
         * @param storage the storage to use for caching.
         */
        suspend fun create(
            dispatcher: RpcDispatcher,
            notifier: RpcNotifier,
            storage: Storage
        ): WalletClient {
            val client = WalletClient(
                backendUrl = "http://127.0.0.1",
                secret = "",
                storage = storage,
                secureArea = null,
                httpClientEngineFactory = null,
                dispatcherAndNotifier = Pair(dispatcher, notifier)
            )
            client.initialize()
            return client
        }
    }

    private suspend fun initialize() {
        lock.withLock {
            getSharedDataInternal(checkWithWalletBackend = false)
            _signedInUser.value = localSharedData?.signedInUser
            onSharedDataUpdated()

            // First time this runs it will ping the wallet backend. We don't want this to
            // fail because the user might start the wallet app without actually having an
            // Internet connection.
            //
            // This is all a bit moot because on first run, we'll call refreshPublicData()
            // anyway as part of the setup flow...
            //
            try {
                val (publicData, _) = getPublicDataInternal(checkWithWalletBackend = false)
                onPublicDataUpdated(publicData)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.w(TAG, "Ignoring error getting public data in initialize()", e)
            }
        }
    }

    // ----------------------------------------------------------------------------------------

    /**
     * The trusted issuers according to the backend.
     */
    val issuerTrustManager = ConfigurableTrustManager(
        identifier = "backendIssuerTrustManager",
        entries = emptyList()
    )

    /**
     * The trusted readers according to the backend.
     */
    val readerTrustManager = ConfigurableTrustManager(
        identifier = "backendReaderTrustManager",
        entries = emptyList()
    )

    /**
     * The latest public data retrieved from the backend.
     */
    val publicData: WalletClientPublicData
        get() = localPublicData!!

    private suspend fun onPublicDataUpdated(publicData: WalletClientPublicData) {
        check(lock.isLocked)
        issuerTrustManager.setEntries(publicData.trustedIssuers)
        readerTrustManager.setEntries(publicData.trustedReaders)
    }

    // ----------------------------------------------------------------------------------------

    val _sharedData = MutableStateFlow<WalletClientSharedData?>(null)

    /**
     * Data shared between all logged-in clients or `null` if the user is not signed in.
     */
    val sharedData: StateFlow<WalletClientSharedData?> = _sharedData.asStateFlow()

    private suspend fun onSharedDataUpdated() {
        check(lock.isLocked)
        _sharedData.value = getWalletClientSharedData()
    }

    private suspend fun getWalletClientSharedData(checkWithWalletServer: Boolean = false): WalletClientSharedData? {
        check(lock.isLocked)
        val encodedSharedData = try {
            getSharedDataInternal(checkWithWalletServer).first?.toByteArray() ?: return null
        } catch (e: WalletBackendNotSignedInException) {
            return null
        }
        if (encodedSharedData.isEmpty()) {
            return WalletClientSharedData()
        }
        val walletClientSharedData = WalletClientSharedData.fromCbor(encodedSharedData)
        return walletClientSharedData
    }

    /**
     * Sets new shared data.
     *
     * This data will be pushed to the backend and distributed to other clients.
     *
     * @param sharedData the data to set.
     * @throws WalletBackendNotSignedInException if not signed in.
     * @throws WalletClientBackendUnreachableException if unable to reach the wallet backend.
     * @throws WalletClientAuthorizationException if not authorized to access the wallet backend.
     */
    @Throws(
        WalletClientBackendUnreachableException::class,
        WalletClientAuthorizationException::class,
        WalletBackendNotSignedInException::class,
        CancellationException::class
    )
    suspend fun setSharedData(sharedData: WalletClientSharedData) {
        lock.withLock {
            putSharedDataInternal(ByteString(sharedData.toCbor()))
            _sharedData.value = getWalletClientSharedData()
        }
    }
}

/**
 * A locally stored copy of the shared data.
 *
 * @property dataVersion the version of the data we retrieved from the wallet backend.
 * @property decryptedData the data we received from the wallet backend, after decrypting it.
 * @property signedInUser the user who signed in.
 */
@CborSerializable
internal data class LocalSharedData(
    val signedInUser: WalletClientSignedInUser,
    val dataVersion: Long,
    val decryptedData: ByteString
) {
    companion object
}