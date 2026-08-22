enum Destination: Hashable {
    case walletScreen(documentId: String?, justAddedAtMillis: Int64? = nil)
    case settingsScreen
    case documentInfoScreen(documentId: String)
    case documentInfoExtrasScreen(documentId: String)
    case credentialInfoScreen(documentId: String, credentialId: String)
    case proximityPresentment(documentId: String)
    case addToWallet
    case provisioning(issuerUrl: String, credentialId: String?, provisionedDocumentIdentifier: String? = nil)
    case provisioningFromOffer(credentialOfferUri: String)
    case requestVerification
}
