package org.multipaz.wallet.client

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.eventlogger.EventLogger
import org.multipaz.eventlogger.EventSimple
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.Logger
import org.multipaz.wallet.shared.Domains
import org.multipaz.wallet.shared.WalletBackendNotSignedInException
import kotlin.time.Clock

private const val TAG = "PeriodicBookkeeping"

/**
 * Runs periodic background housekeeping for the wallet, including:
 * 1. Refreshing public data (trust roots, issuer metadata, configuration)
 * 2. Refreshing shared data and syncing document store
 * 3. Refreshing document credentials for all provisioned documents
 * 4. Refreshing reader keys
 * 5. Refreshing RICAL and VICAL entries in provided [trustManagers]
 * 6. Logging a [EventSimple] with [PeriodicBookkeepingEventDetails] stored in [EventSimple.appData] via [eventLogger], if provided.
 *
 * @param documentStore the [DocumentStore] containing provisioned documents.
 * @param provisioningModel the [ProvisioningModel] used to refresh OpenID4VCI credentials.
 * @param trustManagers list of [TrustManager] instances whose VICAL/RICAL entries should be checked for updates.
 * @param httpClient optional [HttpClient] used to fetch trust list updates over HTTP/HTTPS.
 * @param eventLogger optional [EventLogger] to record the bookkeeping event.
 * @param initialPreconsentSetting optional [DocumentPreconsentSetting] to apply when syncing new documents.
 * @param trigger optional string identifying the source/trigger of the refresh.
 * @param onDocumentOrderChanged optional callback invoked with the new document order mapped to local document IDs.
 * @return `true` if all tasks completed without error, or `false` if one or more tasks failed/were unreachable.
 */
suspend fun WalletClient.runPeriodicBookkeeping(
    documentStore: DocumentStore,
    provisioningModel: ProvisioningModel,
    trustManagers: List<TrustManager> = emptyList(),
    httpClient: HttpClient = HttpClient(),
    eventLogger: EventLogger? = null,
    initialPreconsentSetting: DocumentPreconsentSetting? = null,
    trigger: String = "periodic_worker",
    onDocumentOrderChanged: ((documentOrder: List<String>) -> Unit)? = null,
): Boolean {
    Logger.i(TAG, "Starting periodic bookkeeping (trigger=$trigger)...")
    val startTime = Clock.System.now()
    var success = true
    var publicDataUpdated = false
    var publicDataError: String? = null
    var sharedDataUpdated = false
    var sharedDataError: String? = null
    var totalRefreshedCredentialsCount = 0
    var totalDocumentsChecked = 0
    var refreshedDocumentsCount = 0
    val credentialRefreshErrors = mutableListOf<String>()
    var readerKeysRefreshed = 0
    var readerKeysError: String? = null
    var totalUpdatedTrustEntriesCount = 0
    val trustManagersChecked = mutableListOf<String>()
    val trustManagerErrors = mutableListOf<String>()

    // 1. Download Latest Public Data
    try {
        Logger.i(TAG, "Refreshing public data...")
        publicDataUpdated = refreshPublicData()
        if (publicDataUpdated) {
            Logger.i(TAG, "Downloaded new public data from backend")
        } else {
            Logger.i(TAG, "Public data is already up to date")
        }
    } catch (e: WalletClientBackendUnreachableException) {
        Logger.w(TAG, "Wallet backend unreachable during public data refresh", e)
        publicDataError = e.message ?: "Backend unreachable"
        success = false
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.e(TAG, "Error refreshing public data", e)
        publicDataError = e.message ?: e.toString()
        success = false
    }

    // 2. Download Latest Shared Data & Sync Document Store
    if (signedInUser.value != null) {
        try {
            Logger.i(TAG, "Refreshing shared data...")
            sharedDataUpdated = refreshSharedData()
            if (sharedDataUpdated) {
                Logger.i(TAG, "Downloaded new shared data from backend")
            } else {
                Logger.i(TAG, "Shared data is already up to date")
            }
            sharedData.value?.let { sharedData ->
                documentStore.syncWithSharedData(
                    sharedData = sharedData,
                    mpzPassIsoMdocDomain = Domains.DOMAIN_MDOC_SOFTWARE,
                    mpzPassSdJwtVcDomain = Domains.DOMAIN_SDJWT_SOFTWARE,
                    mpzPassKeylessSdJwtVcDomain = Domains.DOMAIN_SDJWT_KEYLESS,
                    walletClient = this,
                    initialPreconsentSetting = initialPreconsentSetting,
                    onDocumentOrderChanged = onDocumentOrderChanged,
                )
            }
        } catch (e: WalletBackendNotSignedInException) {
            Logger.i(TAG, "User not signed in, skipping shared data refresh")
        } catch (e: WalletClientBackendUnreachableException) {
            Logger.w(TAG, "Wallet backend unreachable during shared data refresh", e)
            sharedDataError = e.message ?: "Backend unreachable"
            success = false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "Error refreshing shared data", e)
            sharedDataError = e.message ?: e.toString()
            success = false
        }
    }

    // 3. Refresh Credentials for All Provisioned Documents
    try {
        val documents = documentStore.listDocuments()
        totalDocumentsChecked = documents.size
        Logger.i(TAG, "Checking credentials for ${documents.size} documents...")
        for (document in documents) {
            val authData = document.authorizationData
            if (authData != null) {
                try {
                    val addedCount = provisioningModel.openID4VCIRefreshCredentials(
                        document = document,
                        authorizationData = authData,
                        clientPreferences = getOpenID4VCIClientPreferences(),
                        backend = getOpenID4VCIBackend()
                    )
                    totalRefreshedCredentialsCount += addedCount
                    if (addedCount > 0) {
                        refreshedDocumentsCount++
                    }
                    Logger.i(TAG, "Document ${document.identifier}: refreshed $addedCount credentials")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val errorMsg = "${document.identifier}: ${e.message ?: e.toString()}"
                    Logger.w(TAG, "Failed refreshing credentials for document ${document.identifier}", e)
                    credentialRefreshErrors.add(errorMsg)
                    success = false
                }
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.e(TAG, "Error listing documents for credential refresh", e)
        credentialRefreshErrors.add(e.message ?: e.toString())
        success = false
    }

    // 4. Refresh Reader Keys
    try {
        Logger.i(TAG, "Refreshing reader keys...")
        readerKeysRefreshed = refreshReaderKeys()
        Logger.i(TAG, "Refreshed $readerKeysRefreshed reader keys")
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Failed refreshing reader keys", e)
        readerKeysError = e.message ?: e.toString()
    }

    // 5. Refresh RICAL and VICAL entries in trust managers
    for (trustManager in trustManagers) {
        trustManagersChecked.add(trustManager.identifier)
        try {
            Logger.i(TAG, "Refreshing trust entries in '${trustManager.identifier}'...")
            val updatedCount = trustManager.updateEntries(httpClient = httpClient)
            totalUpdatedTrustEntriesCount += updatedCount
            if (updatedCount > 0) {
                Logger.i(TAG, "Updated $updatedCount trust entries in '${trustManager.identifier}'")
            } else {
                Logger.i(TAG, "Trust entries in '${trustManager.identifier}' are already up to date")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val errorMsg = "${trustManager.identifier}: ${e.message ?: e.toString()}"
            Logger.w(TAG, "Failed refreshing trust entries in '${trustManager.identifier}'", e)
            trustManagerErrors.add(errorMsg)
            success = false
        }
    }

    val endTime = Clock.System.now()
    val runtimeDuration = endTime - startTime
    val runtimeDurationMs = runtimeDuration.inWholeMilliseconds

    // 6. Log EventSimple containing PeriodicBookkeepingEventDetails in appData
    if (eventLogger != null) {
        try {
            val details = PeriodicBookkeepingEventDetails(
                trigger = trigger,
                success = success,
                publicDataRefreshed = publicDataUpdated,
                publicDataError = publicDataError,
                sharedDataRefreshed = sharedDataUpdated,
                sharedDataError = sharedDataError,
                refreshedCredentialsCount = totalRefreshedCredentialsCount,
                totalDocumentsChecked = totalDocumentsChecked,
                refreshedDocumentsCount = refreshedDocumentsCount,
                credentialRefreshErrors = credentialRefreshErrors,
                readerKeysRefreshedCount = readerKeysRefreshed,
                readerKeysError = readerKeysError,
                updatedTrustEntriesCount = totalUpdatedTrustEntriesCount,
                trustManagersChecked = trustManagersChecked,
                trustManagerErrors = trustManagerErrors,
                runtimeDurationMs = runtimeDurationMs,
            )
            eventLogger.addEvent(
                EventSimple(
                    timestamp = Clock.System.now(),
                    data = ByteString(),
                    appData = mapOf(PeriodicBookkeepingEventDetails.EVENT_APP_DATA_KEY to details.toDataItem())
                )
            )
            Logger.i(TAG, "Logged PeriodicBookkeeping EventSimple with event details (trigger: $trigger, duration: ${runtimeDurationMs}ms, success: $success)")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Error logging EventSimple for periodic bookkeeping", e)
        }
    }

    Logger.i(TAG, "Periodic bookkeeping finished in ${runtimeDurationMs}ms. Overall success: $success")
    return success
}
