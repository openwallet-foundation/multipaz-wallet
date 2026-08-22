import SwiftUI
import Lottie
import Multipaz

struct WalletScreen: View {
    @Environment(ViewModel.self) private var viewModel
    
    let documentId: String?
    let justAddedAtMillis: Int64?

    @State private var showDeleteConfirmation = false
    @State private var documentToDelete: DocumentInfo? = nil
    @State private var showJustAdded: Bool
    
    init(documentId: String?, justAddedAtMillis: Int64? = nil) {
        self.documentId = documentId
        self.justAddedAtMillis = justAddedAtMillis
        let isJustAdded: Bool
        if let millis = justAddedAtMillis {
            let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
            isJustAdded = (nowMillis - millis) < 5000 && nowMillis >= millis
        } else {
            isJustAdded = false
        }
        _showJustAdded = State(initialValue: isJustAdded)
    }

    var body: some View {
        let focusedDocument = viewModel.documentModel.documentInfos.first {
            $0.document.identifier == documentId
        }
        ZStack(alignment: .bottom) {
            VStack {
            VerticalCardList(
                cardInfos: viewModel.documentModel.documentInfos,
                focusedCard: focusedDocument,
                unfocusedVisiblePercent: 25, // Show a bit more of the overlapping cards
                allowCardReordering: true,
                showStackWhileFocused: false,
                state: viewModel.verticalCardListState,
                showCardInfo: { cardInfo in
                    let docInfo = cardInfo as! DocumentInfo
                    let targetDocument = focusedDocument ?? docInfo
                    let typeDisplayName = targetDocument.document.typeDisplayName ?? "Pass"
                    VStack {
                        if showJustAdded {
                            VStack(spacing: 16) {
                                LottieView(animation: .named("success_animation"))
                                    .playing(loopMode: .playOnce)
                                    .resizable()
                                    .frame(width: 120, height: 120)
                                Text("The pass was successfully added")
                                    .font(.headline)
                                    .foregroundColor(.primary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 24)
                            .task {
                                try? await Task.sleep(nanoseconds: 3_000_000_000)
                                withAnimation(.easeInOut(duration: 0.5)) {
                                    showJustAdded = false
                                }
                                viewModel.verticalCardListState.animateListTransitions = true
                            }
                        } else {
                            VStack {
                                FloatingItemList {
                                    if targetDocument.document.isSyncing {
                                        let syncedSecondaryText = targetDocument.document.provisionedDocumentSetupNeeded
                                            ? "Complete setup…"
                                            : "Ready to use on this device"
                                        FloatingItemText(
                                            text: "Synced to your account",
                                            showChevron: true,
                                            secondary: syncedSecondaryText,
                                            image: { Image(systemName: "arrow.triangle.2.circlepath") }
                                        ).onTapGesture {
                                            if targetDocument.document.provisionedDocumentSetupNeeded {
                                                if let provisionedDocuments = viewModel.walletClient.sharedData.value?.provisionedDocuments,
                                                   let provDoc = provisionedDocuments.first(where: { $0.identifier == targetDocument.document.provisionedDocumentIdentifier }) as? WalletClientProvisionedDocumentOpenID4VCI {
                                                    viewModel.push(.provisioning(issuerUrl: provDoc.url, credentialId: provDoc.credentialId, provisionedDocumentIdentifier: provDoc.identifier))
                                                }
                                            } else {
                                                viewModel.push(.settingsScreen)
                                            }
                                        }
                                    }

                                    if !targetDocument.document.provisionedDocumentSetupNeeded {
                                        FloatingItemText(
                                            text: "\(typeDisplayName) info",
                                            showChevron: true,
                                            secondary: "Pass details and certificate info",
                                            image: { Image(systemName: "person.crop.rectangle") }
                                        ).onTapGesture {
                                            viewModel.push(.documentInfoScreen(documentId: targetDocument.document.identifier))
                                        }

                                        FloatingItemText(
                                            text: "View and manage activity",
                                            showChevron: true,
                                            secondary: "Logging is enabled",
                                            image: { Image(systemName: "timer") }
                                        ).onTapGesture {
                                            print("TODO: go to activity page")
                                        }

                                        FloatingItemText(
                                            text: "In person-sharing and consent",
                                            showChevron: true,
                                            secondary: "Always ask before sharing",
                                            image: { Image(systemName: "wave.3.right") }
                                        ).onTapGesture {
                                            print("TODO: go to pre-consent configuration")
                                        }
                                    }
                                }

                                FloatingItemList {
                                    FloatingItemText(
                                        text: AttributedString(
                                            "Remove",
                                            attributes: {
                                                var container = AttributeContainer()
                                                container.foregroundColor = .red
                                                return container
                                            }()),
                                        image: { Image(systemName: "trash").foregroundStyle(.red) },
                                    )
                                }.onTapGesture {
                                    documentToDelete = targetDocument
                                    showDeleteConfirmation = true
                                }
                            }
                        }
                    }
                    .padding()
                },
                emptyContent: {
                    // This view appears inside the dashed placeholder
                    VStack(spacing: 12) {
                        Image(systemName: "plus.rectangle.on.rectangle")
                            .font(.system(size: 32))
                            .foregroundColor(.gray)
                        Text("No Documents")
                            .font(.headline)
                            .foregroundColor(.gray)
                        Text("Tap to add your first pass or ID")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                },
                onCardReordered: { cardInfo, newIndex in
                    let document = cardInfo as! DocumentInfo
                    print("User moved \(document.document.displayName ?? "card") to index \(newIndex)")
                    Task {
                        try? await viewModel.documentModel.setDocumentPosition(documentInfo: document, position: newIndex)
                    }
                },
                onCardFocused: { cardInfo in
                    viewModel.push(.walletScreen(documentId: cardInfo.identifier))
                },
                onCardFocusedTapped: { _ in
                    viewModel.verticalCardListState.unfocus {
                        viewModel.popWithoutAnimation()
                    }
                },
                onCardFocusedStackTapped: { _ in
                    viewModel.verticalCardListState.unfocus {
                        viewModel.popWithoutAnimation()
                    }
                }
            )
            }
            
            if viewModel.verticalCardListState.internalFocusedCardIdentifier == nil {
                floatingMenuBar
                    .padding(.bottom, 16)
            }
        }
        .id(documentId ?? "root")
        .frame(maxWidth: .infinity, alignment: .leading)
        .navigationBarBackButtonHidden(documentId != nil)
        .navigationTitle(
            viewModel.verticalCardListState.internalFocusedCardIdentifier == nil
            ? BuildConfig.shared.APP_NAME : ""
        )
        .navigationBarTitleDisplayMode(.inline)
        .refreshable {
            do {
                let _ = try await viewModel.walletClient.refreshPublicData()
                if viewModel.walletClient.signedInUser.value != nil {
                    try await viewModel.walletClient.refreshSharedData()
                    await viewModel.syncSharedData()
                }
                try await viewModel.walletClient.refreshReaderKeys()
            } catch {
                print("Error refreshing wallet: \(error)")
            }
        }
        .onAppear {
            let isJustAdded: Bool
            if let millis = justAddedAtMillis {
                let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
                isJustAdded = (nowMillis - millis) < 5000 && nowMillis >= millis
            } else {
                isJustAdded = false
            }
            viewModel.verticalCardListState.animateListTransitions = !isJustAdded
        }
        .onDisappear {
            viewModel.verticalCardListState.animateListTransitions = true
        }
        .alert("Remove Document?", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) {
                documentToDelete = nil
            }
            Button("Remove", role: .destructive) {
                if let doc = documentToDelete {
                    Task {
                        do {
                            try await viewModel.documentStore.deleteDocumentFromWalletBackend(
                                document: doc.document,
                                walletClient: viewModel.walletClient
                            )
                            await MainActor.run {
                                viewModel.verticalCardListState.unfocus {
                                    viewModel.popWithoutAnimation()
                                }
                            }
                        } catch {
                            print("Error deleting document: \(error)")
                        }
                    }
                    documentToDelete = nil
                }
            }
        } message: {
            Text("Are you sure you want to remove this document? This cannot be undone.")
        }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if viewModel.verticalCardListState.internalFocusedCardIdentifier != nil {
                    Button(action: {
                        viewModel.verticalCardListState.unfocus {
                            viewModel.popWithoutAnimation()
                        }
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.body.bold())
                    }
                    .transition(.opacity)
                } else {
                    Image("AppLogo")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 32, height: 32)
                }
            }
            .sharedBackgroundVisibility(
                viewModel.verticalCardListState.internalFocusedCardIdentifier == nil ? .hidden : .automatic
            )
            if viewModel.verticalCardListState.internalFocusedCardIdentifier == nil {
                ToolbarItem(placement: .topBarTrailing) {
                    avatarButton
                }
                .sharedBackgroundVisibility(.hidden)
            } else if let focusedDoc = focusedDocument, !focusedDoc.document.provisionedDocumentSetupNeeded {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: {
                        if let documentId = documentId {
                            viewModel.push(.proximityPresentment(documentId: documentId))
                        }
                    }) {
                        Image(systemName: "qrcode")
                    }
                }
                .sharedBackgroundVisibility(.hidden)
            }
        }
    }
    
    @ViewBuilder
    private var avatarButton: some View {
        if viewModel.signedInUser != nil {
            // Button 1: Signed-in state
            Button {
                viewModel.push(.settingsScreen)
            } label: {
                viewModel.signedInUser!.profilePictureView(size: 32.0)
            }
        } else {
            // Button 2: Logged-out state
            Button {
                viewModel.push(.settingsScreen)
            } label: {
                Image(systemName: "person.crop.circle")
                    .resizable()
                    .frame(width: 32.0, height: 32.0)
            }
        }
    }
    
    private var floatingMenuBar: some View {
        HStack(spacing: 0) {
            Button(action: {
                viewModel.push(.requestVerification)
            }) {
                Image(systemName: "checkmark.circle")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundColor(.primary)
                    .frame(width: 56, height: 48)
            }
            
            Divider()
                .frame(height: 24)
                .background(Color.primary.opacity(0.15))
            
            Button(action: {
                viewModel.push(.addToWallet)
            }) {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundColor(.primary)
                    .frame(width: 56, height: 48)
            }
        }
        .padding(.horizontal, 8)
        .background(.ultraThinMaterial)
        .cornerRadius(24)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(Color.primary.opacity(0.15), lineWidth: 0.5)
        )
        .shadow(color: Color.black.opacity(0.15), radius: 10, x: 0, y: 5)
    }
}
