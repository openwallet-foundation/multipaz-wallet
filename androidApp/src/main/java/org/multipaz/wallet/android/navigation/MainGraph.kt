package org.multipaz.wallet.android.navigation

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.compose.cards.VerticalCardListState
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.prompt.PromptModel
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
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
import org.multipaz.wallet.android.ui.InfoDialog
import org.multipaz.wallet.android.ui.SignInClearEncryptionKeyDialog
import org.multipaz.wallet.android.ui.SignOutConfirmationDialog
import org.multipaz.wallet.android.ui.WalletScreen
import org.multipaz.wallet.android.ui.document.CredentialInfoScreen
import org.multipaz.wallet.android.ui.document.DocumentEventListScreen
import org.multipaz.wallet.android.ui.document.DocumentInfoExtrasScreen
import org.multipaz.wallet.android.ui.document.DocumentInfoScreen
import org.multipaz.wallet.android.ui.document.ManageTrustedReadersAddReaderDialog
import org.multipaz.wallet.android.ui.document.ManageTrustedReadersScreen
import org.multipaz.wallet.android.ui.document.PreconsentSettingsScreen
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
import org.multipaz.wallet.android.ui.verification.VerificationScreen
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientBackendUnreachableException
import org.multipaz.wallet.client.WalletClientProvisionedDocumentOpenID4VCI
import org.multipaz.wallet.client.deleteDocumentFromWalletBackend
import org.multipaz.wallet.client.isSyncing
import org.multipaz.wallet.client.provisionedDocumentIdentifier
import org.multipaz.wallet.client.setProvisionedDocumentIdentifier
import org.multipaz.wallet.client.syncWithSharedData
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.CredentialIssuerOpenID4VCI
import org.multipaz.wallet.shared.Domains
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val TAG = "MainGraph"

fun mainGraph(
    backStack: MutableList<NavKey>,
    verticalCardListState: VerticalCardListState,
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
    readerTrustManager: CompositeTrustManager,
    isSigningIn: MutableState<Boolean>,
    isSigningOut: MutableState<Boolean>,
    onSignIn: suspend (Context, WalletClient, SignInWithGoogle, MutableList<NavKey>, Boolean, Boolean) -> Unit,
    onSignOut: suspend (WalletClient, SettingsModel, SignInWithGoogle) -> Unit
): (NavKey) -> NavEntry<NavKey>? {
    fun getTrustManagerModelFromId(identifier: String): TrustManagerModel {
        return when (identifier) {
            "backendIssuerTrustManager" -> backendIssuerTrustManagerModel
            "userIssuerTrustManager" -> userIssuerTrustManagerModel
            "backendReaderTrustManager" -> backendReaderTrustManagerModel
            "userReaderTrustManager" -> userReaderTrustManagerModel
            else -> throw IllegalStateException("Unexpected TrustManager id $identifier")
        }
    }

    return { key ->
        when (key) {
            is ProvisioningDestination -> NavEntry(key) {
                Logger.i(TAG, "Provisioning destination: $key")
                ProvisioningRoute(
                    provisioningModel = provisioningModel,
                    walletClient = walletClient,
                    imageLoader = imageLoader,
                    credentialIssuer = key.credentialIssuer,
                    openID4VCICredentialOffer = key.openID4VCICredentialOfferUri,
                    openID4VCIIssuerUrl = key.openID4VCIIssuerUrl,
                    openID4VCICredentialId = key.openID4VCICredentialId,
                    onCloseClicked = {
                        backStack.removeAt(backStack.size - 1)
                    },
                    onComplete = { document, provisionedDocument ->
                        backStack.clear()
                        backStack.add(WalletDestination())
                        backStack.add(
                            WalletDestination(
                                documentId = document.identifier,
                                justAddedAtMillis = Clock.System.now().toEpochMilliseconds(),
                            )
                        )
                        // If the user is signed in, deal with updating the Wallet Backend
                        if (walletClient.signedInUser.value != null) {
                            coroutineScope.launch {
                                try {
                                    if (key.provisionedDocumentIdentifier != null) {
                                        // OK, we're done provisioning a document for that identifier. Delete
                                        // the placeholder and set our newly provisioned document as holding
                                        // the provisioned document
                                        Logger.i(TAG, "Provisioned a document from provisioned document in shared data")
                                        documentStore.listDocuments().find {
                                            it.provisionedDocumentIdentifier == key.provisionedDocumentIdentifier
                                        }?.let { placeholderDocument ->
                                            documentStore.deleteDocument(placeholderDocument.identifier)
                                        }
                                        document.setProvisionedDocumentIdentifier(key.provisionedDocumentIdentifier)
                                    } else {
                                        // We've provisioned a new document, add it to the shared data so other
                                        // clients can pick it up.
                                        document.setProvisionedDocumentIdentifier(provisionedDocument.identifier)
                                        Logger.i(TAG, "Adding a new provisioned document to shared data")
                                        walletClient.refreshSharedData()
                                        walletClient.setSharedData(
                                            sharedData = walletClient.sharedData.value!!.addProvisionedDocument(
                                                provisionedDocument
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    backStack.add(
                                        ErrorDialogDestination(
                                            title = context.getString(R.string.provisioning_add_pass_to_shared_data_error_title),
                                            textMarkdown = context.getString(
                                                R.string.provisioning_add_pass_to_shared_data_error_message_md,
                                                e.toString()
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onFailed = {
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is WalletDestination -> {
                val previousKey = backStack.getOrNull(backStack.size - 2)
                val metadata = if (previousKey is WalletDestination) {
                    NavDisplay.transitionSpec { EnterTransition.None togetherWith ExitTransition.None } +
                            NavDisplay.popTransitionSpec { EnterTransition.None togetherWith ExitTransition.None }
                } else {
                    emptyMap()
                }
                NavEntry(key, metadata = metadata) {
                    val justAdded = key.justAddedAtMillis?.let {
                        Clock.System.now() - Instant.fromEpochMilliseconds(it) < 1.seconds
                    } ?: false

                    // Disable animations for a card we just added
                    if (justAdded) {
                        verticalCardListState.animateListTransitions = false
                    } else {
                        verticalCardListState.animateListTransitions = true
                    }

                    WalletScreen(
                        verticalCardListState = verticalCardListState,
                        walletClient = walletClient,
                        documentStore = documentStore,
                        documentModel = documentModel,
                        settingsModel = settingsModel,
                        focusedDocumentId = key.documentId,
                        justAdded = justAdded,
                        onAvatarClicked = { backStack.add(SettingsDestination) },
                        onAddClicked = { backStack.add(AddToWalletDestination) },
                        onVerifyClicked = { backStack.add(VerificationDestination) },
                        onDocumentClicked = { documentInfo ->
                            backStack.add(WalletDestination(
                                documentId = documentInfo.document.identifier
                            ))
                        },
                        onDocumentQrClicked = { documentInfo ->
                            backStack.add(DocumentQrPresentmentDialogDestination(documentInfo.document.identifier))
                        },
                        onDocumentActivityClicked = { documentInfo ->
                            backStack.add(DocumentEventListDestination(documentInfo.document.identifier))
                        },
                        onDocumentInfoClicked = { documentInfo ->
                            backStack.add(DocumentInfoDestination(documentInfo.document.identifier))
                        },
                        onDocumentRemoveClicked = { documentInfo ->
                            backStack.add(
                                RemoveDocumentConfirmationDialogDestination(
                                    documentId = documentInfo.document.identifier,
                                    isSyncing = documentInfo.document.isSyncing
                                )
                            )
                        },
                        onDocumentSetupClicked = { documentInfo ->
                            walletClient.sharedData.value?.provisionedDocuments?.find {
                                it.identifier == documentInfo.document.provisionedDocumentIdentifier
                            }?.let { provisionedDocument ->
                                // Only support OpenID4VCI right now
                                provisionedDocument as WalletClientProvisionedDocumentOpenID4VCI
                                backStack.add(
                                    ProvisioningDestination(
                                        openID4VCIIssuerUrl = provisionedDocument.url,
                                        openID4VCICredentialId = provisionedDocument.credentialId,
                                        provisionedDocumentIdentifier = provisionedDocument.identifier
                                    )
                                )
                            }
                        },
                        onDocumentSyncClicked = { documentInfo ->
                            backStack.add(
                                InfoDialogDestination(
                                    title = context.getString(R.string.wallet_screen_sync_info_dialog_title),
                                    textMarkdown = if (documentInfo.document.mpzPassId != null) {
                                        context.getString(R.string.wallet_screen_sync_info_dialog_text_mpzpass_md)
                                    } else {
                                        context.getString(R.string.wallet_screen_sync_info_dialog_text_provisioned_document_md)
                                    }
                                )
                            )
                        },
                        onDocumentPreconsentSettingsClicked = { documentInfo ->
                            backStack.add(PreconsentSettingsDestination(documentInfo.document.identifier))
                        },
                        onBackClicked = {
                            backStack.removeAt(backStack.size - 1)
                        },
                        showToast = showToast
                    )
                }
            }
            is DocumentQrPresentmentDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                DocumentQrPresentmentDialog(
                    documentStore = documentStore,
                    documentId = key.documentId,
                    scope = coroutineScope,
                    onDismissed = { backStack.removeAt(backStack.size - 1) },
                    onTransactionUnderway = {
                        backStack.removeAt(backStack.size - 1)
                    },
                )
            }
            is RemoveDocumentConfirmationDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                val textMarkdown = if (key.isSyncing) {
                    stringResource(R.string.app_navigation_remove_document_text_syncing)
                } else {
                    stringResource(R.string.app_navigation_remove_document_text)
                }
                ConfirmationDialog(
                    title = stringResource(R.string.app_navigation_remove_document_title),
                    textMarkdown = textMarkdown,
                    confirmButtonText = stringResource(R.string.app_navigation_remove_document_confirm),
                    onDismissed = { backStack.removeAt(backStack.size - 1) },
                    onConfirmClicked = {
                        coroutineScope.launch {
                            try {
                                val document = documentStore.lookupDocument(key.documentId)
                                document?.let {
                                    documentStore.deleteDocumentFromWalletBackend(
                                        document = document,
                                        walletClient = walletClient,
                                    )
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                backStack.add(
                                    ErrorDialogDestination(
                                        title = context.getString(R.string.app_navigation_delete_document_error_dialog_title),
                                        textMarkdown = context.getString(
                                            R.string.app_navigation_delete_document_error_dialog_text,
                                            e.toString()
                                        )
                                    )
                                )
                            }
                        }
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is DocumentInfoDestination -> NavEntry(key) {
                DocumentInfoScreen(
                    documentId = key.documentId,
                    documentModel = documentModel,
                    settingsModel = settingsModel,
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    onDeveloperExtrasClicked = {
                        backStack.add(DocumentInfoExtrasDestination(key.documentId))
                    }
                )
            }
            is DocumentInfoExtrasDestination -> NavEntry(key) {
                // No need for translations, this is a dev-mode feature
                DocumentInfoExtrasScreen(
                    documentId = key.documentId,
                    documentModel = documentModel,
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    onRefreshCredentialsClicked = {
                        coroutineScope.launch {
                            val document = documentStore.lookupDocument(key.documentId)
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
                        backStack.add(CredentialInfoDestination(key.documentId, credentialId))
                    }
                )
            }
            is PreconsentSettingsDestination -> NavEntry(key) {
                PreconsentSettingsScreen(
                    documentId = key.documentId,
                    documentModel = documentModel,
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    onManageTrustedReaders = {
                        backStack.add(ManageTrustedReadersDestination(key.documentId))
                    }
                )
            }
            is ManageTrustedReadersDestination -> NavEntry(key) {
                ManageTrustedReadersScreen(
                    documentId = key.documentId,
                    documentModel = documentModel,
                    readerTrustManager = readerTrustManager,
                    imageLoader = imageLoader,
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    onAddReaderClicked = { certData ->
                        backStack.add(
                            ManageTrustedReadersAddReaderDialogDestination(
                                key.documentId,
                                certData
                            )
                        )
                    },
                    onViewCertificateClicked = { cert ->
                        backStack.add(CertificateViewerDestination.create(cert))
                    }
                )
            }
            is ManageTrustedReadersAddReaderDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                ManageTrustedReadersAddReaderDialog(
                    documentId = key.documentId,
                    certData = key.certData,
                    documentModel = documentModel,
                    readerTrustManager = readerTrustManager,
                    imageLoader = imageLoader,
                    onDismissed = { backStack.removeAt(backStack.size - 1) }
                )
            }
            is CredentialInfoDestination -> NavEntry(key) {
                CredentialInfoScreen(
                    documentModel = documentModel,
                    documentId = key.documentId,
                    credentialId = key.credentialId,
                    onViewCertificateChain = { certChain ->
                        backStack.add(CertificateViewerDestination.create(certChain))
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast
                )
            }
            is AddToWalletDestination -> NavEntry(key) {
                AddToWalletScreen(
                    walletClient = walletClient,
                    settingsModel = settingsModel,
                    imageLoader = imageLoader,
                    onCredentialIssuerClicked = { credentialIssuer ->
                        credentialIssuer as CredentialIssuerOpenID4VCI
                        backStack.add(
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
                        backStack.add(
                            ProvisioningDestination(
                                credentialIssuer = null,
                                openID4VCIIssuerUrl = issuerUrl
                            )
                        )
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast
                )
            }
            is AboutDestination -> NavEntry(key) {
                AboutScreen(
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast
                )
            }
            is SettingsDestination -> NavEntry(key) {
                SettingsScreen(
                    walletClient = walletClient,
                    settingsModel = settingsModel,
                    isSigningOut = isSigningOut,
                    isSigningIn = isSigningIn,
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    onUseWithoutGoogleAccountClicked = {
                        backStack.add(SignOutConfirmationDialogDestination)
                    },
                    onSignInToGoogleClicked = {
                        coroutineScope.launch {
                            isSigningIn.value = true
                            onSignIn(
                                context,
                                walletClient,
                                signInWithGoogle,
                                backStack,
                                true,
                                false,
                            )
                            walletClient.sharedData.value?.let {
                                documentStore.syncWithSharedData(
                                    sharedData = it,
                                    mpzPassIsoMdocDomain = Domains.DOMAIN_MDOC_SOFTWARE,
                                    mpzPassSdJwtVcDomain = Domains.DOMAIN_SDJWT_SOFTWARE,
                                    mpzPassKeylessSdJwtVcDomain = Domains.DOMAIN_SDJWT_KEYLESS
                                )
                            }
                            isSigningIn.value = false
                        }
                    },
                    onTrustedIssuersClicked = {
                        backStack.add(TrustedIssuersDestination)
                    },
                    onTrustedReadersClicked = {
                        backStack.add(TrustedVerifiersDestination)
                    },
                    onActivityLoggingClicked = {
                        backStack.add(ActivityLoggingSettingsDestination)
                    },
                    onDeveloperSettingsClicked = {
                        backStack.add(DeveloperSettingsDestination)
                    },
                    onAboutClicked = {
                        backStack.add(AboutDestination)
                    },
                    showToast = showToast,
                )
            }
            is ActivityLoggingSettingsDestination -> NavEntry(key) {
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
                            backStack.add(SettingsActivityLogDisableConfirmationDialogDestination)
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
                        backStack.add(EventListDestination)
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast,
                )
            }
            is SettingsActivityLogDisableConfirmationDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                ConfirmationDialog(
                    title = stringResource(R.string.app_navigation_disable_activity_log_title),
                    textMarkdown = stringResource(R.string.app_navigation_disable_activity_log_text),
                    confirmButtonText = stringResource(R.string.app_navigation_disable_activity_log_confirm),
                    onDismissed = { backStack.removeAt(backStack.size - 1) },
                    onConfirmClicked = {
                        coroutineScope.launch {
                            try {
                                eventLogger.deleteAllEvents()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                backStack.add(
                                    ErrorDialogDestination(
                                        title = context.getString(R.string.app_navigation_error_deleting_activity_log_title),
                                        textMarkdown = context.getString(R.string.app_navigation_error_deleting_activity_log_text)
                                    )
                                )
                            }
                            settingsModel.eventLoggingEnabled.value = false
                        }
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is EventListDestination -> NavEntry(key) {
                EventListScreen(
                    eventLogger = eventLogger,
                    imageLoader = imageLoader,
                    documentModel = documentModel,
                    onDeleteAllEvents = { backStack.add(DeleteAllEventsConfirmationDialogDestination) },
                    onEventClicked = { event ->
                        backStack.add(EventViewerDestination(event.identifier))
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast
                )
            }
            is DeleteAllEventsConfirmationDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                ConfirmationDialog(
                    title = stringResource(R.string.app_navigation_delete_all_events_title),
                    textMarkdown = stringResource(R.string.app_navigation_delete_all_events_text),
                    confirmButtonText = stringResource(R.string.app_navigation_delete_all_events_confirm),
                    onDismissed = { backStack.removeAt(backStack.size - 1) },
                    onConfirmClicked = {
                        coroutineScope.launch {
                            try {
                                eventLogger.deleteAllEvents()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                backStack.add(
                                    ErrorDialogDestination(
                                        title = context.getString(R.string.app_navigation_error_deleting_activity_log_title),
                                        textMarkdown = context.getString(R.string.app_navigation_error_deleting_activity_log_text)
                                    )
                                )
                            }
                        }
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is DocumentEventListDestination -> NavEntry(key) {
                DocumentEventListScreen(
                    eventLogger = eventLogger,
                    documentId = key.documentId,
                    imageLoader = imageLoader,
                    documentModel = documentModel,
                    onDeleteAllEvents = {
                        backStack.add(
                            DeleteAllEventsForDocumentConfirmationDialogDestination(key.documentId)
                        )
                    },
                    onEventClicked = { event ->
                        backStack.add(EventViewerDestination(event.identifier))
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast
                )
            }
            is DeleteAllEventsForDocumentConfirmationDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                ConfirmationDialog(
                    title = stringResource(R.string.app_navigation_delete_all_events_for_document_title),
                    textMarkdown = stringResource(R.string.app_navigation_delete_all_events_for_document_text),
                    confirmButtonText = stringResource(R.string.app_navigation_delete_all_events_for_document_confirm),
                    onDismissed = { backStack.removeAt(backStack.size - 1) },
                    onConfirmClicked = {
                        coroutineScope.launch {
                            try {
                                val eventsForDocument = eventLogger.getEvents().filter {
                                    it.isForDocumentId(key.documentId)
                                }
                                eventsForDocument.forEach { event ->
                                    eventLogger.deleteEvent(event)
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                backStack.add(
                                    ErrorDialogDestination(
                                        title = context.getString(R.string.app_navigation_error_delete_events_for_document_title),
                                        textMarkdown = context.getString(R.string.app_navigation_error_delete_event_for_document_text)
                                    )
                                )
                            }
                        }
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is EventViewerDestination -> NavEntry(key) {
                EventViewerScreen(
                    eventLogger = eventLogger,
                    eventId = key.eventId,
                    documentTypeRepository = documentTypeRepository,
                    documentModel = documentModel,
                    imageLoader = imageLoader,
                    onEventDelete = {
                        backStack.add(DeleteEventConfirmationDialogDestination(key.eventId))
                    },
                    onViewCertificateChain = { certChain ->
                        backStack.add(CertificateViewerDestination.create(certChain))
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    promptModel = promptModel,
                    showToast = showToast,
                )
            }
            is DeleteEventConfirmationDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                ConfirmationDialog(
                    title = stringResource(R.string.app_navigation_delete_event_title),
                    textMarkdown = stringResource(R.string.app_navigation_delete_event_text),
                    confirmButtonText = stringResource(R.string.app_navigation_delete_event_confirm),
                    onDismissed = { backStack.removeAt(backStack.size - 1) },
                    onConfirmClicked = {
                        coroutineScope.launch {
                            try {
                                val event = eventLogger.getEvents().find { it.identifier == key.eventId }
                                eventLogger.deleteEvent(event!!)
                                backStack.removeAt(backStack.size - 1)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                backStack.add(
                                    ErrorDialogDestination(
                                        title = context.getString(R.string.app_navigation_error_delete_event_title),
                                        textMarkdown = context.getString(R.string.app_navigation_error_delete_event_text)
                                    )
                                )
                            }
                        }
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is SignInClearEncryptionKeyDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                SignInClearEncryptionKeyDialog(
                    onConfirm = {
                        backStack.removeAt(backStack.size - 1)
                        coroutineScope.launch {
                            isSigningIn.value = true
                            onSignIn(
                                context,
                                walletClient,
                                signInWithGoogle,
                                backStack,
                                true,
                                true,
                            )
                            isSigningIn.value = false
                        }
                    },
                    onDismissed = { backStack.removeAt(backStack.size - 1) }
                )
            }
            is DeveloperSettingsDestination -> NavEntry(key) {
                DeveloperSettingsScreen(
                    walletClient = walletClient,
                    settingsModel = settingsModel,
                    onCorruptEncryptionKeyClicked = {
                        coroutineScope.launch {
                            try {
                                signInWithGoogle.corruptEncryptionKey(walletClient.signedInUser.value!!.id)
                                backStack.add(
                                    ErrorDialogDestination(
                                        title = context.getString(R.string.app_navigation_corrupt_key_success_title),
                                        textMarkdown = context.getString(R.string.app_navigation_corrupt_key_success_text)
                                    )
                                )
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                backStack.add(
                                    ErrorDialogDestination(
                                        title = context.getString(R.string.app_navigation_corrupt_key_error_title),
                                        textMarkdown = context.getString(
                                            R.string.app_navigation_corrupt_key_error_text,
                                            e.toString()
                                        )
                                    )
                                )
                            }
                        }
                    },
                    onConfigureWalletBackendClicked = {
                        backStack.add(DeveloperSettingsConfigureWalletBackendDialogDestination)
                    },
                    onRunFirstTimeSetupClicked = {
                        backStack.clear()
                        backStack.add(SetupWelcomeScreenDestination)
                    },
                    onClearAppDataClicked = {
                        backStack.add(DeveloperSettingsClearAppDataDialogDestination)
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast
                )
            }
            is DeveloperSettingsClearAppDataDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                ConfirmationDialog(
                    title = context.getString(R.string.dev_settings_clear_app_data_dialog_title),
                    textMarkdown = context.getString(R.string.dev_settings_clear_app_data_dialog_text),
                    confirmButtonText = context.getString(R.string.dev_settings_clear_app_data_dialog_confirm),
                    onDismissed = { backStack.removeAt(backStack.size - 1) },
                    onConfirmClicked = {
                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        am.clearApplicationUserData()
                    }
                )
            }
            is DeveloperSettingsConfigureWalletBackendDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                DeveloperSettingsConfigureWalletBackendDialog(
                    settingsModel = settingsModel,
                    onConfirmed = { walletBackendUrl ->
                        val newUrl = walletBackendUrl ?: BuildConfig.BACKEND_URL
                        if (newUrl != walletClient.url) {
                            coroutineScope.launch {
                                try {
                                    onSignOut(
                                        walletClient,
                                        settingsModel,
                                        signInWithGoogle
                                    )
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Logger.w(
                                        TAG,
                                        "Ignoring exception when signing out for new wallet server",
                                        e
                                    )
                                }
                                documentStore.listDocumentIds().forEach {
                                    documentStore.deleteDocument(it)
                                }
                                backStack.add(
                                    DeveloperSettingsConnectToWalletServerDialogDestination(
                                        walletBackendUrl = walletBackendUrl
                                    )
                                )
                            }
                        } else {
                            backStack.removeAt(backStack.size - 1)
                        }
                    },
                    onDismissed = {
                        backStack.removeAt(backStack.size - 1)
                    },
                )
            }
            is DeveloperSettingsConnectToWalletServerDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                DeveloperSettingsConnectToWalletServerDialog(
                    walletClient = walletClient,
                    settingsModel = settingsModel,
                    newWalletBackendUrl = key.walletBackendUrl,
                    onSuccess = {
                        backStack.removeAt(backStack.size - 1)
                        backStack.removeAt(backStack.size - 1)
                    },
                    onFailed = { error ->
                        showToast("$error")
                        backStack.removeAt(backStack.size - 1)
                    },
                    onDismissed = {
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is SignOutConfirmationDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                SignOutConfirmationDialog(
                    onConfirmed = {
                        backStack.removeAt(backStack.size - 1)
                        coroutineScope.launch {
                            isSigningOut.value = true
                            try {
                                onSignOut(
                                    walletClient,
                                    settingsModel,
                                    signInWithGoogle
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
                                backStack.add(
                                    ErrorDialogDestination(
                                        title = context.getString(R.string.app_navigation_error_signing_out_title),
                                        textMarkdown = context.getString(
                                            R.string.app_navigation_error_signing_out_text,
                                            e.toString()
                                        )
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
                        backStack.removeAt(backStack.size - 1)
                    },
                )
            }
            is ErrorDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                ErrorDialog(
                    title = key.title,
                    textMarkdown = key.textMarkdown,
                    onDismissed = {
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is InfoDialogDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                InfoDialog(
                    title = key.title,
                    textMarkdown = key.textMarkdown,
                    onDismissed = {
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is TrustedIssuersDestination -> NavEntry(key) {
                TrustManagerScreen(
                    builtIn = backendIssuerTrustManagerModel,
                    user = userIssuerTrustManagerModel,
                    isVical = true,
                    imageLoader = imageLoader,
                    onTrustEntryClicked = { trustManagerId, trustEntryId ->
                        backStack.add(
                            TrustEntryDestination(
                                trustManagerId = trustManagerId,
                                trustEntryId = trustEntryId
                            )
                        )
                    },
                    onTrustEntryAdded = { trustManagerId, trustEntryId ->
                        backStack.add(
                            TrustEntryDestination(
                                trustManagerId = trustManagerId,
                                trustEntryId = trustEntryId,
                                justImported = true
                            )
                        )
                    },
                    onTrustEntryImportError = { errorTitle, errorMessage ->
                        backStack.add(
                            ErrorDialogDestination(
                                title = errorTitle,
                                textMarkdown = errorMessage
                            )
                        )
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast
                )
            }
            is TrustedVerifiersDestination -> NavEntry(key) {
                TrustManagerScreen(
                    builtIn = backendReaderTrustManagerModel,
                    user = userReaderTrustManagerModel,
                    isVical = false,
                    imageLoader = imageLoader,
                    onTrustEntryClicked = { trustManagerId, trustEntryId ->
                        backStack.add(
                            TrustEntryDestination(
                                trustManagerId = trustManagerId,
                                trustEntryId = trustEntryId
                            )
                        )
                    },
                    onTrustEntryAdded = { trustManagerId, trustEntryId ->
                        backStack.add(
                            TrustEntryDestination(
                                trustManagerId = trustManagerId,
                                trustEntryId = trustEntryId,
                                justImported = true
                            )
                        )
                    },
                    onTrustEntryImportError = { errorTitle, errorMessage ->
                        backStack.add(
                            ErrorDialogDestination(
                                title = errorTitle,
                                textMarkdown = errorMessage
                            )
                        )
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast
                )
            }
            is TrustEntryDestination -> NavEntry(key) {
                TrustEntryScreen(
                    trustManagerModel = getTrustManagerModelFromId(key.trustManagerId),
                    trustEntryId = key.trustEntryId,
                    justImported = key.justImported,
                    imageLoader = imageLoader,
                    onViewSignerCertificateChain = { certificateChain ->
                        backStack.add(
                            CertificateViewerDestination.create(certificateChain)
                        )
                    },
                    onViewVicalEntry = { certNum ->
                        backStack.add(
                            TrustEntryVicalEntryDestination(
                                trustManagerId = key.trustManagerId,
                                trustEntryId = key.trustEntryId,
                                vicalCertNumber = certNum
                            )
                        )
                    },
                    onViewRicalEntry = { certNum ->
                        backStack.add(
                            TrustEntryRicalEntryDestination(
                                trustManagerId = key.trustManagerId,
                                trustEntryId = key.trustEntryId,
                                ricalCertNumber = certNum
                            )
                        )
                    },
                    onEditClicked = {
                        backStack.add(
                            TrustEntryEditDestination(
                                trustManagerId = key.trustManagerId,
                                trustEntryId = key.trustEntryId,
                            )
                        )
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast,
                )
            }
            is TrustEntryEditDestination -> NavEntry(key) {
                TrustEntryEditScreen(
                    trustManagerModel = getTrustManagerModelFromId(key.trustManagerId),
                    trustEntryId = key.trustEntryId,
                    imageLoader = imageLoader,
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast,
                )
            }
            is TrustEntryVicalEntryDestination -> NavEntry(key) {
                TrustEntryVicalEntryScreen(
                    trustManagerModel = getTrustManagerModelFromId(key.trustManagerId),
                    vicalTrustEntryId = key.trustEntryId,
                    certNum = key.vicalCertNumber,
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                )
            }
            is TrustEntryRicalEntryDestination -> NavEntry(key) {
                TrustEntryRicalEntryScreen(
                    trustManagerModel = getTrustManagerModelFromId(key.trustManagerId),
                    ricalTrustEntryId = key.trustEntryId,
                    certNum = key.ricalCertNumber,
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                )
            }
            is CertificateViewerDestination -> NavEntry(key) {
                when (val dataItem = Cbor.decode(key.certificateData.fromBase64Url())) {
                    is CborArray -> CertificateViewerScreen(
                        x509CertChain = X509CertChain.fromDataItem(dataItem),
                        onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    )
                    else -> CertificateViewerScreen(
                        x509Cert = X509Cert.fromDataItem(dataItem),
                        onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    )
                }
            }
            is VerificationDestination -> NavEntry(key) {
                VerificationScreen(
                    walletClient = walletClient,
                    settingsModel = settingsModel,
                    onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    showToast = showToast
                )
            }
            else -> null
        }
    }
}
