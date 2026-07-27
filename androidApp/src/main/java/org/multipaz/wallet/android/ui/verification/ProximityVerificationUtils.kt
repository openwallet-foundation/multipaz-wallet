package org.multipaz.wallet.android.ui.verification

import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Simple
import org.multipaz.mdoc.nfc.ScanMdocReaderResult
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.wallet.client.verification.ProximityReaderModel

private const val TAG = "ProximityVerificationUtils"

val nfcPollingFramesInsertionSupported by lazy {
    // Use an allow-list until b/460804407 is resolved and used in Multipaz
    if (Build.MANUFACTURER == "Google" && (
                Build.MODEL.startsWith("Pixel 8") ||
                        Build.MODEL.startsWith("Pixel 9") ||
                        Build.MODEL.startsWith("Pixel 10") ||
                        Build.MODEL.startsWith("Pixel 11")
                )
    ) {
        Logger.i(TAG, "Device is on allow-list for nfcPollingFramesInsertionSupported")
        true
    } else {
        Logger.w(TAG, "Device is not allow-list for nfcPollingFramesInsertionSupported")
        false
    }
}

suspend fun handleNfcHandover(
    scanResult: ScanMdocReaderResult,
    proximityReaderModel: ProximityReaderModel,
) {
    if (proximityReaderModel.state.value != ProximityReaderModel.State.IDLE) {
        Logger.i(TAG, "Ignoring NFC handover, state is already ${proximityReaderModel.state.value}")
        return
    }
    try {
        proximityReaderModel.reset()
        proximityReaderModel.setMdocTransportOptions(
            MdocTransportOptions(
                bleUseL2CAP = false,             // Doesn't work with Apple Wallet
                bleUseL2CAPInEngagement = true
            )
        )
        proximityReaderModel.setConnectionEndpoint(
            deviceEngagement = Cbor.decode(scanResult.encodedDeviceEngagement.toByteArray()),
            handover = scanResult.handover,
            existingTransport = scanResult.transport,
            nfcHandoverType = scanResult.type,
            durationNfcTapToEngagement = scanResult.processingDuration
        )
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Error handling NFC handover endpoint setup", e)
    }
}

suspend fun handleQrCodeScanned(
    mdocUrl: String,
    proximityReaderModel: ProximityReaderModel,
) {
    check(mdocUrl.startsWith("mdoc:"))
    if (proximityReaderModel.state.value != ProximityReaderModel.State.IDLE) {
        Logger.i(TAG, "Ignoring QR code scan, state is already ${proximityReaderModel.state.value}")
        return
    }
    try {
        val deviceEngagement = Cbor.decode(mdocUrl.substringAfter("mdoc:").fromBase64Url())
        proximityReaderModel.reset()
        proximityReaderModel.setMdocTransportOptions(
            MdocTransportOptions(
                bleUseL2CAP = true,
                bleUseL2CAPInEngagement = true
            )
        )
        proximityReaderModel.setConnectionEndpoint(
            deviceEngagement = deviceEngagement,
            handover = Simple.NULL,
            existingTransport = null,
            nfcHandoverType = null,
            durationNfcTapToEngagement = null
        )
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Error parsing QR code and setting endpoint", e)
    }
}
