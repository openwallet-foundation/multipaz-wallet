package org.multipaz.wallet.android

import org.multipaz.compose.digitalcredentials.CredentialManagerPresentmentActivity

class WalletCredentialManagerPresentmentActivity: CredentialManagerPresentmentActivity() {
    override suspend fun getSettings(): Settings {
        return Settings(
            source = App.getPresentmentSource(),
            privilegedAllowList =
                assets.open("privilegedUserAgents.json").bufferedReader().use {
                    it.readText()
                }
        )
    }
}
