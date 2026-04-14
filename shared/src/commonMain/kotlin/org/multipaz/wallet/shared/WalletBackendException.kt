package org.multipaz.wallet.shared

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcException


/**
 * Base class for all exceptions thrown by the wallet backend.
 *
 * @property message a message describing the exception or `null`.
 */
@CborSerializable
@RpcException
sealed class WalletBackendException(message: String? = null): Exception(message) {
    companion object
}
