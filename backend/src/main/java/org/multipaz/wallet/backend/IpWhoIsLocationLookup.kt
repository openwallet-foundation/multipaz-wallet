package org.multipaz.wallet.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.util.Logger
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Implementation of [IpLocationLookup] using the ipwhois.io API with in-memory caching.
 *
 * @param httpClientEngine optional [HttpClientEngine] for custom HTTP transport (e.g. testing with MockEngine).
 * @param clock the time source for TTL calculation.
 * @param cacheTtlMillis duration in milliseconds before a cached lookup expires (default 1 hour).
 */
class IpWhoIsLocationLookup(
    httpClientEngine: HttpClientEngine? = null,
    private val clock: Clock = Clock.System,
    private val cacheTtlMillis: Long = 1.hours.inWholeMilliseconds
) : IpLocationLookup {

    private val httpClient = if (httpClientEngine != null) {
        HttpClient(httpClientEngine)
    } else {
        HttpClient(Java)
    }

    private data class CacheEntry(
        val location: String?,
        val timestampMillis: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    override suspend fun lookup(ipAddress: String?): String? {
        if (ipAddress.isNullOrBlank()) {
            return null
        }
        val targetIp = ipAddress.split(",").firstOrNull()?.trim()
        if (targetIp.isNullOrBlank()) {
            return null
        }

        if (isPrivateOrLocalIp(targetIp)) {
            return "Local Network"
        }

        val now = clock.now().toEpochMilliseconds()
        val existingEntry = cache[targetIp]
        if (existingEntry != null && (now - existingEntry.timestampMillis) < cacheTtlMillis) {
            return existingEntry.location
        }

        return mutex.withLock {
            val entryUnderLock = cache[targetIp]
            if (entryUnderLock != null && (now - entryUnderLock.timestampMillis) < cacheTtlMillis) {
                return@withLock entryUnderLock.location
            }

            val resolvedLocation = fetchLocationFromApi(targetIp)
            cache[targetIp] = CacheEntry(
                location = resolvedLocation,
                timestampMillis = now
            )
            resolvedLocation
        }
    }

    private fun isPrivateOrLocalIp(ip: String): Boolean {
        return try {
            val inet = InetAddress.getByName(ip)
            inet.isLoopbackAddress || inet.isSiteLocalAddress || inet.isLinkLocalAddress || inet.isAnyLocalAddress
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun fetchLocationFromApi(ip: String): String? {
        return try {
            val responseText = httpClient.get("https://ipwho.is/$ip").bodyAsText()
            val jsonObj = json.parseToJsonElement(responseText).jsonObject
            val success = jsonObj["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!success) {
                return null
            }
            val city = jsonObj["city"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val country = jsonObj["country"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val region = jsonObj["region"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

            when {
                city != null && country != null -> "$city, $country"
                region != null && country != null -> "$region, $country"
                country != null -> country
                else -> null
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to resolve IP location for $ip", e)
            null
        }
    }

    companion object {
        private const val TAG = "IpWhoIsLocationLookup"
    }
}
