package org.multipaz.wallet.shared

import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.Serializable
import org.multipaz.cbor.annotation.CborSerializable

/**
 * Data describing a known credential issuer
 *
 * @property name name to show to the user.
 * @property iconUrl URL with card art to show to the user.
 */
@Serializable
@CborSerializable
sealed class CredentialIssuer(
    open val name: String,
    open val iconUrl: String,
) {
    companion object
}