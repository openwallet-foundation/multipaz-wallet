import Foundation
import Multipaz
import Observation

@MainActor
@Observable
class ProximityPresentmentModel {
    enum State: Equatable {
        case idle
        case generatingQrCode
        case readyToShowQrCode(uri: String)
        case connecting
        case waitingForRequest
        case waitingForUserInput
        case sendingResponse
        case completed(errorMessage: String?, isCannotSatisfy: Bool)
    }

    var state: State = .idle
    var qrCodeToShow: String? = nil
    var transactionError: Error? = nil
    var selectedDocuments: [Document] = []

    private var transactionTask: Task<Void, Never>? = nil

    func start(
        document: Document?,
        source: PresentmentSource,
        onConnected: @escaping @MainActor () -> Void
    ) {
        cancel()
        state = .generatingQrCode
        qrCodeToShow = nil
        transactionError = nil
        selectedDocuments = document != nil ? [document!] : []

        let eDeviceKeyCurve = EcCurve.p256
        let transportFactory = MdocTransportFactoryDefault()

        let bleUuid = UUID.companion.randomUUID(random: KotlinRandom.companion)
        let connectionMethods = [
            MdocConnectionMethodBle(
                supportsPeripheralServerMode: true,
                supportsCentralClientMode: false,
                peripheralServerModeUuid: bleUuid,
                centralClientModeUuid: nil,
                peripheralServerModePsm: nil,
                peripheralServerModeMacAddress: nil
            )
        ]
        let transportOptions = MdocTransportOptions(
            bleUseL2CAP: false,
            bleUseL2CAPInEngagement: true
        )

        transactionTask = Task {
            do {
                let eDeviceKey = try await Crypto.shared.createEcPrivateKey(curve: eDeviceKeyCurve)

                let advertisedTransports = try await ConnectionHelperKt.advertise(
                    connectionMethods,
                    role: MdocRole.mdoc,
                    transportFactory: transportFactory,
                    options: transportOptions
                )

                let deviceEngagement = buildDeviceEngagement(
                    eDeviceKey: eDeviceKey.publicKey,
                    version: nil
                ) { builder in
                    advertisedTransports.forEach {
                        builder.addConnectionMethod(connectionMethod: $0.connectionMethod)
                    }
                    builder.addCapability(capability: Capability.readerAuthAllSupport, value: true.toDataItem())
                    builder.addCapability(capability: Capability.extendedRequestSupport, value: true.toDataItem())
                }

                let encodedDeviceEngagement = try Cbor.shared.encode(item: deviceEngagement.toDataItem())
                let uri = "mdoc:" + encodedDeviceEngagement.toBase64Url()

                self.qrCodeToShow = uri
                self.state = .readyToShowQrCode(uri: uri)

                let transport = try await ConnectionHelperKt.waitForConnection(
                    advertisedTransports,
                    eSenderKey: eDeviceKey.publicKey
                )

                self.state = .waitingForRequest
                onConnected()

                try await Iso18013Presentment(
                    transport: transport,
                    eDeviceKey: eDeviceKey,
                    deviceEngagement: deviceEngagement.toDataItem(),
                    handover: Simple.companion.NULL,
                    source: source,
                    keyAgreementPossible: [eDeviceKeyCurve],
                    preselectedDocuments: [],
                    insertSequenceNumbers: false,
                    timeout: KotlinDurationCompanion.shared.fromSeconds(seconds: 15),
                    timeoutSubsequentRequests: KotlinDurationCompanion.shared.fromSeconds(seconds: 30),
                    onWaitingForRequest: {
                        Task { @MainActor in
                            self.state = .waitingForRequest
                        }
                    },
                    onWaitingForUserInput: {
                        Task { @MainActor in
                            self.state = .waitingForUserInput
                        }
                    },
                    onDocumentsInFocus: { docs in
                        Task { @MainActor in
                            self.selectedDocuments = docs
                        }
                    },
                    onSendingResponse: {
                        Task { @MainActor in
                            self.state = .sendingResponse
                        }
                    }
                )

                if !Task.isCancelled {
                    self.state = .completed(errorMessage: nil, isCannotSatisfy: false)
                }
            } catch {
                if !Task.isCancelled {
                    self.transactionError = error
                    let cannotSatisfy = self.isCannotSatisfyRequest(error)
                    self.state = .completed(
                        errorMessage: error.localizedDescription,
                        isCannotSatisfy: cannotSatisfy
                    )
                }
            }

            if !Task.isCancelled {
                self.transactionTask = nil
            }
        }
    }

    func cancel() {
        transactionTask?.cancel()
        transactionTask = nil
        qrCodeToShow = nil
        transactionError = nil
        selectedDocuments = []
        state = .idle
    }

    func reset() {
        cancel()
    }

    private func isCannotSatisfyRequest(_ error: Error) -> Bool {
        #if DEBUG
        let nsError = error as NSError
        print("ProximityPresentment error: \(error), nsError: \(nsError), userInfo: \(nsError.userInfo)")
        #endif
        if error is PresentmentCannotSatisfyRequestException {
            return true
        }
        let nsErrorObj = error as NSError
        if let kotlinException = nsErrorObj.userInfo["KotlinException"] {
            if kotlinException is PresentmentCannotSatisfyRequestException {
                return true
            }
            let typeName = String(describing: type(of: kotlinException))
            if typeName.contains("PresentmentCannotSatisfyRequestException") {
                return true
            }
        }
        let errorDescription = String(describing: error)
        if errorDescription.contains("PresentmentCannotSatisfyRequestException") {
            return true
        }
        return false
    }
}
