import SwiftUI
import Multipaz

struct CredentialInfoScreen: View {
    @Environment(ViewModel.self) private var viewModel
    let documentId: String
    let credentialId: String
    
    // State to show alerts for certificate details
    @State private var alertTitle = ""
    @State private var alertMessage = ""
    @State private var showAlert = false
    
    var body: some View {
        let documentInfo = viewModel.documentModel.documentInfos.first {
            $0.document.identifier == documentId
        }
        let credentialInfo = documentInfo?.credentialInfos.first {
            $0.credential.identifier == credentialId
        }
        
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("This screen contains low-level technical details about a specific credential")
                    .font(.footnote)
                if let credentialInfo = credentialInfo {
                    SegmentedItemList {
                        SegmentedItemHeadingAndText(
                            heading: "Type",
                            text: credentialInfo.credential.credentialType
                        )
                        SegmentedItemHeadingAndText(
                            heading: "Identifier",
                            text: credentialInfo.credential.identifier
                        )
                        SegmentedItemHeadingAndText(
                            heading: "Domain",
                            text: credentialInfo.credential.domain
                        )
                        SegmentedItemHeadingAndText(
                            heading: "Certified",
                            text: credentialInfo.credential.isCertified ? "Yes" : "No"
                        )
                        
                        if credentialInfo.credential.isCertified {
                            SegmentedItemHeadingAndDateTime(
                                heading: "Valid From",
                                dateTime: toSwiftDate(credentialInfo.credential.validFrom)
                            )
                            SegmentedItemHeadingAndDateTime(
                                heading: "Valid Until",
                                dateTime: toSwiftDate(credentialInfo.credential.validUntil)
                            )
                            SegmentedItemHeadingAndText(
                                heading: "Issuer provided data",
                                text: "\(credentialInfo.credential.issuerProvidedData.size) bytes"
                            )
                            SegmentedItemHeadingAndText(
                                heading: "Usage Count",
                                text: credentialInfo.credential.usageCount.description
                            )
                            
                            // Revocation Status display
                            revocationSection(credential: credentialInfo.credential)
                            
                            // Specific credential types: MdocCredential
                            if let mdoc = credentialInfo.credential as? MdocCredential {
                                SegmentedItemHeadingAndText(
                                    heading: "ISO mdoc DocType",
                                    text: mdoc.docType
                                )
                                SegmentedItemHeadingAndText(
                                    heading: "ISO mdoc DS Key Certificate",
                                    text: "Click for details",
                                    showChevron: true
                                )
                                .onTapGesture {
                                    showDsKeyCertDetails(credential: mdoc)
                                }
                            }
                            
                            // Specific credential types: SdJwtVcCredential
                            if let sdjwt = credentialInfo.credential as? SdJwtVcCredential {
                                SegmentedItemHeadingAndText(
                                    heading: "Verifiable Credential Type",
                                    text: sdjwt.vct
                                )
                            }
                        }
                        
                        if let secureAreaBound = credentialInfo.credential as? SecureAreaBoundCredential {
                            SegmentedItemHeadingAndText(
                                heading: "Secure Area",
                                text: secureAreaBound.secureArea.displayName
                            )
                            SegmentedItemHeadingAndText(
                                heading: "Secure Area Identifier",
                                text: secureAreaBound.secureArea.identifier
                            )
                            if let keyInfo = credentialInfo.keyInfo {
                                SegmentedItemHeadingAndText(
                                    heading: "Device Key Algorithm",
                                    text: keyInfo.algorithm.description_
                                )
                            }
                            SegmentedItemHeadingAndText(
                                heading: "Device Key Invalidated",
                                text: credentialInfo.keyInvalidated ? "YES" : "No"
                            )
                            SegmentedItemHeadingAndText(
                                heading: "Device Key Attestation",
                                text: "Click for details",
                                showChevron: true
                            )
                            .onTapGesture {
                                showDeviceKeyAttestationDetails(credential: secureAreaBound)
                            }
                        } else {
                            SegmentedItemHeadingAndText(
                                heading: "Secure Area",
                                text: "N/A"
                            )
                        }
                    }
                    
                    if credentialInfo.credential.isCertified {
                        CredentialClaimsSection(credentialInfo: credentialInfo)
                    }
                } else {
                    SegmentedItemList {
                        SegmentedItemCenteredText(text: "No info for credential")
                    }
                }
            }
            .padding()
        }
        .background(Color(UIColor.systemGroupedBackground))
        .navigationTitle("Credential info")
        .navigationBarTitleDisplayMode(.inline)
        .alert(isPresented: $showAlert) {
            Alert(
                title: Text(alertTitle),
                message: Text(alertMessage),
                dismissButton: .default(Text("OK"))
            )
        }
    }
    
    @ViewBuilder
    private func revocationSection(credential: Credential) -> some View {
        if let mdoc = credential as? MdocCredential,
           let revocationStatus = mdoc.mso.revocationStatus {
            if let statusList = revocationStatus as? RevocationStatus.StatusList {
                SegmentedItemHeadingAndText(
                    heading: "Status List Revocation",
                    text: "Index: \(statusList.idx)\nUrl: \(statusList.uri)"
                )
            } else if let idList = revocationStatus as? RevocationStatus.IdentifierList {
                SegmentedItemHeadingAndText(
                    heading: "Identifier List Revocation",
                    text: "Identifier: \(formatBytes(idList.id))\nUrl: \(idList.uri)"
                )
            } else if revocationStatus is RevocationStatus.Unknown {
                SegmentedItemHeadingAndText(
                    heading: "Revocation info",
                    text: "Not parsed"
                )
            }
        }
    }
    
    private func toSwiftDate(_ instant: KotlinInstant) -> Date {
        return Date(timeIntervalSince1970: Double(instant.epochSeconds))
    }
    
    private func showDsKeyCertDetails(credential: MdocCredential) {
        Task {
            do {
                let dataSize = Int32(credential.issuerProvidedData.size)
                let issuerSigned = try Cbor.shared.decode(encodedCbor: credential.issuerProvidedData.toByteArray(startIndex: 0, endIndex: dataSize))
                let issuerAuth = issuerSigned.get(key: "issuerAuth").asCoseSign1
                
                let coseLabel = CoseNumberLabel(number: Cose.shared.COSE_LABEL_X5CHAIN)
                guard let certChainVal = issuerAuth.unprotectedHeaders[coseLabel] else {
                    showAlert(title: "Error", message: "Could not find x5chain in unprotected headers")
                    return
                }
                
                let certChain = certChainVal.asX509CertChain
                showAlert(title: "ISO mdoc DS Key Certificate", message: formatCertChain(certChain))
            } catch {
                showAlert(title: "Error", message: "Failed to extract certificate details: \(error.localizedDescription)")
            }
        }
    }
    
    private func showDeviceKeyAttestationDetails(credential: SecureAreaBoundCredential) {
        Task {
            do {
                let attestation = try await credential.getAttestation()
                if let certChain = attestation.certChain {
                    showAlert(title: "Device Key Attestation", message: formatCertChain(certChain))
                } else {
                    showAlert(title: "Device Key Attestation", message: "No attestation for Device Key")
                }
            } catch {
                showAlert(title: "Error", message: "Failed to get attestation: \(error.localizedDescription)")
            }
        }
    }
    
    private func formatCertChain(_ certChain: X509CertChain) -> String {
        let certs = certChain.certificates
        var result = "Certificate chain has \(certs.count) certificates:\n\n"
        for i in 0..<certs.count {
            let cert = certs[i]
            result += "Certificate [\(i)]:\n"
            result += "Subject: \(cert.subject)\n"
            result += "Issuer: \(cert.issuer)\n"
            result += "Validity: \(toSwiftDate(cert.validityNotBefore)) to \(toSwiftDate(cert.validityNotAfter))\n\n"
        }
        return result
    }
    
    private func showAlert(title: String, message: String) {
        self.alertTitle = title
        self.alertMessage = message
        self.showAlert = true
    }
    
    private func formatBytes(_ byteString: ByteString) -> String {
        let data = byteString.toNSData() as Data
        return data.map { String(format: "%02x", $0) }.joined()
    }
}

struct CredentialClaimsSection: View {
    let credentialInfo: CredentialInfo
    
    var body: some View {
        if let mdocClaims = credentialInfo.claims as? [MdocClaim] {
            let grouped = Dictionary(grouping: mdocClaims, by: { $0.namespaceName })
            let sortedNamespaces = grouped.keys.sorted()
            ForEach(sortedNamespaces, id: \.self) { namespace in
                let claims = grouped[namespace] ?? []
                SegmentedItemList(title: "Namespace \(namespace)") {
                    ForEach(0..<claims.count, id: \.self) { index in
                        let claim = claims[index]
                        SegmentedItemListClaim(
                            claim: claim,
                            heading: claim.dataElementName
                        )
                    }
                }
            }
        } else {
            let claims = credentialInfo.claims
            SegmentedItemList(title: "Claims") {
                ForEach(0..<claims.count, id: \.self) { index in
                    let claim = claims[index]
                    let claimName = getClaimName(claim)
                    SegmentedItemListClaim(
                        claim: claim,
                        heading: claimName
                    )
                }
            }
        }
    }
    
    private func getClaimName(_ claim: Claim) -> String {
        if let jsonClaim = claim as? JsonClaim {
            return jsonClaim.claimPath.map { $0.jsonPrimitive.content }.joined(separator: ".")
        } else {
            return claim.displayName
        }
    }
}
