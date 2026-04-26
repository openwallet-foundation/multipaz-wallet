import SwiftUI
import Multipaz

struct StartScreen: View {
    @Environment(ViewModel.self) private var viewModel
    
    var body: some View {
        ScrollView {
            VStack(alignment: .center, spacing: 20) {
                FloatingItemList(title: "Wallet objects") {
                    if viewModel.walletObjects == nil {
                        FloatingItemCenteredText(text: "Not signed in")
                    } else if viewModel.walletObjects!.isEmpty {
                        FloatingItemCenteredText(text: "No wallet objects available")
                    } else {
                        ForEach(viewModel.walletObjects!, id: \.identifier) { walletObject in
                            let creationTimeStr = walletObject.creationTime.toNSDate().formatted()
                            FloatingItemText(
                                text: walletObject.name,
                                secondary: "Count \(walletObject.count), created at \(creationTimeStr)",
                                trailingContent: {
                                    HStack(spacing: 10) {
                                        Button(
                                            action: { Task {
                                                do {
                                                    try await viewModel.walletClient.updateWalletObject(
                                                        walletObject: WalletObject(
                                                            identifier: walletObject.identifier,
                                                            creationTime: walletObject.creationTime,
                                                            name: walletObject.name,
                                                            count: walletObject.count + 1
                                                        )
                                                    )
                                                } catch {
                                                    print("Error updating wallet object: \(error)")
                                                }
                                            }}
                                        ) {
                                            Image(systemName: "plus.circle.fill")
                                        }
                                        
                                        Button(
                                            action: { Task {
                                                do {
                                                    try await viewModel.walletClient.removeWalletObject(walletObject: walletObject)
                                                } catch {
                                                    print("Error removing wallet object: \(error)")
                                                }
                                            }}
                                        ) {
                                            Image(systemName: "trash")
                                                .foregroundColor(.red)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                
                Button(
                    action: { Task {
                        do {
                            try await viewModel.walletClient.refresh()
                        } catch {
                            print("Error refreshing: \(error)")
                        }
                    } }
                ) {
                    Text("Refresh")
                }.buttonStyle(.borderedProminent).buttonBorderShape(.capsule)

                Button(
                    action: { Task {
                        do {
                            try await viewModel.walletClient.addWalletObject(
                                walletObject: WalletObject(
                                    identifier: "",
                                    creationTime: KotlinInstant.companion.DISTANT_PAST,
                                    name: "New wallet object (from iOS)",
                                    count: 0
                                )
                            )
                        } catch {
                            print("addWalletObject() failed: \(error)")
                        }
                    } }
                ) {
                    Text("Add wallet object")
                }.buttonStyle(.borderedProminent).buttonBorderShape(.capsule)

            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .navigationTitle(BuildConfig.shared.APP_NAME)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                avatarButton
            }
        }
    }
    
    @ViewBuilder
    private var avatarButton: some View {
        if viewModel.signedInUser != nil {
            // Button 1: Signed-in state
            Button {
                viewModel.path.append(Destination.settingsScreen)
            } label: {
                viewModel.signedInUser!.profilePictureView(size: 32.0)
            }
        } else {
            // Button 2: Logged-out state
            Button {
                viewModel.path.append(Destination.settingsScreen)
            } label: {
                Image(systemName: "person.crop.circle")
                    .resizable()
                    .frame(width: 32.0, height: 32.0)
            }
        }
    }
}
