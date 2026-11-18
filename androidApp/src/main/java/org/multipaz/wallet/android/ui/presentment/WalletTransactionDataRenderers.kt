package org.multipaz.wallet.android.ui.presentment

import org.multipaz.compose.presentment.DelegateTransactionRendererCompose
import org.multipaz.prompt.TransactionDataRendererRepository
import org.multipaz.utopia.knowntypes.DelegateTransaction

/**
 * The wallet's renderers for transaction data in the consent prompt.
 *
 * A [DelegateTransaction] is drawn by the SDK's [DelegateTransactionRendererCompose], as a
 * spending permission. Transaction types without a renderer here keep the consent prompt's
 * built-in rendering.
 */
val walletTransactionDataRenderers = TransactionDataRendererRepository().apply {
    addRenderer(DelegateTransaction.identifier, DelegateTransactionRendererCompose)
}
