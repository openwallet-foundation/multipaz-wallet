import SwiftUI
import Foundation

struct SignInWithGoogleUserData: Codable, Equatable {
    let id: String
    let givenName: String?
    let familyName: String?
    let displayName: String?
    let profilePicture: Data?
}

extension SignInWithGoogleUserData {
    
    @ViewBuilder
    func profilePictureView(size: CGFloat = 40) -> some View {
        if let profilePicture = profilePicture, let uiImage = UIImage(data: profilePicture) {
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFill()
                .frame(width: size, height: size)
                .clipShape(Circle())
        } else {
            // Fallback to initials
            let name = displayName ?? "G"
            
            let initials = name.split(separator: " ")
                .compactMap { $0.first?.uppercased() }
                .prefix(2)
                .joined()
            
            // Generate a deterministic color
            let color = generateColor(from: name)
            
            Circle()
                .fill(color)
                .frame(width: size, height: size)
                .overlay(
                    Text(initials)
                        .font(.system(size: size / 2))
                        .foregroundColor(.white)
                )
        }
    }
    
    private func generateColor(from string: String) -> Color {
        // Using a standard djb2 hash to ensure colors stay consistent across app launches
        var hash: Int = 5381
        for char in string.utf8 {
            hash = ((hash << 5) &+ hash) &+ Int(char)
        }
        
        let red = Double((hash & 0xFF0000) >> 16) / 255.0
        let green = Double((hash & 0x00FF00) >> 8) / 255.0
        let blue = Double(hash & 0x0000FF) / 255.0
        
        return Color(red: red, green: green, blue: blue)
    }
}

