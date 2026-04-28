package org.multipaz.wallet.client

import kotlinx.io.bytestring.ByteString

/**
 * A provisioned document using OpenID4VCI as the provisioning protocol.
 *
 * @property url the URL of the OpenID4VCI server.
 * @property credentialId the identifier of the credential to use at the URL.
 */
data class WalletClientProvisionedDocumentOpenID4VCI(
    override val identifier: String,
    override val cardArt: ByteString?,
    override val displayName: String?,
    override val typeDisplayName: String?,
    val url: String,
    val credentialId: String,

    // We should include a bearer token which identifies the applicant in the issuer's System
    // of Record and which can be passed to the OpenID4VCI server so the right applicant is selected
    // and _some_ authentication is still performed. Compare with traditional wallets apps where
    // your credit cards exist but can't be used until you present the 3-digit CVC.
    //
    // This will require changes/extensions in the OpenID4VCI protocol. Until this happens, just using
    // credentialId is sufficient since most of the time holders only have a single credential of a given
    // type with a given issuer.
    //
): WalletClientProvisionedDocument(identifier, cardArt, displayName, typeDisplayName)
