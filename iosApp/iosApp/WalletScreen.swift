import SwiftUI
import Lottie
import Multipaz
import LinkPresentation

struct WalletScreen: View {
    @Environment(ViewModel.self) private var viewModel
    
    let documentId: String?
    let justAdded: Bool
    
    /// Controls whether `VerticalCardList` animates spatial transitions when focusing or
    /// unfocusing cards.
    ///
    /// - When tapping a card in the unfocused list, this is `true` so the card expands smoothly
    ///   into the focused detail view.
    /// - When navigating directly to a newly added/provisioned document, this is `true` but
    ///   `verticalCardListState` is pre-initialized with the new card's identifier so the card
    ///   renders immediately in place without an entrance animation, while still allowing a
    ///   smooth collapse animation when dismissing back to the list.
    /// - During unfocusing gestures/back navigation, `isUnfocusing` is combined with this flag
    ///   to guarantee the collapse animation always plays cleanly.
    let animateListTransitions: Bool

    @State private var showDeleteConfirmation = false
    @State private var documentToDelete: DocumentInfo? = nil
    @State private var showShareConfirmation = false
    @State private var isUnfocusing = false
    @State private var toastMessage: String? = nil
    @State private var showJustAdded: Bool
    @State private var titleAndFabVisible: Bool
    @State private var isFirstAppear = true
    @State private var devModeNumTimesPressed: Int = 0
    @State private var toastTask: Task<Void, Never>? = nil
    @State private var qrPresentmentDocumentId: String? = nil
    
    init(
        documentId: String?,
        justAdded: Bool = false,
        animateListTransitions: Bool = false
    ) {
        self.documentId = documentId
        self.justAdded = justAdded
        self.animateListTransitions = animateListTransitions
        _showJustAdded = State(initialValue: justAdded)
        _titleAndFabVisible = State(initialValue: documentId == nil)
    }

    private var isPreviousScreenCardList: Bool {
        if let last = viewModel.path.dropLast().last {
            if case .walletScreen(let docId, _, _) = last {
                return docId == nil
            }
        }
        return false
    }

    private func handleBack() {
        if isPreviousScreenCardList {
            if !isUnfocusing {
                isUnfocusing = true
                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                    titleAndFabVisible = true
                }
                viewModel.verticalCardListState.unfocus {
                    isUnfocusing = false
                    viewModel.popWithoutAnimation()
                }
            }
        } else if documentId != nil {
            if !isUnfocusing {
                isUnfocusing = true
                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                    titleAndFabVisible = true
                }
                viewModel.verticalCardListState.unfocus {
                    isUnfocusing = false
                    viewModel.popToRootWithoutAnimation()
                }
            }
        } else if !viewModel.path.isEmpty {
            viewModel.path.removeLast()
        }
    }

    private var focusedDocument: DocumentInfo? {
        let doc = viewModel.documentModel.documentInfos.first {
            $0.document.identifier == documentId
        }
        return doc
    }

    var body: some View {
        let isFocused = !titleAndFabVisible
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                customTopBar
                cardListView
            }
            
            floatingMenuBar
                .padding(.bottom, 16)
                .opacity(isFocused ? 0.0 : 1.0)
                .offset(y: isFocused ? 32 : 0)
                .allowsHitTesting(!isFocused)
                .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isFocused)

            if let toast = toastMessage {
                Text(toast)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.ultraThinMaterial)
                    .clipShape(Capsule())
                    .overlay(
                        Capsule()
                            .stroke(Color.primary.opacity(0.12), lineWidth: 0.5)
                    )
                    .shadow(color: Color.black.opacity(0.15), radius: 10, x: 0, y: 5)
                    .padding(.horizontal, 24)
                    .padding(.bottom, isFocused ? 32 : 80)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .zIndex(100)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .toolbarVisibility(.hidden, for: .navigationBar)
        .navigationBarHidden(true)
        .refreshable {
            await viewModel.refreshWallet()
        }
        .onAppear {
            titleAndFabVisible = (documentId == nil)
            if justAdded {
                showJustAdded = true
            }
        }
        .onChange(of: documentId) { _, newDocId in
            titleAndFabVisible = (newDocId == nil)
        }
        .onChange(of: justAdded) { _, newValue in
            if newValue {
                showJustAdded = true
            }
        }
        .background {
            ScreenEdgeSwipeGesture(isEnabled: isFocused) {
                handleBack()
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
        .overlay {
            qrPresentmentOverlay
                .ignoresSafeArea()
        }
        .animation(.easeInOut(duration: 0.2), value: qrPresentmentDocumentId)
    }

    @ViewBuilder
    private var qrPresentmentOverlay: some View {
        if let qrDocId = qrPresentmentDocumentId {
            DocumentQrPresentmentDialog(
                documentId: qrDocId,
                onDismissed: {
                    qrPresentmentDocumentId = nil
                },
                onTransactionUnderway: {
                    qrPresentmentDocumentId = nil
                    viewModel.showProximityPresentment(documentId: qrDocId)
                }
            )
            .transition(.opacity.combined(with: .scale(scale: 0.95)))
        }
    }

    @ViewBuilder
    private var customTopBar: some View {
        let isFocused = !titleAndFabVisible
        ZStack {
            Text(BuildConfig.shared.APP_NAME)
                .font(.headline)
                .contentShape(Rectangle())
                .onTapGesture {
                    handleTitleTap()
                }
                .opacity(isFocused ? 0.0 : 1.0)
                .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isFocused)

            HStack {
                ZStack {
                    Button(action: {
                        handleBack()
                    }) {
                        ZStack {
                            Circle()
                                .fill(.ultraThinMaterial)
                                .overlay(
                                    Circle()
                                        .stroke(Color.primary.opacity(0.12), lineWidth: 0.5)
                                )
                                .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 1)
                                .frame(width: 44, height: 44)

                            Image(systemName: "chevron.backward")
                                .font(.system(size: 22, weight: .semibold))
                                .foregroundStyle(.primary)
                        }
                        .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .opacity(isFocused ? 1.0 : 0.0)
                    .scaleEffect(isFocused ? 1.0 : 0.8)
                    .allowsHitTesting(isFocused)

                    Image("AppLogo")
                        .renderingMode(.original)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 32, height: 32)
                        .opacity(isFocused ? 0.0 : 1.0)
                        .scaleEffect(isFocused ? 0.8 : 1.0)
                        .allowsHitTesting(!isFocused)
                }
                .frame(width: 44, height: 44)
                .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isFocused)

                Spacer()

                ZStack(alignment: .trailing) {
                    if let focusedDoc = focusedDocument, !focusedDoc.document.provisionedDocumentSetupNeeded {
                        HStack(spacing: 8) {
                            if focusedDoc.document.mpzPassId != nil {
                                Button(action: {
                                    showShareConfirmation = true
                                }) {
                                    ZStack {
                                        Circle()
                                            .fill(.ultraThinMaterial)
                                            .overlay(
                                                Circle()
                                                    .stroke(Color.primary.opacity(0.12), lineWidth: 0.5)
                                            )
                                            .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 1)
                                            .frame(width: 44, height: 44)

                                        Image(systemName: "square.and.arrow.up")
                                            .font(.system(size: 20, weight: .semibold))
                                            .foregroundStyle(.primary)
                                    }
                                    .frame(width: 44, height: 44)
                                    .contentShape(Circle())
                                }
                                .buttonStyle(.plain)
                            }
                            if focusedDoc.isProximityPresentable {
                                Button(action: {
                                    if let documentId = documentId {
                                        qrPresentmentDocumentId = documentId
                                    }
                                }) {
                                    ZStack {
                                        Circle()
                                            .fill(.ultraThinMaterial)
                                            .overlay(
                                                Circle()
                                                    .stroke(Color.primary.opacity(0.12), lineWidth: 0.5)
                                            )
                                            .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 1)
                                            .frame(width: 44, height: 44)

                                        Image(systemName: "qrcode")
                                            .font(.system(size: 22, weight: .semibold))
                                            .foregroundStyle(.primary)
                                    }
                                    .frame(width: 44, height: 44)
                                    .contentShape(Circle())
                                }
                                .buttonStyle(.plain)
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
        }
        .frame(height: 44)
        .padding(.horizontal, 16)
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
            animateListTransitions: animateListTransitions || isUnfocusing,
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
                viewModel.push(.walletScreen(documentId: cardInfo.identifier, animateListTransitions: true))
            },
            onCardFocusedTapped: { _ in
                handleBack()
            },
            onCardFocusedStackTapped: { _ in
                handleBack()
            }
        )
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
            .buttonStyle(.plain)
        } else {
            // Button 2: Logged-out state
            Button {
                viewModel.push(.settingsScreen)
            } label: {
                Image(systemName: "person.crop.circle")
                    .resizable()
                    .frame(width: 32.0, height: 32.0)
                    .foregroundStyle(.primary)
            }
            .buttonStyle(.plain)
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
                }
            } else {
                VStack {
                    if targetDocument.isProximityPresentable {
                        PresentQrCodePromptView()
                            .contentShape(Rectangle())
                            .onTapGesture {
                                qrPresentmentDocumentId = targetDocument.document.identifier
                            }
                    }

                    FloatingItemList {
                        if viewModel.signedInUser != nil && targetDocument.document.isSyncing {
                            let syncedSecondaryText = targetDocument.document.provisionedDocumentSetupNeeded
                                ? "Complete setup…"
                                : "Ready to use on this device"
                            FloatingItemText(
                                text: "Synced to your account",
                                showChevron: true,
                                secondary: syncedSecondaryText,
                                image: { Image(systemName: "arrow.triangle.2.circlepath").frame(width: 24, height: 24) }
                            ).onTapGesture {
                                if targetDocument.document.provisionedDocumentSetupNeeded {
                                    if let provisionedDocuments = viewModel.walletClient.sharedData.value?.provisionedDocuments,
                                       let provDoc = provisionedDocuments.first(where: { $0.identifier == targetDocument.document.provisionedDocumentIdentifier }) as? WalletClientProvisionedDocumentOpenID4VCI {
                                        viewModel.push(.provisioning(issuerUrl: provDoc.url, credentialId: provDoc.credentialId, provisionedDocumentIdentifier: provDoc.identifier))
                                    }
                                } else {
                                    viewModel.push(.deviceSessions)
                                }
                            }
                        }

                        if !targetDocument.document.provisionedDocumentSetupNeeded {
                            FloatingItemText(
                                text: "\(typeDisplayName) info",
                                showChevron: true,
                                secondary: "Pass details and certificate info",
                                image: { Image(systemName: "person.crop.rectangle").frame(width: 24, height: 24) }
                            ).onTapGesture {
                                viewModel.push(.documentInfoScreen(documentId: targetDocument.document.identifier))
                            }

                            FloatingItemText(
                                text: "View and manage activity",
                                showChevron: true,
                                secondary: "Logging is enabled",
                                image: { Image(systemName: "timer").frame(width: 24, height: 24) }
                            ).onTapGesture {
                                print("TODO: go to activity page")
                            }

                            FloatingItemText(
                                text: "In person-sharing and consent",
                                showChevron: true,
                                secondary: "Always ask before sharing",
                                image: { Image(systemName: "wave.3.right").frame(width: 24, height: 24) }
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
                            image: { Image(systemName: "trash").foregroundStyle(.red).frame(width: 24, height: 24) }
                        )
                    }.onTapGesture {
                        documentToDelete = targetDocument
                        showDeleteConfirmation = true
                    }
                }
            }
        }
        .padding()
        .opacity(isUnfocusing ? 0.0 : 1.0)
        .animation(.easeInOut(duration: 0.15), value: isUnfocusing)
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
                    handleBack()
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

    private func showToast(_ message: String) {
        toastTask?.cancel()
        withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
            toastMessage = message
        }
        toastTask = Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                withAnimation(.easeInOut(duration: 0.3)) {
                    toastMessage = nil
                }
            }
        }
    }

    private func handleTitleTap() {
        guard BuildConfig.shared.DEVELOPER_MODE_AVAILABLE else { return }
        if viewModel.settings.devMode {
            showToast("Developer mode is already enabled")
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
        } else {
            if devModeNumTimesPressed == 4 {
                showToast("Developer mode is now enabled. See the Settings screen for details")
                viewModel.settings.devMode = true
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                devModeNumTimesPressed = 0
            } else {
                let tapsRemaining = 4 - devModeNumTimesPressed
                if tapsRemaining > 1 {
                    showToast("Tap \(tapsRemaining) more times to enable developer mode")
                } else {
                    showToast("Tap 1 more time to enable developer mode")
                }
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                devModeNumTimesPressed += 1
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

private struct PresentQrCodePromptView: View {
    @State private var showPresentQrCode = false

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "qrcode")
                .font(.system(size: 18, weight: .semibold))
            Text("Present QR code to reader")
                .font(.footnote.weight(.semibold))
        }
        .foregroundStyle(.primary)
        .frame(height: 20)
        .offset(y: -38)
        .padding(.bottom, -30)
        .opacity(showPresentQrCode ? 1.0 : 0.0)
        .task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            withAnimation(.easeInOut(duration: 0.5)) {
                showPresentQrCode = true
            }
        }
    }
}

extension DocumentInfo {
    var isProximityPresentable: Bool {
        credentialInfos.contains {
            $0.credential is MdocCredential || $0.credential is KeyBoundSdJwtVcCredential
        }
    }
}

