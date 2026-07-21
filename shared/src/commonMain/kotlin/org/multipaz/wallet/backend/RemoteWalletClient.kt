package org.multipaz.wallet.backend

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.wallet.shared.ClientType

/**
 * Data class representing a remote wallet client registered in backend storage.
 *
 * @property clientId unique identifier for the client.
 * @property signedInUser details of the signed-in user, or `null` if not signed in.
 * @property clientType the platform type of the client ([ClientType.IOS], [ClientType.ANDROID], [ClientType.WEB]), if known.
 * @property lastSeenMillis timestamp in epoch milliseconds when the client last communicated with the backend, if known.
 */
@CborSerializable
data class RemoteWalletClient(
    val clientId: String,
    val signedInUser: SignedInUser?,
    val clientType: ClientType? = null,
    val lastSeenMillis: Long? = null
) {
    companion object
}
