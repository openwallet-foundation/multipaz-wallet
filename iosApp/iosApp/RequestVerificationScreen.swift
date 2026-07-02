import SwiftUI

struct RequestVerificationScreen: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.shield")
                .font(.system(size: 64))
                .foregroundColor(.secondary)
            Text("Verification")
                .font(.title2)
                .bold()
            Text("Verification is not yet implemented on iOS.")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Verify")
        .navigationBarTitleDisplayMode(.inline)
    }
}
