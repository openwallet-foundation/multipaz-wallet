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
                            case .walletScreen(let documentId, let justAddedAtMillis, let animateListTransitions):
                                let justAdded: Bool = {
                                    if let millis = justAddedAtMillis {
                                        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
                                        return abs(nowMillis - millis) < 5000
                                    }
                                    return false
                                }()
                                WalletScreen(
                                    documentId: documentId,
                                    justAdded: justAdded,
                                    animateListTransitions: animateListTransitions
                                )
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
                            case .scanCredentialOffer:
                                ScanCredentialOfferScreen()
                            case .enterIssuerUrl:
                                EnterIssuerUrlScreen()
                            case .provisioning(let issuerUrl, let credentialId, let provisionedDocumentIdentifier):
                                ProvisioningScreen(issuerUrl: issuerUrl, credentialId: credentialId, provisionedDocumentIdentifier: provisionedDocumentIdentifier)
                            case .provisioningFromOffer(let credentialOfferUri):
                                ProvisioningScreen(credentialOfferUri: credentialOfferUri)
                            case .requestVerification:
                                RequestVerificationScreen()
                            case .deviceSessions:
                                DeviceSessionsScreen()
                            case .trustedIssuers:
                                TrustManagerScreen(isVical: true)
                            case .trustedVerifiers:
                                TrustManagerScreen(isVical: false)
                            case .trustEntry(let trustManagerId, let trustEntryId, let justImported):
                                TrustEntryScreen(
                                    trustManagerId: trustManagerId,
                                    trustEntryId: trustEntryId,
                                    justImported: justImported
                                )
                            case .trustEntryEdit(let trustManagerId, let trustEntryId):
                                TrustEntryEditScreen(
                                    trustManagerId: trustManagerId,
                                    trustEntryId: trustEntryId
                                )
                            case .trustEntryVicalEntry(let trustManagerId, let vicalTrustEntryId, let certNum):
                                TrustEntryVicalEntryScreen(
                                    trustManagerId: trustManagerId,
                                    vicalTrustEntryId: vicalTrustEntryId,
                                    certNum: certNum
                                )
                            case .trustEntryRicalEntry(let trustManagerId, let ricalTrustEntryId, let certNum):
                                TrustEntryRicalEntryScreen(
                                    trustManagerId: trustManagerId,
                                    ricalTrustEntryId: ricalTrustEntryId,
                                    certNum: certNum
                                )
                            case .certificateViewer(let certChain):
                                CertificateViewerScreen(certChain: certChain)
                            case .certificateViewerSingle(let certificate):
                                CertificateViewerScreen(certificate: certificate)
                            }
                        }
                }
            }
            if let documentId = viewModel.proximityPresentmentDocumentId {
                ProximityPresentmentScreen(documentId: documentId)
                    .transition(.opacity)
                    .zIndex(1)
            }
            PromptDialogs(promptModel: viewModel.promptModel)
                .zIndex(2)
        }
        .animation(.easeInOut(duration: 0.3), value: viewModel.proximityPresentmentDocumentId)
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
            if url.isFileURL && url.pathExtension.lowercased() == "mpzpass" {
                let shouldStopAccessing = url.startAccessingSecurityScopedResource()
                defer {
                    if shouldStopAccessing {
                        url.stopAccessingSecurityScopedResource()
                    }
                }
                if let data = try? Data(contentsOf: url) {
                    Task {
                        do {
                            try await viewModel.importAndShowMpzPass(data: data)
                        } catch {
                            print("Error importing opened mpzpass: \(error)")
                        }
                    }
                }
            } else {
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
}

#Preview {
    ContentView()
}
