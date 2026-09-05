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

/**
 * State machine and orchestrator for proximity-based ISO/IEC 18013-5 mdoc verification.
 *
 * Coordinates device engagement, reader key generation, session transcript construction,
 * transport negotiation (NFC / BLE), request transmission, and response decryption.
 */
class ProximityReaderModel {
    /**
     * States of the proximity reader state machine.
     */
    enum class State {
        /**
         * The reader model is idle and ready to accept device engagement and handover data via [setConnectionEndpoint].
         */
        IDLE,

        /**
         * Device engagement and handover have been configured; waiting for a query and device request via [setDeviceRequest].
         */
        WAITING_FOR_DEVICE_REQUEST,

        /**
         * Device request has been set; waiting for verification to begin via [start].
         */
        WAITING_FOR_START,

        /**
         * The reader is actively connecting to the holder device and exchanging messages.
         */
        CONNECTING,

        /**
         * The reader flow has finished (either successfully or with an error) and resources have been cleaned up.
         */
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

    /**
     * The result of the proximity verification exchange, or `null` if verification has not completed successfully.
     */
    val result: ProximityReaderModelResult?
        get() = _result

    private var _sessionTranscript: DataItem? = null

    /**
     * The CBOR session transcript for the active verification session.
     *
     * @throws IllegalStateException If read when in [State.IDLE] or [State.COMPLETED].
     */
    val sessionTranscript: DataItem
        get() {
            check(_state.value != State.IDLE && _state.value != State.COMPLETED)
            return _sessionTranscript!!
        }

    private var deviceEngagement: DeviceEngagement? = null
    private var _eReaderKey: EcPrivateKey? = null

    /**
     * The reader's ephemeral private key used for session encryption.
     *
     * @throws IllegalStateException If read when in [State.IDLE] or [State.COMPLETED].
     */
    val eReaderKey: EcPrivateKey
        get() {
            check(_state.value != State.IDLE && _state.value != State.COMPLETED)
            return _eReaderKey!!
        }

    /**
     * Resets the reader model back to [State.IDLE], canceling any running reader session and clearing all state.
     */
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

    /**
     * Configures transport options (e.g. BLE transport parameters) to use when creating the mdoc reader transport.
     *
     * @param options The [MdocTransportOptions] to configure.
     */
    fun setMdocTransportOptions(options: MdocTransportOptions) {
        mdocTransportOptions = options
    }

    /**
     * Configures the connection endpoint following device engagement and handover.
     *
     * Transitions the state from [State.IDLE] to [State.WAITING_FOR_DEVICE_REQUEST].
     *
     * @param deviceEngagement CBOR [DataItem] containing the holder's device engagement.
     * @param handover CBOR [DataItem] containing the handover structure (e.g. NFC handover or QR engagement handover).
     * @param existingTransport Optional pre-established [MdocTransport], if available.
     * @param nfcHandoverType Optional NFC handover type if NFC engagement was used.
     * @param durationNfcTapToEngagement Optional measured duration from NFC tap to engagement.
     * @throws IllegalStateException If the current state is not [State.IDLE].
     */
    @Throws(IllegalStateException::class)
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
        Logger.dCbor(TAG, "sessionTranscript", _sessionTranscript!!)
        _state.value = State.WAITING_FOR_DEVICE_REQUEST
    }

    /**
     * Sets the verification query and device request to transmit to the holder.
     *
     * Transitions the state from [State.WAITING_FOR_DEVICE_REQUEST] to [State.WAITING_FOR_START].
     *
     * @param query The [Query] being executed.
     * @param deviceRequest The ISO/IEC 18013-5 [DeviceRequest] to send.
     * @throws IllegalStateException If the current state is not [State.WAITING_FOR_DEVICE_REQUEST].
     */
    @Throws(IllegalStateException::class)
    fun setDeviceRequest(
        query: Query,
        deviceRequest: DeviceRequest
    ) {
        check(_state.value == State.WAITING_FOR_DEVICE_REQUEST)
        this.query = query
        this.deviceRequest = deviceRequest
        _state.value = State.WAITING_FOR_START
    }

    /**
     * Starts the proximity reader communication flow with the holder device.
     *
     * Transitions the state from [State.WAITING_FOR_START] to [State.CONNECTING]. The reader session runs
     * asynchronously in the provided [scope].
     *
     * @param scope The [CoroutineScope] in which to launch the reader communication flow.
     * @throws IllegalStateException If the current state is not [State.WAITING_FOR_START].
     */
    @Throws(IllegalStateException::class)
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

        Logger.dCbor(TAG, "handover", Cbor.encode(handover))

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
            Logger.dCbor(TAG, "DeviceRequest", deviceRequest!!.toDataItem())
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
