import SwiftUI
import Multipaz

extension Session: @retroactive Identifiable {
    public var id: String { clientId }
}

struct DeviceSessionsScreen: View {
    @Environment(ViewModel.self) private var viewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var sessions: [Session]? = nil
    @State private var currentClientId: String? = nil
    @State private var isLoading = true
    @State private var errorMessage: String? = nil
    @State private var sessionToSignOut: Session? = nil
    @State private var showSignOutDialog = false
    
    private var sortedSessions: [Session] {
        guard let list = sessions else { return [] }
        return list.sorted { s1, s2 in
            let isS1Current = (currentClientId != nil && s1.clientId == currentClientId)
            let isS2Current = (currentClientId != nil && s2.clientId == currentClientId)
            if isS1Current != isS2Current {
                return isS1Current
            }
            return s1.lastSeenMillis > s2.lastSeenMillis
        }
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerNote
                sessionsList
                
                if let errorMessage = errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Device sessions")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    Task {
                        await fetchSessions()
                    }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(isLoading)
            }
        }
        .task {
            await fetchSessions()
        }
        .onChange(of: viewModel.signedInUser) { _, newValue in
            if newValue == nil {
                dismiss()
            }
        }
        .alert("Sign out session?", isPresented: $showSignOutDialog, presenting: sessionToSignOut) { targetSession in
            Button("Sign out", role: .destructive) {
                Task {
                    do {
                        try await viewModel.walletClient.signOutSession(clientId: targetSession.clientId)
                        await fetchSessions()
                    } catch {
                        self.errorMessage = error.localizedDescription
                    }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: { targetSession in
            let targetName = targetSession.clientDevice ?? targetSession.clientPlatform ?? fallbackDeviceName(for: targetSession)
            Text("This will sign out \(targetName) from your account and it will lose access to synced wallet data.")
        }
    }
    
    @ViewBuilder
    private var headerNote: some View {
        let accountIdentifier = viewModel.signedInUser?.id ?? "your account"
        Text("Devices signed in to \(accountIdentifier) have access to your synced wallet data. You can review active sessions and sign out of any device at any time.")
            .font(.footnote)
            .padding(.horizontal)
    }
    
    @ViewBuilder
    private var sessionsList: some View {
        FloatingItemList {
            if isLoading && sessions == nil {
                FloatingItemContainer {
                    HStack(spacing: 16) {
                        Spacer()
                        ProgressView()
                        Text("Loading device sessions…")
                            .font(.body)
                            .italic()
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    .padding(.vertical, 8)
                }
            } else if let sessionList = sessions {
                if sessionList.isEmpty {
                    FloatingItemCenteredText(text: "No active device sessions found.")
                } else {
                    ForEach(sortedSessions) { session in
                        sessionRow(session: session)
                    }
                }
            }
        }
        .padding(.horizontal)
    }
    
    @ViewBuilder
    private func sessionRow(session: Session) -> some View {
        let isCurrentDevice = (currentClientId != nil && session.clientId == currentClientId)
        let deviceName = session.clientDevice ?? fallbackDeviceName(for: session)
        let iconName = deviceIcon(for: session)
        let secondary = secondaryText(for: session, isCurrentDevice: isCurrentDevice)
        
        FloatingItemText(
            text: deviceName,
            secondary: secondary,
            image: {
                Image(systemName: iconName)
                    .font(.body)
                    .frame(width: 24, height: 24)
            },
            trailingContent: {
                if !isCurrentDevice {
                    Button {
                        sessionToSignOut = session
                        showSignOutDialog = true
                    } label: {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                            .foregroundColor(.red)
                            .frame(width: 24, height: 24)
                    }
                    .buttonStyle(.borderless)
                }
            }
        )
    }
    
    private func fallbackDeviceName(for session: Session) -> String {
        switch session.clientType {
        case .web: return "Web client"
        case .android: return "Android device"
        case .ios: return "iOS device"
        default: return "Unknown device"
        }
    }
    
    private func deviceIcon(for session: Session) -> String {
        switch session.clientType {
        case .web: return "laptopcomputer"
        case .android, .ios: return "iphone"
        default: return "iphone"
        }
    }
    
    private func lastSeenText(for session: Session) -> String {
        let date = Date(timeIntervalSince1970: Double(session.lastSeenMillis) / 1000.0)
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: Date())
    }
    
    private func secondaryText(for session: Session, isCurrentDevice: Bool) -> String {
        var parts: [String] = []
        if isCurrentDevice {
            parts.append("This device")
        }
        if let platform = session.clientPlatform, !platform.trimmingCharacters(in: .whitespaces).isEmpty {
            parts.append(platform)
        }
        if let location = session.location, !location.trimmingCharacters(in: .whitespaces).isEmpty {
            parts.append(location)
        }
        if !isCurrentDevice {
            parts.append(lastSeenText(for: session))
        }
        return parts.joined(separator: " • ")
    }
    
    private func fetchSessions() async {
        isLoading = true
        errorMessage = nil
        do {
            if currentClientId == nil {
                do {
                    currentClientId = try await viewModel.walletClient.getClientId()
                } catch {
                    print("Failed to get current clientId: \(error)")
                }
            }
            let locale = Locale.current.identifier
            sessions = try await viewModel.walletClient.getSessions(lang: locale)
        } catch {
            self.errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
