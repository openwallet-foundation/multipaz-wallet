package org.multipaz.wallet.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import org.multipaz.mdoc.rical.SignedRical
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.trustmanagement.TrustEntry
import org.multipaz.trustmanagement.TrustEntryRical
import org.multipaz.trustmanagement.TrustEntryVical
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.util.Logger

private const val TAG = "TrustManagerExt"

/**
 * Result of checking and updating a [TrustEntry].
 */
sealed interface TrustEntryUpdateResult {
    /**
     * The entry was successfully updated with a newer version.
     *
     * @property listType the type of trust list (e.g. "VICAL" or "RICAL").
     * @property issueId the new issue ID or ID number of the trust list, or `null` if not specified.
     * @property previousIssueId the previous issue ID or ID number, or `null` if not specified.
     * @property certificateCount the number of certificates in the updated trust list.
     */
    data class Updated(
        val listType: String,
        val issueId: Long?,
        val previousIssueId: Long? = null,
        val certificateCount: Int = 0,
    ) : TrustEntryUpdateResult

    /**
     * The entry is already at the latest version.
     *
     * @property listType the type of trust list (e.g. "VICAL" or "RICAL").
     */
    data class AlreadyUpToDate(
        val listType: String,
    ) : TrustEntryUpdateResult

    /** The entry does not have an update URL or is not a VICAL/RICAL entry. */
    data object NoUpdateUrl : TrustEntryUpdateResult
}

/**
 * Checks for an update for a single [TrustEntry] in this [TrustManager] and updates it if a newer version is available.
 *
 * @param entry the trust entry to check and update.
 * @param httpClient the [HttpClient] to use for downloading.
 * @return a [TrustEntryUpdateResult] indicating the outcome.
 * @throws Exception if an error occurs while downloading or parsing/validating the updated trust list.
 */
suspend fun TrustManager.updateTrustEntry(
    entry: TrustEntry,
    httpClient: HttpClient = HttpClient(),
): TrustEntryUpdateResult {
    return when (entry) {
        is TrustEntryVical -> {
            val signedVical = SignedVical.parse(
                encodedSignedVical = entry.encodedSignedVical.toByteArray(),
                disableSignatureVerification = true
            )
            val url = signedVical.vical.vicalUrl
            if (url.isNullOrBlank()) {
                return TrustEntryUpdateResult.NoUpdateUrl
            }

            val response = httpClient.get(url)
            if (!response.status.isSuccess()) {
                throw IllegalStateException("HTTP ${response.status.value}: ${response.status.description}")
            }
            val bytes = response.readRawBytes()

            val downloadedSignedVical = SignedVical.parse(
                encodedSignedVical = bytes,
                disableSignatureVerification = false
            )
            val currentIssueId = signedVical.vical.vicalIssueID
            val downloadedIssueId = downloadedSignedVical.vical.vicalIssueID

            if (currentIssueId != null && downloadedIssueId != null && downloadedIssueId <= currentIssueId) {
                TrustEntryUpdateResult.AlreadyUpToDate(listType = "VICAL")
            } else if (currentIssueId == null && downloadedIssueId == null &&
                bytes.contentEquals(entry.encodedSignedVical.toByteArray())
            ) {
                TrustEntryUpdateResult.AlreadyUpToDate(listType = "VICAL")
            } else {
                updateVical(
                    entry = entry,
                    encodedSignedVical = ByteString(bytes)
                )
                TrustEntryUpdateResult.Updated(
                    listType = "VICAL",
                    issueId = downloadedIssueId,
                    previousIssueId = currentIssueId,
                    certificateCount = downloadedSignedVical.vical.certificateInfos.size
                )
            }
        }
        is TrustEntryRical -> {
            val signedRical = SignedRical.parse(
                encodedSignedRical = entry.encodedSignedRical.toByteArray(),
                disableSignatureVerification = true
            )
            val url = signedRical.rical.latestRicalUrl
            if (url.isNullOrBlank()) {
                return TrustEntryUpdateResult.NoUpdateUrl
            }

            val response = httpClient.get(url)
            if (!response.status.isSuccess()) {
                throw IllegalStateException("HTTP ${response.status.value}: ${response.status.description}")
            }
            val bytes = response.readRawBytes()

            val downloadedSignedRical = SignedRical.parse(
                encodedSignedRical = bytes,
                disableSignatureVerification = false
            )
            val currentId = signedRical.rical.id
            val downloadedId = downloadedSignedRical.rical.id

            if (currentId != null && downloadedId != null && downloadedId <= currentId) {
                TrustEntryUpdateResult.AlreadyUpToDate(listType = "RICAL")
            } else if (currentId == null && downloadedId == null &&
                bytes.contentEquals(entry.encodedSignedRical.toByteArray())
            ) {
                TrustEntryUpdateResult.AlreadyUpToDate(listType = "RICAL")
            } else {
                updateRical(
                    entry = entry,
                    encodedSignedRical = ByteString(bytes)
                )
                TrustEntryUpdateResult.Updated(
                    listType = "RICAL",
                    issueId = downloadedId,
                    previousIssueId = currentId,
                    certificateCount = downloadedSignedRical.rical.certificateInfos.size
                )
            }
        }
        else -> TrustEntryUpdateResult.NoUpdateUrl
    }
}

/**
 * Checks and updates all VICAL and RICAL entries in this [TrustManager] that have an update URL.
 *
 * @param httpClient the [HttpClient] to use for downloading updates.
 * @return the number of entries successfully updated.
 * @throws Exception if reading entries from storage fails.
 */
suspend fun TrustManager.updateEntries(
    httpClient: HttpClient = HttpClient(),
): Int {
    var updatedCount = 0
    val entries = getEntries()
    for (entry in entries) {
        try {
            val result = updateTrustEntry(entry = entry, httpClient = httpClient)
            if (result is TrustEntryUpdateResult.Updated) {
                updatedCount++
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Error updating trust entry ${entry.identifier}", e)
        }
    }
    return updatedCount
}

/**
 * Adds a signed VICAL to the trust manager after validating its signature.
 *
 * @param encodedSignedVical The raw encoded bytes of the signed VICAL.
 * @param metadata Associated metadata for the VICAL.
 * @return The newly created and persisted [TrustEntryVical].
 * @throws Exception if signature verification fails or the data is malformed.
 */
@Throws(Exception::class)
suspend fun TrustManager.addVicalWithValidation(
    encodedSignedVical: ByteString,
    metadata: TrustMetadata = TrustMetadata(),
): TrustEntryVical {
    SignedVical.parse(
        encodedSignedVical = encodedSignedVical.toByteArray(),
        disableSignatureVerification = false
    )
    return addVical(
        encodedSignedVical = encodedSignedVical,
        metadata = metadata
    )
}

/**
 * Adds a signed RICAL to the trust manager after validating its signature.
 *
 * @param encodedSignedRical The raw encoded bytes of the signed RICAL.
 * @param metadata Associated metadata for the RICAL.
 * @return The newly created and persisted [TrustEntryRical].
 * @throws Exception if signature verification fails or the data is malformed.
 */
@Throws(Exception::class)
suspend fun TrustManager.addRicalWithValidation(
    encodedSignedRical: ByteString,
    metadata: TrustMetadata = TrustMetadata(),
): TrustEntryRical {
    SignedRical.parse(
        encodedSignedRical = encodedSignedRical.toByteArray(),
        disableSignatureVerification = false
    )
    return addRical(
        encodedSignedRical = encodedSignedRical,
        metadata = metadata
    )
}

/**
 * Validates the signature and structure of a signed VICAL.
 *
 * @param encodedSignedVical The raw encoded bytes of the signed VICAL.
 * @throws Exception if signature verification fails or the data is malformed.
 */
@Throws(Exception::class)
suspend fun validateSignedVical(encodedSignedVical: ByteArray) {
    SignedVical.parse(
        encodedSignedVical = encodedSignedVical,
        disableSignatureVerification = false
    )
}

/**
 * Validates the signature and structure of a signed RICAL.
 *
 * @param encodedSignedRical The raw encoded bytes of the signed RICAL.
 * @throws Exception if signature verification fails or the data is malformed.
 */
@Throws(Exception::class)
suspend fun validateSignedRical(encodedSignedRical: ByteArray) {
    SignedRical.parse(
        encodedSignedRical = encodedSignedRical,
        disableSignatureVerification = false
    )
}

