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
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.mpzpass.MpzPass
import org.multipaz.prompt.PromptModel
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.util.Logger
import org.multipaz.wallet.android.App
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.signin.SignInWithGoogle
import org.multipaz.wallet.android.signin.SignInWithGoogleDismissedException
import org.multipaz.wallet.android.signin.rememberSignInWithGoogle
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientBackendUnreachableException
import org.multipaz.wallet.client.WalletClientSignedInUser
import org.multipaz.wallet.client.syncWithSharedData
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
    promptModel: PromptModel,
    documentStore: DocumentStore,
    documentModel: DocumentModel,
    documentTypeRepository: DocumentTypeRepository,
    settingsModel: SettingsModel,
    eventLogger: SimpleEventLogger,
    provisioningModel: ProvisioningModel,
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
    val navController = rememberNavController()
    val context = LocalContext.current
    val isSigningIn = remember { mutableStateOf(false) }
    val isSigningOut = remember { mutableStateOf(false) }

    fun getTrustManagerModelFromId(identifier: String): TrustManagerModel {
        return when (identifier) {
            "backendIssuerTrustManager" -> backendIssuerTrustManagerModel
            "userIssuerTrustManager" -> userIssuerTrustManagerModel
            "backendReaderTrustManager" -> backendReaderTrustManagerModel
            "userReaderTrustManager" -> userReaderTrustManagerModel
            else -> throw IllegalStateException("Unexpected TrustManager id $identifier")
        }
    }

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
            navController.navigate(ProvisioningDestination(
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
                        navController.navigate(ErrorDialogDestination(
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

                    navController.navigate(
                        WalletDestination(
                            documentId = document.identifier,
                            justAddedAtMillis = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                navController.navigate(ErrorDialogDestination(
                    title = context.getString(R.string.app_navigation_error_importing_pass_title),
                    textMarkdown = context.getString(R.string.app_navigation_error_importing_pass_something_went_wrong, e.toString())
                ))
            }
        }
    }

    LaunchedEffect(true) {
        while (true) {
            val documentId = documentIdToViewChannel.receive()
            navController.navigate(WalletDestination(documentId = documentId))
        }
    }

    val startDestination: Destination = if (settingsModel.firstTimeSetupDone.collectAsState().value) {
        MainGraph
    } else {
        SetupGraph
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
        setupGraph(
            navController = navController,
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
        mainGraph(
            navController = navController,
            walletClient = walletClient,
            documentStore = documentStore,
            documentModel = documentModel,
            settingsModel = settingsModel,
            eventLogger = eventLogger,
            documentTypeRepository = documentTypeRepository,
            provisioningModel = provisioningModel,
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
            isSigningIn = isSigningIn,
            isSigningOut = isSigningOut,
            onSignIn = ::signIn,
            onSignOut = ::signOut
        )
    }
}

internal suspend fun signIn(
    context: Context,
    walletClient: WalletClient,
    signInWithGoogle: SignInWithGoogle,
    navController: NavController,
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
        navController.navigate(SignInClearEncryptionKeyDialogDestination)
    } catch (_: WalletClientBackendUnreachableException) {
        navController.navigate(ErrorDialogDestination(
            title = context.getString(R.string.app_navigation_error_signing_in_unreachable_title),
            textMarkdown = context.getString(R.string.app_navigation_error_signing_in_unreachable_text)
        ))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        navController.navigate(ErrorDialogDestination(
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
            Logger.i(TAG, "Failed refreshing with wallet backend at start-up, it's unreachable", e)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Unexpected exception at start-up while refreshing", e)
        }
    }
    try {
        Logger.i(TAG, "Refreshing public data with wallet backend at start-up")
        walletClient.refreshPublicData()
    } catch (e: WalletClientBackendUnreachableException) {
        Logger.i(TAG, "Failed refreshing with wallet backend at start-up, it's unreachable", e)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Unexpected exception at start-up while refreshing", e)
    }
}