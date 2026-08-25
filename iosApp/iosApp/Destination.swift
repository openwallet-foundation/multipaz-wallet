import Foundation
import Multipaz

enum Destination: Hashable {
    case walletScreen(documentId: String?, justAddedAtMillis: Int64? = nil, animateListTransitions: Bool = false)
    case settingsScreen
    case documentInfoScreen(documentId: String)
    case documentInfoExtrasScreen(documentId: String)
    case credentialInfoScreen(documentId: String, credentialId: String)
    case proximityPresentment(documentId: String)
    case addToWallet
    case scanCredentialOffer
    case enterIssuerUrl
    case provisioning(issuerUrl: String, credentialId: String?, provisionedDocumentIdentifier: String? = nil)
    case provisioningFromOffer(credentialOfferUri: String)
    case requestVerification
    case deviceSessions
    case trustedIssuers
    case trustedVerifiers
    case trustEntry(trustManagerId: String, trustEntryId: String, justImported: Bool)
    case trustEntryEdit(trustManagerId: String, trustEntryId: String)
    case trustEntryVicalEntry(trustManagerId: String, vicalTrustEntryId: String, certNum: Int)
    case trustEntryRicalEntry(trustManagerId: String, ricalTrustEntryId: String, certNum: Int)
    case certificateViewer(certChain: X509CertChain)
    case certificateViewerSingle(certificate: X509Cert)
}
