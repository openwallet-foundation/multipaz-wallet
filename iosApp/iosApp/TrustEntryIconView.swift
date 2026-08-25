import SwiftUI
import Multipaz

struct TrustEntryIconView: View {
    let entryInfo: TrustEntryInfo
    var size: CGFloat = 40
    
    var body: some View {
        if let iconData = entryInfo.entry.metadata.displayIcon?.toNSData(), let uiImage = UIImage(data: iconData) {
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: size * 0.15))
        } else if let iconUrl = entryInfo.entry.metadata.displayIconUrl, !iconUrl.isEmpty, let url = URL(string: iconUrl) {
            AsyncImage(url: url) { image in
                image.resizable()
                    .scaledToFit()
            } placeholder: {
                ProgressView()
            }
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.15))
        } else {
            DeterministicAvatarView(name: entryInfo.displayName, size: size)
        }
    }
}

struct DeterministicAvatarView: View {
    let name: String
    var size: CGFloat = 40
    
    var body: some View {
        let initials = name.split(separator: " ")
            .compactMap { $0.first?.uppercased() }
            .prefix(2)
            .joined()
        
        let color = generateColor(from: name)
        
        Circle()
            .fill(color)
            .frame(width: size, height: size)
            .overlay(
                Text(initials.isEmpty ? "?" : initials)
                    .font(.system(size: size * 0.4, weight: .semibold))
                    .foregroundColor(.white)
            )
    }
    
    private func generateColor(from string: String) -> Color {
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
