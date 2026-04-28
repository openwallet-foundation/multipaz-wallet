package org.multipaz.wallet.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.document.DocumentStore
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientBackendUnreachableException
import org.multipaz.wallet.client.syncWithSharedData
import org.multipaz.wallet.shared.Domains
import org.multipaz.wallet.shared.WalletBackendNotSignedInException

private const val TAG = "AppJustLaunched"

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
