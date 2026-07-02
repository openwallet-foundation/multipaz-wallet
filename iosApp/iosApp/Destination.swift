enum Destination: Hashable {
    case walletScreen(documentId: String?)
    case settingsScreen
    case documentInfoScreen(documentId: String)
    case documentInfoExtrasScreen(documentId: String)
    case credentialInfoScreen(documentId: String, credentialId: String)
    case proximityPresentment(documentId: String)
    case addToWallet
    case provisioning(issuerUrl: String, credentialId: String?)
    case provisioningFromOffer(credentialOfferUri: String)
    case requestVerification
}
