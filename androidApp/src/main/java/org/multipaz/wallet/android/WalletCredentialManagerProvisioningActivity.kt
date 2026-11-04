package org.multipaz.wallet.android

import android.content.Intent
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
        val app = App.getInstance()
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        return app.handleCredmanCredentialOffer(offer.offerUri)
    }
}
