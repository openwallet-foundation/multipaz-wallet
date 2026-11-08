package org.multipaz.wallet.android.navigation

import android.annotation.SuppressLint
import android.content.Context
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import coil3.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.multipaz.compose.document.DocumentModel
import org.multipaz.document.DocumentStore
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.encodeCardArt
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.ErrorDialog
import org.multipaz.wallet.android.ui.provisioning.ProvisioningCompleteScreen
import org.multipaz.wallet.android.ui.provisioning.ProvisioningRoute
import org.multipaz.wallet.client.DocumentPreconsentSetting
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.setPreconsentSetting
import org.multipaz.wallet.client.setProvisionedDocumentIdentifier
import org.multipaz.wallet.shared.BuildConfig

private const val TAG = "ProvisioningGraph"

@SuppressLint("LocalContextGetResourceValueCall")
fun provisioningGraph(
    backStack: MutableList<NavKey>,
    walletClient: WalletClient,
    documentStore: DocumentStore,
    documentModel: DocumentModel,
    settingsModel: SettingsModel,
    provisioningModel: ProvisioningModel,
    imageLoader: ImageLoader,
    coroutineScope: CoroutineScope,
    context: Context,
    onComplete: () -> Unit,
    onCanceled: () -> Unit,
    onFailed: (Throwable) -> Unit,
): (NavKey) -> NavEntry<NavKey>? {
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
                        onCanceled()
                    },
                    onComplete = { document, provisionedDocument ->
                        coroutineScope.launch {
                            var errorDialogToAdd: ErrorDialogDestination? = null
                            if (walletClient.signedInUser.value != null) {
                                try {
                                    document.setProvisionedDocumentIdentifier(provisionedDocument.identifier)
                                    Logger.i(TAG, "Adding a new provisioned document to shared data")
                                    walletClient.refreshSharedData()
                                    val provisionedDocumentToAdd = provisionedDocument.cardArt?.let { cardArt ->
                                        val origBytes = cardArt.size
                                        val scaledCardArt = encodeCardArt(
                                            cardArt = cardArt,
                                            maxBytes = BuildConfig.MAX_CARD_ART_SIZE_IN_SHARED_DATA_KB * 1024
                                        )
                                        if (scaledCardArt != null) {
                                            val newBytes = scaledCardArt.size
                                            Logger.i(
                                                TAG,
                                                "Scaled down card art from $origBytes bytes to $newBytes bytes"
                                            )
                                            provisionedDocument.withCardArt(scaledCardArt)
                                        } else {
                                            null
                                        }
                                    } ?: provisionedDocument
                                    walletClient.setSharedData(
                                        sharedData = walletClient.sharedData.value!!.addProvisionedDocument(
                                            provisionedDocumentToAdd
                                        ),
                                        suppressSpinner = true
                                    )
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Logger.e(TAG, "Error adding pass", e)
                                    errorDialogToAdd = ErrorDialogDestination(
                                        title = context.getString(R.string.provisioning_add_pass_to_shared_data_error_title),
                                        textMarkdown = context.getString(
                                            R.string.provisioning_add_pass_to_shared_data_error_message_md,
                                            e.toString()
                                        )
                                    )
                                }
                            }
                            if (!settingsModel.preconsentForNewDocuments.value) {
                                document.setPreconsentSetting(DocumentPreconsentSetting.AlwaysRequireConsent)
                            } else {
                                document.setPreconsentSetting(DocumentPreconsentSetting.NeverRequireConsent)
                            }
                            documentModel.documentInfos.first { list ->
                                list.any { it.document.identifier == document.identifier }
                            }
                            backStack.clear()
                            backStack.add(
                                ProvisioningCompleteDestination(
                                    documentId = document.identifier,
                                    autoFinish = errorDialogToAdd == null
                                )
                            )
                            if (errorDialogToAdd != null) {
                                backStack.add(errorDialogToAdd)
                            }
                        }
                    },
                    onFailed = { error ->
                        onFailed(error)
                    }
                )
            }
            is ProvisioningCompleteDestination -> NavEntry(key) {
                ProvisioningCompleteScreen(
                    documentModel = documentModel,
                    documentId = key.documentId,
                    autoFinish = key.autoFinish,
                    onComplete = onComplete
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
                        onComplete()
                    }
                )
            }
            else -> null
        }
    }
}
