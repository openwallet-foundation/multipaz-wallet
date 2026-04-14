package org.multipaz.wallet.client

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable

/**
 * Display data about the user currently signed in.
 *
 * @property id the user's ID, typically their email address.
 * @property displayName the user's display name, typically their real name, if available.
 * @property profilePicture the user's profile picture, if available.
 */
@CborSerializable
data class WalletClientSignedInUser(
    val id: String,
    val displayName: String?,
    val profilePicture: ByteString?
)