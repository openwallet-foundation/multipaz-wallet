package org.multipaz.wallet.client.verification

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.eventlogger.EventVerification
import org.multipaz.eventlogger.EventVerificationDigitalCredentials
import org.multipaz.eventlogger.EventVerificationIso18013Proximity
import org.multipaz.mdoc.engagement.EngagementType
import org.multipaz.mdoc.engagement.toEngagementType
import org.multipaz.mdoc.transport.NfcHybridTransportStats
import kotlin.time.Duration

/**
 * Telemetry and metrics for a verification exchange.
 */
@CborSerializable
sealed class VerificationTelemetry {
    abstract val durationRequestSentToResponseReceived: Duration?

    companion object
}

/**
 * Telemetry for an ISO/IEC 18013-5 proximity verification exchange.
 */
data class ProximityVerificationTelemetry(
    val engagementType: EngagementType,
    val durationNfcTapToEngagement: Duration? = null,
    val durationEngagementReceivedToRequestSent: Duration? = null,
    override val durationRequestSentToResponseReceived: Duration? = null,
    val durationScanningTime: Duration? = null,
    val nfcHybridTransportStats: NfcHybridTransportStats? = null,
) : VerificationTelemetry()

/**
 * Telemetry for a W3C Digital Credentials API verification exchange.
 */
data class DigitalCredentialsVerificationTelemetry(
    val requestJson: String,
    val responseJson: String,
    val origin: String? = null,
    val appId: String? = null,
    override val durationRequestSentToResponseReceived: Duration? = null,
) : VerificationTelemetry()

/**
 * Generic telemetry for other verification exchanges.
 */
data class GenericVerificationTelemetry(
    override val durationRequestSentToResponseReceived: Duration? = null,
) : VerificationTelemetry()

/**
 * Extracts [VerificationTelemetry] from a logged [EventVerification].
 */
fun EventVerification.toTelemetry(): VerificationTelemetry? {
    return when (this) {
        is EventVerificationIso18013Proximity -> ProximityVerificationTelemetry(
            engagementType = engagementType,
            durationNfcTapToEngagement = durationNfcTapToEngagement,
            durationEngagementReceivedToRequestSent = durationEngagementReceivedToRequestSent,
            durationRequestSentToResponseReceived = durationRequestSentToResponseReceived,
            durationScanningTime = durationScanningTime,
            nfcHybridTransportStats = nfcHybridTransportStats,
        )
        is EventVerificationDigitalCredentials -> DigitalCredentialsVerificationTelemetry(
            requestJson = requestJson,
            responseJson = responseJson,
            origin = origin,
            appId = appId,
            durationRequestSentToResponseReceived = durationRequestSentToResponseReceived,
        )
        else -> durationRequestSentToResponseReceived?.let {
            GenericVerificationTelemetry(durationRequestSentToResponseReceived = it)
        }
    }
}

/**
 * Extracts [ProximityVerificationTelemetry] from a live [ProximityReaderModelResult].
 */
fun ProximityReaderModelResult.toTelemetry(): ProximityVerificationTelemetry {
    return ProximityVerificationTelemetry(
        engagementType = nfcHandoverType?.toEngagementType() ?: EngagementType.QR_CODE,
        durationNfcTapToEngagement = durationNfcTapToEngagement,
        durationEngagementReceivedToRequestSent = durationEngagementReceivedToRequestSent,
        durationRequestSentToResponseReceived = durationRequestSentToResponseReceived,
        durationScanningTime = durationScanningTime,
        nfcHybridTransportStats = nfcHybridTransportStats,
    )
}

/**
 * Formats a [Duration] in human-readable units (ms, s, min, h) appropriate for display.
 */
fun Duration.formatDuration(): String {
    if (this < Duration.ZERO) {
        return "-${(-this).formatDuration()}"
    }
    val totalMs = inWholeMilliseconds
    if (totalMs < 1000) {
        return "$totalMs ms"
    }
    val totalSec = inWholeSeconds
    if (totalSec < 60) {
        val tenths = (totalMs % 1000) / 100
        return if (tenths > 0) "$totalSec.$tenths s" else "$totalSec s"
    }
    val totalMin = inWholeMinutes
    if (totalMin < 60) {
        val remSec = totalSec % 60
        return if (remSec > 0) "$totalMin min $remSec s" else "$totalMin min"
    }
    val totalHours = inWholeHours
    val remMin = totalMin % 60
    return if (remMin > 0) "$totalHours h $remMin min" else "$totalHours h"
}
