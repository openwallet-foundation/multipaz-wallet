package org.multipaz.wallet.shared

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.trustmanagement.TrustEntry

/**
 * Public data available to all wallet clients.
 *
 * This object contains data provided by the wallet backend to any client, that is,
 * independent of whether the client is signed in to an account.
 *
 * Because newer versions may be processed by older clients, any additions to this
 * data structure MUST be backwards compatible.
 *
 * @property version the version of this data.
 * @property trustedIssuers a list of credential issuers that are considered trusted.
 * @property trustedReaders a list of credential readers that are considered trusted.
 */
@CborSerializable(schemaHash = "r1pWGvb-WhQNLz2U6buGhz_4J2vXFXE3VVEaedLwpYs")
data class WalletClientPublicData(
    val version: Long,
    val trustedIssuers: List<TrustEntry>,
    val trustedReaders: List<TrustEntry>,
) {
    companion object
}
