import SwiftUI
import GoogleSignIn
import GoogleSignInSwift

/*
struct GoogleLoginView: View {
    @State private var isSigningIn = false
    
    private let getNonceFn: @Sendable () async -> String
    private let onUserLoggedIn: (SignInWithGoogleResult) -> Void
    
    init(
        getNonceFn: @escaping () async -> String,
        onUserLoggedIn: @escaping (SignInWithGoogleResult) -> Void
    ) {
        self.getNonceFn = getNonceFn
        self.onUserLoggedIn = onUserLoggedIn
    }
    
    var body: some View {
        GoogleSignInButton(style: .icon) {
            // Wrap the async call in a Task to bridge from the sync button action
            Task {
                await handleSignIn()
            }
        }
        .disabled(isSigningIn)
    }

    @MainActor
    func handleSignIn() async {
        isSigningIn = true
        defer { isSigningIn = false }

        let backendNonce = await getNonceFn()

        // Securely access the root view controller from the main thread
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootViewController = windowScene.keyWindow?.rootViewController else {
            return
        }

        let driveScope = "https://www.googleapis.com/auth/drive.appdata"

        do {
            // 1. Await the Google Sign-In result directly
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: rootViewController,
                hint: nil,
                additionalScopes: [driveScope],
                nonce: backendNonce
            )

            guard let idToken = result.user.idToken?.tokenString else {
                return
            }
            
            print("idToken: \(idToken)")

            // 2. Extract the Access Token for Drive authorization
            let accessToken = result.user.accessToken.tokenString

            // 3. Await the Drive network calls
            let driveManager = GoogleDriveManager(accessToken: accessToken)
            let encryptionKey = try await driveManager.retrieveOrCreateEncryptionKey()

            // 4. Success! You have the ID Token for your backend, and the 32-byte key
            print("TODO: send ID Token to server: \(idToken)")
            print("Key retrieved successfully. Byte count: \(encryptionKey.count)")
            
            // 5. Build the user data object
            let user = result.user
            let profile = user.profile
            
            // Fetch profile picture data asynchronously if a URL is available
            var profilePicData: Data? = nil
            if let imageURL = profile?.imageURL(withDimension: 120) {
                if let (data, _) = try? await URLSession.shared.data(from: imageURL) {
                    profilePicData = data
                }
            }

            let userData = SignInWithGoogleUserData(
                id: user.userID ?? UUID().uuidString,
                givenName: profile?.givenName,
                familyName: profile?.familyName,
                displayName: profile?.name,
                profilePicture: profilePicData
            )
            
            // 6. Trigger the callback
            onUserLoggedIn(SignInWithGoogleResult(
                nonce: backendNonce,
                idToken: idToken,
                signInData: userData,
                walletServerEncryptionKey: encryptionKey
            ))
        } catch {
            print("Sign in or Drive operation failed: \(error)")
            // If the user declined the Drive scope but accepted the sign-in,
            // or if the network failed, sign them out for a clean slate.
            GIDSignIn.sharedInstance.signOut()
        }
    }
}
*/

enum SignInError: Error {
    case signInFailed
}

@MainActor
func signInWithGoogle(
    backendNonce: String
) async throws -> SignInWithGoogleResult {
    // Securely access the root view controller from the main thread
    guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
          let rootViewController = windowScene.keyWindow?.rootViewController else {
        throw SignInError.signInFailed
    }

    let driveScope = "https://www.googleapis.com/auth/drive.appdata"

    do {
        // 1. Await the Google Sign-In result directly
        let result = try await GIDSignIn.sharedInstance.signIn(
            withPresenting: rootViewController,
            hint: nil,
            additionalScopes: [driveScope],
            nonce: backendNonce
        )

        guard let idToken = result.user.idToken?.tokenString else {
            throw SignInError.signInFailed
        }
        
        print("idToken: \(idToken)")

        // 2. Extract the Access Token for Drive authorization
        let accessToken = result.user.accessToken.tokenString

        // 3. Await the Drive network calls
        let driveManager = GoogleDriveManager(accessToken: accessToken)
        let encryptionKey = try await driveManager.retrieveOrCreateEncryptionKey()

        // 4. Success! You have the ID Token for your backend, and the 32-byte key
        print("TODO: send ID Token to server: \(idToken)")
        print("Key retrieved successfully. Byte count: \(encryptionKey.count)")
        
        // 5. Build the user data object
        let user = result.user
        let profile = user.profile
        
        // Fetch profile picture data asynchronously if a URL is available
        var profilePicData: Data? = nil
        if let imageURL = profile?.imageURL(withDimension: 120) {
            if let (data, _) = try? await URLSession.shared.data(from: imageURL) {
                profilePicData = data
            }
        }

        let userData = SignInWithGoogleUserData(
            id: user.userID ?? UUID().uuidString,
            givenName: profile?.givenName,
            familyName: profile?.familyName,
            displayName: profile?.name,
            profilePicture: profilePicData
        )
        
        // 6. Trigger the callback
        return SignInWithGoogleResult(
            nonce: backendNonce,
            idToken: idToken,
            signInData: userData,
            walletServerEncryptionKey: encryptionKey
        )
    } catch {
        print("Sign in or Drive operation failed: \(error)")
        // If the user declined the Drive scope but accepted the sign-in,
        // or if the network failed, sign them out for a clean slate.
        GIDSignIn.sharedInstance.signOut()
        throw SignInError.signInFailed
    }
}
