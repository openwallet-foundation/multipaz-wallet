package org.multipaz.wallet.backend

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class IpWhoIsLocationLookupTest {

    private class TestClock(var currentInstant: Instant) : Clock {
        override fun now(): Instant = currentInstant
    }

    @Test
    fun testPublicIpResolutionSuccess() = runTest {
        val jsonResponse = """
            {
                "ip": "8.8.8.8",
                "success": true,
                "city": "Mountain View",
                "country": "United States",
                "region": "California"
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            assertEquals("https://ipwho.is/8.8.8.8", request.url.toString())
            respond(
                content = jsonResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val lookup = IpWhoIsLocationLookup(httpClientEngine = mockEngine)
        val location = lookup.lookup("8.8.8.8")
        assertEquals("Mountain View, United States", location)
    }

    @Test
    fun testReservedOrFailedIpResolution() = runTest {
        val jsonResponse = """
            {
                "ip": "192.0.2.1",
                "success": false,
                "message": "reserved range"
            }
        """.trimIndent()

        val mockEngine = MockEngine { respond(content = jsonResponse, status = HttpStatusCode.OK) }
        val lookup = IpWhoIsLocationLookup(httpClientEngine = mockEngine)
        assertNull(lookup.lookup("192.0.2.1"))
    }

    @Test
    fun testPrivateAndLocalIps() = runTest {
        val requestCount = AtomicInteger(0)
        val mockEngine = MockEngine {
            requestCount.incrementAndGet()
            respond(content = "{}", status = HttpStatusCode.OK)
        }
        val lookup = IpWhoIsLocationLookup(httpClientEngine = mockEngine)

        assertEquals("Local Network", lookup.lookup("127.0.0.1"))
        assertEquals("Local Network", lookup.lookup("192.168.1.100"))
        assertEquals("Local Network", lookup.lookup("10.0.0.1"))
        assertEquals("Local Network", lookup.lookup("::1"))
        assertEquals(0, requestCount.get())
    }

    @Test
    fun testNullOrBlankIp() = runTest {
        val requestCount = AtomicInteger(0)
        val mockEngine = MockEngine {
            requestCount.incrementAndGet()
            respond(content = "{}", status = HttpStatusCode.OK)
        }
        val lookup = IpWhoIsLocationLookup(httpClientEngine = mockEngine)

        assertNull(lookup.lookup(null))
        assertNull(lookup.lookup(""))
        assertNull(lookup.lookup("   "))
        assertEquals(0, requestCount.get())
    }

    @Test
    fun testInMemoryCacheAndTtlExpiration() = runTest {
        val requestCount = AtomicInteger(0)
        val jsonResponse = """
            {
                "ip": "1.1.1.1",
                "success": true,
                "city": "Sydney",
                "country": "Australia"
            }
        """.trimIndent()

        val mockEngine = MockEngine {
            requestCount.incrementAndGet()
            respond(
                content = jsonResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val testClock = TestClock(Instant.fromEpochMilliseconds(1_000_000L))
        val lookup = IpWhoIsLocationLookup(
            httpClientEngine = mockEngine,
            clock = testClock,
            cacheTtlMillis = 1.hours.inWholeMilliseconds
        )

        // First call triggers HTTP request
        val loc1 = lookup.lookup("1.1.1.1")
        assertEquals("Sydney, Australia", loc1)
        assertEquals(1, requestCount.get())

        // Second call within 1 hour hits cache (no new HTTP request)
        testClock.currentInstant = testClock.currentInstant.plus(30.minutes)
        val loc2 = lookup.lookup("1.1.1.1")
        assertEquals("Sydney, Australia", loc2)
        assertEquals(1, requestCount.get())

        // Call after 1 hour expires cache and triggers new HTTP request
        testClock.currentInstant = testClock.currentInstant.plus(40.minutes)
        val loc3 = lookup.lookup("1.1.1.1")
        assertEquals("Sydney, Australia", loc3)
        assertEquals(2, requestCount.get())
    }
}
