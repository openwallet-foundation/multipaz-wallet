import SwiftUI
import Multipaz

struct TrustEntryVicalEntryScreen: View {
    @Environment(ViewModel.self) private var viewModel
    let trustManagerId: String
    let vicalTrustEntryId: String
    let certNum: Int
    
    private var vicalCertInfo: VicalCertificateInfo? {
        guard let model = viewModel.getTrustManagerModel(identifier: trustManagerId),
              let info = model.entries?.first(where: { $0.id == vicalTrustEntryId }),
              let vical = info.signedVical?.vical,
              certNum < vical.certificateInfos.count else {
            return nil
        }
        return vical.certificateInfos[certNum]
    }
    
    private var displayName: String {
        guard let certInfo = vicalCertInfo else { return "Certificate" }
        if let cn = certInfo.certificate.subject.components["2.5.4.3"]?.value {
            return cn
        }
        return certInfo.certificate.subject.name
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let certInfo = vicalCertInfo {
                    VStack(alignment: .center, spacing: 12) {
                        DeterministicAvatarView(name: displayName, size: 80)
                            .frame(maxWidth: .infinity)
                        
                        Text(displayName)
                            .font(.title2)
                            .bold()
                            .multilineTextAlignment(.center)
                    }
                    .padding(.vertical, 8)
                    
                    FloatingItemList(title: "VICAL entry") {
                        FloatingItemHeadingAndText(
                            heading: "Document types",
                            text: certInfo.docTypes.joined(separator: "\n")
                        )
                        if !certInfo.extensions.isEmpty {
                            let extensionsStr = certInfo.extensions.map { key, value in
                                "\(key): \(Cbor.shared.toDiagnostics(item: value, options: [.prettyPrint]))"
                            }.joined(separator: "\n")
                            FloatingItemHeadingAndText(
                                heading: "Extensions",
                                text: extensionsStr
                            )
                        }
                    }
                    
                    X509CertViewer(certificate: certInfo.certificate)
                } else {
                    Text("Certificate details not found")
                        .foregroundColor(.secondary)
                        .padding()
                }
            }
            .padding()
        }
        .navigationTitle("VICAL entry")
        .navigationBarTitleDisplayMode(.inline)
    }
}
