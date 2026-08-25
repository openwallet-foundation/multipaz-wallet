package org.multipaz.wallet.android.ui.verification

import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Simple
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodNfcV2
import org.multipaz.mdoc.nfc.ScanMdocReaderResult
import org.multipaz.mdoc.transport.MdocTransportClosedException
import org.multipaz.mdoc.transport.MdocTransportException
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.nfc.NfcTagLostException
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

private fun Throwable.isTagLostOrTransportClosed(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is NfcTagLostException ||
            current is MdocTransportClosedException ||
            current is MdocTransportException
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

suspend fun handleNfcHandover(
    scanResult: ScanMdocReaderResult,
    proximityReaderModel: ProximityReaderModel,
): Boolean {
    if (proximityReaderModel.state.value != ProximityReaderModel.State.IDLE &&
        proximityReaderModel.state.value != ProximityReaderModel.State.COMPLETED
    ) {
        Logger.i(TAG, "Ignoring NFC handover, state is already ${proximityReaderModel.state.value}")
        return false
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

        val isNfcOnly = scanResult.transport.connectionMethod is MdocConnectionMethodNfcV2
        if (isNfcOnly) {
            proximityReaderModel.state.first { it == ProximityReaderModel.State.COMPLETED || it == ProximityReaderModel.State.IDLE }
            if (proximityReaderModel.state.value == ProximityReaderModel.State.COMPLETED) {
                val err = proximityReaderModel.error
                if (err != null) {
                    if (err.isTagLostOrTransportClosed()) {
                        Logger.i(TAG, "Tag lost during NFC-only transfer, resetting model for re-tap", err)
                        proximityReaderModel.reset()
                        throw err
                    } else {
                        throw err
                    }
                }
            }
        }
        return true
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Error handling NFC handover endpoint setup", e)
        proximityReaderModel.reset()
        throw e
    }
}

suspend fun handleQrCodeScanned(
    mdocUrl: String,
    proximityReaderModel: ProximityReaderModel,
) {
    check(mdocUrl.startsWith("mdoc:"))
    if (proximityReaderModel.state.value != ProximityReaderModel.State.IDLE &&
        proximityReaderModel.state.value != ProximityReaderModel.State.COMPLETED
    ) {
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
