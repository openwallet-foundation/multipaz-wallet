package org.multipaz.wallet.client

class WalletClientBackendUnreachableException(
    message: String? = null,
    cause: Throwable? = null
): WalletClientException(message, cause)