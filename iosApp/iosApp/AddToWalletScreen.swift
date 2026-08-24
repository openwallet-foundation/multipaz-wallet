import SwiftUI
import Multipaz
import UniformTypeIdentifiers

struct AddToWalletScreen: View {
    @Environment(ViewModel.self) private var viewModel
    
    @State private var credentialIssuers: [CredentialIssuer]? = nil
    @State private var errorLoading: Error? = nil
    
    @State private var showFilePicker = false
    @State private var importErrorMessage: String? = nil
    @State private var showImportErrorAlert = false
    
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
                                showChevron: true,
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
                
                FloatingItemList {
                    FloatingItemText(
                        text: "Import pass from file",
                        showChevron: true,
                        image: { Image(systemName: "square.and.arrow.down").frame(width: 24, height: 24) }
                    ).onTapGesture {
                        showFilePicker = true
                    }
                    
                    FloatingItemText(
                        text: "Scan credential offer",
                        showChevron: true,
                        image: { Image(systemName: "qrcode.viewfinder").frame(width: 24, height: 24) }
                    ).onTapGesture {
                        viewModel.push(.scanCredentialOffer)
                    }
                    
                    FloatingItemText(
                        text: "Enter issuer URL",
                        showChevron: true,
                        image: { Image(systemName: "building.columns").frame(width: 24, height: 24) }
                    ).onTapGesture {
                        viewModel.push(.enterIssuerUrl)
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical)
        }
        .navigationTitle("Add to wallet")
        .navigationBarTitleDisplayMode(.inline)
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.item, .data],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                let shouldStopAccessing = url.startAccessingSecurityScopedResource()
                defer {
                    if shouldStopAccessing {
                        url.stopAccessingSecurityScopedResource()
                    }
                }
                do {
                    let data = try Data(contentsOf: url)
                    Task {
                        do {
                            try await viewModel.importAndShowMpzPass(data: data)
                        } catch {
                            await MainActor.run {
                                self.importErrorMessage = error.localizedDescription
                                self.showImportErrorAlert = true
                            }
                        }
                    }
                } catch {
                    self.importErrorMessage = error.localizedDescription
                    self.showImportErrorAlert = true
                }
            case .failure(let error):
                self.importErrorMessage = error.localizedDescription
                self.showImportErrorAlert = true
            }
        }
        .alert("Error importing pass", isPresented: $showImportErrorAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(importErrorMessage ?? "Something went wrong")
        }
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
    }
}
