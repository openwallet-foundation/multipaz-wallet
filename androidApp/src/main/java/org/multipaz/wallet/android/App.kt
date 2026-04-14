package org.multipaz.wallet.android

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.google.android.gms.location.LocationServices
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.branding.Branding
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.compose.provisioning.ProvisioningBottomSheet
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.context.applicationContext
import org.multipaz.digitalcredentials.DigitalCredentials
import org.multipaz.digitalcredentials.getDefault
import org.multipaz.document.DocumentStore
import org.multipaz.document.buildDocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.addKnownTypes
import org.multipaz.eventlogger.Event
import org.multipaz.eventlogger.EventPresentmentIso18013Proximity
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.mdoc.zkp.longfellow.LongfellowZkSystem
import org.multipaz.presentment.PresentmentSource
import org.multipaz.presentment.SimplePresentmentSource
import org.multipaz.prompt.promptModelRequestConsent
import org.multipaz.provisioning.DocumentProvisioningHandler
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.Storage
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.Logger
import org.multipaz.util.Platform
import org.multipaz.utopia.knowntypes.addUtopiaTypes
import org.multipaz.wallet.android.navigation.AppNavHost
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.Location
import org.multipaz.wallet.shared.fromAndroidLocation
import org.multipaz.wallet.shared.toDataItem

class App private constructor() {

    private lateinit var storage: Storage
    private lateinit var secureArea: SecureArea
    private lateinit var softwareSecureArea: SoftwareSecureArea
    private lateinit var documentTypeRepository: DocumentTypeRepository
    private lateinit var secureAreaRepository: SecureAreaRepository
    private lateinit var zkSystemRepository: ZkSystemRepository
    private lateinit var documentStore: DocumentStore
    private lateinit var documentModel: DocumentModel
    private lateinit var eventLogger: SimpleEventLogger
    private lateinit var presentmentSource: PresentmentSource
    private lateinit var provisioningModel: ProvisioningModel

    private lateinit var walletClient: WalletClient

    private lateinit var userIssuerTrustManager: TrustManager
    private lateinit var issuerTrustManager: CompositeTrustManager
    private lateinit var userReaderTrustManager: TrustManager
    private lateinit var readerTrustManager: CompositeTrustManager
    private lateinit var userIssuerTrustManagerModel: TrustManagerModel
    private lateinit var backendIssuerTrustManagerModel: TrustManagerModel
    private lateinit var userReaderTrustManagerModel: TrustManagerModel
    private lateinit var backendReaderTrustManagerModel: TrustManagerModel
    private lateinit var settingsModel: SettingsModel
    private val promptModel = Platform.promptModel

    private val credentialOffers = Channel<String>()

    private suspend fun initialize() {
        storage = Platform.nonBackedUpStorage
        secureArea = Platform.getSecureArea(storage)
        softwareSecureArea = SoftwareSecureArea.create(storage)
        secureAreaRepository = SecureAreaRepository.Builder()
            .add(secureArea)
            .add(softwareSecureArea)
            .build()
        zkSystemRepository = ZkSystemRepository()
        val longfellow = LongfellowZkSystem()
        longfellow.addDefaultCircuits()
        zkSystemRepository.add(longfellow)
        documentTypeRepository = DocumentTypeRepository()
        documentTypeRepository.addKnownTypes()
        documentTypeRepository.addUtopiaTypes()
        documentStore = buildDocumentStore(storage = storage, secureAreaRepository = secureAreaRepository) {}
        documentModel = DocumentModel.create(
            documentStore = documentStore,
            documentTypeRepository = documentTypeRepository
        )
        eventLogger = SimpleEventLogger(
            storage = storage,
            onAddEvent = ::onAddEvent
        )
        presentmentSource = SimplePresentmentSource(
            documentStore = documentStore,
            documentTypeRepository = documentTypeRepository,
            zkSystemRepository = zkSystemRepository,
            eventLogger = eventLogger,
            resolveTrustFn = { requester ->
                requester.certChain?.let { certChain ->
                    val trustResult = readerTrustManager.verify(certChain.certificates)
                    if (trustResult.isTrusted) {
                        return@SimplePresentmentSource trustResult.trustPoints.first().metadata
                    }
                }
                return@SimplePresentmentSource null
            },
            showConsentPromptFn = ::promptModelRequestConsent,
            preferSignatureToKeyAgreement = true,
            domainsMdocSignature = listOf(DOMAIN_MDOC_USER_AUTH, DOMAIN_MDOC_SOFTWARE),
            domainsMdocKeyAgreement = emptyList(),
            domainsKeyBoundSdJwt = listOf(DOMAIN_SDJWT_USER_AUTH, DOMAIN_SDJWT_SOFTWARE),
            domainsKeylessSdJwt = listOf(DOMAIN_SDJWT_KEYLESS, DOMAIN_SDJWT_SOFTWARE),
        )
        val digitalCredentials = DigitalCredentials.getDefault()
        if (digitalCredentials.registerAvailable) {
            try {
                digitalCredentials.register(
                    documentStore = documentStore,
                    documentTypeRepository = documentTypeRepository,
                )
            } catch (e: Throwable) {
                Logger.w(TAG, "Error registering with W3C DC API", e)
            }

            // Re-register if document store changes...
            CoroutineScope(Dispatchers.IO).launch {
                documentStore.eventFlow
                    .onEach { event ->
                        Logger.i(
                            TAG,
                            "DocumentStore event ${event::class.simpleName} ${event.documentId}"
                        )
                        try {
                            digitalCredentials.register(
                                documentStore = documentStore,
                                documentTypeRepository = documentTypeRepository,
                            )
                        } catch (e: Throwable) {
                            Logger.w(TAG, "Error registering with W3C DC API", e)
                        }
                    }
                    .launchIn(this)
            }
        }
        provisioningModel = ProvisioningModel(
            documentProvisioningHandler = DocumentProvisioningHandler(
                documentStore = documentStore,
                secureArea = secureArea
            ),
            httpClient = HttpClient(Android) {
                followRedirects = false
            },
            promptModel = promptModel,
            authorizationSecureArea = secureArea
        )

        settingsModel = SettingsModel.create(storage)
        val walletBackendUrl = settingsModel.walletBackendUrl.value ?: BuildConfig.BACKEND_URL
        Logger.i(TAG, "Using wallet backend URL $walletBackendUrl")
        walletClient = WalletClient.create(
            url = walletBackendUrl,
            secret = BuildConfig.BACKEND_SECRET,
            storage = storage,
            secureArea = secureArea,
            httpClientEngineFactory = Android
        )

        userIssuerTrustManager = TrustManager(
            storage = storage,
            identifier = "userIssuerTrustManager"
        )
        issuerTrustManager = CompositeTrustManager(
            trustManagers = listOf(walletClient.issuerTrustManager, userIssuerTrustManager),
            identifier = "issuerTrustManager"
        )

        userReaderTrustManager = TrustManager(
            storage = storage,
            identifier = "userReaderTrustManager"
        )
        readerTrustManager = CompositeTrustManager(
            trustManagers = listOf(walletClient.readerTrustManager, userReaderTrustManager),
            identifier = "readerTrustManager"
        )

        val coroutineScope = CoroutineScope(Dispatchers.IO)
        userIssuerTrustManagerModel = TrustManagerModel(userIssuerTrustManager, coroutineScope)
        backendIssuerTrustManagerModel = TrustManagerModel(walletClient.issuerTrustManager, coroutineScope)
        userReaderTrustManagerModel = TrustManagerModel(userReaderTrustManager, coroutineScope)
        backendReaderTrustManagerModel = TrustManagerModel(walletClient.readerTrustManager, coroutineScope)
    }

    private var currentToast: Toast? = null

    private fun showToast(message: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        currentToast!!.show()
    }

    // Called by [SimpleEventLogger] whenever an event is added
    //
    // Should return `null` to drop the event, otherwise application-specific data which will be
    // amended to the event.
    //
    private suspend fun onAddEvent(event: Event): Map<String, org.multipaz.cbor.DataItem>? {
        if (!settingsModel.eventLoggingEnabled.value) {
            return null
        }
        val map = mutableMapOf<String, org.multipaz.cbor.DataItem>()
        if (settingsModel.eventLoggingLocationEnabled.value && event.logLocation) {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
                    val androidLocation = fusedLocationClient.lastLocation.await()
                    if (androidLocation != null) {
                        val location = Location.fromAndroidLocation(androidLocation)
                        map["location"] = location.toDataItem()
                    } else {
                        Logger.w(TAG, "location not available")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Logger.w(TAG, "Error getting location", e)
                }
            }
        }
        return map
    }

    // Only log proximity presentment events
    private val Event.logLocation: Boolean
        get() = this is EventPresentmentIso18013Proximity

    @Composable
    fun Content() {
        val context = LocalPlatformContext.current
        val imageLoader = remember {
            val engineFactory = Android
            val httpClient = HttpClient(engineFactory.create()) {
            }
            ImageLoader.Builder(context)
                .components {
                    add(KtorNetworkFetcherFactory(httpClient))
                }
                .build()
        }

        LaunchedEffect(true) {
            while (true) {
                val credentialOffer = credentialOffers.receive()
                if (provisioningModel.isActive) {
                    Logger.e(TAG, "Provisioning is already in progress")
                } else {
                    provisioningModel.launchOpenID4VCIProvisioning(
                        offerUri = credentialOffer,
                        clientPreferences = walletClient.getOpenID4VCIClientPreferences(),
                        backend = walletClient.getOpenID4VCIBackend()
                    )
                }
            }
        }

        val currentBranding = Branding.Current.collectAsState().value
        currentBranding.theme {
            PromptDialogs(
                promptModel = promptModel,
                imageLoader = imageLoader
            )
            ProvisioningBottomSheet(
                provisioningModel = provisioningModel,
                waitForRedirectLinkInvocation = { state ->
                    walletClient.waitForAppLinkInvocation(state)
                },
                onFinishedProvisioning = { document, isNewlyIssued ->
                    if (document != null && isNewlyIssued) {
                        // TODO:  navController.navigate(DocumentViewerDestination(document.identifier))
                    }
                },
                issuerUrl = null,
                clientPreferences = flow { walletClient.getOpenID4VCIClientPreferences() },
                backend = flow { walletClient.getOpenID4VCIBackend() }
            )
            AppNavHost(
                walletClient = walletClient,
                promptModel = promptModel,
                documentStore = documentStore,
                documentModel = documentModel,
                documentTypeRepository = documentTypeRepository,
                settingsModel = settingsModel,
                eventLogger = eventLogger,
                provisioningModel = provisioningModel,
                imageLoader = imageLoader,
                userIssuerTrustManagerModel = userIssuerTrustManagerModel,
                backendIssuerTrustManagerModel = backendIssuerTrustManagerModel,
                userReaderTrustManagerModel = userReaderTrustManagerModel,
                backendReaderTrustManagerModel = backendReaderTrustManagerModel,
                issuerTrustManager = issuerTrustManager,
                readerTrustManager = readerTrustManager,
                mpzPassesToImportChannel = mpzPassesToImportChannel,
                documentIdToViewChannel = documentIdToViewChannel,
                showToast = ::showToast
            )
        }
    }

    private val mpzPassesToImportChannel = Channel<ByteString>()

    fun importMpzPass(encodedMpzPass: ByteArray) {
        CoroutineScope(Dispatchers.Default).launch {
            mpzPassesToImportChannel.send(ByteString(encodedMpzPass))
        }
    }

    /**
     * Handle a link (either a app link, universal link or custom URL schema link).
     */
    fun handleUrl(url: String) {
        Logger.i(TAG, "Handling URL $url")
        if (url.startsWith(OID4VCI_CREDENTIAL_OFFER_URL_SCHEME)
            || url.startsWith(HAIP_VCI_URL_SCHEME)) {
            val queryIndex = url.indexOf('?')
            if (queryIndex >= 0) {
                CoroutineScope(Dispatchers.Default).launch {
                    credentialOffers.send(url)
                }
            }
        } else if (url.startsWith(walletClient.appLinkBaseUrl)) {
            CoroutineScope(Dispatchers.Default).launch {
                walletClient.processAppLinkInvocation(url)
            }
        } else {
            Logger.e(TAG, "Unhandled URL: '$url'")
        }
    }

    private val documentIdToViewChannel = Channel<String>()

    fun viewDocument(documentId: String) {
        CoroutineScope(Dispatchers.Default).launch {
            documentIdToViewChannel.send(documentId)
        }
    }

    companion object {
        private const val TAG = "App"
        private var app: App? = null
        private val lock = Mutex()

        private const val OID4VCI_CREDENTIAL_OFFER_URL_SCHEME = "openid-credential-offer://"
        private const val HAIP_VCI_URL_SCHEME = "haip-vci://"

        const val DOMAIN_MDOC_USER_AUTH = "mdoc_user_auth"
        const val DOMAIN_MDOC_SOFTWARE = "mdoc_software"
        const val DOMAIN_SDJWT_USER_AUTH = "sdjwt_user_auth"
        const val DOMAIN_SDJWT_KEYLESS = "sdjwt_keyless"
        const val DOMAIN_SDJWT_SOFTWARE = "sdjwt_software"

        const val ACTION_VIEW_DOCUMENT = "org.multipaz.wallet.android.action.viewDocument"

        suspend fun getInstance(): App {
            lock.withLock {
                if (app != null) {
                    return app!!
                }
                app = App()
                app!!.initialize()
                return app!!
            }
        }

        suspend fun getPresentmentSource(): PresentmentSource {
            // TODO: optimize this so we only initialize what's needed for the PresentmentSource
            val app = getInstance()
            return app.presentmentSource
        }
    }
}
