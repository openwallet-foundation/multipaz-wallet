import SwiftUI
import Multipaz

struct SettingsScreen: View {
    @Environment(ViewModel.self) private var viewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                FloatingItemList(title: "Account") {
                    if let signedInUser = viewModel.signedInUser {
                        FloatingItemText(
                            text: signedInUser.displayName ?? "Google user",
                            secondary: signedInUser.id,
                            image: { signedInUser.profilePictureView(size: 40) }
                        )
                        FloatingItemText(
                            text: "Use without a Google account",
                            image: { Image(systemName: "person.badge.minus") }
                        ).onTapGesture {
                            Task {
                                do {
                                    try await viewModel.walletClient.signOut()
                                } catch {
                                    print("signOut() failed: \(error)")
                                }
                            }
                        }
                    } else {
                        FloatingItemText(
                            text: "Sign in to your Google account",
                            image: { Image(systemName: "person.badge.plus") }
                        ).onTapGesture {
                            Task {
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
                let markdown = if viewModel.signedInUser != nil {
                    "Your passes sync across all devices signed in to this Google account. Your data is encrypted before it reaches the application backend, and your encryption key is safely stored in your Google Drive, accessible only by this app."
                } else {
                    "Sign in with Google to sync your passes across all your devices. Your data is encrypted before it reaches the application backend, and your encryption key is safely stored in your Google Drive, accessible only by this app."
                }
                VStack(alignment: .leading, spacing: 5) {
                    Image(systemName: "info.circle")
                    Text(LocalizedStringKey(markdown))
                        .font(.footnote)
                }
                
                FloatingItemList {
                    FloatingItemText(
                        text: "Trusted issuing authorities",
                        showChevron: true,
                        image: { Image(systemName: "building.columns") }
                    ).onTapGesture {
                    }
                    FloatingItemText(
                        text: "Trusted verifiers",
                        showChevron: true,
                        image: { Image(systemName: "building.2") }
                    ).onTapGesture {
                    }
                    FloatingItemText(
                        text: "Activity logging",
                        showChevron: true,
                        image: { Image(systemName: "timer") }
                    ).onTapGesture {
                    }
                    FloatingItemText(
                        text: "Developer settings",
                        showChevron: true,
                        image: { Image(systemName: "flask") }
                    ).onTapGesture {
                    }
                    FloatingItemText(
                        text: "About \(BuildConfig.shared.APP_NAME)",
                        showChevron: true,
                        image: { Image(systemName: "info.circle") }
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

