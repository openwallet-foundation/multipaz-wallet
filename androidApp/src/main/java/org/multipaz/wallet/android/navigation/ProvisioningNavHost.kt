package org.multipaz.wallet.android.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import org.multipaz.compose.document.DocumentModel
import org.multipaz.document.DocumentStore
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.WalletClient

@Composable
fun ProvisioningNavHost(
    offerUri: String,
    walletClient: WalletClient,
    documentStore: DocumentStore,
    documentModel: DocumentModel,
    settingsModel: SettingsModel,
    provisioningModel: ProvisioningModel,
    imageLoader: ImageLoader,
    onComplete: () -> Unit,
    onCanceled: () -> Unit,
    onFailed: (Throwable) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val startDestination = ProvisioningDestination(openID4VCICredentialOfferUri = offerUri)
    val backStack = rememberNavBackStack(startDestination)

    val entryProvider = provisioningGraph(
        backStack = backStack,
        walletClient = walletClient,
        documentStore = documentStore,
        documentModel = documentModel,
        settingsModel = settingsModel,
        provisioningModel = provisioningModel,
        imageLoader = imageLoader,
        coroutineScope = coroutineScope,
        context = context,
        onComplete = onComplete,
        onCanceled = onCanceled,
        onFailed = onFailed,
    )

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                onCanceled()
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
