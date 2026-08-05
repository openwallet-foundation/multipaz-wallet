package org.multipaz.wallet.client

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Details recorded when a daily bookkeeping job runs.
 *
 * @property publicDataRefreshed `true` if new public data was downloaded from the backend.
 * @property sharedDataRefreshed `true` if new shared data was downloaded from the backend.
 * @property refreshedCredentialsCount total number of credentials refreshed across all documents.
 * @property readerKeysRefreshed `true` if reader keys were successfully refreshed.
 * @property runtimeDurationMs the duration elapsed while performing the daily bookkeeping job, in milliseconds.
 */
@CborSerializable
data class DailyBookkeepingEventDetails(
    val publicDataRefreshed: Boolean,
    val sharedDataRefreshed: Boolean,
    val refreshedCredentialsCount: Int,
    val readerKeysRefreshed: Boolean,
    val runtimeDurationMs: Long,
) {
    companion object
}
