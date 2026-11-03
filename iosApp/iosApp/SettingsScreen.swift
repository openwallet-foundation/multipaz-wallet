import SwiftUI
import Multipaz

struct SettingsScreen: View {
    @Environment(ViewModel.self) private var viewModel
    @Environment(\.dismiss) var dismiss
    
    @State private var isSigningIn = false
    @State private var isSigningOut = false
    @State private var showFaceMatcherDialog = false
    
    var body: some View {
        ZStack {
            ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    SegmentedItemList(title: "Account") {
                        if let signedInUser = viewModel.signedInUser {
                            if isSigningOut {
                                SegmentedItemContainer {
                                    HStack(spacing: 16) {
                                        Spacer()
                                        ProgressView()
                                        Text("Signing out of your Google Account…")
                                            .font(.body)
                                            .italic()
                                            .foregroundColor(.secondary)
                                        Spacer()
                                    }
                                }
                            } else {
                                SegmentedItemText(
                                    text: signedInUser.displayName ?? "Google user",
                                    showChevron: true,
                                    secondary: signedInUser.id,
                                    image: { signedInUser.profilePictureView(size: 40) }
                                ).onTapGesture {
                                    viewModel.push(.deviceSessions)
                                }
                                SegmentedItemText(
                                    text: "Use without a Google Account",
                                    image: { Image(systemName: "person.badge.minus").frame(width: 24, height: 24) }
                                ).onTapGesture {
                                    Task {
                                        isSigningOut = true
                                        defer { isSigningOut = false }
                                        do {
                                            try await viewModel.walletClient.signOut()
                                        } catch {
                                            print("signOut() failed: \(error)")
                                        }
                                    }
                                }
                            }
                        } else {
                            if isSigningIn {
                                SegmentedItemContainer {
                                    HStack(spacing: 16) {
                                        Spacer()
                                        ProgressView()
                                        Text("Signing in to your Google Account…")
                                            .font(.body)
                                            .italic()
                                            .foregroundColor(.secondary)
                                        Spacer()
                                    }
                                }
                            } else {
                                SegmentedItemText(
                                    text: "Sign in to your Google Account",
                                    image: { Image(systemName: "person.crop.circle").frame(width: 24, height: 24) }
                                ).onTapGesture {
                                    Task {
                                        isSigningIn = true
                                        defer { isSigningIn = false }
                                        do {
                                            let backendNonce = try await viewModel.walletClient.getNonce()
                                            let result = try await signInWithGoogle(backendNonce: backendNonce)
                                            try await viewModel.walletClient.signInWithGoogle(
                                                nonce: result.nonce,
                                                googleIdTokenString: result.idToken,
                                                signedInUser: WalletClientSignedInUser(
                                                    id: result.signInData.id,
                                                    displayName: result.signInData.displayName,
                                                    profilePicture: result.signInData.profilePicture?.toByteString(),
                                                    profilePictureUrl: nil
                                                ),
                                                walletBackendEncryptionKey: result.walletServerEncryptionKey.toByteString(),
                                                resetSharedData: false
                                            )
                                            try await viewModel.walletClient.refreshSharedData()
                                            await viewModel.syncSharedData()
                                        } catch {
                                            print("Signing in failed: \(error)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    let markdown = if viewModel.signedInUser != nil {
                        "Your passes sync across all devices signed in to this Google Account. Your data is encrypted before it reaches the application backend, and your encryption key is safely stored in your Google Drive, accessible only by this app. You can manage passes and device sessions at [\(BuildConfig.shared.BACKEND_URL)](\(BuildConfig.shared.BACKEND_URL))."
                    } else {
                        "Sign in with Google to sync your passes across all your devices. Your data is encrypted before it reaches the application backend, and your encryption key is safely stored in your Google Drive, accessible only by this app. You'll also be able manage passes and device sessions at [\(BuildConfig.shared.BACKEND_URL)](\(BuildConfig.shared.BACKEND_URL))."
                    }
                    VStack(alignment: .leading, spacing: 5) {
                        Image(systemName: "info.circle")
                        Text(LocalizedStringKey(markdown))
                            .font(.footnote)
                    }
                }
                
                SegmentedItemList {
                    SegmentedItemText(
                        text: "Trusted issuers",
                        showChevron: true,
                        image: { Image(systemName: "building.columns").frame(width: 24, height: 24) }
                    ).onTapGesture {
                        viewModel.push(.trustedIssuers)
                    }
                    SegmentedItemText(
                        text: "Trusted verifiers",
                        showChevron: true,
                        image: { Image(systemName: "building.2").frame(width: 24, height: 24) }
                    ).onTapGesture {
                        viewModel.push(.trustedVerifiers)
                    }
                    // Note: "External NFC readers" is omitted on iOS because USB CCID devices are not supported.
                    SegmentedItemText(
                        text: "Activity logging",
                        showChevron: true,
                        image: { Image(systemName: "timer").frame(width: 24, height: 24) }
                    ).onTapGesture {
                    }
                    SegmentedItemText(
                        text: "Pre-consent settings",
                        showChevron: true,
                        image: { Image(systemName: "lock").frame(width: 24, height: 24) }
                    ).onTapGesture {
                    }
                    // TODO: Move presentation face matcher selector to Developer Settings once iOS gains Developer Settings capability.
                    SegmentedItemText(
                        text: "Presentation face matcher",
                        showChevron: true,
                        secondary: viewModel.settings.presentmentFaceMatcher,
                        image: { Image(systemName: "faceid").frame(width: 24, height: 24) }
                    ).onTapGesture {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            showFaceMatcherDialog = true
                        }
                    }
                    if viewModel.settings.devMode {
                        SegmentedItemText(
                            text: "Developer settings",
                            showChevron: true,
                            image: { Image(systemName: "flask").frame(width: 24, height: 24) }
                        ).onTapGesture {
                        }
                    }
                    SegmentedItemText(
                        text: "About \(BuildConfig.shared.APP_NAME)",
                        showChevron: true,
                        image: { Image(systemName: "info.circle").frame(width: 24, height: 24) }
                    ).onTapGesture {
                    }
                }
            }
            .padding()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(UIColor.systemGroupedBackground))

        if showFaceMatcherDialog {
            let matcherChoices = (viewModel.faceMatcherRepository?.all.map { $0.displayName } ?? []) + ["None"]
            SelectFaceMatcherDialog(
                choices: matcherChoices,
                selected: Binding(
                    get: { viewModel.settings.presentmentFaceMatcher },
                    set: { viewModel.settings.presentmentFaceMatcher = $0 }
                ),
                onDismissed: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        showFaceMatcherDialog = false
                    }
                }
            )
            .transition(.opacity.combined(with: .scale(scale: 0.95)))
        }
    }
    .navigationTitle("Settings")
    .navigationBarTitleDisplayMode(.inline)
}
}
