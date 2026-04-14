package org.multipaz.wallet.backend

import org.multipaz.cbor.annotation.CborSerializable

@CborSerializable
sealed class SignedInUser {
    abstract val sharedDataKey: String
}

data class GoogleSignedInUser(
    val id: String
): SignedInUser() {
    override val sharedDataKey
        get() = "g:$id"
}
