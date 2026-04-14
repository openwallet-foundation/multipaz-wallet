import Foundation

struct SignInWithGoogleResult: Codable, Equatable {
    let nonce: String
    let idToken: String
    let signInData: SignInWithGoogleUserData
    let walletServerEncryptionKey: Data
}
