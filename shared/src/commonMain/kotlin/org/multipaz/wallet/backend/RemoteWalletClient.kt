package org.multipaz.wallet.backend

import org.multipaz.cbor.annotation.CborSerializable


@CborSerializable
data class RemoteWalletClient(
    val clientId: String,
    val signedInUser: SignedInUser?,
) {
    companion object
}
