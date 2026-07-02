//
//  ContentView.swift
//  iosApp
//
//  Created by David Zeuthen on 3/22/26.
//

import SwiftUI
import Multipaz

struct ContentView: View {
    @State private var viewModel = ViewModel()
    
    var body: some View {
        ZStack {
            if (viewModel.isLoading) {
                VStack {
                    ProgressView()
                }
            } else {
                NavigationStack(path: $viewModel.path) {
                    WalletScreen(documentId: nil)
                        .navigationDestination(for: Destination.self) { destination in
                            switch destination {
                            case .walletScreen(let documentId):
                                WalletScreen(documentId: documentId)
                            case .settingsScreen: SettingsScreen()
                            case .documentInfoScreen(let documentId):
                                DocumentInfoScreen(documentId: documentId)
                            case .documentInfoExtrasScreen(let documentId):
                                DocumentInfoExtrasScreen(documentId: documentId)
                                                        case .credentialInfoScreen(let documentId, let credentialId):
                                CredentialInfoScreen(documentId: documentId, credentialId: credentialId)
                            case .proximityPresentment(let documentId):
                                ProximityPresentmentScreen(documentId: documentId)
                            case .addToWallet:
                                AddToWalletScreen()
                            case .provisioning(let issuerUrl, let credentialId):
                                ProvisioningScreen(issuerUrl: issuerUrl, credentialId: credentialId)
                            case .provisioningFromOffer(let credentialOfferUri):
                                ProvisioningScreen(credentialOfferUri: credentialOfferUri)
                            case .requestVerification:
                                RequestVerificationScreen()
                            }
                        }
                }
            }
            PromptDialogs(promptModel: viewModel.promptModel)
        }
        .environment(viewModel)
        .onAppear {
            Task {
                await viewModel.load()
                
                // TODO: refreshSharedData()
                /*
                do {
                    let newData = try await viewModel.walletClient.refreshPublicData()
                    print("refreshPublicData() returned \(newData)")
                } catch {
                    print("refreshPublicData() on start-up failed: \(error)")
                }

                do {
                    try await viewModel.walletClient.refreshReaderKeys()
                } catch {
                    print("refreshReaderKeys() on start-up failed: \(error)")
                }
                 */
            }
        }
        .onOpenURL { url in
            let urlString = url.absoluteString
            if urlString.hasPrefix("openid-credential-offer://") || urlString.hasPrefix("haip-vci://") {
                viewModel.push(.provisioningFromOffer(credentialOfferUri: urlString))
            } else if urlString.hasPrefix(viewModel.walletClient.appLinkBaseUrl) {
                Task {
                    try? await viewModel.walletClient.processAppLinkInvocation(url: urlString)
                }
            } else {
                print("Unhandled URL: \(urlString)")
            }
        }
    }
}

#Preview {
    ContentView()
}
