package org.multipaz.wallet.web

import kotlin.js.Json

/**
 * Kotlin/JS external declarations for Google Identity Services.
 * See https://developers.google.com/identity/gsi/web/reference/js-reference
 */

@JsName("google")
external object Google {
    val accounts: Accounts
}

external interface Accounts {
    val id: Id
    val oauth2: OAuth2
}

external interface Id {
    fun initialize(config: IdConfiguration)
    fun renderButton(parent: dynamic, options: GsiButtonConfiguration)
    fun prompt(momentListener: (dynamic) -> Unit = definedExternally)
    fun disableAutoSelect()
    fun storeCredential(credential: dynamic, callback: () -> Unit)
    fun cancel()
    fun revoke(hint: String, callback: (dynamic) -> Unit)
}

external interface OAuth2 {
    fun initTokenClient(config: TokenClientConfig): TokenClient
    fun initCodeClient(config: CodeClientConfig): CodeClient
    fun revoke(accessToken: String, done: () -> Unit)
}

external interface IdConfiguration {
    var client_id: String
    var auto_select: Boolean?
    var callback: (CredentialResponse) -> Unit
    var native_callback: ((dynamic) -> Unit)?
    var cancel_on_tap_outside: Boolean?
    var prompt_parent_id: String?
    var nonce: String?
    var context: String?
    var state_cookie_domain: String?
    var ux_mode: String?
    var allowed_parent_origin: dynamic? // String or Array<String>
    var intermediate_iframe_close_callback: (() -> Unit)?
    var itp_support: Boolean?
    var login_uri: String?
}

external interface CredentialResponse {
    val credential: String
    val select_by: String
}

external interface GsiButtonConfiguration {
    var type: String?
    var theme: String?
    var size: String?
    var text: String?
    var shape: String?
    var logo_alignment: String?
    var width: String?
    var locale: String?
    var click_listener: (() -> Unit)?
}

external interface TokenClientConfig {
    var client_id: String
    var scope: String
    var callback: (TokenResponse) -> Unit
    var error_callback: ((dynamic) -> Unit)?
    var state: String?
    var hint: String?
    var hosted_domain: String?
    var prompt: String?
}

external interface TokenClient {
    fun requestAccessToken(overrideConfig: dynamic = definedExternally)
}

external interface TokenResponse {
    val access_token: String
    val expires_in: String
    val hd: String?
    val prompt: String
    val scope: String
    val token_type: String
    val state: String?
    val error: String?
    val error_description: String?
    val error_uri: String?
}

external interface CodeClientConfig {
    var client_id: String
    var scope: String
    var callback: (CodeResponse) -> Unit
    var error_callback: ((dynamic) -> Unit)?
    var state: String?
    var hint: String?
    var hosted_domain: String?
    var ux_mode: String?
    var redirect_uri: String?
}

external interface CodeClient {
    fun requestCode()
}

external interface CodeResponse {
    val code: String
    val state: String?
    val error: String?
    val error_description: String?
    val error_uri: String?
}
