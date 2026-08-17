package org.multipaz.wallet.android

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.multipaz.cbor.DataItem
import org.multipaz.compose.branding.Branding
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.context.applicationContext
import org.multipaz.digitalcredentials.DigitalCredentials
import org.multipaz.digitalcredentials.getDefault
import org.multipaz.document.Document
import org.multipaz.document.DocumentBadge
import org.multipaz.document.DocumentBadgeColor
import org.multipaz.document.DocumentStore
import org.multipaz.document.buildDocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.addKnownTypes
import org.multipaz.eventlogger.Event
import org.multipaz.eventlogger.EventPresentment
import org.multipaz.eventlogger.EventPresentmentIso18013Proximity
import org.multipaz.eventlogger.EventVerification
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.mdoc.zkp.longfellow.LongfellowZkSystem
import org.multipaz.presentment.ConsentData
import org.multipaz.presentment.CredentialQueryResult
import org.multipaz.presentment.CredentialSelection
import org.multipaz.presentment.PresentmentSource
import org.multipaz.presentment.SimplePresentmentSource
import org.multipaz.prompt.promptModelRequestConsent
import org.multipaz.provisioning.DocumentProvisioningHandler
import org.multipaz.provisioning.DocumentProvisioningSettings
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.request.Requester
import org.multipaz.request.TrustedRequesterIdentity
import org.multipaz.securearea.AndroidKeystoreCreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.UserAuthenticationType
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.Storage
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.util.Logger
import org.multipaz.util.Platform
import org.multipaz.utopia.knowntypes.addUtopiaTypes
import org.multipaz.wallet.android.navigation.AppNavHost
import org.multipaz.wallet.android.navigation.Destination
import org.multipaz.wallet.android.navigation.MdocUrlVerificationNavHost
import org.multipaz.wallet.android.navigation.VerificationShowResponseDestination
import org.multipaz.wallet.android.navigation.WalletDestination
import org.multipaz.wallet.android.ui.CommunicatingWithBackendDialog
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.clearOnSignOut
import org.multipaz.wallet.client.checkPreconsent
import org.multipaz.wallet.client.containsPreselectedDocuments
import org.multipaz.wallet.client.isProximityReader
import org.multipaz.wallet.client.provisionedDocumentSetupNeeded
import org.multipaz.wallet.client.verification.ProximityReaderModel
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.ClientType
import org.multipaz.wallet.shared.CredentialIssuerSecureAreaType
import org.multipaz.wallet.shared.CredentialIssuerSettings
import org.multipaz.wallet.shared.Domains
import org.multipaz.wallet.shared.Location
import org.multipaz.wallet.shared.fromAndroidLocation
import org.multipaz.wallet.shared.fromCbor
import org.multipaz.wallet.shared.toCbor
import org.multipaz.wallet.shared.toDataItem
import java.security.Security

import org.multipaz.nfc.ExternalNfcReaderStore
import org.multipaz.wallet.android.worker.PeriodicBookkeepingScheduler
import org.multipaz.revocation.CachingRevocationChecker
import org.multipaz.revocation.RevocationChecker
import org.multipaz.wallet.client.DocumentPreconsentSetting
import org.multipaz.wallet.client.runPeriodicBookkeeping
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

enum class RefreshReason {
    STARTUP,
    USER_PULL_TO_REFRESH,
    PERIODIC_WORKER,
    DEVELOPER_SETTINGS,
}

class App private constructor() {

    private lateinit var storage: Storage
    private lateinit var secureArea: SecureArea
    private lateinit var softwareSecureArea: SoftwareSecureArea
    private lateinit var documentTypeRepository: DocumentTypeRepository
    private lateinit var secureAreaRepository: SecureAreaRepository
    private lateinit var zkSystemRepository: ZkSystemRepository
    lateinit var documentStore: DocumentStore
        private set
    private lateinit var documentModel: DocumentModel
    lateinit var eventLogger: SimpleEventLogger
        private set
    private lateinit var presentmentSource: PresentmentSource
    lateinit var provisioningModel: ProvisioningModel
        private set

    lateinit var walletClient: WalletClient
        private set

    lateinit var userIssuerTrustManager: TrustManager
        private set
    private lateinit var issuerTrustManager: CompositeTrustManager
    lateinit var userReaderTrustManager: TrustManager
        private set
    private lateinit var readerTrustManager: CompositeTrustManager
    private lateinit var userIssuerTrustManagerModel: TrustManagerModel
    private lateinit var backendIssuerTrustManagerModel: TrustManagerModel
    private lateinit var userReaderTrustManagerModel: TrustManagerModel
    private lateinit var backendReaderTrustManagerModel: TrustManagerModel
    private lateinit var settingsModel: SettingsModel
    private lateinit var proximityReaderModel: ProximityReaderModel
    private lateinit var revocationChecker: RevocationChecker
    lateinit var externalNfcReaderStore: ExternalNfcReaderStore
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
            documentTypeRepository = documentTypeRepository,
            badgeFunction = ::getBadges
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
                for (identity in requester.requesterIdentities) {
                    val certChain = identity.certChain
                    val trustResult = readerTrustManager.verify(certChain.certificates)
                    if (trustResult.isTrusted) {
                        return@SimplePresentmentSource TrustedRequesterIdentity(
                            identity = identity,
                            trustMetadata = trustResult.trustPoints.first().metadata
                        )
                    }
                }
                return@SimplePresentmentSource null
            },
            showConsentPromptFn = ::showConsentPromptFn,
            getBadgesFn = ::getBadges,
            preferSignatureToKeyAgreement = true,
            domainsMdocSignature = listOf(Domains.DOMAIN_MDOC_USER_AUTH, Domains.DOMAIN_MDOC_SOFTWARE),
            domainsMdocKeyAgreement = emptyList(),
            domainsKeyBoundSdJwt = listOf(Domains.DOMAIN_SDJWT_USER_AUTH, Domains.DOMAIN_SDJWT_SOFTWARE),
            domainsKeylessSdJwt = listOf(Domains.DOMAIN_SDJWT_KEYLESS, Domains.DOMAIN_SDJWT_SOFTWARE),
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
        settingsModel = SettingsModel.create(storage)
        val walletBackendUrl = settingsModel.walletBackendUrl.value ?: BuildConfig.BACKEND_URL
        Logger.i(TAG, "Using wallet backend URL $walletBackendUrl")

        provisioningModel = ProvisioningModel(
            documentProvisioningHandler = DocumentProvisioningHandler(
                documentStore = documentStore,
                secureArea = secureArea,
                defaultDocumentProvisioningSettings = DocumentProvisioningSettings(
                    mdocUserAuthDomain = Domains.DOMAIN_MDOC_USER_AUTH,
                    mdocNoUserAuthDomain = Domains.DOMAIN_MDOC_NO_USER_AUTH,
                    sdJwtUserAuthDomain = Domains.DOMAIN_SDJWT_USER_AUTH,
                    sdJwtNoUserAuthDomain = Domains.DOMAIN_SDJWT_NO_USER_AUTH,
                    sdJwtKeylessDomain = Domains.DOMAIN_SDJWT_KEYLESS,
                    requestNoUserAuth = !settingsModel.disableNoUserAuth.value
                ),
                selectSecureArea = { appData, createKeySettings ->
                    val settings = appData?.let {
                        CredentialIssuerSettings.fromCbor(it.toByteArray())
                    }
                    val targetSecureArea = when (settings?.secureAreaToUse) {
                        CredentialIssuerSecureAreaType.PLATFORM_SECURE_AREA, CredentialIssuerSecureAreaType.CLOUD_SECURE_AREA, null -> secureArea
                    }
                    val aks = settings?.androidKeySettings
                    if (targetSecureArea == secureArea && aks != null) {
                        val builder = AndroidKeystoreCreateKeySettings.Builder(createKeySettings.nonce)
                            .setAlgorithm(aks.algorithm)
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
                                aks.userAuthenticationTimeoutMillis.milliseconds,
                                authTypes
                            )
                        }
                        if (createKeySettings.validFrom != null && createKeySettings.validUntil != null) {
                            builder.setValidityPeriod(createKeySettings.validFrom!!, createKeySettings.validUntil!!)
                        }
                        Pair(targetSecureArea, builder.build())
                    } else {
                        Pair(targetSecureArea, createKeySettings)
                    }
                }
            ),
            httpClient = HttpClient(Android) {
                followRedirects = false
            },
            promptModel = promptModel,
            authorizationSecureArea = secureArea,
            eventLogger = eventLogger
        )
        walletClient = WalletClient.create(
            clientType = ClientType.ANDROID,
            url = walletBackendUrl,
            secret = BuildConfig.BACKEND_SECRET,
            storage = storage,
            secureArea = secureArea,
            httpClientEngineFactory = Android,
            numReaderKeys = 10,
            clientDevice = getAndroidClientDevice(),
            clientPlatform = getAndroidClientPlatform()
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
        documentStore.clearOnSignOut(walletClient, coroutineScope)
        userIssuerTrustManagerModel = TrustManagerModel(userIssuerTrustManager, coroutineScope)
        backendIssuerTrustManagerModel = TrustManagerModel(walletClient.issuerTrustManager, coroutineScope)
        userReaderTrustManagerModel = TrustManagerModel(userReaderTrustManager, coroutineScope)
        backendReaderTrustManagerModel = TrustManagerModel(walletClient.readerTrustManager, coroutineScope)

        externalNfcReaderStore = ExternalNfcReaderStore.create(storage)
        proximityReaderModel = ProximityReaderModel()
        revocationChecker = CachingRevocationChecker(
            storage = storage,
            httpClient = HttpClient(Android)
        )

        PeriodicBookkeepingScheduler.schedulePeriodicBookkeeping(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            combine(
                mainActivityResumed,
                mainActivityCurrentDestination,
                quickAccessWalletFocusedDocumentId
            ) { _, _, _ ->
                focusedDocumentId
            }.distinctUntilChanged().collect { documentId ->
                Logger.i(TAG, "focusedDocumentId changed to '$documentId'")
            }
        }
    }

    private data class PendingPreconsentNotification(
        val documentNames: List<String>,
        val verifierName: String?,
        val cardArtBytes: ByteArray?
    )
    private var pendingPreconsentNotification: PendingPreconsentNotification? = null

    /**
     * Evaluated by [SimplePresentmentSource] to prompt for user consent or attempt auto-approval via preconsent.
     *
     * Preconsent is only processed for proximity readers ([Requester.isProximityReader]). Additionally, preconsent is
     * allowed only if:
     * - [preselectedDocuments] is empty (e.g. the user is outside the app when tapping), OR
     * - at least one of the option sets in [ConsentData.credentialQueryResult] contains a document from [preselectedDocuments].
     *
     * If preselected documents are present but none match any candidate option set in the query result, preconsent
     * is skipped so the user is presented with an explicit consent prompt rather than auto-presenting an out-of-focus
     * document.
     *
     * Candidate selections evaluated by [checkPreconsent] are prioritized according to an effective document order
     * combining any explicitly preselected/focused documents first, followed by the remaining documents ordered according
     * to [DocumentModel].
     */
    private suspend fun showConsentPromptFn(
        requester: Requester,
        trustedRequesterIdentity: TrustedRequesterIdentity?,
        consentData: ConsentData,
        preselectedDocuments: List<Document>,
        onDocumentsInFocus: (documents: List<Document>) -> Unit
    ): CredentialSelection? {
        Logger.i(TAG, "showConsentPromptFn: preselectedDocuments: ${preselectedDocuments.map {
            it.identifier + ": " + it.displayName
        }}")
        // Process preconsent, but only for proximity readers, and only if preselectedDocuments is empty
        // or if at least one candidate selection contains a preselected document.
        val allowPreconsent = requester.isProximityReader && (
            preselectedDocuments.isEmpty() ||
                consentData.credentialQueryResult.containsPreselectedDocuments(preselectedDocuments)
        )
        if (allowPreconsent) {
            val preselectedSet = preselectedDocuments.toSet()
            val effectiveDocumentOrder = preselectedDocuments + documentModel.documentInfos.value
                .map { it.document }
                .filter { it !in preselectedSet }

            consentData.credentialQueryResult.checkPreconsent(
                requester = requester,
                preselectedDocuments = effectiveDocumentOrder,
                domainRewriter = { domain ->
                    if (settingsModel.disableNoUserAuth.value) {
                        domain
                    } else {
                        when (domain) {
                            Domains.DOMAIN_MDOC_USER_AUTH -> Domains.DOMAIN_MDOC_NO_USER_AUTH
                            Domains.DOMAIN_SDJWT_USER_AUTH -> Domains.DOMAIN_SDJWT_NO_USER_AUTH
                            else -> domain
                        }
                    }
                }
            )?.let { selection ->
                onDocumentsInFocus(selection.matches.map { it.credential.document })
                pendingPreconsentNotification = PendingPreconsentNotification(
                    documentNames = selection.matches.mapNotNull {
                        it.credential.document.displayName ?: it.credential.document.typeDisplayName
                    },
                    verifierName = trustedRequesterIdentity?.trustMetadata?.displayName,
                    cardArtBytes = selection.matches.firstOrNull()?.credential?.document?.cardArt?.toByteArray()
                )
                return selection
            }
        }
        pendingPreconsentNotification = null
        // Otherwise fall back to consent prompt...
        return promptModelRequestConsent(
            requester = requester,
            trustedRequesterIdentity = trustedRequesterIdentity,
            consentData = consentData,
            preselectedDocuments = preselectedDocuments,
            onDocumentsInFocus = onDocumentsInFocus
        )
    }

    // Called by DocumentModel whenever a document has changed, to recalculate
    // which badges to use for a document. Also called by SimplePresentmentSource.
    //
    private fun getBadges(document: Document): List<DocumentBadge> {
        val badges = mutableListOf<DocumentBadge>()
        if (document.provisionedDocumentSetupNeeded) {
            badges.add(DocumentBadge(
                text = applicationContext.getString(R.string.badges_setup_required),
                color = DocumentBadgeColor(red = 0, green = 0, blue = 0),
            ))
        }
        return badges
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
    private suspend fun onAddEvent(event: Event): Map<String, DataItem>? {
        if (event is EventPresentment) {
            pendingPreconsentNotification?.let { details ->
                val detailsToPost = details
                pendingPreconsentNotification = null
                CoroutineScope(Dispatchers.Default).launch {
                    delay(5.seconds)
                    postPreconsentNotification(
                        context = applicationContext,
                        documentNames = detailsToPost.documentNames,
                        verifierName = detailsToPost.verifierName,
                        eventId = event.identifier,
                        cardArtBytes = detailsToPost.cardArtBytes
                    )
                }
            }
        }
        if (event !is EventVerification && !settingsModel.eventLoggingEnabled.value) {
            return null
        }
        val map = mutableMapOf<String, DataItem>()
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

    // Only log proximity presentment and proximity verification events
    private val Event.logLocation: Boolean
        get() = this is EventPresentmentIso18013Proximity || (this is EventVerification && this.isProximityPresentment())

    @Composable
    fun MdocUrlVerificationContent(mdocUrl: String) {
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
        val currentBranding = Branding.Current.collectAsState().value
        currentBranding.theme {
            PromptDialogs(
                promptModel = promptModel,
                imageLoader = imageLoader
            )
            if (walletClient.showSetSharedDataSpinner.collectAsState().value) {
                CommunicatingWithBackendDialog()
            }
            MdocUrlVerificationNavHost(
                mdocUrl = mdocUrl,
                walletClient = walletClient,
                secureArea = secureArea,
                promptModel = promptModel,
                documentStore = documentStore,
                documentModel = documentModel,
                documentTypeRepository = documentTypeRepository,
                zkSystemRepository = zkSystemRepository,
                settingsModel = settingsModel,
                externalNfcReaderStore = externalNfcReaderStore,
                eventLogger = eventLogger,
                provisioningModel = provisioningModel,
                proximityReaderModel = proximityReaderModel,
                imageLoader = imageLoader,
                userIssuerTrustManagerModel = userIssuerTrustManagerModel,
                backendIssuerTrustManagerModel = backendIssuerTrustManagerModel,
                userReaderTrustManagerModel = userReaderTrustManagerModel,
                backendReaderTrustManagerModel = backendReaderTrustManagerModel,
                issuerTrustManager = issuerTrustManager,
                readerTrustManager = readerTrustManager,
                revocationChecker = revocationChecker,
                showToast = ::showToast,
                onFinish = { (context as? android.app.Activity)?.finish() }
            )
        }
    }

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
        val coroutineScope = rememberCoroutineScope()

        val currentBranding = Branding.Current.collectAsState().value
        currentBranding.theme {
            PromptDialogs(
                promptModel = promptModel,
                imageLoader = imageLoader
            )
            if (walletClient.showSetSharedDataSpinner.collectAsState().value) {
                CommunicatingWithBackendDialog()
            }
            AppNavHost(
                app = this,
                walletClient = walletClient,
                httpClientEngineFactory = Android,
                storage = storage,
                secureArea = secureArea,
                promptModel = promptModel,
                documentStore = documentStore,
                documentModel = documentModel,
                documentTypeRepository = documentTypeRepository,
                zkSystemRepository = zkSystemRepository,
                settingsModel = settingsModel,
                externalNfcReaderStore = externalNfcReaderStore,
                eventLogger = eventLogger,
                provisioningModel = provisioningModel,
                proximityReaderModel = proximityReaderModel,
                imageLoader = imageLoader,
                userIssuerTrustManagerModel = userIssuerTrustManagerModel,
                backendIssuerTrustManagerModel = backendIssuerTrustManagerModel,
                userReaderTrustManagerModel = userReaderTrustManagerModel,
                backendReaderTrustManagerModel = backendReaderTrustManagerModel,
                issuerTrustManager = issuerTrustManager,
                readerTrustManager = readerTrustManager,
                revocationChecker = revocationChecker,
                mpzPassesToImportChannel = mpzPassesToImportChannel,
                credentialOffers = credentialOffers,
                documentIdToViewChannel = documentIdToViewChannel,
                eventIdToViewChannel = eventIdToViewChannel,
                requestVerificationFlow = requestVerificationFlow,
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

    private val documentIdToViewChannel = Channel<String>(Channel.UNLIMITED)
    private val eventIdToViewChannel = Channel<String>(Channel.UNLIMITED)

    val requestVerificationFlow = MutableStateFlow<Boolean>(false)
    val quickAccessWalletFocusedDocumentId = MutableStateFlow<String?>(null)
    val mainActivityResumed = MutableStateFlow<Boolean>(false)
    val mainActivityCurrentDestination = MutableStateFlow<Destination?>(null)

    /**
     * Gets the document identifier currently in focus for presentment, or `null` if no document is focused.
     *
     * If [MainActivity] is currently active ([mainActivityResumed] is `true`), this checks the current navigation
     * destination ([mainActivityCurrentDestination]). If the destination is a [WalletDestination], it returns
     * that destination's [WalletDestination.documentId].
     *
     * If [MainActivity] is not active, it falls back to returning [quickAccessWalletFocusedDocumentId], which is
     * maintained by [WalletQuickAccessWalletService] when driving the Quick Access Wallet card chooser.
     */
    val focusedDocumentId: String?
        get() {
            val result = if (mainActivityResumed.value) {
                val destination = mainActivityCurrentDestination.value
                if (destination is WalletDestination) {
                    destination.documentId
                } else {
                    null
                }
            } else {
                quickAccessWalletFocusedDocumentId.value
            }
            Logger.d(TAG, "focusedDocumentId returning '$result'")
            return result
        }

    fun viewDocument(documentId: String) {
        CoroutineScope(Dispatchers.Default).launch {
            documentIdToViewChannel.send(documentId)
        }
    }

    fun viewEvent(eventId: String) {
        CoroutineScope(Dispatchers.Default).launch {
            eventIdToViewChannel.send(eventId)
        }
    }

    fun viewRequestVerificationScreen() {
        settingsModel.verificationIsInPerson.value = false
        requestVerificationFlow.value = true
    }

    private val refreshMutex = Mutex()
    private var lastRefreshTimestamp: Instant? = null

    suspend fun refreshWallet(
        reason: RefreshReason = RefreshReason.PERIODIC_WORKER,
    ): Boolean = refreshMutex.withLock {
        Logger.i(TAG, "refreshWallet: starting refresh (reason=$reason)")
        val now = Clock.System.now()

        // Debounce periodic background worker if we recently ran a refresh (e.g. within 15 minutes)
        if (reason == RefreshReason.PERIODIC_WORKER) {
            val last = lastRefreshTimestamp
            if (last != null && (now - last) < 15.minutes) {
                Logger.i(TAG, "refreshWallet: skipping periodic worker run, last refresh was ${now - last} ago")
                return@withLock true
            }
        }

        val preconsentSetting = if (settingsModel.preconsentForNewDocuments.value) {
            DocumentPreconsentSetting.NeverRequireConsent
        } else {
            DocumentPreconsentSetting.AlwaysRequireConsent
        }

        val trigger = when (reason) {
            RefreshReason.STARTUP -> "startup"
            RefreshReason.USER_PULL_TO_REFRESH -> "pull_to_refresh"
            RefreshReason.PERIODIC_WORKER -> "periodic_worker"
            RefreshReason.DEVELOPER_SETTINGS -> "developer_settings"
        }

        val success = walletClient.runPeriodicBookkeeping(
            documentStore = documentStore,
            provisioningModel = provisioningModel,
            trustManagers = listOf(userIssuerTrustManager, userReaderTrustManager),
            eventLogger = eventLogger,
            initialPreconsentSetting = preconsentSetting,
            trigger = trigger,
        )

        try {
            checkVerificationResults(
                walletClient = walletClient,
                storage = storage,
                eventLogger = eventLogger,
                documentTypeRepository = documentTypeRepository,
                zkSystemRepository = zkSystemRepository,
                issuerTrustManager = issuerTrustManager,
                onResponseReceived = { verification ->
                    postNotification(
                        context = applicationContext,
                        verification = verification
                    )
                }
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Error checking verification results during refresh", e)
        }

        lastRefreshTimestamp = Clock.System.now()
        Logger.i(TAG, "refreshWallet: completed (reason=$reason, success=$success)")
        success
    }

    companion object {
        private const val TAG = "App"
        private var app: App? = null
        private val lock = Mutex()

        private const val OID4VCI_CREDENTIAL_OFFER_URL_SCHEME = "openid-credential-offer://"
        private const val HAIP_VCI_URL_SCHEME = "haip-vci://"
        const val ACTION_VIEW_DOCUMENT = "${BuildConfig.ANDROID_APP_ID}.action.viewDocument"
        const val ACTION_VIEW_EVENT = "${BuildConfig.ANDROID_APP_ID}.action.viewEvent"

        private var cryptoInitialized = false
        private val cryptoInitLock = Mutex()

        // Need this for Brainpool support... maybe make it a build setting if some downstream don't want
        // (or can't) use BouncyCastle.
        private suspend fun cryptoInit() {
            cryptoInitLock.withLock {
                if (!cryptoInitialized) {
                    Logger.i(TAG, "Forcing BouncyCastle to the top of the JCA provider list")
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                    Security.insertProviderAt(BouncyCastleProvider(), 1)
                    cryptoInitialized = true
                }
            }
        }

        suspend fun getInstance(): App {
            lock.withLock {
                if (app != null) {
                    return app!!
                }
                cryptoInit()
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

private fun getAndroidClientDevice(): String {
    val manufacturer = android.os.Build.MANUFACTURER
    val model = android.os.Build.MODEL
    return if (model.lowercase().startsWith(manufacturer.lowercase())) {
        model
    } else {
        val formattedManufacturer = manufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
        }
        "$formattedManufacturer $model"
    }
}

private fun getAndroidClientPlatform(): String {
    return "Android ${android.os.Build.VERSION.RELEASE}"
}
