package org.multipaz.wallet.shared

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable

/**
 * Data describing a known credential issuer
 *
 * @property name name to show to the user.
 * @property iconUrl card art to show to the user.
 */
@CborSerializable
sealed class CredentialIssuer(
    open val name: String,
    open val iconUrl: String
) {
    companion object
}