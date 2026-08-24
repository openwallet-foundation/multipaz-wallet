import SwiftUI
import Multipaz

struct SettingsScreen: View {
    @Environment(ViewModel.self) private var viewModel
    @Environment(\.dismiss) var dismiss
    
    @State private var isSigningIn = false
    @State private var isSigningOut = false
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                FloatingItemList(title: "Account") {
                    if let signedInUser = viewModel.signedInUser {
                        if isSigningOut {
                            FloatingItemContainer {
                                HStack(spacing: 16) {
                                    Spacer()
                                    ProgressView()
                                    Text("Signing out of your Google Account…")
                                        .font(.body)
                                        .italic()
                                        .foregroundColor(.secondary)
                                    Spacer()
                                }
                                .padding(.vertical, 8)
                            }
                        } else {
                            FloatingItemText(
                                text: signedInUser.displayName ?? "Google user",
                                showChevron: true,
                                secondary: signedInUser.id,
                                image: { signedInUser.profilePictureView(size: 40) }
                            ).onTapGesture {
                                viewModel.push(.deviceSessions)
                            }
                            FloatingItemText(
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
                            FloatingItemContainer {
                                HStack(spacing: 16) {
                                    Spacer()
                                    ProgressView()
                                    Text("Signing in to your Google Account…")
                                        .font(.body)
                                        .italic()
                                        .foregroundColor(.secondary)
                                    Spacer()
                                }
                                .padding(.vertical, 8)
                            }
                        } else {
                            FloatingItemText(
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
                
                FloatingItemList {
                    FloatingItemText(
                        text: "Trusted issuers",
                        showChevron: true,
                        image: { Image(systemName: "building.columns").frame(width: 24, height: 24) }
                    ).onTapGesture {
                    }
                    FloatingItemText(
                        text: "Trusted verifiers",
                        showChevron: true,
                        image: { Image(systemName: "building.2").frame(width: 24, height: 24) }
                    ).onTapGesture {
                    }
                    // Note: "External NFC readers" is omitted on iOS because USB CCID devices are not supported.
                    FloatingItemText(
                        text: "Activity logging",
                        showChevron: true,
                        image: { Image(systemName: "timer").frame(width: 24, height: 24) }
                    ).onTapGesture {
                    }
                    FloatingItemText(
                        text: "Pre-consent settings",
                        showChevron: true,
                        image: { Image(systemName: "lock").frame(width: 24, height: 24) }
                    ).onTapGesture {
                    }
                    if viewModel.settings.devMode {
                        FloatingItemText(
                            text: "Developer settings",
                            showChevron: true,
                            image: { Image(systemName: "flask").frame(width: 24, height: 24) }
                        ).onTapGesture {
                        }
                    }
                    FloatingItemText(
                        text: "About \(BuildConfig.shared.APP_NAME)",
                        showChevron: true,
                        image: { Image(systemName: "info.circle").frame(width: 24, height: 24) }
                    ).onTapGesture {
                    }
                }
            }
            .padding([.leading, .trailing])
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
    }
}
