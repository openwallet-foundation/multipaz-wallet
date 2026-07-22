package org.multipaz.wallet.android.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.prompt.PromptModel
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.securearea.SecureArea
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.verification.ProximityReaderModel
import org.multipaz.wallet.client.WalletClient
import org.multipaz.eventlogger.SimpleEventLogger

@Composable
fun MdocUrlVerificationNavHost(
    mdocUrl: String,
    walletClient: WalletClient,
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
    showToast: (message: String) -> Unit,
    onFinish: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val startDestination = RequestVerificationFromMdocUrlDestination(mdocUrl = mdocUrl)
    val backStack = rememberNavBackStack(startDestination)

    val entryProvider = mdocUrlVerificationGraph(
        backStack = backStack,
        walletClient = walletClient,
        secureArea = secureArea,
        documentModel = documentModel,
        settingsModel = settingsModel,
        documentTypeRepository = documentTypeRepository,
        zkSystemRepository = zkSystemRepository,
        proximityReaderModel = proximityReaderModel,
        imageLoader = imageLoader,
        promptModel = promptModel,
        coroutineScope = coroutineScope,
        showToast = showToast,
        eventLogger = eventLogger,
        backendIssuerTrustManagerModel = backendIssuerTrustManagerModel,
        userIssuerTrustManagerModel = userIssuerTrustManagerModel,
        readerTrustManager = readerTrustManager,
        issuerTrustManager = issuerTrustManager,
        onFinish = onFinish
    )

    NavDisplay(
        backStack = backStack,
        onBack = { 
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                onFinish()
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        sceneStrategies = listOf(DialogSceneStrategy()),
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        entryProvider = { key ->
            entryProvider(key) ?: throw IllegalStateException("No entry for $key")
        }
    )
}
