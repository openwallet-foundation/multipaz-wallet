package org.multipaz.wallet.shared

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Data class representing an active device session for a signed-in user.
 *
 * @property clientId uniquely identifies the client instance.
 * @property clientType the platform type of the client ([ClientType.IOS], [ClientType.ANDROID], [ClientType.WEB]).
 * @property lastSeenMillis timestamp in milliseconds when the client last communicated with the backend.
 * @property location approximate location derived from IP address, or `null` if unavailable.
 * @property clientDevice the name of the device, or `null` if unknown.
 * @property clientPlatform the name and version of the platform, or `null` if not provided.
 */
@CborSerializable
data class Session(
    val clientId: String,
    val clientType: ClientType,
    val lastSeenMillis: Long,
    val location: String? = null,
    val clientDevice: String? = null,
    val clientPlatform: String? = null
) {
    companion object
}
