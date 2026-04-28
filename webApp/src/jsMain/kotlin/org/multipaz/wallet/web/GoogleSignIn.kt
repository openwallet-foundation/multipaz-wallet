package org.multipaz.wallet.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.browser.document
import org.multipaz.util.Logger
import kotlin.js.Promise
import kotlinx.coroutines.await

private const val TAG = "GoogleSignIn"

class GoogleSignIn(private val clientId: String) {

    private var codeDeferred: CompletableDeferred<String>? = null

    fun initialize() {
        // No-op for Code flow (handled via initCodeClient)
    }

    /**
     * Opens the OAuth2 popup for an authorization code.
     * This MUST be called directly in a user gesture handler (like onClick).
     */
    fun requestCode(scope: String): Promise<String> {
        val promise = Promise<String> { resolve, reject ->
            val client = GoogleIdentity.accounts.oauth2.initCodeClient(js("{}").unsafeCast<CodeClientConfig>().apply {
                client_id = clientId
                this.scope = scope
                this.ux_mode = "popup"
                this.redirect_uri = "postmessage"
                this.callback = { response ->
                    if (response.error != null) {
                        reject(Exception("OAuth2 Error: ${response.error} - ${response.error_description}"))
                    } else {
                        resolve(response.code)
                    }
                }
                this.error_callback = { err ->
                    reject(Exception("OAuth2 Error: $err"))
                }
            })
            client.requestCode()
        }
        return promise
    }
}
