import SwiftUI
import SafariServices
import Lottie
import Multipaz

struct ProvisioningScreen: View {
    @Environment(ViewModel.self) private var viewModel
    @Environment(\.dismiss) private var dismiss
    
    let issuerUrl: String?
    let credentialId: String?
    let provisionedDocumentIdentifier: String?
    let credentialOfferUri: String?
    
    @State private var provisioningState: ProvisioningModel.State = ProvisioningModel.Idle()
    @State private var issuerMetadata: ProvisioningMetadata? = nil
    @State private var errorLoading: Error? = nil
    @State private var provisioningTask: Task<Void, Never>? = nil
    @State private var hasCompleted = false
    @State private var hasStarted = false
    
    // Auth secret challenge fields
    @State private var passphrase = ""
    @State private var isShowingSafari = false
    @State private var isRedirectReceived = false
    
    init(issuerUrl: String, credentialId: String?, provisionedDocumentIdentifier: String? = nil) {
        self.issuerUrl = issuerUrl
        self.credentialId = credentialId
        self.provisionedDocumentIdentifier = provisionedDocumentIdentifier
        self.credentialOfferUri = nil
    }
    
    init(credentialOfferUri: String) {
        self.issuerUrl = nil
        self.credentialId = nil
        self.provisionedDocumentIdentifier = nil
        self.credentialOfferUri = credentialOfferUri
    }
    
    var body: some View {
        VStack {
            if errorLoading != nil {
                errorView
            } else {
                switch onEnum(of: provisioningState) {
                case .idle:
                    if let metadata = issuerMetadata {
                        CredentialSelectionView(metadata: metadata) { selectedId in
                            launchProvisioning(selectedId: selectedId)
                        }
                    } else {
                        progressView
                    }
                    
                case .initial, .connected:
                    progressView
                    
                case .authorizing(let authorizingState):
                    if let challenge = authorizingState.authorizationChallenges.first {
                        switch onEnum(of: challenge) {
                        case .oAuth(let oauthChallenge):
                            oauthView(challenge: oauthChallenge)
                        case .secretText(let secretChallenge):
                            secretTextView(challenge: secretChallenge)
                        }
                    } else {
                        progressView
                    }
                    
                case .processingAuthorization, .authorized, .requestingCredentials, .credentialsIssued:
                    progressView
                    
                case .error:
                    errorView
                }
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    cancelProvisioning()
                } label: {
                    Image(systemName: "xmark")
                        .font(.body.bold())
                }
            }
        }
        .onAppear {
            if !hasStarted {
                hasStarted = true
                startProvisioning()
            }
        }
        .onDisappear {
            if !hasCompleted && !isShowingSafari {
                hasCompleted = true
                provisioningTask?.cancel()
                Task {
                    try? await viewModel.provisioningModel.reset()
                }
            }
        }
    }
    
    private var errorView: some View {
        VStack(spacing: 20) {
            LottieView(animation: .named("error_animation"))
                .playing(loopMode: .playOnce)
                .resizable()
                .frame(width: 120, height: 120)
            Text("Something went wrong")
                .font(.headline)
                .foregroundColor(.primary)
        }
        .task {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            cancelProvisioning()
        }
    }
    
    private var progressView: some View {
        ProgressView()
            .scaleEffect(1.5)
    }
    
    private func cancelProvisioning() {
        hasCompleted = true
        provisioningTask?.cancel()
        Task {
            try? await viewModel.provisioningModel.reset()
        }
        dismiss()
    }
    
    private func oauthView(challenge: AuthorizationChallenge.OAuth) -> some View {
        progressView
            .onAppear {
                isShowingSafari = true
            }
            .fullScreenCover(isPresented: $isShowingSafari) {
                if let url = URL(string: challenge.url) {
                    SafariView(url: url) {
                        if !isRedirectReceived {
                            self.errorLoading = NSError(
                                domain: "Provisioning",
                                code: 1,
                                userInfo: [NSLocalizedDescriptionKey: "Authentication cancelled"]
                            )
                        }
                    }
                    .ignoresSafeArea()
                }
            }
            .task {
                // Wait for App Link/Deep Link redirect
                do {
                    let redirectUrl = try await viewModel.walletClient.waitForAppLinkInvocation(state: challenge.state)
                    isRedirectReceived = true
                    isShowingSafari = false
                    try await viewModel.provisioningModel.provideAuthorizationResponse(
                        response: AuthorizationResponse.OAuth(id: challenge.id, parameterizedRedirectUrl: redirectUrl)
                    )
                } catch {
                    self.errorLoading = error
                }
            }
    }
    
    private func secretTextView(challenge: AuthorizationChallenge.SecretText) -> some View {
        VStack(spacing: 20) {
            let isNumeric = challenge.request.isNumeric
            Text(isNumeric ? "Enter PIN" : "Enter Passphrase")
                .font(.headline)
            
            if isNumeric {
                SecureField("PIN", text: $passphrase)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)
                    .padding()
            } else {
                SecureField("Passphrase", text: $passphrase)
                    .textFieldStyle(.roundedBorder)
                    .padding()
            }
            
            if let desc = challenge.request.description_ {
                Text(desc)
                    .font(.caption)
                    .foregroundColor(.gray)
                    .padding(.horizontal)
            }
            
            Button("Submit") {
                Task {
                    do {
                        try await viewModel.provisioningModel.provideAuthorizationResponse(
                            response: AuthorizationResponse.SecretText(id: challenge.id, secret: passphrase)
                        )
                    } catch {
                        self.errorLoading = error
                    }
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(passphrase.isEmpty)
        }
    }
    
    private func startProvisioning() {
        provisioningTask = Task {
            do {
                try await viewModel.provisioningModel.reset()
                
                // Collect states from flow
                Task {
                    for await state in viewModel.provisioningModel.state {
                        guard !Task.isCancelled else { break }
                        await MainActor.run {
                            self.provisioningState = state
                            
                            // If credentials are successfully issued, finalize saving
                            if let issuedState = state as? ProvisioningModel.CredentialsIssued {
                                if !hasCompleted {
                                    hasCompleted = true
                                    handleSuccess(issuedState: issuedState)
                                }
                            }
                        }
                    }
                }
                
                if let offerUri = credentialOfferUri {
                    let clientPreferences = try await viewModel.walletClient.getOpenID4VCIClientPreferences()
                    let backend = try await viewModel.walletClient.getOpenID4VCIBackend()
                    viewModel.provisioningModel.launchOpenID4VCIProvisioning(
                        offerUri: offerUri,
                        clientPreferences: clientPreferences,
                        backend: backend,
                        appData: nil // TODO
                    )
                } else if let credId = credentialId, let url = issuerUrl {
                    let clientPreferences = try await viewModel.walletClient.getOpenID4VCIClientPreferences()
                    let backend = try await viewModel.walletClient.getOpenID4VCIBackend()
                    viewModel.provisioningModel.launchOpenID4VCIProvisioning(
                        issuerUrl: url,
                        credentialId: credId,
                        clientPreferences: clientPreferences,
                        backend: backend,
                        appData: nil // TODO
                    )
                } else if let url = issuerUrl {
                    let clientPreferences = try await viewModel.walletClient.getOpenID4VCIClientPreferences()
                    let metadata = try await viewModel.provisioningModel.getOpenID4VCIIssuerMetadata(
                        issuerUrl: url,
                        clientPreferences: clientPreferences
                    )
                    await MainActor.run {
                        self.issuerMetadata = metadata
                    }
                }
            } catch {
                await MainActor.run {
                    self.errorLoading = error
                }
            }
        }
    }
    
    private func launchProvisioning(selectedId: String) {
        Task {
            do {
                let clientPreferences = try await viewModel.walletClient.getOpenID4VCIClientPreferences()
                let backend = try await viewModel.walletClient.getOpenID4VCIBackend()
                viewModel.provisioningModel.launchOpenID4VCIProvisioning(
                    issuerUrl: issuerUrl ?? "",
                    credentialId: selectedId,
                    clientPreferences: clientPreferences,
                    backend: backend,
                    appData: nil // TODO
                )
            } catch {
                await MainActor.run {
                    self.errorLoading = error
                }
            }
        }
    }
    
    private func handleSuccess(issuedState: ProvisioningModel.CredentialsIssued) {
        Task {
            do {
                let document = issuedState.document
                let metadata = viewModel.provisioningModel.metadata.value
                let url = metadata?.url ?? issuerUrl ?? ""
                let credId = (metadata?.credentials.keys.first as? String) ?? credentialId ?? ""
                
                let provisionedDocument = WalletClientProvisionedDocumentOpenID4VCI(
                    identifier: UUID().uuidString.lowercased(),
                    cardArt: document.cardArt,
                    displayName: document.displayName,
                    typeDisplayName: document.typeDisplayName,
                    url: url,
                    credentialId: credId
                )
                
                var placeholderToDelete: String? = nil
                if let provDocId = provisionedDocumentIdentifier {
                    let documents = try await viewModel.documentStore.listDocuments(sort: false)
                    if let placeholder = documents.first(where: { $0.provisionedDocumentIdentifier == provDocId && $0.identifier != document.identifier }) {
                        placeholderToDelete = placeholder.identifier
                    }
                    try await document.setProvisionedDocumentIdentifier(identifier: provDocId)
                } else if viewModel.signedInUser != nil {
                    try await document.setProvisionedDocumentIdentifier(identifier: provisionedDocument.identifier)
                    try await viewModel.walletClient.refreshSharedData()
                    let provisionedDocumentToAdd: WalletClientProvisionedDocument
                    if let cardArt = provisionedDocument.cardArt {
                        let maxBytes = Int(BuildConfig.shared.MAX_CARD_ART_SIZE_IN_SHARED_DATA_KB * 1024)
                        if let scaledCardArt = encodeCardArt(cardArt: cardArt, maxBytes: maxBytes) {
                            provisionedDocumentToAdd = provisionedDocument.withCardArt(cardArt: scaledCardArt)
                        } else {
                            provisionedDocumentToAdd = provisionedDocument
                        }
                    } else {
                        provisionedDocumentToAdd = provisionedDocument
                    }
                    if let currentSharedData = viewModel.walletClient.sharedData.value {
                        let newSharedData = try await currentSharedData.addProvisionedDocument(provisionedDocument: provisionedDocumentToAdd)
                        try await viewModel.walletClient.setSharedData(sharedData: newSharedData, suppressSpinner: true)
                    }
                }
                
                try await document.setPreconsentSetting(value: DocumentPreconsentSetting.NeverRequireConsent.shared)
                
                // Wait for DocumentModel to have the document in its list so focusedDocument is found immediately
                var waitCount = 0
                while !viewModel.documentModel.documentInfos.contains(where: { $0.document.identifier == document.identifier }) && waitCount < 100 {
                    try? await Task.sleep(nanoseconds: 20_000_000)
                    waitCount += 1
                }
                
                if let placeholderId = placeholderToDelete {
                    if let newDocInfo = viewModel.documentModel.documentInfos.first(where: { $0.document.identifier == document.identifier }),
                       let oldIdx = viewModel.documentModel.documentInfos.firstIndex(where: { $0.document.identifier == placeholderId }) {
                        try? await viewModel.documentModel.setDocumentPosition(documentInfo: newDocInfo, position: oldIdx)
                    }
                    try? await viewModel.documentStore.deleteDocument(identifier: placeholderId)
                }
                
                await MainActor.run {
                    viewModel.verticalCardListState.internalFocusedCardIdentifier = document.identifier
                    viewModel.verticalCardListState.lastFocusedCardIdentifier = document.identifier
                    viewModel.verticalCardListState.model.lastFocusedCardIdentifier = document.identifier
                    
                    if let placeholderId = placeholderToDelete,
                       let idx = viewModel.verticalCardListState.displayOrderIdentifiers.firstIndex(of: placeholderId) {
                        viewModel.verticalCardListState.displayOrderIdentifiers[idx] = document.identifier
                        viewModel.verticalCardListState.model.displayOrderIdentifiers = viewModel.verticalCardListState.displayOrderIdentifiers
                    }
                    
                    // Navigate back to wallet focused on the new document with justAddedAtMillis
                    let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
                    viewModel.selectedDocumentId = document.identifier
                    viewModel.justAddedAtMillis = nowMillis
                    var transaction = Transaction()
                    transaction.disablesAnimations = true
                    withTransaction(transaction) {
                        viewModel.path.removeAll()
                    }
                }
            } catch {
                await MainActor.run {
                    self.errorLoading = error
                }
            }
        }
    }
}

struct CredentialSelectionView: View {
    let metadata: ProvisioningMetadata
    let onSelected: (String) -> Void
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("\(metadata.display.text) offers the following passes. Select a pass to continue.")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .padding(.horizontal)
                
                // Collect credential ids
                let keys = Array(metadata.credentials.keys).compactMap { $0 as? String }
                
                FloatingItemList {
                    ForEach(keys, id: \.self) { id in
                        let credential = metadata.credentials[id]!
                        
                        FloatingItemText(
                            text: credential.display.text,
                            showChevron: true,
                            secondary: toHumanReadable(credential.format),
                            image: {
                                if let logoData = credential.display.logo?.toNSData() {
                                    Image(uiImage: UIImage(data: logoData) ?? UIImage())
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(width: 24, height: 24)
                                } else {
                                    Image(systemName: "doc.plaintext")
                                }
                            }
                        ).onTapGesture {
                            onSelected(id)
                        }
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical)
        }
        .navigationTitle("Select pass")
    }
    
    private func toHumanReadable(_ format: CredentialFormat) -> String {
        switch onEnum(of: format) {
        case .mdoc(let mdoc):
            return "ISO mdoc • \(mdoc.docType)"
        case .sdJwt(let sdJwt):
            return "IETF SD-JWT VC • \(sdJwt.vct)"
        }
    }
}

struct SafariView: UIViewControllerRepresentable {
    let url: URL
    let onDismiss: () -> Void
    
    func makeCoordinator() -> Coordinator {
        Coordinator(onDismiss: onDismiss)
    }
    
    func makeUIViewController(context: Context) -> SFSafariViewController {
        let controller = SFSafariViewController(url: url)
        controller.delegate = context.coordinator
        return controller
    }
    
    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
    
    class Coordinator: NSObject, SFSafariViewControllerDelegate {
        let onDismiss: () -> Void
        
        init(onDismiss: @escaping () -> Void) {
            self.onDismiss = onDismiss
        }
        
        func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
            onDismiss()
        }
    }
}
