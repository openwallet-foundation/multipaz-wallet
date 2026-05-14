package org.multipaz.wallet.client.verification

import org.multipaz.cbor.DataItem
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.nfc.MdocHandoverType
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.mdoc.response.DeviceResponse
import org.multipaz.mdoc.transport.NfcHybridTransportStats
import kotlin.time.Duration

data class ProximityReaderModelResult(
    val status: Long?,
    val query: Query?,
    val deviceRequest: DeviceRequest?,
    val deviceResponse: DeviceResponse?,
    val sessionTranscript: DataItem,
    val eReaderKey: EcPrivateKey,

    val nfcHandoverType: MdocHandoverType?,
    val durationNfcTapToEngagement: Duration?,
    val durationEngagementReceivedToRequestSent: Duration,
    val durationRequestSentToResponseReceived: Duration,
    val durationScanningTime: Duration?,
    val connectionMethod: MdocConnectionMethod,
    val nfcHybridTransportStats: NfcHybridTransportStats?
)
