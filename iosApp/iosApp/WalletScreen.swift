import SwiftUI
import Multipaz

struct WalletScreen: View {
    @Environment(ViewModel.self) private var viewModel
    
    let documentId: String?

    @State private var showDeleteConfirmation = false
    @State private var documentToDelete: DocumentInfo? = nil

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
                    let typeDisplayName = docInfo.document.typeDisplayName ?? "Pass"
                    VStack {
                        FloatingItemList {
                            FloatingItemText(
                                text: "\(typeDisplayName) info",
                                secondary: "Pass details and certificate info",
                                image: { Image(systemName: "person.crop.rectangle") },
                                trailingContent: { Image(systemName: "chevron.right").foregroundColor(.gray) }
                            ).onTapGesture {
                                viewModel.push(.documentInfoScreen(documentId: docInfo.document.identifier))
                            }

                            FloatingItemText(
                                text: "View and manage activity",
                                secondary: "Logging is enabled",
                                image: { Image(systemName: "timer") },
                                trailingContent: { Image(systemName: "chevron.right").foregroundColor(.gray) }
                            ).onTapGesture {
                                print("TODO: go to activity page")
                            }

                            FloatingItemText(
                                text: "In person-sharing and consent",
                                secondary: "Always ask before sharing",
                                image: { Image(systemName: "wave.3.right") },
                                trailingContent: { Image(systemName: "chevron.right").foregroundColor(.gray) }
                            ).onTapGesture {
                                print("TODO: go to pre-consent configuration")
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
                            documentToDelete = docInfo
                            showDeleteConfirmation = true
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
        .alert("Remove Document?", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) {
                documentToDelete = nil
            }
            Button("Remove", role: .destructive) {
                if let doc = documentToDelete {
                    Task {
                        do {
                            try await viewModel.documentStore.deleteDocument(identifier: doc.document.identifier)
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
            } else {
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
