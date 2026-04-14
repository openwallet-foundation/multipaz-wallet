package org.multipaz.wallet.android

import io.ktor.client.engine.android.Android
import org.multipaz.compose.presentment.UriSchemePresentmentActivity

class WalletUriSchemePresentmentActivity: UriSchemePresentmentActivity() {
    override suspend fun getSettings(): Settings {
        val app = App.getInstance()
        return Settings(
            source = App.getPresentmentSource(),
            httpClientEngineFactory = Android,
        )
    }
}
