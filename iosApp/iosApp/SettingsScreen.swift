import SwiftUI
import Multipaz

struct SettingsScreen: View {
    @Environment(ViewModel.self) private var viewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        ScrollView {
            VStack {
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
                                            profilePicture: result.signInData.profilePicture?.toByteString()
                                        ),
                                        walletBackendEncryptionKey: result.walletServerEncryptionKey.toByteString(),
                                        resetSharedData: false
                                    )
                                } catch {
                                    print("Signing in failed: \(error)")
                                }
                            }
                        }
                    }
                }
                let markdown = if viewModel.signedInUser != nil {
                    "Passes that sync are available from any device signed in to this Google " +
                            "account. Data is end-to-end encrypted. " +
                            "[Learn more](https://wallet.multipaz.org/pass-syncing.html)"
                } else {
                    "Sign in to your Google account to make passes that sync available to any " +
                            "device signed into the account. Data is end-to-end encrypted. " +
                            "[Learn more](https://wallet.multipaz.org/pass-syncing.html)"
                }
                VStack(alignment: .leading, spacing: 5) {
                    Image(systemName: "info.circle")
                    Text(LocalizedStringKey(markdown))
                        .font(.footnote)
                }
                
                FloatingItemList(title: "Issuers and readers") {
                    FloatingItemText(
                        text: "Trusted issuing authorities",
                        image: { Image(systemName: "building.columns") }
                    ).onTapGesture {
                    }
                    FloatingItemText(
                        text: "Trusted credential readers",
                        image: { Image(systemName: "building.2") }
                    ).onTapGesture {
                    }
                }

                FloatingItemList(title: "Other") {
                    FloatingItemText(
                        text: "Developer settings",
                        image: { Image(systemName: "flask") }
                    ).onTapGesture {
                    }
                    FloatingItemText(
                        text: "About \(BuildConfig.shared.APP_NAME)",
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

