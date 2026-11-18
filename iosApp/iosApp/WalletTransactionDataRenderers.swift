import Multipaz

/// The wallet's renderers for transaction data in the consent prompt.
///
/// A `DelegateTransaction` is drawn by the SDK's `DelegateTransactionRendererSwiftUi`, as a
/// spending permission. Transaction types without a renderer here keep the consent prompt's
/// built-in rendering.
let walletTransactionDataRenderers: TransactionDataRendererRepository = {
    let repository = TransactionDataRendererRepository()
    repository.addRenderer(
        identifier: DelegateTransaction.shared.identifier,
        renderer: DelegateTransactionRendererSwiftUi()
    )
    return repository
}()
