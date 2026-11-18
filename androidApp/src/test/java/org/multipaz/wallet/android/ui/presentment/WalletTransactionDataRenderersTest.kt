package org.multipaz.wallet.android.ui.presentment

import org.junit.Assert.assertSame
import org.junit.Test
import org.multipaz.compose.presentment.DelegateTransactionRendererCompose
import org.multipaz.utopia.knowntypes.DelegateTransaction

class WalletTransactionDataRenderersTest {

    // AP2 requires the signed Mandate Content to be shown, so `delegate` must not fall back to the
    // consent prompt's built-in rendering, which shows only a heading for it.
    @Test
    fun delegateIsDrawnByTheSdkRenderer() {
        assertSame(
            DelegateTransactionRendererCompose,
            walletTransactionDataRenderers.getRenderer(DelegateTransaction.identifier)
        )
    }
}
