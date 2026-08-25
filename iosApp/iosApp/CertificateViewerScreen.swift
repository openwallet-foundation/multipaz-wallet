import SwiftUI
import Multipaz
import UniformTypeIdentifiers

struct CertificateViewerScreen: View {
    let certificates: [X509Cert]
    @State private var selectedIndex = 0
    @State private var showCopiedAlert = false
    
    init(certChain: X509CertChain) {
        self.certificates = certChain.certificates
    }
    
    init(certificate: X509Cert) {
        self.certificates = [certificate]
    }
    
    var body: some View {
        VStack(spacing: 0) {
            if certificates.count > 1 {
                Picker("Certificate", selection: $selectedIndex) {
                    ForEach(0..<certificates.count, id: \.self) { index in
                        Text("Cert \(index + 1)\(index == 0 ? " (Leaf)" : index == certificates.count - 1 ? " (Root)" : "")")
                            .tag(index)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.top, 8)
            }
            
            if !certificates.isEmpty {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        X509CertViewer(certificate: certificates[selectedIndex])
                        
                        FloatingItemList(title: "PEM") {
                            FloatingItemText(
                                text: "Copy certificate PEM",
                                showChevron: false,
                                image: { Image(systemName: "doc.on.doc").frame(width: 24, height: 24) }
                            ).onTapGesture {
                                UIPasteboard.general.string = certificates[selectedIndex].toPem()
                                showCopiedAlert = true
                            }
                        }
                    }
                    .padding()
                }
            } else {
                Text("No certificates to display")
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
        .navigationTitle(certificates.count > 1 ? "Certificate Chain" : "Certificate")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Copied to clipboard", isPresented: $showCopiedAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Certificate PEM copied to clipboard.")
        }
    }
}
