package org.multipaz.wallet.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.util.Platform
import org.multipaz.wallet.client.WalletClient
import io.ktor.client.engine.js.Js
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.ephemeral.EphemeralStorage

fun main() {
    window.onload = {
        val root = document.getElementById("root")
        if (root != null) {
            root.innerHTML = """
                <h1>${BuildConfig.APP_NAME}</h1>
                <p>Version: ${BuildConfig.VERSION}</p>
                <p>Powered by Multipaz SDK ${Platform.version}</p>
                <div id="status">Initializing WalletClient...</div>
                <div id="issuers"></div>
            """.trimIndent()
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val storage = EphemeralStorage() // TODO: Platform.nonBackedUpStorage
                val secureArea = SoftwareSecureArea.create(storage) // TODO: Platform.getSecureArea(storage)
                
                // Use the proxied URL in development, or the same origin in production.
                // Since we proxy /rpc, we can just use the current origin.
                val backendUrl = window.location.origin
                
                val walletClient = WalletClient.create(
                    url = backendUrl,
                    secret = BuildConfig.BACKEND_SECRET,
                    storage = storage,
                    secureArea = secureArea,
                    httpClientEngineFactory = Js
                )
                
                document.getElementById("status")?.innerHTML = "Connected to backend: ${walletClient.url}"
                
                val issuers = walletClient.getCredentialIssuers()
                val issuersHtml = issuers.joinToString("<br>") { issuer ->
                    "<b>${issuer.name}</b>"
                }
                document.getElementById("issuers")?.innerHTML = """
                    <h3>Credential Issuers:</h3>
                    $issuersHtml
                """.trimIndent()
                
            } catch (e: Exception) {
                document.getElementById("status")?.innerHTML = "Error: ${e.message}"
                console.error(e)
            }
        }
    }
}
