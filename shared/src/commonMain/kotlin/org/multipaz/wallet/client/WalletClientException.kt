package org.multipaz.wallet.client

sealed class WalletClientException(
    message: String? = null,
    cause: Throwable? = null
): Exception(message, cause)