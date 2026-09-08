package org.multipaz.wallet.client.verification

import org.multipaz.cbor.Simple
import org.multipaz.eventlogger.EventVerificationDefault
import org.multipaz.eventlogger.EventVerificationDigitalCredentials
import org.multipaz.eventlogger.EventVerificationIso18013Proximity
import org.multipaz.mdoc.engagement.EngagementType
import org.multipaz.mdoc.transport.NfcHybridTransportStats
import org.multipaz.verification.Iso18013PresentmentRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class VerificationTelemetryTest {

    private fun createPresentmentRecord(): Iso18013PresentmentRecord {
        return Iso18013PresentmentRecord(
            response = Simple.NULL,
            sessionTranscript = Simple.NULL,
            request = Simple.NULL,
            eDeviceKey = null,
            encryptionInfo = null,
            origin = null
        )
    }

    @Test
    fun testProximityTelemetryFromEventAndCborRoundtrip() {
        val stats = NfcHybridTransportStats(
            numSent = 10,
            numSentViaNfc = 8,
            numSentViaTransport = 2,
            numReceived = 12,
            numReceivedFirstOnNfc = 11,
            numReceivedFirstOnTransport = 1,
            nfcDisconnectedDuringTransaction = false
        )
        val event = EventVerificationIso18013Proximity(
            identifier = "evt-prox",
            timestamp = Instant.fromEpochMilliseconds(1000),
            presentmentRecord = createPresentmentRecord(),
            engagementType = EngagementType.NFC_STATIC_HANDOVER,
            durationNfcTapToEngagement = 150.milliseconds,
            durationEngagementReceivedToRequestSent = 45.milliseconds,
            durationRequestSentToResponseReceived = 300.milliseconds,
            durationScanningTime = 80.milliseconds,
            nfcHybridTransportStats = stats
        )

        val telemetry = event.toTelemetry()
        assertNotNull(telemetry)
        assertIs<ProximityVerificationTelemetry>(telemetry)
        assertEquals(EngagementType.NFC_STATIC_HANDOVER, telemetry.engagementType)
        assertEquals(150.milliseconds, telemetry.durationNfcTapToEngagement)
        assertEquals(45.milliseconds, telemetry.durationEngagementReceivedToRequestSent)
        assertEquals(300.milliseconds, telemetry.durationRequestSentToResponseReceived)
        assertEquals(80.milliseconds, telemetry.durationScanningTime)
        assertEquals(stats, telemetry.nfcHybridTransportStats)

        val cbor = telemetry.toCbor()
        val decoded = VerificationTelemetry.fromCbor(cbor)
        assertIs<ProximityVerificationTelemetry>(decoded)
        assertEquals(telemetry, decoded)
    }

    @Test
    fun testDigitalCredentialsTelemetryFromEventAndCborRoundtrip() {
        val event = EventVerificationDigitalCredentials(
            identifier = "evt-dc",
            timestamp = Instant.fromEpochMilliseconds(2000),
            presentmentRecord = createPresentmentRecord(),
            requestJson = """{"request": 1}""",
            responseJson = """{"response": 2}""",
            durationRequestSentToResponseReceived = 420.milliseconds,
            origin = "https://example.verifier.org",
            appId = "org.example.app"
        )

        val telemetry = event.toTelemetry()
        assertNotNull(telemetry)
        assertIs<DigitalCredentialsVerificationTelemetry>(telemetry)
        assertEquals("""{"request": 1}""", telemetry.requestJson)
        assertEquals("""{"response": 2}""", telemetry.responseJson)
        assertEquals("https://example.verifier.org", telemetry.origin)
        assertEquals("org.example.app", telemetry.appId)
        assertEquals(420.milliseconds, telemetry.durationRequestSentToResponseReceived)

        val cbor = telemetry.toCbor()
        val decoded = VerificationTelemetry.fromCbor(cbor)
        assertIs<DigitalCredentialsVerificationTelemetry>(decoded)
        assertEquals(telemetry, decoded)
    }

    @Test
    fun testGenericTelemetryFromDefaultEventAndCborRoundtrip() {
        val eventWithDuration = EventVerificationDefault(
            identifier = "evt-def-1",
            timestamp = Instant.fromEpochMilliseconds(3000),
            presentmentRecord = createPresentmentRecord(),
            durationRequestSentToResponseReceived = 500.milliseconds
        )
        val telemetry = eventWithDuration.toTelemetry()
        assertNotNull(telemetry)
        assertIs<GenericVerificationTelemetry>(telemetry)
        assertEquals(500.milliseconds, telemetry.durationRequestSentToResponseReceived)

        val cbor = telemetry.toCbor()
        val decoded = VerificationTelemetry.fromCbor(cbor)
        assertIs<GenericVerificationTelemetry>(decoded)
        assertEquals(telemetry, decoded)

        val eventWithoutDuration = EventVerificationDefault(
            identifier = "evt-def-2",
            timestamp = Instant.fromEpochMilliseconds(4000),
            presentmentRecord = createPresentmentRecord(),
            durationRequestSentToResponseReceived = null
        )
        assertNull(eventWithoutDuration.toTelemetry())
    }

    @Test
    fun testFormatDuration() {
        assertEquals("0 ms", 0.milliseconds.formatDuration())
        assertEquals("350 ms", 350.milliseconds.formatDuration())
        assertEquals("999 ms", 999.milliseconds.formatDuration())
        assertEquals("1 s", 1000.milliseconds.formatDuration())
        assertEquals("1.2 s", 1200.milliseconds.formatDuration())
        assertEquals("10.5 s", 10500.milliseconds.formatDuration())
        assertEquals("45 s", 45000.milliseconds.formatDuration())
        assertEquals("1 min", 60000.milliseconds.formatDuration())
        assertEquals("1 min 15 s", 75000.milliseconds.formatDuration())
        assertEquals("10 min", 600000.milliseconds.formatDuration())
        assertEquals("10 min 23 s", 623000.milliseconds.formatDuration())
        assertEquals("1 h", 3600000.milliseconds.formatDuration())
        assertEquals("1 h 5 min", 3900000.milliseconds.formatDuration())
    }
}
