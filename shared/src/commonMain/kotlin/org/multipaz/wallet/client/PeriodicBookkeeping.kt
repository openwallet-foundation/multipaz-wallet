package org.multipaz.wallet.client

import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.eventlogger.EventLogger
import org.multipaz.eventlogger.EventSimple
import org.multipaz.provisioning.ProvisioningModel
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
 * 5. Logging a [EventSimple] with [PeriodicBookkeepingEventDetails] stored in [EventSimple.appData] via [eventLogger], if provided.
 *
 * @param documentStore the [DocumentStore] containing provisioned documents.
 * @param provisioningModel the [ProvisioningModel] used to refresh OpenID4VCI credentials.
 * @param eventLogger optional [EventLogger] to record the bookkeeping event.
 * @return `true` if all tasks completed without error, or `false` if one or more tasks failed/were unreachable.
 */
suspend fun WalletClient.runPeriodicBookkeeping(
    documentStore: DocumentStore,
    provisioningModel: ProvisioningModel,
    eventLogger: EventLogger? = null,
): Boolean {
    Logger.i(TAG, "Starting periodic bookkeeping...")
    val startTime = Clock.System.now()
    var success = true
    var publicDataUpdated = false
    var sharedDataUpdated = false
    var totalRefreshedCredentialsCount = 0
    var readerKeysRefreshed = false

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
        success = false
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.e(TAG, "Error refreshing public data", e)
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
                    mpzPassKeylessSdJwtVcDomain = Domains.DOMAIN_SDJWT_KEYLESS
                )
            }
        } catch (e: WalletBackendNotSignedInException) {
            Logger.i(TAG, "User not signed in, skipping shared data refresh")
        } catch (e: WalletClientBackendUnreachableException) {
            Logger.w(TAG, "Wallet backend unreachable during shared data refresh", e)
            success = false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "Error refreshing shared data", e)
            success = false
        }
    }

    // 3. Refresh Credentials for All Provisioned Documents
    try {
        val documents = documentStore.listDocuments()
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
                    Logger.i(TAG, "Document ${document.identifier}: refreshed $addedCount credentials")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Logger.w(TAG, "Failed refreshing credentials for document ${document.identifier}", e)
                    success = false
                }
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.e(TAG, "Error listing documents for credential refresh", e)
        success = false
    }

    // 4. Refresh Reader Keys
    try {
        Logger.i(TAG, "Refreshing reader keys...")
        refreshReaderKeys()
        readerKeysRefreshed = true
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Failed refreshing reader keys", e)
    }

    val endTime = Clock.System.now()
    val runtimeDuration = endTime - startTime
    val runtimeDurationMs = runtimeDuration.inWholeMilliseconds

    // 5. Log EventSimple containing PeriodicBookkeepingEventDetails in appData
    if (eventLogger != null) {
        try {
            val details = PeriodicBookkeepingEventDetails(
                publicDataRefreshed = publicDataUpdated,
                sharedDataRefreshed = sharedDataUpdated,
                refreshedCredentialsCount = totalRefreshedCredentialsCount,
                readerKeysRefreshed = readerKeysRefreshed,
                runtimeDurationMs = runtimeDurationMs,
            )
            eventLogger.addEvent(
                EventSimple(
                    timestamp = Clock.System.now(),
                    data = ByteString(),
                    appData = mapOf("PeriodicBookkeepingEventDetails" to details.toDataItem())
                )
            )
            Logger.i(TAG, "Logged PeriodicBookkeeping EventSimple with event details (duration: ${runtimeDurationMs}ms)")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Error logging EventSimple for periodic bookkeeping", e)
        }
    }

    Logger.i(TAG, "Periodic bookkeeping finished in ${runtimeDurationMs}ms. Overall success: $success")
    return success
}
