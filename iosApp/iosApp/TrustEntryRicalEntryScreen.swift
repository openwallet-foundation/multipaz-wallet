import SwiftUI
import Multipaz

struct TrustEntryRicalEntryScreen: View {
    @Environment(ViewModel.self) private var viewModel
    let trustManagerId: String
    let ricalTrustEntryId: String
    let certNum: Int
    
    private var ricalCertInfo: RicalCertificateInfo? {
        guard let model = viewModel.getTrustManagerModel(identifier: trustManagerId),
              let info = model.entries?.first(where: { $0.id == ricalTrustEntryId }),
              let rical = info.signedRical?.rical,
              certNum < rical.certificateInfos.count else {
            return nil
        }
        return rical.certificateInfos[certNum]
    }
    
    private var displayName: String {
        guard let certInfo = ricalCertInfo else { return "Certificate" }
        if let name = certInfo.name, !name.isEmpty {
            return name
        }
        if let cn = certInfo.certificate.subject.components["2.5.4.3"]?.value {
            return cn
        }
        return certInfo.certificate.subject.name
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let certInfo = ricalCertInfo {
                    VStack(alignment: .center, spacing: 12) {
                        DeterministicAvatarView(name: displayName, size: 80)
                            .frame(maxWidth: .infinity)
                        
                        Text(displayName)
                            .font(.title2)
                            .bold()
                            .multilineTextAlignment(.center)
                    }
                    .padding(.vertical, 8)
                    
                    let hasEntryDetails = certInfo.name != nil ||
                        certInfo.type != nil ||
                        certInfo.issuingCountry != nil ||
                        certInfo.stateOrProvinceName != nil ||
                        !certInfo.extensions.isEmpty
                    if hasEntryDetails {
                        SegmentedItemList(title: "RICAL entry") {
                            if let name = certInfo.name {
                                SegmentedItemHeadingAndText(
                                    heading: "Name",
                                    text: name
                                )
                            }
                            if let type = certInfo.type {
                                SegmentedItemHeadingAndText(
                                    heading: "Type",
                                    text: type
                                )
                            }
                            if let issuingCountry = certInfo.issuingCountry {
                                SegmentedItemHeadingAndText(
                                    heading: "Issuing country",
                                    text: issuingCountry
                                )
                            }
                            if let stateOrProvince = certInfo.stateOrProvinceName {
                                SegmentedItemHeadingAndText(
                                    heading: "State or province",
                                    text: stateOrProvince
                                )
                            }
                            if !certInfo.extensions.isEmpty {
                                let extensionsStr = certInfo.extensions.map { key, value in
                                    "\(key): \(Cbor.shared.toDiagnostics(item: value, options: [.prettyPrint]))"
                                }.joined(separator: "\n")
                                SegmentedItemHeadingAndText(
                                    heading: "Extensions",
                                    text: extensionsStr
                                )
                            }
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
        .background(Color(UIColor.systemGroupedBackground))
        .navigationTitle("RICAL entry")
        .navigationBarTitleDisplayMode(.inline)
    }
}
