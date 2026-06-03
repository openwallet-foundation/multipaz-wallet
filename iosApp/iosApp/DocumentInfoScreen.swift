import SwiftUI
import Multipaz

struct DocumentInfoScreen: View {
    @Environment(ViewModel.self) private var viewModel
    let documentId: String
    
    var body: some View {
        let documentInfo = viewModel.documentModel.documentInfos.first {
            $0.document.identifier == documentId
        }
        
        VStack {
            if let documentInfo = documentInfo,
               let credentialInfo = documentInfo.credentialInfos.first {
                let claimsToShow = credentialInfo.claims.filter { claim in
                    let jsonIgnoredClaims = Set(["iss", "vct", "iat", "nbf", "exp", "cnf", "status"])
                    return !jsonIgnoredClaims.contains(getClaimIdentifier(claim))
                }
                let certInfo = getCertInfo(credentialInfo: credentialInfo)
                
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        
                        FloatingItemList {
                            ForEach(0..<claimsToShow.count, id: \.self) { index in
                                FloatingItemHeadingAndContent(
                                    heading: claimsToShow[index].displayName,
                                    content: { RenderClaimValue(claim: claimsToShow[index]) }
                                )
                            }
                        }
                        
                        FloatingItemList(title: "Certificate Info") {
                            if let date = certInfo.signedAt {
                                FloatingItemHeadingAndDate(heading: "Signed At", date: date)
                            }
                            if let date = certInfo.validFrom {
                                FloatingItemHeadingAndDate(heading: "Valid From", date: date)
                            }
                            if let date = certInfo.validUntil {
                                FloatingItemHeadingAndDate(heading: "Valid Until", date: date)
                            }
                            if let date = certInfo.expectedUpdate {
                                FloatingItemHeadingAndDate(heading: "Expected Update", date: date)
                            }
                        }
                        
                        Spacer()
                    }
                    .padding()
                }
            } else {
                Text("Document details not found")
                    .foregroundColor(.gray)
            }
        }
        .navigationTitle("\(documentInfo?.document.typeDisplayName ?? "Document") info")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: {
                    viewModel.push(.documentInfoExtrasScreen(documentId: documentId))
                }) {
                    Image(systemName: "flask")
                }
            }
        }
    }
    
    private func getClaimIdentifier(_ claim: Claim) -> String {
        if let mdocClaim = claim as? MdocClaim {
            return mdocClaim.dataElementName
        } else if let jsonClaim = claim as? JsonClaim {
            return jsonClaim.claimPath.map { $0.jsonPrimitive.content }.joined(separator: ".")
        } else {
            return claim.displayName
        }
    }
    
    
    private struct CertInfo {
        var signedAt: Date?
        var validFrom: Date?
        var validUntil: Date?
        var expectedUpdate: Date?
    }
    
    private func getCertInfo(credentialInfo: CredentialInfo) -> CertInfo {
        var info = CertInfo()
        if let mdocCred = credentialInfo.credential as? MdocCredential {
            let mso = mdocCred.mso
            info.signedAt = toSwiftDate(mso.signedAt)
            info.validFrom = toSwiftDate(mso.validFrom)
            info.validUntil = toSwiftDate(mso.validUntil)
            info.expectedUpdate = toSwiftDate(mso.expectedUpdate)
        } else if let claims = credentialInfo.claims as? [JsonClaim] {
            info.signedAt = getInstant(claims: claims, claimName: "iat")
            info.validFrom = getInstant(claims: claims, claimName: "nbf")
            info.validUntil = getInstant(claims: claims, claimName: "exp")
        }
        return info
    }
    
    private func toSwiftDate(_ instant: KotlinInstant?) -> Date? {
        guard let instant = instant else { return nil }
        return Date(timeIntervalSince1970: Double(instant.epochSeconds))
    }
    
    private func getInstant(claims: [JsonClaim], claimName: String) -> Date? {
        guard let claim = claims.first(where: {
            $0.claimPath.count == 1 && $0.claimPath.first?.jsonPrimitive.content == claimName
        }) else {
            return nil
        }
        if let longVal = claim.value.jsonPrimitive.longOrNull?.int64Value {
            return Date(timeIntervalSince1970: Double(longVal))
        }
        return nil
    }
}
