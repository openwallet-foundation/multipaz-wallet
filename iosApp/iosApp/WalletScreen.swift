import SwiftUI
import Lottie
import Multipaz
import LinkPresentation

struct WalletScreen: View {
    @Environment(ViewModel.self) private var viewModel
    
    let documentId: String?
    let justAddedAtMillis: Int64?

    @State private var showDeleteConfirmation = false
    @State private var documentToDelete: DocumentInfo? = nil
    @State private var showShareConfirmation = false
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

    private var focusedDocument: DocumentInfo? {
        viewModel.documentModel.documentInfos.first {
            $0.document.identifier == documentId
        }
    }

    var body: some View {
        let isFocused = viewModel.verticalCardListState.internalFocusedCardIdentifier != nil
        ZStack(alignment: .bottom) {
            VStack {
                cardListView
            }
            
            floatingMenuBar
                .padding(.bottom, 16)
                .opacity(isFocused ? 0.0 : 1.0)
                .offset(y: isFocused ? 32 : 0)
                .allowsHitTesting(!isFocused)
                .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isFocused)
        }
        .id(documentId ?? "root")
        .frame(maxWidth: .infinity, alignment: .leading)
        .navigationBarBackButtonHidden(documentId != nil)
        .navigationTitle(
            isFocused ? "" : BuildConfig.shared.APP_NAME
        )
        .navigationBarTitleDisplayMode(.inline)
        .refreshable {
            await viewModel.refreshWallet()
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
            if documentId == nil && viewModel.verticalCardListState.internalFocusedCardIdentifier != nil {
                viewModel.verticalCardListState.unfocus {}
            }
        }
        .onDisappear {
            viewModel.verticalCardListState.animateListTransitions = true
            if documentId != nil && viewModel.path.isEmpty {
                viewModel.verticalCardListState.unfocus {}
            }
        }
        .background {
            ScreenEdgeSwipeGesture(isEnabled: isFocused && viewModel.path.count == 1) {
                viewModel.verticalCardListState.unfocus {
                    viewModel.popWithoutAnimation()
                }
            }
        }
        .alert("Remove Document?", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) {
                documentToDelete = nil
            }
            Button("Remove", role: .destructive) {
                if let doc = documentToDelete {
                    deleteConfirmedDocument(doc)
                    documentToDelete = nil
                }
            }
        } message: {
            Text("Are you sure you want to remove this document? This cannot be undone.")
        }
        .alert("Share pass", isPresented: $showShareConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Share") {
                if let focusedDoc = focusedDocument {
                    sharePass(documentInfo: focusedDoc)
                }
            }
        } message: {
            Text("Once shared, this action cannot be undone. The recipient will receive a copy of this pass and will be able to forward it to anyone.")
        }
        .toolbar {
            toolbarContent
        }
    }

    @ViewBuilder
    private var cardListView: some View {
        VerticalCardList(
            cardInfos: viewModel.documentModel.documentInfos,
            focusedCard: focusedDocument,
            unfocusedVisiblePercent: 25,
            allowCardReordering: true,
            showStackWhileFocused: false,
            state: viewModel.verticalCardListState,
            showCardInfo: { cardInfo in
                let docInfo = cardInfo as! DocumentInfo
                cardInfoView(targetDocument: focusedDocument ?? docInfo)
            },
            emptyContent: {
                emptyWalletPlaceholder
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

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        let isFocused = viewModel.verticalCardListState.internalFocusedCardIdentifier != nil
        ToolbarItem(placement: .topBarLeading) {
            ZStack {
                Button(action: {
                    viewModel.verticalCardListState.unfocus {
                        viewModel.popWithoutAnimation()
                    }
                }) {
                    Image(systemName: "chevron.left")
                        .font(.body.bold())
                }
                .opacity(isFocused ? 1.0 : 0.0)
                .scaleEffect(isFocused ? 1.0 : 0.8)
                .allowsHitTesting(isFocused)

                Image("AppLogo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 32, height: 32)
                    .opacity(isFocused ? 0.0 : 1.0)
                    .scaleEffect(isFocused ? 0.8 : 1.0)
                    .allowsHitTesting(!isFocused)
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isFocused)
        }
        .sharedBackgroundVisibility(
            isFocused ? .automatic : .hidden
        )
        
        ToolbarItem(placement: .topBarTrailing) {
            ZStack(alignment: .trailing) {
                if let focusedDoc = focusedDocument, !focusedDoc.document.provisionedDocumentSetupNeeded {
                    HStack(spacing: 16) {
                        if focusedDoc.document.mpzPassId != nil {
                            Button(action: {
                                showShareConfirmation = true
                            }) {
                                Image(systemName: "square.and.arrow.up")
                            }
                        }
                        Button(action: {
                            if let documentId = documentId {
                                viewModel.push(.proximityPresentment(documentId: documentId))
                            }
                        }) {
                            Image(systemName: "qrcode")
                        }
                    }
                    .opacity(isFocused ? 1.0 : 0.0)
                    .scaleEffect(isFocused ? 1.0 : 0.8)
                    .allowsHitTesting(isFocused)
                }
                
                avatarButton
                    .opacity(isFocused ? 0.0 : 1.0)
                    .scaleEffect(isFocused ? 0.8 : 1.0)
                    .allowsHitTesting(!isFocused)
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isFocused)
        }
        .sharedBackgroundVisibility(.hidden)
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
    
    @ViewBuilder
    private func cardInfoView(targetDocument: DocumentInfo) -> some View {
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
                        if viewModel.signedInUser != nil && targetDocument.document.isSyncing {
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
                            image: { Image(systemName: "trash").foregroundStyle(.red) }
                        )
                    }.onTapGesture {
                        documentToDelete = targetDocument
                        showDeleteConfirmation = true
                    }
                }
            }
        }
        .padding()
    }

    @ViewBuilder
    private var emptyWalletPlaceholder: some View {
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
    }

    private var floatingMenuBar: some View {
        HStack(spacing: 0) {
            Button(action: {
                viewModel.push(.requestVerification)
            }) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.shield")
                        .font(.system(size: 20, weight: .semibold))
                    Text("Verify")
                        .font(.subheadline.weight(.semibold))
                }
                .foregroundColor(.primary)
                .padding(.horizontal, 16)
                .frame(height: 48)
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
                    .frame(width: 48, height: 48)
            }
        }
        .padding(.horizontal, 4)
        .background(.ultraThinMaterial)
        .cornerRadius(24)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(Color.primary.opacity(0.15), lineWidth: 0.5)
        )
        .shadow(color: Color.black.opacity(0.15), radius: 10, x: 0, y: 5)
    }
    
    private func deleteConfirmedDocument(_ doc: DocumentInfo) {
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
    }
    
    private func sharePass(documentInfo: DocumentInfo) {
        guard let passData = documentInfo.document.mpzPassData else {
            print("sharePass: document has no mpzPassData")
            return
        }
        Task {
            do {
                let data = passData.toNSData() as Data
                let rawName = documentInfo.document.displayName ?? documentInfo.document.typeDisplayName ?? "pass"
                let sanitizedName = rawName.replacingOccurrences(of: "[^a-zA-Z0-9._-]", with: "_", options: .regularExpression)
                let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("shared_docs", isDirectory: true)
                try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
                let fileUrl = tempDir.appendingPathComponent("\(sanitizedName).mpzpass")
                try data.write(to: fileUrl)
                print("sharePass: Successfully written \(data.count) bytes to \(fileUrl.path)")
                
                try? await Task.sleep(nanoseconds: 300_000_000)
                await MainActor.run {
                    presentShareSheet(url: fileUrl, title: rawName)
                }
            } catch {
                print("Error sharing pass: \(error)")
            }
        }
    }
}

final class MpzPassActivityItemSource: NSObject, UIActivityItemSource {
    let url: URL
    let title: String
    
    init(url: URL, title: String) {
        self.url = url
        self.title = title
        super.init()
    }
    
    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any {
        return url
    }
    
    func activityViewController(_ activityViewController: UIActivityViewController, itemForActivityType activityType: UIActivity.ActivityType?) -> Any? {
        return url
    }
    
    func activityViewController(_ activityViewController: UIActivityViewController, dataTypeIdentifierForActivityType activityType: UIActivity.ActivityType?) -> String {
        return "org.multipaz.mpzpass"
    }
    
    func activityViewController(_ activityViewController: UIActivityViewController, subjectForActivityType activityType: UIActivity.ActivityType?) -> String {
        return title
    }
    
    func activityViewControllerLinkMetadata(_ activityViewController: UIActivityViewController) -> LPLinkMetadata? {
        let metadata = LPLinkMetadata()
        metadata.title = title
        metadata.originalURL = url
        metadata.url = url
        return metadata
    }
}

@MainActor
private func presentShareSheet(url: URL, title: String) {
    guard let windowScene = UIApplication.shared.connectedScenes
        .compactMap({ $0 as? UIWindowScene })
        .first(where: { $0.activationState == .foregroundActive }) ?? UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
          let keyWindow = windowScene.windows.first(where: { $0.isKeyWindow }) ?? windowScene.windows.first,
          let rootVC = keyWindow.rootViewController else {
        print("presentShareSheet: unable to find rootViewController")
        return
    }
    
    var topController = rootVC
    while let presented = topController.presentedViewController, !presented.isBeingDismissed {
        topController = presented
    }
    
    let itemSource = MpzPassActivityItemSource(url: url, title: title)
    let activityVC = UIActivityViewController(activityItems: [itemSource], applicationActivities: nil)
    if let popover = activityVC.popoverPresentationController {
        popover.sourceView = topController.view
        popover.sourceRect = CGRect(x: topController.view.bounds.midX, y: topController.view.bounds.midY, width: 0, height: 0)
        popover.permittedArrowDirections = []
    }
    topController.present(activityVC, animated: true)
}

private struct ScreenEdgeSwipeGesture: UIViewRepresentable {
    let isEnabled: Bool
    let action: () -> Void

    func makeUIView(context: Context) -> GestureView {
        let view = GestureView()
        view.coordinator = context.coordinator
        return view
    }

    func updateUIView(_ uiView: GestureView, context: Context) {
        context.coordinator.action = action
        context.coordinator.isEnabled = isEnabled
        uiView.updateGesture()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(isEnabled: isEnabled, action: action)
    }

    class GestureView: UIView {
        var coordinator: Coordinator?
        private var panGesture: UIPanGestureRecognizer?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            updateGesture()
        }

        func updateGesture() {
            guard let window = self.window, let coordinator = coordinator else { return }
            if panGesture == nil {
                let gesture = UIPanGestureRecognizer(target: coordinator, action: #selector(Coordinator.handlePan(_:)))
                gesture.delegate = coordinator
                window.addGestureRecognizer(gesture)
                panGesture = gesture
            }
            panGesture?.isEnabled = coordinator.isEnabled
        }

        override func willMove(toWindow newWindow: UIWindow?) {
            super.willMove(toWindow: newWindow)
            if newWindow == nil, let gesture = panGesture, let window = self.window {
                window.removeGestureRecognizer(gesture)
                panGesture = nil
            }
        }
    }

    class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var isEnabled: Bool
        var action: () -> Void
        private var isTriggered = false

        init(isEnabled: Bool, action: @escaping () -> Void) {
            self.isEnabled = isEnabled
            self.action = action
        }

        @objc func handlePan(_ recognizer: UIPanGestureRecognizer) {
            guard isEnabled else { return }
            let translation = recognizer.translation(in: recognizer.view)
            let velocity = recognizer.velocity(in: recognizer.view)

            switch recognizer.state {
            case .changed:
                if !isTriggered && translation.x > 30 && velocity.x > 80 && abs(translation.x) > abs(translation.y) * 1.2 {
                    isTriggered = true
                    action()
                }
            case .ended, .cancelled, .failed:
                isTriggered = false
            default:
                break
            }
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            return true
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard isEnabled, let pan = gestureRecognizer as? UIPanGestureRecognizer, let view = pan.view else { return false }
            let velocity = pan.velocity(in: view)
            return velocity.x > 0 && abs(velocity.x) > abs(velocity.y) * 1.2
        }
    }
}
