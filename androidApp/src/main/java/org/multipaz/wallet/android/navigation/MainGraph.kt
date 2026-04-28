package org.multipaz.wallet.android.navigation

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import coil3.ImageLoader
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.prompt.PromptModel
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.wallet.android.App
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.isForDocumentId
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.signin.SignInWithGoogle
import org.multipaz.wallet.android.ui.settings.AboutScreen
import org.multipaz.wallet.android.ui.provisioning.AddToWalletScreen
import org.multipaz.wallet.android.ui.CertificateViewerScreen
import org.multipaz.wallet.android.ui.ConfirmationDialog
import org.multipaz.wallet.android.ui.DocumentQrPresentmentDialog
import org.multipaz.wallet.android.ui.ErrorDialog
import org.multipaz.wallet.android.ui.SignInClearEncryptionKeyDialog
import org.multipaz.wallet.android.ui.SignOutConfirmationDialog
import org.multipaz.wallet.android.ui.WalletScreen
import org.multipaz.wallet.android.ui.document.CredentialInfoScreen
import org.multipaz.wallet.android.ui.document.DocumentEventListScreen
import org.multipaz.wallet.android.ui.document.DocumentInfoExtrasScreen
import org.multipaz.wallet.android.ui.document.DocumentInfoScreen
import org.multipaz.wallet.android.ui.provisioning.ProvisioningRoute
import org.multipaz.wallet.android.ui.settings.ActivityLoggingSettingsScreen
import org.multipaz.wallet.android.ui.settings.DeveloperSettingsConfigureWalletBackendDialog
import org.multipaz.wallet.android.ui.settings.DeveloperSettingsConnectToWalletServerDialog
import org.multipaz.wallet.android.ui.settings.DeveloperSettingsScreen
import org.multipaz.wallet.android.ui.settings.EventListScreen
import org.multipaz.wallet.android.ui.settings.EventViewerScreen
import org.multipaz.wallet.android.ui.settings.SettingsScreen
import org.multipaz.wallet.android.ui.settings.TrustEntryEditScreen
import org.multipaz.wallet.android.ui.settings.TrustEntryRicalEntryScreen
import org.multipaz.wallet.android.ui.settings.TrustEntryScreen
import org.multipaz.wallet.android.ui.settings.TrustEntryVicalEntryScreen
import org.multipaz.wallet.android.ui.settings.TrustManagerScreen
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientBackendUnreachableException
import org.multipaz.wallet.client.syncWithSharedData
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.CredentialIssuerOpenID4VCI
import org.multipaz.wallet.shared.Domains
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val TAG = "MainGraph"

fun NavGraphBuilder.mainGraph(
    navController: NavController,
    walletClient: WalletClient,
    documentStore: DocumentStore,
    documentModel: DocumentModel,
    settingsModel: SettingsModel,
    eventLogger: SimpleEventLogger,
    documentTypeRepository: DocumentTypeRepository,
    provisioningModel: ProvisioningModel,
    imageLoader: ImageLoader,
    promptModel: PromptModel,
    signInWithGoogle: SignInWithGoogle,
    mpzPassesToImportChannel: Channel<ByteString>,
    coroutineScope: CoroutineScope,
    context: Context,
    showToast: (message: String) -> Unit,
    backendIssuerTrustManagerModel: TrustManagerModel,
    userIssuerTrustManagerModel: TrustManagerModel,
    backendReaderTrustManagerModel: TrustManagerModel,
    userReaderTrustManagerModel: TrustManagerModel,
    isSigningIn: MutableState<Boolean>,
    isSigningOut: MutableState<Boolean>,
    onSignIn: suspend (Context, WalletClient, SignInWithGoogle, NavController, Boolean, Boolean) -> Unit,
    onSignOut: suspend (WalletClient, SettingsModel, SignInWithGoogle) -> Unit
) {
    fun getTrustManagerModelFromId(identifier: String): TrustManagerModel {
        return when (identifier) {
            "backendIssuerTrustManager" -> backendIssuerTrustManagerModel
            "userIssuerTrustManager" -> userIssuerTrustManagerModel
            "backendReaderTrustManager" -> backendReaderTrustManagerModel
            "userReaderTrustManager" -> userReaderTrustManagerModel
            else -> throw IllegalStateException("Unexpected TrustManager id $identifier")
        }
    }

    navigation<MainGraph>(startDestination = WalletDestination()) {
        composable<ProvisioningDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<ProvisioningDestination>()
            ProvisioningRoute(
                provisioningModel = provisioningModel,
                walletClient = walletClient,
                imageLoader = imageLoader,
                credentialIssuer = destination.credentialIssuer,
                openID4VCICredentialOffer = destination.openID4VCICredentialOfferUri,
                openID4VCIIssuerUrl = destination.openID4VCIIssuerUrl,
                onCloseClicked = {
                    navController.popBackStack()
                    coroutineScope.launch {
                        delay(3.seconds)
                        provisioningModel.cancel()
                    }
                },
                onComplete = { documentId ->
                    navController.navigate(WalletDestination(
                        documentId = documentId,
                        justAddedAtMillis = Clock.System.now().toEpochMilliseconds()
                    )) {
                        popUpTo<WalletDestination> {
                            inclusive = true
                        }
                    }
                    coroutineScope.launch {
                        delay(3.seconds)
                        provisioningModel.cancel()
                    }
                },
                onFailed = {
                    navController.popBackStack()
                    coroutineScope.launch {
                        delay(3.seconds)
                        provisioningModel.cancel()
                    }
                }
            )
        }
        composable<WalletDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<WalletDestination>()
            var focusedDocumentId by rememberSaveable { mutableStateOf(destination.documentId) }
            val justAddedRecently = destination.justAddedAtMillis?.let {
                Clock.System.now() - Instant.fromEpochMilliseconds(it) < 1.seconds
            } ?: false
            var justAdded by remember { mutableStateOf(justAddedRecently) }

            LaunchedEffect(destination.documentId) {
                if (destination.documentId != null) {
                    focusedDocumentId = destination.documentId
                }
            }
            BackHandler(enabled = focusedDocumentId != null) {
                focusedDocumentId = null
            }
            WalletScreen(
                walletClient = walletClient,
                documentStore = documentStore,
                documentModel = documentModel,
                settingsModel = settingsModel,
                focusedDocumentId = focusedDocumentId,
                justAdded = justAdded,
                onAvatarClicked = { navController.navigate(SettingsDestination) },
                onAddClicked = { navController.navigate(AddToWalletDestination) },
                onDocumentClicked = { documentId ->
                    focusedDocumentId = documentId
                },
                onDocumentQrClicked = { documentId ->
                    navController.navigate(DocumentQrPresentmentDialogDestination(documentId))
                },
                onDocumentActivityClicked = { documentId ->
                    navController.navigate(DocumentEventListDestination(documentId))
                },
                onDocumentInfoClicked = { documentId ->
                    navController.navigate(DocumentInfoDestination(documentId))
                },
                onDocumentRemoveClicked = { documentId ->
                    navController.navigate(RemoveDocumentConfirmationDialogDestination(documentId))
                },
                onBackClicked = {
                    if (focusedDocumentId != null) {
                        focusedDocumentId = null
                        justAdded = false
                    } else {
                        navController.popBackStack()
                    }
                },
                showToast = showToast
            )
        }
        dialog<DocumentQrPresentmentDialogDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<DocumentQrPresentmentDialogDestination>()
            DocumentQrPresentmentDialog(
                documentStore = documentStore,
                documentId = destination.documentId,
                scope = coroutineScope,
                onDismissed = { navController.popBackStack() },
                onTransactionUnderway = {
                    navController.popBackStack()
                },
            )
        }
        dialog<RemoveDocumentConfirmationDialogDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<RemoveDocumentConfirmationDialogDestination>()
            ConfirmationDialog(
                title = stringResource(R.string.app_navigation_remove_document_title),
                textMarkdown = stringResource(R.string.app_navigation_remove_document_text),
                confirmButtonText = stringResource(R.string.app_navigation_remove_document_confirm),
                onDismissed = { navController.popBackStack() },
                onConfirmClicked = {
                    coroutineScope.launch {
                        // Remove pass from shared data, if applicable...
                        //
                        // Note that this can go wrong if e.g. we don't have an Internet connection
                        // or the wallet backend is down. This means that you need to be online to delete a pass,
                        // we may want to relax this requirement in the future.
                        //
                        try {
                            val document = documentStore.lookupDocument(destination.documentId)
                            if (document?.mpzPassId != null && walletClient.sharedData.value != null) {
                                walletClient.refreshSharedData()
                                walletClient.sharedData.value?.let { sharedData ->
                                    val pass = sharedData.getMpzPasses().find { it.uniqueId == document.mpzPassId }
                                    if (pass != null) {
                                        walletClient.setSharedData(
                                            sharedData.removeMpzPass(pass)
                                        )
                                    }
                                }
                            }
                            documentStore.deleteDocument(destination.documentId)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            navController.navigate(ErrorDialogDestination(
                                title = context.getString(R.string.app_navigation_delete_document_error_dialog_title),
                                textMarkdown = context.getString(
                                    R.string.app_navigation_delete_document_error_dialog_text,
                                    e.toString()
                                )
                            ))
                        }
                    }
                    navController.popBackStack()
                }
            )
        }
        composable<DocumentInfoDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<DocumentInfoDestination>()
            DocumentInfoScreen(
                documentId = destination.documentId,
                documentModel = documentModel,
                settingsModel = settingsModel,
                onBackClicked = { navController.popBackStack() },
                onDeveloperExtrasClicked = {
                    navController.navigate(DocumentInfoExtrasDestination(destination.documentId))
                }
            )
        }
        composable<DocumentInfoExtrasDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<DocumentInfoExtrasDestination>()
            // No need for translations, this is a dev-mode feature
            DocumentInfoExtrasScreen(
                documentId = destination.documentId,
                documentModel = documentModel,
                onBackClicked = { navController.popBackStack() },
                onRefreshCredentialsClicked = {
                    coroutineScope.launch {
                        val document = documentStore.lookupDocument(destination.documentId)
                        val authorizationData = document?.authorizationData
                        if (authorizationData == null) {
                            showToast("Cannot refresh. Document isn't from a OpenID4VCI server")
                        } else {
                            try {
                                showToast("Refreshing credentials...")
                                val numCredentialsAdded = provisioningModel.openID4VCIRefreshCredentials(
                                    document = document,
                                    authorizationData = authorizationData,
                                    clientPreferences = walletClient.getOpenID4VCIClientPreferences(),
                                    backend = walletClient.getOpenID4VCIBackend()
                                )
                                showToast("Refreshed ${numCredentialsAdded} credentials")
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Logger.w(TAG, "Error refreshing credentials", e)
                                showToast("Error refreshing credentials: ${e.message}")
                            }
                        }
                    }
                },
                onCredentialClicked = { credentialId ->
                    navController.navigate(CredentialInfoDestination(destination.documentId, credentialId))
                }
            )
        }
        composable<CredentialInfoDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<CredentialInfoDestination>()
            CredentialInfoScreen(
                documentModel = documentModel,
                documentId = destination.documentId,
                credentialId = destination.credentialId,
                onViewCertificateChain = { certChain ->
                    navController.navigate(CertificateViewerDestination.create(certChain))
                },
                onBackClicked = { navController.popBackStack() },
                showToast = showToast
            )
        }
        composable<AddToWalletDestination> {
            AddToWalletScreen(
                walletClient = walletClient,
                settingsModel = settingsModel,
                imageLoader = imageLoader,
                onCredentialIssuerClicked = { credentialIssuer ->
                    credentialIssuer as CredentialIssuerOpenID4VCI
                    navController.navigate(
                        ProvisioningDestination(
                            credentialIssuer = credentialIssuer
                        )
                    )
                },
                onImportMpzPass = { encodedMpzPass ->
                    coroutineScope.launch {
                        mpzPassesToImportChannel.send(encodedMpzPass)
                    }
                },
                onCredentialIssuerUrl = { issuerUrl ->
                    navController.navigate(
                        ProvisioningDestination(
                            credentialIssuer = null,
                            openID4VCIIssuerUrl = issuerUrl
                        )
                    )
                },
                onBackClicked = { navController.popBackStack() },
                showToast = showToast
            )
        }
        composable<AboutDestination> {
            AboutScreen(
                onBackClicked = { navController.popBackStack() },
                showToast = showToast
            )
        }
        composable<SettingsDestination> {
            SettingsScreen(
                walletClient = walletClient,
                settingsModel = settingsModel,
                isSigningOut = isSigningOut,
                isSigningIn = isSigningIn,
                onBackClicked = { navController.popBackStack() },
                onUseWithoutGoogleAccountClicked = {
                    navController.navigate(SignOutConfirmationDialogDestination)
                },
                onSignInToGoogleClicked = {
                    coroutineScope.launch {
                        isSigningIn.value = true
                        signIn(
                            context = context,
                            walletClient = walletClient,
                            signInWithGoogle = signInWithGoogle,
                            navController = navController,
                            explicitSignIn = true,
                            resetEncryptionKey = false,
                        )
                        documentStore.syncWithSharedData(
                            sharedData = walletClient.sharedData.value!!,
                            mpzPassIsoMdocDomain = Domains.DOMAIN_MDOC_SOFTWARE,
                            mpzPassSdJwtVcDomain = Domains.DOMAIN_SDJWT_SOFTWARE,
                            mpzPassKeylessSdJwtVcDomain = Domains.DOMAIN_SDJWT_KEYLESS
                        )
                        isSigningIn.value = false
                    }
                },
                onTrustedIssuersClicked = {
                    navController.navigate(TrustedIssuersDestination)
                },
                onTrustedReadersClicked = {
                    navController.navigate(TrustedVerifiersDestination)
                },
                onActivityLoggingClicked = {
                    navController.navigate(ActivityLoggingSettingsDestination)
                },
                onDeveloperSettingsClicked = {
                    navController.navigate(DeveloperSettingsDestination)
                },
                onAboutClicked = {
                    navController.navigate(AboutDestination)
                },
                showToast = showToast,
            )
        }
        composable<ActivityLoggingSettingsDestination> {
            @OptIn(ExperimentalPermissionsApi::class)
            val locationPermissionState = rememberPermissionState(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) { isGranted ->
                settingsModel.eventLoggingLocationEnabled.value = isGranted
            }
            ActivityLoggingSettingsScreen(
                settingsModel = settingsModel,
                onActivityLoggingEnabledToggled = { newEnabledValue ->
                    if (!newEnabledValue) {
                        navController.navigate(SettingsActivityLogDisableConfirmationDialogDestination)
                    } else {
                        settingsModel.eventLoggingEnabled.value = true
                    }
                },
                onActivityLoggingLocationEnabledToggled = { newEnabledValue ->
                    if (newEnabledValue) {
                        @OptIn(ExperimentalPermissionsApi::class)
                        if (locationPermissionState.status.isGranted) {
                            settingsModel.eventLoggingLocationEnabled.value = true
                        } else {
                            locationPermissionState.launchPermissionRequest()
                        }
                    } else {
                        settingsModel.eventLoggingLocationEnabled.value = false
                    }
                },
                onActivityLogView = {
                    navController.navigate(EventListDestination)
                },
                onBackClicked = { navController.popBackStack() },
                showToast = showToast,
            )
        }
        dialog<SettingsActivityLogDisableConfirmationDialogDestination> {
            ConfirmationDialog(
                title = stringResource(R.string.app_navigation_disable_activity_log_title),
                textMarkdown = stringResource(R.string.app_navigation_disable_activity_log_text),
                confirmButtonText = stringResource(R.string.app_navigation_disable_activity_log_confirm),
                onDismissed = { navController.popBackStack() },
                onConfirmClicked = {
                    coroutineScope.launch {
                        try {
                            eventLogger.deleteAllEvents()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            navController.navigate(ErrorDialogDestination(
                                title = context.getString(R.string.app_navigation_error_deleting_activity_log_title),
                                textMarkdown = context.getString(R.string.app_navigation_error_deleting_activity_log_text)
                            ))
                        }
                        settingsModel.eventLoggingEnabled.value = false
                    }
                    navController.popBackStack()
                }
            )
        }
        composable<EventListDestination> {
            EventListScreen(
                eventLogger = eventLogger,
                imageLoader = imageLoader,
                documentModel = documentModel,
                onDeleteAllEvents = { navController.navigate(DeleteAllEventsConfirmationDialogDestination) },
                onEventClicked = { event ->
                    navController.navigate(EventViewerDestination(event.identifier))
                },
                onBackClicked = { navController.popBackStack() },
                showToast = showToast
            )
        }
        dialog<DeleteAllEventsConfirmationDialogDestination> {
            ConfirmationDialog(
                title = stringResource(R.string.app_navigation_delete_all_events_title),
                textMarkdown = stringResource(R.string.app_navigation_delete_all_events_text),
                confirmButtonText = stringResource(R.string.app_navigation_delete_all_events_confirm),
                onDismissed = { navController.popBackStack() },
                onConfirmClicked = {
                    coroutineScope.launch {
                        try {
                            eventLogger.deleteAllEvents()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            navController.navigate(ErrorDialogDestination(
                                title = context.getString(R.string.app_navigation_error_deleting_activity_log_title),
                                textMarkdown = context.getString(R.string.app_navigation_error_deleting_activity_log_text)
                            ))
                        }
                    }
                    navController.popBackStack()
                }
            )
        }
        composable<DocumentEventListDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<DocumentEventListDestination>()
            DocumentEventListScreen(
                eventLogger = eventLogger,
                documentId = destination.documentId,
                imageLoader = imageLoader,
                documentModel = documentModel,
                onDeleteAllEvents = { navController.navigate(
                    DeleteAllEventsForDocumentConfirmationDialogDestination(destination.documentId))
                },
                onEventClicked = { event ->
                    navController.navigate(EventViewerDestination(event.identifier))
                },
                onBackClicked = { navController.popBackStack() },
                showToast = showToast
            )
        }
        dialog<DeleteAllEventsForDocumentConfirmationDialogDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<DeleteAllEventsForDocumentConfirmationDialogDestination>()
            ConfirmationDialog(
                title = stringResource(R.string.app_navigation_delete_all_events_for_document_title),
                textMarkdown = stringResource(R.string.app_navigation_delete_all_events_for_document_text),
                confirmButtonText = stringResource(R.string.app_navigation_delete_all_events_for_document_confirm),
                onDismissed = { navController.popBackStack() },
                onConfirmClicked = {
                    coroutineScope.launch {
                        try {
                            val eventsForDocument = eventLogger.getEvents().filter {
                                it.isForDocumentId(destination.documentId)
                            }
                            eventsForDocument.forEach { event ->
                                eventLogger.deleteEvent(event)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            navController.navigate(ErrorDialogDestination(
                                title = context.getString(R.string.app_navigation_error_delete_events_for_document_title),
                                textMarkdown = context.getString(R.string.app_navigation_error_delete_event_for_document_text)
                            ))
                        }
                    }
                    navController.popBackStack()
                }
            )
        }
        composable<EventViewerDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<EventViewerDestination>()
            EventViewerScreen(
                eventLogger = eventLogger,
                eventId = destination.eventId,
                documentTypeRepository = documentTypeRepository,
                documentModel = documentModel,
                imageLoader = imageLoader,
                onEventDelete = {
                    navController.navigate(DeleteEventConfirmationDialogDestination(destination.eventId))
                },
                onViewCertificateChain = { certChain ->
                    navController.navigate(CertificateViewerDestination.create(certChain))
                },
                onBackClicked = { navController.popBackStack() },
                promptModel = promptModel,
                showToast = showToast,
            )
        }
        dialog<DeleteEventConfirmationDialogDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<DeleteEventConfirmationDialogDestination>()
            ConfirmationDialog(
                title = stringResource(R.string.app_navigation_delete_event_title),
                textMarkdown = stringResource(R.string.app_navigation_delete_event_text),
                confirmButtonText = stringResource(R.string.app_navigation_delete_event_confirm),
                onDismissed = { navController.popBackStack() },
                onConfirmClicked = {
                    coroutineScope.launch {
                        try {
                            val event = eventLogger.getEvents().find { it.identifier == destination.eventId }
                            eventLogger.deleteEvent(event!!)
                            navController.popBackStack()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            navController.navigate(ErrorDialogDestination(
                                title = context.getString(R.string.app_navigation_error_delete_event_title),
                                textMarkdown = context.getString(R.string.app_navigation_error_delete_event_text)
                            ))
                        }
                    }
                    navController.popBackStack()
                }
            )
        }
        dialog<SignInClearEncryptionKeyDialogDestination> {
            SignInClearEncryptionKeyDialog(
                onConfirm = {
                    navController.popBackStack()
                    coroutineScope.launch {
                        isSigningIn.value = true
                        signIn(
                            context = context,
                            walletClient = walletClient,
                            signInWithGoogle = signInWithGoogle,
                            navController = navController,
                            explicitSignIn = true,
                            resetEncryptionKey = true,
                        )
                        isSigningIn.value = false
                    }
                },
                onDismissed = { navController.popBackStack() }
            )
        }
        composable<DeveloperSettingsDestination> {
            DeveloperSettingsScreen(
                walletClient = walletClient,
                settingsModel = settingsModel,
                onCorruptEncryptionKeyClicked = {
                    coroutineScope.launch {
                        try {
                            signInWithGoogle.corruptEncryptionKey(walletClient.signedInUser.value!!.id)
                            navController.navigate(
                                ErrorDialogDestination(
                                    title = context.getString(R.string.app_navigation_corrupt_key_success_title),
                                    textMarkdown = context.getString(R.string.app_navigation_corrupt_key_success_text)
                                )
                            )
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            navController.navigate(
                                ErrorDialogDestination(
                                    title = context.getString(R.string.app_navigation_corrupt_key_error_title),
                                    textMarkdown = context.getString(R.string.app_navigation_corrupt_key_error_text, e.toString())
                                )
                            )
                        }
                    }
                },
                onConfigureWalletBackendClicked = {
                    navController.navigate(DeveloperSettingsConfigureWalletBackendDialogDestination)
                },
                onRunFirstTimeSetupClicked = {
                    navController.popBackStack()
                    settingsModel.firstTimeSetupDone.value = false
                    navController.navigate(SetupGraph) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onClearAppDataClicked = {
                    navController.navigate(DeveloperSettingsClearAppDataDialogDestination)
                },
                onBackClicked = { navController.popBackStack() },
                showToast = showToast
            )
        }
        dialog<DeveloperSettingsClearAppDataDialogDestination> {
            ConfirmationDialog(
                title = context.getString(R.string.dev_settings_clear_app_data_dialog_title),
                textMarkdown = context.getString(R.string.dev_settings_clear_app_data_dialog_text),
                confirmButtonText = context.getString(R.string.dev_settings_clear_app_data_dialog_confirm),
                onDismissed = { navController.popBackStack() },
                onConfirmClicked = {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    am.clearApplicationUserData()
                }
            )
        }
        dialog<DeveloperSettingsConfigureWalletBackendDialogDestination> {
            DeveloperSettingsConfigureWalletBackendDialog(
                settingsModel = settingsModel,
                onConfirmed = { walletBackendUrl ->
                    val newUrl = walletBackendUrl ?: BuildConfig.BACKEND_URL
                    if (newUrl != walletClient.url) {
                        coroutineScope.launch {
                            try {
                                signOut(
                                    walletClient = walletClient,
                                    settingsModel = settingsModel,
                                    signInWithGoogle = signInWithGoogle
                                )
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Logger.w(TAG, "Ignoring exception when signing out for new wallet server", e)
                            }
                            documentStore.listDocumentIds().forEach {
                                documentStore.deleteDocument(it)
                            }
                            navController.navigate(
                                DeveloperSettingsConnectToWalletServerDialogDestination(
                                    walletBackendUrl = walletBackendUrl
                                )
                            )
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onDismissed = {
                    navController.popBackStack()
                },
            )
        }
        dialog<DeveloperSettingsConnectToWalletServerDialogDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<DeveloperSettingsConnectToWalletServerDialogDestination>()
            DeveloperSettingsConnectToWalletServerDialog(
                walletClient = walletClient,
                settingsModel = settingsModel,
                newWalletBackendUrl = destination.walletBackendUrl,
                onSuccess = {
                    navController.popBackStack()
                    navController.popBackStack()
                },
                onFailed = { error ->
                    showToast("$error")
                    navController.popBackStack()
                },
                onDismissed = {
                    navController.popBackStack()
                }
            )
        }
        dialog<SignOutConfirmationDialogDestination> {
            SignOutConfirmationDialog(
                onConfirmed = {
                    navController.popBackStack()
                    coroutineScope.launch {
                        isSigningOut.value = true
                        try {
                            signOut(
                                walletClient = walletClient,
                                settingsModel = settingsModel,
                                signInWithGoogle = signInWithGoogle
                            )
                        } catch (e: WalletClientBackendUnreachableException) {
                            // This happens when signing out and the wallet backend is
                            // unreachable, as the very last step of signing out. Don't
                            // bother the user with this
                            Logger.w(
                                TAG,
                                "Ignoring WalletBackendUnreachableException when signing out",
                                e
                            )
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            navController.navigate(
                                ErrorDialogDestination(
                                    title = context.getString(R.string.app_navigation_error_signing_out_title),
                                    textMarkdown = context.getString(R.string.app_navigation_error_signing_out_text, e.toString())
                                )
                            )
                        }
                        documentStore.listDocumentIds().forEach {
                            documentStore.deleteDocument(it)
                        }
                        isSigningOut.value = false
                    }
                },
                onDismissed = {
                    navController.popBackStack()
                },
            )
        }
        dialog<ErrorDialogDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<ErrorDialogDestination>()
            ErrorDialog(
                title = destination.title,
                textMarkdown = destination.textMarkdown,
                onDismissed = {
                    navController.popBackStack()
                }
            )
        }
        composable<TrustedIssuersDestination> { backStackEntry ->
            TrustManagerScreen(
                builtIn = backendIssuerTrustManagerModel,
                user = userIssuerTrustManagerModel,
                isVical = true,
                imageLoader = imageLoader,
                onTrustEntryClicked = { trustManagerId, trustEntryId ->
                    navController.navigate(
                        TrustEntryDestination(
                            trustManagerId = trustManagerId,
                            trustEntryId = trustEntryId
                        )
                    )
                },
                onTrustEntryAdded = { trustManagerId, trustEntryId ->
                    navController.navigate(
                        TrustEntryDestination(
                            trustManagerId = trustManagerId,
                            trustEntryId = trustEntryId,
                            justImported = true
                        )
                    )
                },
                onTrustEntryImportError = { errorTitle, errorMessage ->
                    navController.navigate(ErrorDialogDestination(
                        title = errorTitle,
                        textMarkdown = errorMessage
                    ))
                },
                onBackClicked = { navController.popBackStack() },
                showToast = showToast
            )
        }
        composable<TrustedVerifiersDestination> { backStackEntry ->
            TrustManagerScreen(
                builtIn = backendReaderTrustManagerModel,
                user = userReaderTrustManagerModel,
                isVical = false,
                imageLoader = imageLoader,
                onTrustEntryClicked = { trustManagerId, trustEntryId ->
                    navController.navigate(
                        TrustEntryDestination(
                            trustManagerId = trustManagerId,
                            trustEntryId = trustEntryId
                        )
                    )
                },
                onTrustEntryAdded = { trustManagerId, trustEntryId ->
                    navController.navigate(
                        TrustEntryDestination(
                            trustManagerId = trustManagerId,
                            trustEntryId = trustEntryId,
                            justImported = true
                        )
                    )
                },
                onTrustEntryImportError = { errorTitle, errorMessage ->
                    navController.navigate(ErrorDialogDestination(
                        title = errorTitle,
                        textMarkdown = errorMessage
                    ))
                },
                onBackClicked = { navController.popBackStack() },
                showToast = showToast
            )
        }
        composable<TrustEntryDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<TrustEntryDestination>()
            TrustEntryScreen(
                trustManagerModel = getTrustManagerModelFromId(destination.trustManagerId),
                trustEntryId = destination.trustEntryId,
                justImported = destination.justImported,
                imageLoader = imageLoader,
                onViewSignerCertificateChain = { certificateChain ->
                    navController.navigate(
                        CertificateViewerDestination.create(certificateChain)
                    )
                },
                onViewVicalEntry = { certNum ->
                    navController.navigate(
                        TrustEntryVicalEntryDestination(
                            trustManagerId = destination.trustManagerId,
                            trustEntryId = destination.trustEntryId,
                            vicalCertNumber = certNum
                        )
                    )
                },
                onViewRicalEntry = { certNum ->
                    navController.navigate(
                        TrustEntryRicalEntryDestination(
                            trustManagerId = destination.trustManagerId,
                            trustEntryId = destination.trustEntryId,
                            ricalCertNumber = certNum
                        )
                    )
                },
                onEditClicked = {
                    navController.navigate(
                        TrustEntryEditDestination(
                            trustManagerId = destination.trustManagerId,
                            trustEntryId = destination.trustEntryId,
                        )
                    )
                },
                onBackClicked = { navController.popBackStack() },
                showToast = showToast,
            )
        }
        composable<TrustEntryEditDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<TrustEntryDestination>()
            TrustEntryEditScreen(
                trustManagerModel = getTrustManagerModelFromId(destination.trustManagerId),
                trustEntryId = destination.trustEntryId,
                imageLoader = imageLoader,
                onBackClicked = { navController.popBackStack() },
                showToast = showToast,
            )
        }
        composable<TrustEntryVicalEntryDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<TrustEntryVicalEntryDestination>()
            TrustEntryVicalEntryScreen(
                trustManagerModel = getTrustManagerModelFromId(destination.trustManagerId),
                vicalTrustEntryId = destination.trustEntryId,
                certNum = destination.vicalCertNumber,
                onBackClicked = { navController.popBackStack() },
            )
        }
        composable<TrustEntryRicalEntryDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<TrustEntryRicalEntryDestination>()
            TrustEntryRicalEntryScreen(
                trustManagerModel = getTrustManagerModelFromId(destination.trustManagerId),
                ricalTrustEntryId = destination.trustEntryId,
                certNum = destination.ricalCertNumber,
                onBackClicked = { navController.popBackStack() },
            )
        }
        composable<CertificateViewerDestination> { backStackEntry ->
            val destination = backStackEntry.toRoute<CertificateViewerDestination>()
            when (val dataItem = Cbor.decode(destination.certificateData.fromBase64Url())) {
                is CborArray -> CertificateViewerScreen(
                    x509CertChain = X509CertChain.fromDataItem(dataItem),
                    onBackClicked = { navController.popBackStack() },
                )
                else -> CertificateViewerScreen(
                    x509Cert = X509Cert.fromDataItem(dataItem),
                    onBackClicked = { navController.popBackStack() },
                )
            }
        }
    }
}
