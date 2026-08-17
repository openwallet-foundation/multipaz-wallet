package org.multipaz.wallet.client

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.eventlogger.EventSimple

/**
 * Details recorded when a periodic bookkeeping / refresh job runs.
 *
 * @property trigger the source/trigger of the refresh ("startup", "pull_to_refresh", "periodic_worker", "developer_settings").
 * @property success `true` if all refresh tasks completed without error.
 * @property publicDataRefreshed `true` if new public data was downloaded from the backend.
 * @property publicDataError error message if public data refresh failed, or `null`.
 * @property sharedDataRefreshed `true` if new shared data was downloaded from the backend.
 * @property sharedDataError error message if shared data refresh failed, or `null`.
 * @property refreshedCredentialsCount total number of credentials refreshed across all documents.
 * @property totalDocumentsChecked total number of documents checked for credentials refresh.
 * @property refreshedDocumentsCount total number of documents that received fresh credentials.
 * @property credentialRefreshErrors list of error messages encountered during credential refresh.
 * @property readerKeysRefreshedCount the number of reader keys that were refreshed / replenished.
 * @property readerKeysError error message if reader keys refresh failed, or `null`.
 * @property updatedTrustEntriesCount total number of VICAL/RICAL trust entries updated across trust managers.
 * @property trustManagersChecked list of trust manager identifiers checked for updates.
 * @property trustManagerErrors list of error messages encountered while updating trust managers.
 * @property runtimeDurationMs the duration elapsed while performing the refresh job, in milliseconds.
 */
@CborSerializable
data class PeriodicBookkeepingEventDetails(
    val trigger: String = "unknown",
    val success: Boolean = true,
    val publicDataRefreshed: Boolean = false,
    val publicDataError: String? = null,
    val sharedDataRefreshed: Boolean = false,
    val sharedDataError: String? = null,
    val refreshedCredentialsCount: Int = 0,
    val totalDocumentsChecked: Int = 0,
    val refreshedDocumentsCount: Int = 0,
    val credentialRefreshErrors: List<String> = emptyList(),
    val readerKeysRefreshedCount: Int = 0,
    val readerKeysError: String? = null,
    val updatedTrustEntriesCount: Int = 0,
    val trustManagersChecked: List<String> = emptyList(),
    val trustManagerErrors: List<String> = emptyList(),
    val runtimeDurationMs: Long = 0L,
) {
    companion object {
        const val EVENT_APP_DATA_KEY = "PeriodicBookkeepingEventDetails"

        fun fromEventSimple(event: EventSimple): PeriodicBookkeepingEventDetails? {
            val item = event.appData[EVENT_APP_DATA_KEY] ?: return null
            return try {
                fromDataItem(item)
            } catch (_: Exception) {
                null
            }
        }
    }
}
