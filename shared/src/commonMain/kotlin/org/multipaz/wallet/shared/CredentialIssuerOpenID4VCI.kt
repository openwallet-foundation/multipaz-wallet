package org.multipaz.wallet.shared

/**
 * A credential issuer using OpenID4VCI as the provisioning protocol.
 *
 * @property url the OpenID4VCI URL.
 * @property id the identifier of the credential to use at the URL.
 */
data class CredentialIssuerOpenID4VCI(
    override val name: String,
    override val iconUrl: String,
    val url: String,
    val id: String,
): CredentialIssuer(name, iconUrl)
