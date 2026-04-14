package org.multipaz.wallet.client


class WalletClientAuthorizationException(
    message: String? = null,
    cause: Throwable? = null
): WalletClientException(message, cause)