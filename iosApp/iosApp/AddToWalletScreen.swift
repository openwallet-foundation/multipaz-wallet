import SwiftUI
import Multipaz

struct AddToWalletScreen: View {
    @Environment(ViewModel.self) private var viewModel
    
    @State private var credentialIssuers: [CredentialIssuer]? = nil
    @State private var errorLoading: Error? = nil
    
    @State private var showUrlDialog = false
    @State private var customIssuerUrl = ""
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Select a pass type to start adding to your wallet")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .padding(.horizontal)
                
                FloatingItemList {
                    if let error = errorLoading {
                        FloatingItemCenteredText(text: "Error loading issuers. Are you online?")
                    } else if credentialIssuers == nil {
                        FloatingItemCenteredText(text: "Loading issuers")
                    } else if credentialIssuers!.isEmpty {
                        FloatingItemCenteredText(text: "No issuers available")
                    } else {
                        ForEach(0..<credentialIssuers!.count, id: \.self) { index in
                            let issuer = credentialIssuers![index]
                            let openIdIssuer = issuer as? CredentialIssuerOpenID4VCI
                            FloatingItemText(
                                text: issuer.name,
                                image: {
                                    AsyncImage(url: URL(string: issuer.iconUrl)) { image in
                                        image.resizable()
                                             .aspectRatio(contentMode: .fit)
                                    } placeholder: {
                                        ProgressView()
                                    }
                                    .frame(width: 1.586 * 24.0, height: 24.0)
                                }
                            ).onTapGesture {
                                if let openIdIssuer = openIdIssuer {
                                    viewModel.push(.provisioning(issuerUrl: openIdIssuer.url, credentialId: openIdIssuer.id))
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal)
                
                if viewModel.settings.devMode {
                    FloatingItemList {
                        FloatingItemText(
                            text: "Enter Issuer URL…",
                            image: { Image(systemName: "building.columns") }
                        ).onTapGesture {
                            customIssuerUrl = viewModel.settings.provisioningServerUrl
                            showUrlDialog = true
                        }
                    }
                    .padding(.horizontal)
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Add to wallet")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            Task {
                do {
                    let list = try await viewModel.walletClient.getCredentialIssuers()
                    await MainActor.run {
                        self.credentialIssuers = list
                    }
                } catch {
                    await MainActor.run {
                        self.errorLoading = error
                    }
                }
            }
        }
        .alert("Enter the URL for the issuer", isPresented: $showUrlDialog) {
            TextField("Issuer server URL", text: $customIssuerUrl)
                .keyboardType(.URL)
                .autocapitalization(.none)
            Button("Connect") {
                viewModel.settings.provisioningServerUrl = customIssuerUrl
                viewModel.push(.provisioning(issuerUrl: customIssuerUrl, credentialId: nil))
            }
            Button("Reset to default") {
                let defaultUrl = "https://issuer.multipaz.org/issuer"
                viewModel.settings.provisioningServerUrl = defaultUrl
                customIssuerUrl = defaultUrl
                viewModel.push(.provisioning(issuerUrl: defaultUrl, credentialId: nil))
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}
