package org.multipaz.wallet.backend

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable

/**
 * Data shared between wallet instances signed in to the same account.
 *
 * @property walletServerEncryptionKeySha256 the SHA-256 of the encryption key used to encrypt the data.
 * @property version the version of the data.
 * @property data the data (encrypted by the client)
 */
@CborSerializable
data class SharedData(
    val walletServerEncryptionKeySha256: ByteString,
    val version: Long,
    val data: ByteString,
) {
    companion object
}