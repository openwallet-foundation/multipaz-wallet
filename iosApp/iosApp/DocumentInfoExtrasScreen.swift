import SwiftUI
import Multipaz

struct DocumentInfoExtrasScreen: View {
    @Environment(ViewModel.self) private var viewModel
    let documentId: String
    
    var body: some View {
        let documentInfo = viewModel.documentModel.documentInfos.first {
            $0.document.identifier == documentId
        }
        
        let credentialsByDomain = groupCredentials(documentInfo: documentInfo)
        let sortedDomains = credentialsByDomain.keys.sorted(by: >)
        
        VStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("This is a low-level view of the credentials backing this pass, organized by domain.")
                        .font(.footnote)
                    if sortedDomains.isEmpty {
                        Text("No credentials found for this document.")
                            .foregroundColor(.gray)
                            .padding(.top, 10)
                    } else {
                        ForEach(sortedDomains, id: \.self) { domain in
                            let creds = credentialsByDomain[domain] ?? []
                            FloatingItemList(title: domain) {
                                ForEach(0..<creds.count, id: \.self) { index in
                                    let credential = creds[index]
                                    let isCertified = credential.isCertified
                                    
                                    let heading = isCertified ?
                                        "\(credential.credentialType) with use-count \(credential.usageCount)" :
                                        credential.credentialType
                                    
                                    let text = isCertified ?
                                        "Valid from \(formatInstant(credential.validFrom)) until \(formatInstant(credential.validUntil))" :
                                        "Pending certification"
                                    
                                    FloatingItemHeadingAndText(
                                        heading: heading,
                                        text: text,
                                        showChevron: true
                                    )
                                    .onTapGesture {
                                        viewModel.push(.credentialInfoScreen(documentId: documentId, credentialId: credential.identifier))
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer()
                }
                .padding()
            }
        }
        .navigationTitle("Credentials")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func groupCredentials(documentInfo: DocumentInfo?) -> [String: [Credential]] {
        guard let documentInfo = documentInfo else { return [:] }
        let credentials = documentInfo.credentialInfos.map { $0.credential }
        return Dictionary(grouping: credentials, by: { $0.domain })
    }
    
    private func formatInstant(_ instant: KotlinInstant) -> String {
        let date = Date(timeIntervalSince1970: Double(instant.epochSeconds))
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}
