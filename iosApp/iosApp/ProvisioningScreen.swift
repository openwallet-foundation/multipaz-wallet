import SwiftUI
import SafariServices
import Multipaz

struct ProvisioningScreen: View {
    @Environment(ViewModel.self) private var viewModel
    @Environment(\.dismiss) private var dismiss
    
    let issuerUrl: String?
    let credentialId: String?
    let credentialOfferUri: String?
    
    @State private var provisioningState: ProvisioningModel.State = ProvisioningModel.Idle()
    @State private var issuerMetadata: ProvisioningMetadata? = nil
    @State private var errorLoading: Error? = nil
    
    // Auth secret challenge fields
    @State private var passphrase = ""
    @State private var isShowingSafari = false
    
    init(issuerUrl: String, credentialId: String?) {
        self.issuerUrl = issuerUrl
        self.credentialId = credentialId
        self.credentialOfferUri = nil
    }
    
    init(credentialOfferUri: String) {
        self.issuerUrl = nil
        self.credentialId = nil
        self.credentialOfferUri = credentialOfferUri
    }
    
    var body: some View {
        VStack {
            if let error = errorLoading {
                VStack(spacing: 20) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.system(size: 50))
                        .foregroundColor(.red)
                    Text("Provisioning Failed")
                        .font(.headline)
                    Text(error.localizedDescription)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .padding()
                    Button("Close") {
                        dismiss()
                    }
                    .buttonStyle(.borderedProminent)
                }
            } else {
                switch onEnum(of: provisioningState) {
                case .idle:
                    if let metadata = issuerMetadata {
                        CredentialSelectionView(metadata: metadata) { selectedId in
                            launchProvisioning(selectedId: selectedId)
                        }
                    } else if credentialOfferUri != nil {
                        progressView(message: "Processing offer...")
                    } else {
                        progressView(message: "Connecting to issuer...")
                    }
                    
                case .initial:
                    progressView(message: "Connecting to issuer...")
                    
                case .connected:
                    progressView(message: "Connected to issuer...")
                    
                case .authorizing(let authorizingState):
                    if let challenge = authorizingState.authorizationChallenges.first {
                        switch onEnum(of: challenge) {
                        case .oAuth(let oauthChallenge):
                            oauthView(challenge: oauthChallenge)
                        case .secretText(let secretChallenge):
                            secretTextView(challenge: secretChallenge)
                        }
                    } else {
                        progressView(message: "Authorizing...")
                    }
                    
                case .processingAuthorization:
                    progressView(message: "Verifying authorization...")
                    
                case .authorized:
                    progressView(message: "Authorized!")
                    
                case .requestingCredentials:
                    progressView(message: "Requesting your credentials...")
                    
                case .credentialsIssued:
                    progressView(message: "Credentials issued!")
                    
                case .error(let errorState):
                    VStack(spacing: 20) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 50))
                            .foregroundColor(.red)
                        Text("Provisioning Error")
                            .font(.headline)
                        Text(errorState.err.message ?? "An error occurred during provisioning.")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                            .padding()
                        Button("Close") {
                            dismiss()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
            }
        }
        .navigationTitle("Provisioning")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            startProvisioning()
        }
    }
    
    private func progressView(message: String) -> some View {
        VStack(spacing: 20) {
            ProgressView()
                .scaleEffect(1.5)
            Text(message)
                .font(.subheadline)
                .foregroundColor(.gray)
        }
    }
    
    private func oauthView(challenge: AuthorizationChallenge.OAuth) -> some View {
        VStack(spacing: 20) {
            ProgressView()
                .scaleEffect(1.5)
            Text("Authorization Required")
                .font(.headline)
            Text("Opening secure browser to authenticate...")
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            Button("Reopen Browser") {
                isShowingSafari = true
            }
            .buttonStyle(.bordered)
            .padding(.top)
        }
        .onAppear {
            isShowingSafari = true
        }
        .fullScreenCover(isPresented: $isShowingSafari) {
            if let url = URL(string: challenge.url) {
                SafariView(url: url)
                    .ignoresSafeArea()
            }
        }
        .task {
            // Wait for App Link/Deep Link redirect
            do {
                let redirectUrl = try await viewModel.walletClient.waitForAppLinkInvocation(state: challenge.state)
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
        Task {
            do {
                try await viewModel.provisioningModel.reset()
                
                // Collect states from flow
                Task {
                    for await state in viewModel.provisioningModel.state {
                        await MainActor.run {
                            self.provisioningState = state
                            
                            // If credentials are successfully issued, finalize saving
                            if let issuedState = state as? ProvisioningModel.CredentialsIssued {
                                handleSuccess(issuedState: issuedState)
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
                        backend: backend
                    )
                } else if let credId = credentialId, let url = issuerUrl {
                    let clientPreferences = try await viewModel.walletClient.getOpenID4VCIClientPreferences()
                    let backend = try await viewModel.walletClient.getOpenID4VCIBackend()
                    viewModel.provisioningModel.launchOpenID4VCIProvisioning(
                        issuerUrl: url,
                        credentialId: credId,
                        clientPreferences: clientPreferences,
                        backend: backend
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
                    backend: backend
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
                guard let metadata = viewModel.provisioningModel.metadata.value,
                      let firstKey = Array(metadata.credentials.keys).first as? String else {
                    return
                }
                
                let provisionedDocument = WalletClientProvisionedDocumentOpenID4VCI(
                    identifier: UUID().uuidString.lowercased(),
                    cardArt: document.cardArt,
                    displayName: document.displayName,
                    typeDisplayName: document.typeDisplayName,
                    url: metadata.url,
                    credentialId: firstKey
                )
                
                if viewModel.signedInUser != nil {
                    try await document.setProvisionedDocumentIdentifier(identifier: provisionedDocument.identifier)
                    try await viewModel.walletClient.refreshSharedData()
                    if let currentSharedData = viewModel.walletClient.sharedData.value {
                        let newSharedData = try await currentSharedData.addProvisionedDocument(provisionedDocument: provisionedDocument)
                        try await viewModel.walletClient.setSharedData(sharedData: newSharedData)
                    }
                }
                
                await MainActor.run {
                    // Navigate back to wallet
                    viewModel.path.removeAll()
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
        VStack(alignment: .leading, spacing: 20) {
            Text("Select a credential type to add:")
                .font(.headline)
                .padding(.horizontal)
            
            // Collect credential ids
            let keys = Array(metadata.credentials.keys).compactMap { $0 as? String }
            
            FloatingItemList {
                ForEach(keys, id: \.self) { id in
                    let credential = metadata.credentials[id]!
                    
                    FloatingItemText(
                        text: credential.display.text,
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
    }
    
    private func toHumanReadable(_ format: CredentialFormat) -> String {
        switch onEnum(of: format) {
        case .mdoc(let mdoc):
            return "mdoc (\(mdoc.docType))"
        case .sdJwt(let sdJwt):
            return "SD-JWT (\(sdJwt.vct))"
        }
    }
}

struct SafariView: UIViewControllerRepresentable {
    let url: URL
    
    func makeUIViewController(context: Context) -> SFSafariViewController {
        return SFSafariViewController(url: url)
    }
    
    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}
