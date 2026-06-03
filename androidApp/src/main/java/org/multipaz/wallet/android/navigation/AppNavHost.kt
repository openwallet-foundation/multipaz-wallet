package org.multipaz.wallet.android.navigation

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.compose.cards.rememberVerticalCardListState
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.mpzpass.MpzPass
import org.multipaz.prompt.PromptModel
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.signin.SignInWithGoogle
import org.multipaz.wallet.android.signin.SignInWithGoogleDismissedException
import org.multipaz.wallet.android.signin.rememberSignInWithGoogle
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientBackendUnreachableException
import org.multipaz.wallet.client.WalletClientSignedInUser
import org.multipaz.wallet.client.syncWithSharedData
import org.multipaz.wallet.client.verification.ProximityReaderModel
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.Domains
import org.multipaz.wallet.shared.WalletBackendEncryptionKeyMismatchException
import org.multipaz.wallet.shared.WalletBackendNotSignedInException
import kotlin.time.Clock

private const val TAG = "AppNavHost"

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun AppNavHost(
    walletClient: WalletClient,
    httpClientEngineFactory: HttpClientEngineFactory<*>,
    storage: Storage,
    secureArea: SecureArea,
    promptModel: PromptModel,
    documentStore: DocumentStore,
    documentModel: DocumentModel,
    documentTypeRepository: DocumentTypeRepository,
    zkSystemRepository: ZkSystemRepository,
    settingsModel: SettingsModel,
    eventLogger: SimpleEventLogger,
    provisioningModel: ProvisioningModel,
    proximityReaderModel: ProximityReaderModel,
    imageLoader: ImageLoader,
    userIssuerTrustManagerModel: TrustManagerModel,
    backendIssuerTrustManagerModel: TrustManagerModel,
    userReaderTrustManagerModel: TrustManagerModel,
    backendReaderTrustManagerModel: TrustManagerModel,
    issuerTrustManager: CompositeTrustManager,
    readerTrustManager: CompositeTrustManager,
    mpzPassesToImportChannel: Channel<ByteString>,
    credentialOffers: Channel<String>,
    documentIdToViewChannel: Channel<String>,
    showToast: (message: String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val signInWithGoogle = rememberSignInWithGoogle()
    val context = LocalContext.current
    val isSigningIn = remember { mutableStateOf(false) }
    val isSigningOut = remember { mutableStateOf(false) }
    val verticalCardListState = rememberVerticalCardListState()

    val startDestination: Destination = if (settingsModel.firstTimeSetupDone.collectAsState().value) {
        WalletDestination()
    } else {
        SetupWelcomeScreenDestination
    }
    val backStack = rememberNavBackStack(startDestination)

    LaunchedEffect(Unit) {
        // TODO: Only run this code the first time this screen is shown
        if (settingsModel.firstTimeSetupDone.value) {
            appJustLaunched(
                walletClient = walletClient,
                documentStore = documentStore
            )
        }
    }

    LaunchedEffect(true) {
        while (true) {
            val credentialOffer = credentialOffers.receive()
            backStack.add(ProvisioningDestination(
                credentialIssuer = null,
                openID4VCICredentialOfferUri = credentialOffer,
                openID4VCIIssuerUrl = null
            ))
        }
    }

    LaunchedEffect(true) {
        while (true) {
            val encodedPass = mpzPassesToImportChannel.receive()
            try {
                var pass: MpzPass? = MpzPass.fromDataItem(Cbor.decode(encodedPass.toByteArray()))
                val existingDoc = documentStore.listDocuments().find { it.mpzPassId == pass!!.uniqueId }
                existingDoc?.mpzPassVersion?.let { existingVersion ->
                    if (existingVersion >= pass!!.version) {
                        backStack.add(ErrorDialogDestination(
                            title = context.getString(R.string.app_navigation_error_importing_pass_title),
                            textMarkdown = context.getString(R.string.app_navigation_error_importing_pass_already_in_wallet)
                        ))
                        pass = null
                    }
                }
                if (pass != null) {
                    // Update shared data... this can go wrong if e.g. we don't have an Internet connection
                    // or the wallet backend is down. This means that you need to be online to import a pass,
                    // we may want to relax this requirement in the future.
                    //
                    walletClient.sharedData.value?.let { sharedData ->
                        walletClient.refreshSharedData()
                        walletClient.setSharedData(
                            sharedData
                                .removeMpzPass(pass)
                                .addMpzPass(pass)
                        )
                    }

                    // ... then import it locally
                    val document = documentStore.importMpzPass(
                        mpzPass = pass,
                        isoMdocDomain = Domains.DOMAIN_MDOC_SOFTWARE,
                        sdJwtVcDomain = Domains.DOMAIN_SDJWT_SOFTWARE,
                        keylessSdJwtVcDomain = Domains.DOMAIN_SDJWT_KEYLESS
                    )

                    backStack.add(
                        WalletDestination(
                            documentId = document.identifier,
                            justAddedAtMillis = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                backStack.add(ErrorDialogDestination(
                    title = context.getString(R.string.app_navigation_error_importing_pass_title),
                    textMarkdown = context.getString(R.string.app_navigation_error_importing_pass_something_went_wrong, e.toString())
                ))
            }
        }
    }

    LaunchedEffect(true) {
        while (true) {
            val documentId = documentIdToViewChannel.receive()
            backStack.add(WalletDestination(documentId = documentId))
        }
    }

    val setupEntryProvider = setupGraph(
        backStack = backStack,
        walletClient = walletClient,
        documentStore = documentStore,
        settingsModel = settingsModel,
        signInWithGoogle = signInWithGoogle,
        coroutineScope = coroutineScope,
        context = context,
        showToast = showToast,
        onAppJustLaunched = ::appJustLaunched,
        onSignIn = ::signIn,
        onSignOut = ::signOut
    )
    val mainEntryProvider = mainGraph(
        backStack = backStack,
        verticalCardListState = verticalCardListState,
        walletClient = walletClient,
        httpClientEngineFactory = httpClientEngineFactory,
        storage = storage,
        secureArea = secureArea,
        documentStore = documentStore,
        documentModel = documentModel,
        settingsModel = settingsModel,
        eventLogger = eventLogger,
        documentTypeRepository = documentTypeRepository,
        zkSystemRepository = zkSystemRepository,
        provisioningModel = provisioningModel,
        proximityReaderModel = proximityReaderModel,
        imageLoader = imageLoader,
        promptModel = promptModel,
        signInWithGoogle = signInWithGoogle,
        mpzPassesToImportChannel = mpzPassesToImportChannel,
        coroutineScope = coroutineScope,
        context = context,
        showToast = showToast,
        backendIssuerTrustManagerModel = backendIssuerTrustManagerModel,
        userIssuerTrustManagerModel = userIssuerTrustManagerModel,
        backendReaderTrustManagerModel = backendReaderTrustManagerModel,
        userReaderTrustManagerModel = userReaderTrustManagerModel,
        readerTrustManager = readerTrustManager,
        issuerTrustManager = issuerTrustManager,
        isSigningIn = isSigningIn,
        isSigningOut = isSigningOut,
        onSignIn = ::signIn,
        onSignOut = ::signOut
    )

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        sceneStrategies = listOf(DialogSceneStrategy()),
        modifier = Modifier.fillMaxSize(),
        entryProvider = { key ->
            setupEntryProvider(key) ?: mainEntryProvider(key)
                ?: throw IllegalStateException("No entry for $key")
        }
    )
}

internal suspend fun signIn(
    context: Context,
    walletClient: WalletClient,
    signInWithGoogle: SignInWithGoogle,
    backStack: MutableList<NavKey>,
    explicitSignIn: Boolean,
    resetEncryptionKey: Boolean,
) {
    try {
        signInUnguarded(
            walletClient = walletClient,
            signInWithGoogle = signInWithGoogle,
            explicitSignIn = explicitSignIn,
            resetEncryptionKey = resetEncryptionKey,
        )
    } catch (_: SignInWithGoogleDismissedException) {
        /* Do nothing */
    } catch (_: WalletBackendEncryptionKeyMismatchException) {
        backStack.add(SignInClearEncryptionKeyDialogDestination)
    } catch (_: WalletClientBackendUnreachableException) {
        backStack.add(ErrorDialogDestination(
            title = context.getString(R.string.app_navigation_error_signing_in_unreachable_title),
            textMarkdown = context.getString(R.string.app_navigation_error_signing_in_unreachable_text)
        ))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        backStack.add(ErrorDialogDestination(
            title = context.getString(R.string.app_navigation_error_signing_in_unreachable_title),
            textMarkdown = context.getString(R.string.app_navigation_error_signing_in_text, e.toString())
        ))
    }
}

private suspend fun signInUnguarded(
    walletClient: WalletClient,
    signInWithGoogle: SignInWithGoogle,
    explicitSignIn: Boolean,
    resetEncryptionKey: Boolean
) {
    val nonce = walletClient.getNonce()
    val signInResult = signInWithGoogle.signIn(
        explicitSignIn = explicitSignIn,
        serverClientId = BuildConfig.BACKEND_CLIENT_ID,
        nonce = nonce,
        httpClientEngineFactory = Android,
        resetEncryptionKey = resetEncryptionKey
    )
    walletClient.signInWithGoogle(
        nonce = nonce,
        googleIdTokenString = signInResult.googleIdTokenString,
        signedInUser = WalletClientSignedInUser(
            id = signInResult.signInData.id,
            displayName = signInResult.signInData.displayName,
            profilePicture = signInResult.signInData.profilePicture
        ),
        walletBackendEncryptionKey = signInResult.walletServerEncryptionKey,
        resetSharedData = resetEncryptionKey
    )
}

internal suspend fun signOut(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    signInWithGoogle: SignInWithGoogle
) {
    // We specifically allow signing out _even_ if you're not online. The only thing that
    // can fail is that WalletClient fails to notify wallet backend but that is OK
    Logger.i(TAG, "signOut()")
    settingsModel.explicitlySignedOut.value = true
    signInWithGoogle.signedOut()
    walletClient.signOut()
}

// Code which runs when the app has just launched..
//
//
internal suspend fun appJustLaunched(
    walletClient: WalletClient,
    documentStore: DocumentStore
) {
    Logger.i(TAG, "Running code the first time app is launched...")
    if (walletClient.signedInUser.value != null) {
        try {
            Logger.i(TAG, "Refreshing shared data with wallet backend at start-up")
            walletClient.refreshSharedData()
            documentStore.syncWithSharedData(
                sharedData = walletClient.sharedData.value!!,
                mpzPassIsoMdocDomain = Domains.DOMAIN_MDOC_SOFTWARE,
                mpzPassSdJwtVcDomain = Domains.DOMAIN_SDJWT_SOFTWARE,
                mpzPassKeylessSdJwtVcDomain = Domains.DOMAIN_SDJWT_KEYLESS
            )
        } catch (e: WalletBackendNotSignedInException) {
            Logger.i(TAG, "Failed refreshing with wallet backend, not signed in", e)
        } catch (e: WalletClientBackendUnreachableException) {
            Logger.i(TAG, "Failed refreshing shared data with wallet backend at start-up, it's unreachable", e)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Unexpected exception refreshing shared data at start-up", e)
        }
    }

    try {
        Logger.i(TAG, "Refreshing public data with wallet backend at start-up")
        walletClient.refreshPublicData()
    } catch (e: WalletClientBackendUnreachableException) {
        Logger.i(TAG, "Failed refreshing public data with wallet backend at start-up, it's unreachable", e)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Unexpected exception refreshing public data at start-up", e)
    }

    try {
        Logger.i(TAG, "Refreshing reader keys at start-up")
        walletClient.refreshReaderKeys()
    } catch (e: WalletClientBackendUnreachableException) {
        Logger.i(TAG, "Failed refreshing reader keys with wallet backend at start-up, it's unreachable", e)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Unexpected exception refreshing reader keys at start-up", e)
    }
}
