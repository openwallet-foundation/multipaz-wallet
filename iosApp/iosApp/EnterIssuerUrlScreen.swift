import SwiftUI

struct EnterIssuerUrlScreen: View {
    @Environment(ViewModel.self) private var viewModel
    
    @State private var issuingServerUrl: String = ""
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Enter the URL of an OpenID4VCI server to inquire about the credentials it supports and start the provisioning process.")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .padding(.horizontal)
                
                TextField("Issuer server URL", text: $issuingServerUrl)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .padding(.horizontal)
                
                Button("Reset to default") {
                    issuingServerUrl = "https://issuer.multipaz.org/issuer"
                    viewModel.settings.provisioningServerUrl = issuingServerUrl
                }
                .font(.subheadline)
                .padding(.horizontal)
                
                Spacer()
                    .frame(height: 8)
                
                Button {
                    let trimmed = issuingServerUrl.trimmingCharacters(in: .whitespacesAndNewlines)
                    viewModel.settings.provisioningServerUrl = trimmed
                    viewModel.push(.provisioning(issuerUrl: trimmed, credentialId: nil))
                } label: {
                    Text("Connect")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .disabled(issuingServerUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .padding(.horizontal)
            }
            .padding(.vertical)
        }
        .navigationTitle("Enter issuer URL")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            issuingServerUrl = viewModel.settings.provisioningServerUrl
        }
    }
}
