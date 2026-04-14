package org.multipaz.wallet.client

import org.multipaz.cbor.annotation.CborSerializable
import kotlin.time.Instant

@CborSerializable
data class WalletObject(
    val identifier: String = "",
    val creationTime: Instant = Instant.DISTANT_PAST,
    val name: String,
    val count: Int
) {
    companion object
}