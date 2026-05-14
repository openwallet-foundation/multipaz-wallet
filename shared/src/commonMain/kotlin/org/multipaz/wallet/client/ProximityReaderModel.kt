package org.multipaz.wallet.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborArray
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.engagement.DeviceEngagement
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.mdoc.response.DeviceResponse
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG = "ProximityReaderModel"

enum class EngagementType(val description: String) {
    QR("QR code"),
    NFC_STATIC_HANDOVER("NFC Static Handover"),
    NFC_NEGOTIATED_HANDOVER("NFC Negotiated Handover"),
}

data class ProximityReaderModelResult(
    val status: Long?,
    val readerQuery: ReaderQuery?,
    val deviceRequest: DeviceRequest?,
    val deviceResponse: DeviceResponse?,
    val sessionTranscript: DataItem,
    val eReaderKey: EcPrivateKey,

    val engagementType: EngagementType,
    val durationEngagementReceivedToRequestSent: Duration,
    val durationRequestSentToResponseReceived: Duration,
    val durationScanningTime: Duration?,
    val connectionMethod: MdocConnectionMethod
)

class ProximityReaderModel {
    enum class State {
        IDLE,
        WAITING_FOR_DEVICE_REQUEST,
        WAITING_FOR_START,
        CONNECTING,
        COMPLETED,
    }

    private val _state = MutableStateFlow<State>(State.IDLE)

    /**
     * The current state.
     */
    val state = _state.asStateFlow()

    private var _scope: CoroutineScope? = null

    /**
     * A [CoroutineScope] for the verification process.
     *
     * Any coroutine launched in this scope will be automatically canceled when verification completes.
     *
     * This should only be read in states which aren't [State.IDLE] and [State.COMPLETED]. It will throw
     * [IllegalStateException] if this is not the case.
     */
    val scope: CoroutineScope
        get() {
            check(_scope != null)
            check(_state.value != State.IDLE && _state.value != State.COMPLETED)
            return _scope!!
        }

    private var _error: Throwable? = null

    /**
     * If reading the credentials fails, this will be set with a [Throwable] with more information about the failure.
     */
    val error: Throwable?
        get() = _error

    private var _result: ProximityReaderModelResult? = null
    val result: ProximityReaderModelResult?
        get() = _result

    private var _sessionTranscript: DataItem? = null
    val sessionTranscript: DataItem
        get() {
            check(_state.value != State.IDLE && _state.value != State.COMPLETED)
            return _sessionTranscript!!
        }

    private var deviceEngagement: DeviceEngagement? = null
    private var _eReaderKey: EcPrivateKey? = null
    val eReaderKey: EcPrivateKey
        get() {
            check(_state.value != State.IDLE && _state.value != State.COMPLETED)
            return _eReaderKey!!
        }

    fun reset() {
        _scope?.cancel(CancellationException("ReaderModel reset"))
        _scope = null
        _result = null
        _error = null
        _sessionTranscript = null
        deviceEngagement = null
        _eReaderKey = null
        mdocTransportOptions = null
        readerQuery = null
        deviceRequest = null
        _deviceEngagement = null
        handover = null
        existingTransport = null
        _state.value = State.IDLE
    }

    private var mdocTransportOptions: MdocTransportOptions? = null
    private var readerQuery: ReaderQuery? = null
    private var deviceRequest: DeviceRequest? = null
    private var _deviceEngagement: DataItem? = null
    private var handover: DataItem? = null
    private var existingTransport: MdocTransport? = null

    fun setMdocTransportOptions(options: MdocTransportOptions) {
        mdocTransportOptions = options
    }

    suspend fun setConnectionEndpoint(
        deviceEngagement: DataItem,
        handover: DataItem,
        existingTransport: MdocTransport? = null
    ) {
        check(_state.value == State.IDLE)
        this._deviceEngagement = deviceEngagement
        this.handover = handover
        this.existingTransport = existingTransport
        _state.value = State.WAITING_FOR_DEVICE_REQUEST

        this.deviceEngagement = DeviceEngagement.fromDataItem(
            this._deviceEngagement!!
        )
        _eReaderKey = Crypto.createEcPrivateKey(this.deviceEngagement!!.eDeviceKey.curve)
        val encodedEReaderKey = Cbor.encode(_eReaderKey!!.publicKey.toCoseKey().toDataItem())
        _sessionTranscript = buildCborArray {
            add(Tagged(24, Bstr(Cbor.encode(deviceEngagement))))
            add(Tagged(24, Bstr(encodedEReaderKey)))
            add(handover)
        }
        Logger.iCbor(TAG, "sessionTranscript", _sessionTranscript!!)
    }

    fun setDeviceRequest(
        readerQuery: ReaderQuery,
        deviceRequest: DeviceRequest
    ) {
        check(_state.value == State.WAITING_FOR_DEVICE_REQUEST)
        _state.value = State.WAITING_FOR_START
        this.readerQuery = readerQuery
        this.deviceRequest = deviceRequest
    }

    /**
     * Sets the model to [State.CONNECTING].
     */
    fun start(
        scope: CoroutineScope,
    ) {
        check(_state.value == State.WAITING_FOR_START)
        _scope = scope
        _state.value = State.CONNECTING
        println("Starting...")
        _scope!!.launch {
            val (result, error) = try {
                Pair(
                    doReaderFlow(
                        deviceEngagement!!,
                        handover!!,
                        existingTransport
                    ),
                    null
                )
            } catch (e: Throwable) {
                Logger.w(TAG, "Error doing reader flow", e)
                Pair(null, e)
            }
            println("Setting state to COMPLETED")
            _result = result
            _error = error
            deviceEngagement = null
            _eReaderKey = null
            readerQuery = null
            deviceRequest = null
            _deviceEngagement = null
            handover = null
            existingTransport = null
            _state.value = State.COMPLETED

            // TODO: Hack to ensure that [state] collectors gets called for State.COMPLETED
            _scope?.launch {
                delay(1.seconds)
                _scope?.cancel(CancellationException("ProximityReaderModel completed"))
                _scope = null
            }
        }
    }

    // Returns the message/status on success, throws otherwise
    private suspend fun doReaderFlow(
        deviceEngagement: DeviceEngagement,
        handover: DataItem,
        existingTransport: MdocTransport?
    ): ProximityReaderModelResult {
        println("In doReaderFlow()")

        val timeOfEngagementReceived = Clock.System.now()

        val transport = if (existingTransport != null) {
            existingTransport
        } else {
            val connectionMethods = MdocConnectionMethod.disambiguate(
                deviceEngagement.connectionMethods,
                MdocRole.MDOC_READER
            )
            val connectionMethod = if (connectionMethods.size == 1) {
                connectionMethods[0]
            } else {
                // TODO: maybe selectConnectionMethod(connectionMethods)
                connectionMethods[0]
            }
            val transport = MdocTransportFactory.Default.createTransport(
                connectionMethod = connectionMethod,
                role = MdocRole.MDOC_READER,
                options = mdocTransportOptions ?: MdocTransportOptions()
            )
            // TODO: maybe if (transport is NfcTransportMdocReader) {
            transport
        }

        Logger.iCbor(TAG, "handover", Cbor.encode(handover))
        val engagementType = if (handover == Simple.NULL) {
            EngagementType.QR
        } else {
            if (handover.asArray[1] == Simple.NULL) {
                EngagementType.NFC_STATIC_HANDOVER
            } else {
                EngagementType.NFC_NEGOTIATED_HANDOVER
            }
        }

        val sessionEncryption = SessionEncryption(
            MdocRole.MDOC_READER,
            eReaderKey,
            deviceEngagement.eDeviceKey,
            Cbor.encode(sessionTranscript),
        )

        println("OK, with transport: $transport")
        val connectionMethod = transport.connectionMethod
        try {
            transport.open(deviceEngagement.eDeviceKey)
            transport.sendMessage(
                sessionEncryption.encryptMessage(
                    messagePlaintext = Cbor.encode(deviceRequest!!.toDataItem()),
                    statusCode = null
                )
            )
            val timeOfFirstMessageSent = Clock.System.now()

            val sessionData = transport.waitForMessage()
            val timeOfFirstResponseReceived = Clock.System.now()
            if (sessionData.isEmpty()) {
                // TODO: showToast("Received transport-specific session termination message from holder")
                transport.close()
                return ProximityReaderModelResult(
                    status = null,
                    readerQuery = readerQuery,
                    deviceRequest = deviceRequest,
                    deviceResponse = null,
                    sessionTranscript = sessionTranscript,
                    eReaderKey = eReaderKey,
                    engagementType = engagementType,
                    durationEngagementReceivedToRequestSent = timeOfFirstMessageSent - timeOfEngagementReceived,
                    durationRequestSentToResponseReceived = timeOfFirstResponseReceived - timeOfFirstMessageSent,
                    durationScanningTime = transport.scanningTime,
                    connectionMethod = connectionMethod
                )
            }

            val (message, status) = sessionEncryption.decryptMessage(sessionData)
            Logger.i(TAG, "Holder sent ${message?.size} bytes status $status")
            if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                //showToast("Received session termination message from holder")
                Logger.i(
                    TAG, "Holder indicated they closed the connection. " +
                            "Closing and ending reader loop"
                )
                transport.close()
            } else {
                Logger.i(
                    TAG, "Holder did not indicate they are closing the connection. " +
                            "Auto-close is enabled, so sending termination message, closing, and " +
                            "ending reader loop"
                )
                transport.sendMessage(SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION))
                transport.close()
            }
            return ProximityReaderModelResult(
                status = status,
                readerQuery = readerQuery,
                deviceRequest = deviceRequest,
                deviceResponse = message?.let { DeviceResponse.fromDataItem(Cbor.decode(it)) },
                sessionTranscript = sessionTranscript,
                eReaderKey = eReaderKey,
                engagementType = engagementType,
                durationEngagementReceivedToRequestSent = timeOfFirstMessageSent - timeOfEngagementReceived,
                durationRequestSentToResponseReceived = timeOfFirstResponseReceived - timeOfFirstMessageSent,
                durationScanningTime = transport.scanningTime,
                connectionMethod = connectionMethod
            )
        } finally {
            /*
            if (updateNfcDialogMessage != null) {
                updateNfcDialogMessage("Transfer complete")
            }
             */
            transport.close()
        }
    }
}
