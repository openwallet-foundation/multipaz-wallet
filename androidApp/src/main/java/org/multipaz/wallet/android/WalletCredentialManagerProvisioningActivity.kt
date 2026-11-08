package org.multipaz.wallet.android

import io.ktor.http.encodeURLParameter
import org.multipaz.compose.digitalcredentials.CredentialManagerProvisioningActivity
import org.multipaz.digitalcredentials.CredmanCredentialOffer

class WalletCredentialManagerProvisioningActivity : CredentialManagerProvisioningActivity() {
    override suspend fun getPrivilegedAllowList(): String {
        return assets.open("privilegedUserAgents.json").bufferedReader().use {
            it.readText()
        }
    }

    override suspend fun handleCredentialOffer(
        origin: String,
        offer: CredmanCredentialOffer,
    ): Result {
        val offerUri = "openid-credential-offer://?credential_offer=" +
            offer.offer.toString().encodeURLParameter()
        return ProvisioningActivity.handleCredmanCredentialOffer(this, offerUri)
    }
}
