package org.multipaz.wallet.shared

/**
 * A credential issuer using OpenID4VCI as the provisioning protocol.
 *
 * @property name name to show to the user.
 * @property iconUrl URL with card art to show to the user.
 * @property url the OpenID4VCI URL.
 * @property id the identifier of the credential to use at the URL or `null` to inquire the issuer
 * about the credentials it supports.
 * @property credentialIssuerSettings optional settings for the credential issuer.
 */
data class CredentialIssuerOpenID4VCI(
    override val name: String,
    override val iconUrl: String,
    val url: String,
    val id: String?,
    override val credentialIssuerSettings: CredentialIssuerSettings? = null,
): CredentialIssuer(name, iconUrl, credentialIssuerSettings)
