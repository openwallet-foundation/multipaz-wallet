package org.multipaz.wallet.shared

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable

@CborSerializable
data class GetSharedDataResult(
    val version: Long,
    val data: ByteString,
) {
    companion object
}