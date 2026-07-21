package org.multipaz.wallet.shared

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Data class representing an active device session for a signed-in user.
 *
 * @property clientId uniquely identifies the client instance.
 * @property clientType the platform type of the client ([ClientType.IOS], [ClientType.ANDROID], [ClientType.WEB]).
 * @property lastSeenMillis timestamp in milliseconds when the client last communicated with the backend.
 */
@CborSerializable
data class Session(
    val clientId: String,
    val clientType: ClientType,
    val lastSeenMillis: Long
) {
    companion object
}
