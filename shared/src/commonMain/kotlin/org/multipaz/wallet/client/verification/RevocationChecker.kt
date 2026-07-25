package org.multipaz.wallet.client.verification

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import org.multipaz.crypto.X509CertChain
import org.multipaz.revocation.IdentifierList
import org.multipaz.revocation.RevocationStatus
import org.multipaz.revocation.StatusList
import org.multipaz.storage.NoRecordStorageException
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Represents the state of a document revocation check.
 */
enum class RevocationCheckState {
    /** The credential/identifier is valid and not revoked or suspended. */
    VALID,
    /** The credential/identifier is explicitly revoked. */
    INVALID,
    /** The credential/identifier is currently suspended. */
    SUSPENDED,
    /** The revocation status is unknown, not provided, or could not be verified/downloaded. */
    UNKNOWN
}

/**
 * Result of checking a document's revocation status.
 *
 * @property state High-level state of the revocation status check.
 * @property error Optional [Throwable] explaining why the check failed or was unknown (null if check succeeded).
 */
data class RevocationCheckResult(
    val state: RevocationCheckState,
    val error: Throwable? = null
)

/**
 * Interface for checking and managing the revocation status of digital credentials.
 *
 * Handles downloading, caching, and verifying ISO/IEC 18013-5 or IETF SD-JWT revocation payloads
 * such as status lists and identifier lists.
 */
interface RevocationChecker {
    /**
     * Checks the revocation status for a given non-null [RevocationStatus] payload.
     *
     * This method **never throws** exceptions for non-cancellation errors (such as network, HTTP, timeout,
     * parsing, or signature verification failures). Instead, those errors are caught internally and returned as a
     * [RevocationCheckResult] with state [RevocationCheckState.UNKNOWN] and the underlying exception in [RevocationCheckResult.error].
     * Standard coroutine [CancellationException]s are preserved and rethrown.
     *
     * @param revocationStatus The revocation status object (StatusList or IdentifierList) extracted from the presentation.
     * @param issuerCertChain The certificate chain of the issuer / document signer, used for signature verification if not included in the status payload.
     * @param atTime The point in time at which to evaluate revocation status validity. Defaults to current system time.
     * @param bypassCache If true, forces downloading a fresh status/identifier list payload from the network rather than using cached data.
     * @return [RevocationCheckResult] indicating whether the credential is valid, revoked, suspended, or unknown.
     */
    suspend fun check(
        revocationStatus: RevocationStatus,
        issuerCertChain: X509CertChain? = null,
        atTime: Instant = Clock.System.now(),
        bypassCache: Boolean = false
    ): RevocationCheckResult

    /**
     * Purges expired revocation cache entries from storage.
     */
    suspend fun purgeExpired()

    /**
     * Clears all cached revocation status and identifier list entries from storage.
     */
    suspend fun clearCache()
}

/**
 * Storage-backed implementation of [RevocationChecker] that caches downloaded status and identifier lists in a [StorageTable].
 *
 * Network calls are wrapped with a timeout ([httpTimeout]) and cached payloads expire after [fallbackCacheTtl]
 * unless an explicit expiration is specified by the list format.
 *
 * @param storage Storage instance used to persist revocation list caches.
 * @param httpClient Ktor [HttpClient] used to fetch revocation status and identifier lists over HTTP/HTTPS.
 * @param fallbackCacheTtl Fallback TTL used for cached revocation status and identifier lists if expiration is not present in the payload.
 * @param httpTimeout Timeout duration for network requests fetching revocation lists.
 */
class StorageRevocationChecker(
    private val storage: Storage,
    private val httpClient: HttpClient = HttpClient(),
    private val fallbackCacheTtl: Duration = 1.days,
    private val httpTimeout: Duration = 10.seconds
) : RevocationChecker {

    private val cacheTableSpec = StorageTableSpec(
        name = "RevocationCache",
        supportExpiration = true,
        supportPartitions = false
    )

    private suspend fun getCacheTable(): StorageTable {
        return storage.getTable(cacheTableSpec)
    }

    override suspend fun purgeExpired() {
        try {
            storage.purgeExpired()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Failed to purge expired revocation cache records", e)
        }
    }

    override suspend fun clearCache() {
        try {
            getCacheTable().deleteAll()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Failed to clear revocation cache", e)
        }
    }

    override suspend fun check(
        revocationStatus: RevocationStatus,
        issuerCertChain: X509CertChain?,
        atTime: Instant,
        bypassCache: Boolean
    ): RevocationCheckResult {
        if (revocationStatus is RevocationStatus.Unknown) {
            return RevocationCheckResult(
                state = RevocationCheckState.UNKNOWN,
                error = IllegalStateException("Revocation status unknown or not present")
            )
        }

        return when (revocationStatus) {
            is RevocationStatus.StatusList -> checkStatusList(revocationStatus, issuerCertChain, atTime, bypassCache)
            is RevocationStatus.IdentifierList -> checkIdentifierList(revocationStatus, issuerCertChain, atTime, bypassCache)
            is RevocationStatus.Unknown -> RevocationCheckResult(
                state = RevocationCheckState.UNKNOWN,
                error = IllegalStateException("Revocation status unknown")
            )
        }
    }

    private suspend fun fetchOrGetCached(
        uri: String,
        acceptHeader: String? = null,
        bypassCache: Boolean = false
    ): Pair<ByteArray, ContentType?> {
        val table = getCacheTable()
        if (!bypassCache) {
            try {
                val cachedData = table.get(uri)
                if (cachedData != null) {
                    return Pair(cachedData.toByteArray(), null)
                }
            } catch (_: NoRecordStorageException) {
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.w(TAG, "Error checking revocation cache for $uri", e)
            }
        }

        val (bytes, contentType) = withTimeout(httpTimeout) {
            val response = httpClient.get(uri) {
                if (acceptHeader != null) {
                    headers.append(HttpHeaders.Accept, acceptHeader)
                }
            }
            if (response.status != HttpStatusCode.OK) {
                throw IllegalStateException("HTTP ${response.status}")
            }
            Pair(response.readRawBytes(), response.contentType())
        }

        // TODO: Use exp or ttl in RevocationStatus when added to Multipaz
        val expirationInstant = Clock.System.now() + fallbackCacheTtl
        try {
            table.insert(key = uri, data = ByteString(bytes), expiration = expirationInstant)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                table.update(key = uri, data = ByteString(bytes), expiration = expirationInstant)
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                Logger.w(TAG, "Error updating revocation data in cache for $uri", e2)
            }
        }
        return Pair(bytes, contentType)
    }

    private suspend fun checkStatusList(
        status: RevocationStatus.StatusList,
        issuerCertChain: X509CertChain?,
        atTime: Instant,
        bypassCache: Boolean
    ): RevocationCheckResult {
        return try {
            val cert = status.certificate ?: issuerCertChain?.certificates?.firstOrNull()
            if (cert == null) {
                return RevocationCheckResult(
                    state = RevocationCheckState.UNKNOWN,
                    error = IllegalStateException("No certificate available for signature verification")
                )
            }
            val (bytes, contentType) = fetchOrGetCached(
                uri = status.uri,
                acceptHeader = "$STATUSLIST_CWT, $STATUSLIST_JWT;q=0.9",
                bypassCache = bypassCache
            )

            val statusList = try {
                if (contentType == STATUSLIST_JWT) {
                    val jwtStr = bytes.decodeToString()
                    try {
                        Logger.dJson(TAG, "Status list (JWT) for ${status.uri}", Json.parseToJsonElement(jwtStr))
                    } catch (_: Exception) {}
                    StatusList.fromJwt(
                        jwt = jwtStr,
                        publicKey = cert.ecPublicKey
                    )
                } else if (contentType == STATUSLIST_CWT) {
                    Logger.dCbor(TAG, "Status list (CWT) for ${status.uri}", bytes)
                    StatusList.fromCwt(
                        cwt = bytes,
                        publicKey = cert.ecPublicKey
                    )
                } else {
                    try {
                        Logger.dCbor(TAG, "Status list (CWT) for ${status.uri}", bytes)
                        StatusList.fromCwt(
                            cwt = bytes,
                            publicKey = cert.ecPublicKey
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        val jwtStr = bytes.decodeToString()
                        try {
                            Logger.dJson(TAG, "Status list (JWT) for ${status.uri}", Json.parseToJsonElement(jwtStr))
                        } catch (_: Exception) {}
                        StatusList.fromJwt(
                            jwt = jwtStr,
                            publicKey = cert.ecPublicKey
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e(TAG, "Failed to parse status list", e)
                return RevocationCheckResult(
                    state = RevocationCheckState.UNKNOWN,
                    error = e
                )
            }

            val statusCode = statusList[status.idx]
            when (statusCode) {
                0 -> RevocationCheckResult(state = RevocationCheckState.VALID)
                1 -> RevocationCheckResult(state = RevocationCheckState.INVALID)
                2 -> RevocationCheckResult(state = RevocationCheckState.SUSPENDED)
                else -> RevocationCheckResult(
                    state = RevocationCheckState.UNKNOWN,
                    error = IllegalStateException("Unexpected status code $statusCode")
                )
            }
        } catch (e: TimeoutCancellationException) {
            Logger.w(TAG, "Timeout checking status list for ${status.uri}", e)
            RevocationCheckResult(
                state = RevocationCheckState.UNKNOWN,
                error = e
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "Error checking status list", e)
            RevocationCheckResult(
                state = RevocationCheckState.UNKNOWN,
                error = e
            )
        }
    }

    private suspend fun checkIdentifierList(
        status: RevocationStatus.IdentifierList,
        issuerCertChain: X509CertChain?,
        atTime: Instant,
        bypassCache: Boolean
    ): RevocationCheckResult {
        return try {
            val cert = status.certificate ?: issuerCertChain?.certificates?.firstOrNull()
            if (cert == null) {
                return RevocationCheckResult(
                    state = RevocationCheckState.UNKNOWN,
                    error = IllegalStateException("No certificate available for signature verification")
                )
            }
            val (bytes, _) = fetchOrGetCached(uri = status.uri, bypassCache = bypassCache)
            Logger.dCbor(TAG, "Identifier list (CWT) for ${status.uri}", bytes)
            val identifierList = IdentifierList.fromCwt(
                cwt = bytes,
                publicKey = cert.ecPublicKey
            )
            if (identifierList.contains(status.id)) {
                RevocationCheckResult(state = RevocationCheckState.INVALID)
            } else {
                RevocationCheckResult(state = RevocationCheckState.VALID)
            }
        } catch (e: TimeoutCancellationException) {
            Logger.w(TAG, "Timeout checking identifier list for ${status.uri}", e)
            RevocationCheckResult(
                state = RevocationCheckState.UNKNOWN,
                error = e
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "Error checking identifier list", e)
            RevocationCheckResult(
                state = RevocationCheckState.UNKNOWN,
                error = e
            )
        }
    }

    companion object {
        private const val TAG = "StorageRevocationChecker"
        private val STATUSLIST_JWT = ContentType("application", "statuslist+jwt")
        private val STATUSLIST_CWT = ContentType("application", "statuslist+cwt")
    }
}
