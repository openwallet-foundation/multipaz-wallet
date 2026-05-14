package org.multipaz.wallet.client.verification

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborArray
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.engagement.DeviceEngagement
import org.multipaz.mdoc.nfc.MdocHandoverType
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.mdoc.response.DeviceResponse
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.NfcHybridTransportMdocReader
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import kotlin.time.Clock
import kotlin.time.Duration

private const val TAG = "ProximityReaderModel"

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
    private var sessionJob: Job? = null

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
        sessionJob?.cancel(CancellationException("ReaderModel reset"))
        sessionJob = null
        _scope = null
        _result = null
        _error = null
        _sessionTranscript = null
        deviceEngagement = null
        _eReaderKey = null
        mdocTransportOptions = null
        query = null
        deviceRequest = null
        _deviceEngagement = null
        handover = null
        existingTransport = null
        _state.value = State.IDLE
    }

    private var mdocTransportOptions: MdocTransportOptions? = null
    private var query: Query? = null
    private var deviceRequest: DeviceRequest? = null
    private var _deviceEngagement: DataItem? = null
    private var handover: DataItem? = null
    private var existingTransport: MdocTransport? = null
    private var nfcHandoverType: MdocHandoverType? = null
    private var durationNfcTapToEngagement: Duration? = null

    fun setMdocTransportOptions(options: MdocTransportOptions) {
        mdocTransportOptions = options
    }

    suspend fun setConnectionEndpoint(
        deviceEngagement: DataItem,
        handover: DataItem,
        existingTransport: MdocTransport? = null,
        nfcHandoverType: MdocHandoverType? = null,
        durationNfcTapToEngagement: Duration? = null,
    ) {
        check(_state.value == State.IDLE)
        this._deviceEngagement = deviceEngagement
        this.handover = handover
        this.existingTransport = existingTransport
        this.nfcHandoverType = nfcHandoverType
        this.durationNfcTapToEngagement = durationNfcTapToEngagement
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
        query: Query,
        deviceRequest: DeviceRequest
    ) {
        check(_state.value == State.WAITING_FOR_DEVICE_REQUEST)
        _state.value = State.WAITING_FOR_START
        this.query = query
        this.deviceRequest = deviceRequest
    }

    /**
     * Sets the model to [State.CONNECTING].
     */
    fun start(
        scope: CoroutineScope,
    ) {
        check(_state.value == State.WAITING_FOR_START)
        _state.value = State.CONNECTING
        Logger.i(TAG, "Starting...")

        val engagement = deviceEngagement!!
        val hand = handover!!
        val transportParam = existingTransport

        val job = scope.launch {
            val currentJob = coroutineContext[Job]
            try {
                _result = doReaderFlow(
                    engagement,
                    hand,
                    transportParam
                )
                _error = null
            } catch (e: CancellationException) {
                Logger.i(TAG, "Reader flow cancelled")
                throw e
            } catch (e: Throwable) {
                Logger.w(TAG, "Error doing reader flow", e)
                _result = null
                _error = e
            } finally {
                if (this@ProximityReaderModel.sessionJob === currentJob) {
                    if (_state.value != State.IDLE) {
                        Logger.i(TAG, "Setting state to COMPLETED")
                        deviceEngagement = null
                        _eReaderKey = null
                        query = null
                        deviceRequest = null
                        _deviceEngagement = null
                        handover = null
                        existingTransport = null
                        nfcHandoverType = null
                        durationNfcTapToEngagement = null
                        _sessionTranscript = null
                        _state.value = State.COMPLETED
                    }
                    _scope = null
                    this@ProximityReaderModel.sessionJob = null
                }
            }
        }
        this.sessionJob = job
        this._scope = CoroutineScope(scope.coroutineContext + job)
    }

    // Returns the message/status on success, throws otherwise
    private suspend fun doReaderFlow(
        deviceEngagement: DeviceEngagement,
        handover: DataItem,
        existingTransport: MdocTransport?
    ): ProximityReaderModelResult {
        Logger.i(TAG, "In doReaderFlow()")

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

        val sessionEncryption = SessionEncryption(
            role = MdocRole.MDOC_READER,
            eSelfKey = eReaderKey,
            remotePublicKey = deviceEngagement.eDeviceKey,
            encodedSessionTranscript = Cbor.encode(sessionTranscript),
            insertSequenceNumbers = nfcHandoverType == MdocHandoverType.V2_HANDOVER
        )

        Logger.i(TAG, "OK, with transport: $transport")
        val connectionMethod = transport.connectionMethod
        try {
            transport.open(deviceEngagement.eDeviceKey)
            Logger.iCbor(TAG, "DeviceRequest", deviceRequest!!.toDataItem())
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
                return ProximityReaderModelResult(
                    status = null,
                    query = query,
                    deviceRequest = deviceRequest,
                    deviceResponse = null,
                    sessionTranscript = sessionTranscript,
                    eReaderKey = eReaderKey,
                    nfcHandoverType = nfcHandoverType,
                    durationNfcTapToEngagement = durationNfcTapToEngagement,
                    durationEngagementReceivedToRequestSent = timeOfFirstMessageSent - timeOfEngagementReceived,
                    durationRequestSentToResponseReceived = timeOfFirstResponseReceived - timeOfFirstMessageSent,
                    durationScanningTime = transport.scanningTime,
                    connectionMethod = connectionMethod,
                    nfcHybridTransportStats = if (transport is NfcHybridTransportMdocReader) { transport.stats } else { null }
                )
            }

            val (message, status) = sessionEncryption.decryptMessage(sessionData)
            Logger.i(TAG, "Holder sent ${message?.size} bytes status $status")
            if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                //showToast("Received session termination message from holder")
                Logger.i(TAG, "Holder indicated they closed the connection. " +
                            "Closing and ending reader loop")
            } else {
                Logger.i(TAG, "Holder did not indicate they are closing the connection. " +
                            "Auto-close is enabled, so sending termination message, closing, and " +
                            "ending reader loop")
                transport.sendMessage(SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION))
            }
            return ProximityReaderModelResult(
                status = status,
                query = query,
                deviceRequest = deviceRequest,
                deviceResponse = message?.let { DeviceResponse.fromDataItem(Cbor.decode(it)) },
                sessionTranscript = sessionTranscript,
                eReaderKey = eReaderKey,
                nfcHandoverType = nfcHandoverType,
                durationNfcTapToEngagement = durationNfcTapToEngagement,
                durationEngagementReceivedToRequestSent = timeOfFirstMessageSent - timeOfEngagementReceived,
                durationRequestSentToResponseReceived = timeOfFirstResponseReceived - timeOfFirstMessageSent,
                durationScanningTime = transport.scanningTime,
                connectionMethod = connectionMethod,
                nfcHybridTransportStats = if (transport is NfcHybridTransportMdocReader) { transport.stats } else { null }
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
