package org.multipaz.wallet.shared

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Thrown when the client attempts to store shared data that exceeds the maximum allowed size.
 *
 * @property message a message describing the exception or `null`.
 */
@CborSerializable
class WalletBackendSharedDataTooLargeException(message: String? = null): WalletBackendException(message)
