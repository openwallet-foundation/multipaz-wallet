package org.multipaz.wallet.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.multipaz.cbor.Simple
import org.multipaz.eventlogger.Event
import org.multipaz.eventlogger.fromCbor
import org.multipaz.eventlogger.toCbor
import org.multipaz.eventlogger.EventVerificationDefault
import org.multipaz.eventlogger.EventVerificationDigitalCredentials
import org.multipaz.eventlogger.EventVerificationIso18013Proximity
import org.multipaz.mdoc.engagement.EngagementType
import org.multipaz.mdoc.transport.NfcHybridTransportStats
import org.multipaz.verification.Iso18013PresentmentRecord
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class EventExtTest {

    private fun createPresentmentRecord(origin: String? = null): Iso18013PresentmentRecord {
        return Iso18013PresentmentRecord(
            response = Simple.NULL,
            sessionTranscript = Simple.NULL,
            request = Simple.NULL,
            eDeviceKey = null,
            encryptionInfo = null,
            origin = origin
        )
    }

    @Test
    fun testEventSerializationRoundtrip() {
        val event = EventVerificationIso18013Proximity(
            identifier = "evt-test",
            timestamp = Instant.fromEpochMilliseconds(1000),
            presentmentRecord = createPresentmentRecord(),
            engagementType = EngagementType.QR_CODE,
            durationNfcTapToEngagement = null,
            durationEngagementReceivedToRequestSent = 100.milliseconds,
            durationRequestSentToResponseReceived = 200.milliseconds,
            durationScanningTime = 50.milliseconds,
            nfcHybridTransportStats = null
        )
        val cbor = event.toCbor()
        val decoded = Event.fromCbor(cbor) as EventVerificationIso18013Proximity
        assertEquals(event.identifier, decoded.identifier)
        assertEquals(event.engagementType, decoded.engagementType)
        assertEquals(event.durationRequestSentToResponseReceived, decoded.durationRequestSentToResponseReceived)
    }

    @Test
    fun testEventSerializationRoundtrip_digitalCredentials() {
        val event = EventVerificationDigitalCredentials(
            identifier = "evt-dc",
            timestamp = Instant.fromEpochMilliseconds(2000),
            presentmentRecord = createPresentmentRecord(origin = "https://example.com"),
            requestJson = """{"requests": []}""",
            responseJson = """{"response": "ok"}""",
            durationRequestSentToResponseReceived = 350.milliseconds,
            origin = "https://example.com",
            appId = "com.example.app"
        )
        val cbor = event.toCbor()
        val decoded = Event.fromCbor(cbor) as EventVerificationDigitalCredentials
        assertEquals(event.identifier, decoded.identifier)
        assertEquals(event.origin, decoded.origin)
        assertEquals(event.appId, decoded.appId)
        assertEquals(event.requestJson, decoded.requestJson)
        assertEquals(event.responseJson, decoded.responseJson)
        assertEquals(event.durationRequestSentToResponseReceived, decoded.durationRequestSentToResponseReceived)
    }

    @Test
    fun testIsProximityPresentment_proximityEvent() {
        val event = EventVerificationIso18013Proximity(
            identifier = "evt-1",
            timestamp = Instant.fromEpochMilliseconds(1000),
            presentmentRecord = createPresentmentRecord(),
            engagementType = EngagementType.QR_CODE,
            durationNfcTapToEngagement = null,
            durationEngagementReceivedToRequestSent = 100.milliseconds,
            durationRequestSentToResponseReceived = 200.milliseconds,
            durationScanningTime = 50.milliseconds,
            nfcHybridTransportStats = null
        )
        assertTrue(event.isProximityPresentment())
        assertEquals(EngagementType.QR_CODE, event.engagementType)
        assertEquals(100.milliseconds, event.durationEngagementReceivedToRequestSent)
        assertEquals(200.milliseconds, event.durationRequestSentToResponseReceived)
    }

    @Test
    fun testIsProximityPresentment_digitalCredentialsEvent() {
        val event = EventVerificationDigitalCredentials(
            identifier = "evt-2",
            timestamp = Instant.fromEpochMilliseconds(2000),
            presentmentRecord = createPresentmentRecord(origin = "https://example.com"),
            requestJson = """{"requests": []}""",
            responseJson = """{"response": "ok"}""",
            durationRequestSentToResponseReceived = 350.milliseconds,
            origin = "https://example.com",
            appId = null
        )
        assertFalse(event.isProximityPresentment())
        assertEquals("https://example.com", event.origin)
        assertEquals(350.milliseconds, event.durationRequestSentToResponseReceived)
    }

    @Test
    fun testIsProximityPresentment_defaultFallback() {
        val proximityFallback = EventVerificationDefault(
            identifier = "evt-3",
            timestamp = Instant.fromEpochMilliseconds(3000),
            presentmentRecord = createPresentmentRecord(origin = null),
            durationRequestSentToResponseReceived = null
        )
        assertTrue(proximityFallback.isProximityPresentment())

        val linkFallback = EventVerificationDefault(
            identifier = "evt-4",
            timestamp = Instant.fromEpochMilliseconds(4000),
            presentmentRecord = createPresentmentRecord(origin = "https://example.com"),
            durationRequestSentToResponseReceived = null
        )
        assertFalse(linkFallback.isProximityPresentment())
    }

    @Test
    fun testIso18013Proximity_withNfcHybridStats() {
        val stats = NfcHybridTransportStats(
            numSent = 5,
            numSentViaNfc = 4,
            numSentViaTransport = 1,
            numReceived = 6,
            numReceivedFirstOnNfc = 5,
            numReceivedFirstOnTransport = 1,
            nfcDisconnectedDuringTransaction = false
        )
        val event = EventVerificationIso18013Proximity(
            identifier = "evt-5",
            timestamp = Instant.fromEpochMilliseconds(5000),
            presentmentRecord = createPresentmentRecord(),
            engagementType = EngagementType.NFC_CONCURRENT_CHANNEL_ENGAGEMENT,
            durationNfcTapToEngagement = 120.milliseconds,
            durationEngagementReceivedToRequestSent = 40.milliseconds,
            durationRequestSentToResponseReceived = 250.milliseconds,
            durationScanningTime = 80.milliseconds,
            nfcHybridTransportStats = stats
        )
        assertTrue(event.isProximityPresentment())
        assertEquals(EngagementType.NFC_CONCURRENT_CHANNEL_ENGAGEMENT, event.engagementType)
        assertEquals(120.milliseconds, event.durationNfcTapToEngagement)
        assertEquals(5, event.nfcHybridTransportStats?.numSent)
        assertEquals(6, event.nfcHybridTransportStats?.numReceived)
    }
}
