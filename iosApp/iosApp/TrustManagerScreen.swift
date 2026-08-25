import SwiftUI
import Multipaz
import UniformTypeIdentifiers

enum ActiveImportPicker: Identifiable {
    case cert
    case vical
    case rical
    
    var id: Int {
        switch self {
        case .cert: return 1
        case .vical: return 2
        case .rical: return 3
        }
    }
}

struct TrustManagerScreen: View {
    @Environment(ViewModel.self) private var viewModel
    let isVical: Bool
    
    @State private var currentImportPicker: ActiveImportPicker? = nil
    @State private var showFilePicker = false
    
    @State private var importErrorMessage: String? = nil
    @State private var showImportErrorAlert = false
    @State private var importErrorTitle: String = "Import error"
    
    @State private var pendingUnsupportedCurveImport: PendingUnsupportedCurveImport? = nil
    @State private var showUnsupportedCurveAlert = false
    
    private struct PendingUnsupportedCurveImport {
        let typeName: String
        let fileData: Data
        let curveName: String
    }
    
    private var builtInModel: TrustManagerModel {
        isVical ? viewModel.backendIssuerTrustManagerModel : viewModel.backendReaderTrustManagerModel
    }
    
    private var userModel: TrustManagerModel {
        isVical ? viewModel.userIssuerTrustManagerModel : viewModel.userReaderTrustManagerModel
    }
    
    private var screenTitle: String {
        isVical ? "Trusted issuers" : "Trusted verifiers"
    }
    
    private var explainerText: String {
        if isVical {
            return "Trusted issuers are used to ensure passes you verify are from authentic, recognized organizations."
        } else {
            return "Trusted verifiers help ensure your personal information is only shared with authorized parties whose identity has been proven."
        }
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(explainerText)
                    .font(.footnote)
                    .padding(.horizontal)
                
                FloatingItemList(title: "Built-in trust anchors") {
                    if builtInModel.isLoading && builtInModel.entries == nil {
                        FloatingItemCenteredText(text: "Loading…")
                    } else if let entries = builtInModel.entries, entries.isEmpty {
                        FloatingItemCenteredText(text: "No built-in trust anchors")
                    } else if let entries = builtInModel.entries {
                        ForEach(entries) { info in
                            FloatingItemText(
                                text: info.displayName,
                                showChevron: true,
                                secondary: info.details,
                                image: { TrustEntryIconView(entryInfo: info, size: 36) }
                            ).onTapGesture {
                                viewModel.push(.trustEntry(
                                    trustManagerId: builtInModel.trustManager.identifier,
                                    trustEntryId: info.id,
                                    justImported: false
                                ))
                            }
                        }
                    }
                }
                .padding(.horizontal)
                
                FloatingItemList(title: "Manually imported trust anchors") {
                    if userModel.isLoading && userModel.entries == nil {
                        FloatingItemCenteredText(text: "Loading…")
                    } else if let entries = userModel.entries, entries.isEmpty {
                        FloatingItemCenteredText(text: "Certificates and trust lists manually imported will appear in this list")
                    } else if let entries = userModel.entries {
                        ForEach(entries) { info in
                            FloatingItemText(
                                text: info.displayName,
                                showChevron: true,
                                secondary: info.details,
                                image: { TrustEntryIconView(entryInfo: info, size: 36) }
                            ).onTapGesture {
                                viewModel.push(.trustEntry(
                                    trustManagerId: userModel.trustManager.identifier,
                                    trustEntryId: info.id,
                                    justImported: false
                                ))
                            }
                        }
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical)
        }
        .navigationTitle(screenTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button {
                        currentImportPicker = .cert
                        showFilePicker = true
                    } label: {
                        Label("Import certificate", systemImage: "key")
                    }
                    
                    if isVical {
                        Button {
                            currentImportPicker = .vical
                            showFilePicker = true
                        } label: {
                            Label("Import VICAL", systemImage: "shield")
                        }
                    } else {
                        Button {
                            currentImportPicker = .rical
                            showFilePicker = true
                        } label: {
                            Label("Import RICAL", systemImage: "shield")
                        }
                    }
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.item, .data],
            allowsMultipleSelection: false
        ) { result in
            let picker = currentImportPicker
            currentImportPicker = nil
            guard let picker else { return }
            switch picker {
            case .cert:
                handleCertFileImport(result: result)
            case .vical:
                handleVicalFileImport(result: result)
            case .rical:
                handleRicalFileImport(result: result)
            }
        }
        .alert(importErrorTitle, isPresented: $showImportErrorAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(importErrorMessage ?? "An unknown error occurred.")
        }
        .alert(
            "Signature verification unsupported",
            isPresented: $showUnsupportedCurveAlert
        ) {
            Button("Import anyway", role: .destructive) {
                if let pending = pendingUnsupportedCurveImport {
                    pendingUnsupportedCurveImport = nil
                    importWithoutVerification(pending: pending)
                }
            }
            Button("Cancel", role: .cancel) {
                pendingUnsupportedCurveImport = nil
            }
        } message: {
            if let pending = pendingUnsupportedCurveImport {
                Text("This \(pending.typeName) is signed using the \(pending.curveName) curve, which is not supported on this platform.\n\nDo you want to import it without signature verification? You should only do this if you have trust in the file.")
            }
        }
        .task {
            await builtInModel.refresh()
            await userModel.refresh()
        }
    }
    
    private func importWithoutVerification(pending: PendingUnsupportedCurveImport) {
        Task {
            do {
                let metadata = TrustMetadata(
                    displayName: nil,
                    displayIcon: nil,
                    displayIconUrl: nil,
                    privacyPolicyUrl: nil,
                    disclaimer: nil,
                    testOnly: false,
                    extensions: [:]
                )
                let entry: TrustEntry
                if pending.typeName == "VICAL" {
                    entry = try await userModel.addVical(
                        encodedSignedVical: pending.fileData,
                        metadata: metadata,
                        disableSignatureVerification: true
                    )
                } else {
                    entry = try await userModel.addRical(
                        encodedSignedRical: pending.fileData,
                        metadata: metadata,
                        disableSignatureVerification: true
                    )
                }
                await MainActor.run {
                    viewModel.push(.trustEntry(
                        trustManagerId: userModel.trustManager.identifier,
                        trustEntryId: entry.identifier,
                        justImported: true
                    ))
                }
            } catch {
                Self.logImportError("Error importing \(pending.typeName) without verification", error)
                await MainActor.run {
                    self.importErrorTitle = "Error importing \(pending.typeName)"
                    if (error as? TrustEntryAlreadyExistsException) != nil || error.localizedDescription.contains("already exists") {
                        self.importErrorMessage = "A trust list with this identifier already exists."
                    } else {
                        self.importErrorMessage = "Importing \(pending.typeName) failed: \(error.localizedDescription)"
                    }
                    self.showImportErrorAlert = true
                }
            }
        }
    }
    
    private func handleCertFileImport(result: Swift.Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            let shouldStop = url.startAccessingSecurityScopedResource()
            defer { if shouldStop { url.stopAccessingSecurityScopedResource() } }
            do {
                let fileData = try Data(contentsOf: url)
                guard let pemString = String(data: fileData, encoding: .utf8) else {
                    throw NSError(domain: "TrustManagerScreen", code: 2, userInfo: [NSLocalizedDescriptionKey: "File is not a valid UTF-8 PEM certificate."])
                }
                let cert = try X509Cert.companion.fromPem(pemEncoding: pemString)
                Task {
                    do {
                        let entry = try await userModel.addX509Cert(
                            certificate: cert,
                            metadata: TrustMetadata(
                                displayName: nil,
                                displayIcon: nil,
                                displayIconUrl: nil,
                                privacyPolicyUrl: nil,
                                disclaimer: nil,
                                testOnly: false,
                                extensions: [:]
                            )
                        )
                        await MainActor.run {
                            viewModel.push(.trustEntry(
                                trustManagerId: userModel.trustManager.identifier,
                                trustEntryId: entry.identifier,
                                justImported: true
                            ))
                        }
                    } catch {
                        Self.logImportError("Error importing certificate", error)
                        await MainActor.run {
                            self.importErrorTitle = "Error importing certificate"
                            if (error as? TrustEntryAlreadyExistsException) != nil || error.localizedDescription.contains("already exists") {
                                self.importErrorMessage = "A certificate with this Subject Key Identifier already exists"
                            } else {
                                self.importErrorMessage = "Importing certificate failed: \(error.localizedDescription)"
                            }
                            self.showImportErrorAlert = true
                        }
                    }
                }
            } catch {
                Self.logImportError("Error reading certificate file", error)
                self.importErrorTitle = "Error importing certificate"
                self.importErrorMessage = "Importing certificate failed: \(error.localizedDescription)"
                self.showImportErrorAlert = true
            }
        case .failure(let error):
            Self.logImportError("File picker error for certificate", error)
            self.importErrorTitle = "Error importing certificate"
            self.importErrorMessage = "Importing certificate failed: \(error.localizedDescription)"
            self.showImportErrorAlert = true
        }
    }
    
    private func handleVicalFileImport(result: Swift.Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            let shouldStop = url.startAccessingSecurityScopedResource()
            defer { if shouldStop { url.stopAccessingSecurityScopedResource() } }
            do {
                let fileData = try Data(contentsOf: url)
                Task {
                    do {
                        let entry = try await userModel.addVical(
                            encodedSignedVical: fileData,
                            metadata: TrustMetadata(
                                displayName: nil,
                                displayIcon: nil,
                                displayIconUrl: nil,
                                privacyPolicyUrl: nil,
                                disclaimer: nil,
                                testOnly: false,
                                extensions: [:]
                            )
                        )
                        await MainActor.run {
                            viewModel.push(.trustEntry(
                                trustManagerId: userModel.trustManager.identifier,
                                trustEntryId: entry.identifier,
                                justImported: true
                            ))
                        }
                    } catch {
                        Self.logImportError("Error importing VICAL", error)
                        
                        // Check if signature verification failed due to unsupported curve
                        if let curve = Self.extractSigningCurve(fromCoseSign1Data: fileData),
                           !Crypto.shared.supportedCurves.contains(curve) {
                            await MainActor.run {
                                self.pendingUnsupportedCurveImport = PendingUnsupportedCurveImport(
                                    typeName: "VICAL",
                                    fileData: fileData,
                                    curveName: Self.curveDisplayName(curve)
                                )
                                self.showUnsupportedCurveAlert = true
                            }
                            return
                        }
                        
                        await MainActor.run {
                            self.importErrorTitle = "Error importing VICAL"
                            if (error as? TrustEntryAlreadyExistsException) != nil || error.localizedDescription.contains("already exists") {
                                self.importErrorMessage = "A trust list with this identifier already exists."
                            } else {
                                self.importErrorMessage = "Importing VICAL failed: \(error.localizedDescription)"
                            }
                            self.showImportErrorAlert = true
                        }
                    }
                }
            } catch {
                Self.logImportError("Error reading VICAL file", error)
                self.importErrorTitle = "Error importing VICAL"
                self.importErrorMessage = "Failed to read file: \(error.localizedDescription)"
                self.showImportErrorAlert = true
            }
        case .failure(let error):
            Self.logImportError("File picker error for VICAL", error)
            self.importErrorTitle = "Error importing VICAL"
            self.importErrorMessage = "Importing VICAL failed: \(error.localizedDescription)"
            self.showImportErrorAlert = true
        }
    }
    
    private func handleRicalFileImport(result: Swift.Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            let shouldStop = url.startAccessingSecurityScopedResource()
            defer { if shouldStop { url.stopAccessingSecurityScopedResource() } }
            do {
                let fileData = try Data(contentsOf: url)
                Task {
                    do {
                        let entry = try await userModel.addRical(
                            encodedSignedRical: fileData,
                            metadata: TrustMetadata(
                                displayName: nil,
                                displayIcon: nil,
                                displayIconUrl: nil,
                                privacyPolicyUrl: nil,
                                disclaimer: nil,
                                testOnly: false,
                                extensions: [:]
                            )
                        )
                        await MainActor.run {
                            viewModel.push(.trustEntry(
                                trustManagerId: userModel.trustManager.identifier,
                                trustEntryId: entry.identifier,
                                justImported: true
                            ))
                        }
                    } catch {
                        Self.logImportError("Error importing RICAL", error)
                        
                        // Check if signature verification failed due to unsupported curve
                        if let curve = Self.extractSigningCurve(fromCoseSign1Data: fileData),
                           !Crypto.shared.supportedCurves.contains(curve) {
                            await MainActor.run {
                                self.pendingUnsupportedCurveImport = PendingUnsupportedCurveImport(
                                    typeName: "RICAL",
                                    fileData: fileData,
                                    curveName: Self.curveDisplayName(curve)
                                )
                                self.showUnsupportedCurveAlert = true
                            }
                            return
                        }
                        
                        await MainActor.run {
                            self.importErrorTitle = "Error importing RICAL"
                            if (error as? TrustEntryAlreadyExistsException) != nil || error.localizedDescription.contains("already exists") {
                                self.importErrorMessage = "A trust list with this identifier already exists."
                            } else {
                                self.importErrorMessage = "Importing RICAL failed: \(error.localizedDescription)"
                            }
                            self.showImportErrorAlert = true
                        }
                    }
                }
            } catch {
                Self.logImportError("Error reading RICAL file", error)
                self.importErrorTitle = "Error importing RICAL"
                self.importErrorMessage = "Failed to read file: \(error.localizedDescription)"
                self.showImportErrorAlert = true
            }
        case .failure(let error):
            Self.logImportError("File picker error for RICAL", error)
            self.importErrorTitle = "Error importing RICAL"
            self.importErrorMessage = "Importing RICAL failed: \(error.localizedDescription)"
            self.showImportErrorAlert = true
        }
    }

    private static func extractSigningCurve(fromCoseSign1Data data: Data) -> EcCurve? {
        let byteString = data.toByteString()
        let byteArray = byteString.toByteArray(startIndex: 0, endIndex: Int32(data.count))
        guard let cbor = try? Cbor.shared.decode(encodedCbor: byteArray) else {
            return nil
        }
        let coseSign1 = CoseSign1.companion.fromDataItem(dataItem: cbor)
        
        // Check leaf certificate in x5chain (unprotected in VICAL, protected in RICAL)
        let x5chainItem = coseSign1.unprotectedHeaders[CoseNumberLabel(number: Cose.shared.COSE_LABEL_X5CHAIN)]
            ?? coseSign1.protectedHeaders[CoseNumberLabel(number: Cose.shared.COSE_LABEL_X5CHAIN)]
        if let certChain = x5chainItem?.asX509CertChain,
           let firstCert = certChain.certificates.first {
            return firstCert.ecPublicKey.curve
        }
        
        // Fallback to algorithm in protected header
        if let algItem = coseSign1.protectedHeaders[CoseNumberLabel(number: Cose.shared.COSE_LABEL_ALG)] {
            let alg = Algorithm.companion.fromCoseAlgorithmIdentifier(coseAlgorithmIdentifier: Int32(algItem.asNumber))
            return alg.curve
        }
        
        return nil
    }

    private static func curveDisplayName(_ curve: EcCurve) -> String {
        return curve.jwkName
    }

    private static func logImportError(_ prefix: String, _ error: Error) {
        print("\(prefix): \(error)")
        if let kotlinThrowable = (error as NSError).userInfo["KotlinException"] as? KotlinThrowable {
            if let cause = kotlinThrowable.cause {
                print("  Underlying cause: \(cause) - \(cause.message ?? "nil")")
            } else {
                print("  Underlying cause: nil")
            }
            kotlinThrowable.printStackTrace()
        }
    }
}

