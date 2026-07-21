package org.multipaz.wallet.web

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.util.Logger
import org.multipaz.wallet.shared.BuildConfig
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

private const val TAG = "KeysApp"

data class KeyInfo(
    val name: String,
    val publicKeyPem: String,
    val publicKeyJwkString: String,
    val certificates: List<String>
)

data class KeysData(
    val walletAttestation: KeyInfo?,
    val keyAttestation: KeyInfo?,
    val readerRoot: KeyInfo?
)

enum class KeysStatus {
    LOADING,
    SUCCESS,
    ERROR
}

val KeysApp = FC<Props> {
    val (status, setStatus) = useState(KeysStatus.LOADING)
    val (errorMessage, setErrorMessage) = useState<String?>(null)
    val (keysData, setKeysData) = useState<KeysData?>(null)
    val (copiedKey, setCopiedKey) = useState<String?>(null)
    val (activeFormats, setActiveFormats) = useState(mapOf("wallet" to "PEM", "key" to "PEM", "reader" to "PEM"))

    useEffectOnce {
        window.document.title = "Public Keys & Certificates"
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val response = window.fetch("/api/keys").await()
                if (!response.ok) {
                    throw Exception("Failed to load public keys (HTTP ${response.status})")
                }
                val text = response.text().await()
                val rootObj = Json.parseToJsonElement(text).jsonObject

                val parseKeyInfo = { keyName: String ->
                    val obj = rootObj[keyName]?.jsonObject
                    if (obj != null) {
                        val name = obj["name"]?.jsonPrimitive?.content ?: keyName
                        val pubKeyPem = obj["publicKeyPem"]?.jsonPrimitive?.content ?: ""
                        val pubKeyJwkString = obj["publicKeyJwkString"]?.jsonPrimitive?.content
                            ?: obj["publicKeyJwk"]?.toString() ?: ""
                        val certs = obj["certificates"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                        KeyInfo(
                            name = name,
                            publicKeyPem = pubKeyPem,
                            publicKeyJwkString = pubKeyJwkString,
                            certificates = certs
                        )
                    } else null
                }

                val walletInfo = parseKeyInfo("walletAttestation")
                val keyInfo = parseKeyInfo("keyAttestation")
                val readerRootInfo = parseKeyInfo("readerRoot")

                setKeysData(KeysData(walletAttestation = walletInfo, keyAttestation = keyInfo, readerRoot = readerRootInfo))
                setStatus(KeysStatus.SUCCESS)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to fetch public keys", e)
                setErrorMessage(e.message ?: "Failed to fetch public keys")
                setStatus(KeysStatus.ERROR)
            }
        }
    }

    val handleCopy: (String, String) -> Unit = { text: String, id: String ->
        window.navigator.clipboard.writeText(text)
        setCopiedKey(id)
        window.setTimeout({
            setCopiedKey(null)
        }, 2000)
    }

    val handleFormatChange: (String, String) -> Unit = { sectionId: String, format: String ->
        setActiveFormats(activeFormats + (sectionId to format))
    }

    div {
        className = ClassName("flex flex-col items-center justify-center min-h-screen bg-slate-900 text-white px-4 py-12 relative overflow-hidden")

        // Animated gradient background accents
        div {
            className = ClassName("absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-blue-600/20 rounded-full blur-3xl -z-10 animate-pulse")
        }
        div {
            className = ClassName("absolute bottom-1/4 left-1/3 w-80 h-80 bg-indigo-600/15 rounded-full blur-3xl -z-10")
        }

        div {
            className = ClassName("max-w-3xl w-full bg-slate-950/80 backdrop-blur-xl border border-slate-800/80 rounded-3xl p-8 shadow-2xl space-y-8 flex flex-col items-center")

            // Header Section
            div {
                className = ClassName("flex flex-col items-center text-center space-y-3")
                div {
                    className = ClassName("relative mb-2")
                    div {
                        className = ClassName("absolute -inset-1 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-full blur opacity-30")
                    }
                    img {
                        src = "https://apps.multipaz.org/multipaz-logo-400x400.png"
                        className = ClassName("relative h-24 w-24 rounded-full border-2 border-slate-800")
                    }
                }
                h1 {
                    className = ClassName("text-3xl font-extrabold text-slate-100 tracking-tight")
                    +"Public Keys & Certificates"
                }
                p {
                    className = ClassName("text-sm text-slate-400 max-w-md leading-relaxed")
                    +"Public keys and root certificates used by ${BuildConfig.APP_NAME} for attestation validation and mdoc reader authentication."
                }
            }

            when (status) {
                KeysStatus.LOADING -> {
                    div {
                        className = ClassName("flex flex-col items-center space-y-4 py-8")
                        div {
                            className = ClassName("w-16 h-16 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin")
                        }
                        p {
                            className = ClassName("text-sm text-slate-400")
                            +"Retrieving public keys..."
                        }
                    }
                }
                KeysStatus.ERROR -> {
                    div {
                        className = ClassName("w-full bg-red-950/50 border border-red-800/50 rounded-2xl p-6 text-center space-y-3")
                        h2 {
                            className = ClassName("text-lg font-bold text-red-300")
                            +"Unable to load public keys"
                        }
                        p {
                            className = ClassName("text-xs text-red-400")
                            +(errorMessage ?: "An unexpected error occurred.")
                        }
                        button {
                            className = ClassName("px-4 py-2 bg-slate-800 hover:bg-slate-700 text-xs text-slate-200 rounded-xl transition-all cursor-pointer")
                            onClick = { window.location.reload() }
                            +"Retry"
                        }
                    }
                }
                KeysStatus.SUCCESS -> {
                    val data = keysData
                    if (data != null) {
                        div {
                            className = ClassName("w-full space-y-8")

                            val walletKey = data.walletAttestation
                            if (walletKey != null && (walletKey.publicKeyPem.isNotEmpty() || walletKey.publicKeyJwkString.isNotEmpty())) {
                                renderKeySection(
                                    title = "Wallet Attestation Key",
                                    badgeText = "WALLET ATTESTATION",
                                    badgeColor = "bg-blue-500/10 text-blue-400 border-blue-500/20",
                                    keyInfo = walletKey,
                                    currentFormat = activeFormats["wallet"] ?: "PEM",
                                    copiedKey = copiedKey,
                                    onCopy = handleCopy,
                                    onFormatChange = handleFormatChange,
                                    sectionId = "wallet",
                                    showPublicKey = true
                                ) {
                                    p {
                                        className = ClassName("text-xs text-slate-400 leading-relaxed")
                                        +"Used by the backend server to sign JWT Wallet Attestations ("
                                        a {
                                            href = "https://github.com/openid/OpenID4VCI"
                                            (this.asDynamic()).target = "_blank"
                                            className = ClassName("text-indigo-400 hover:underline font-medium")
                                            +"OpenID4VCI"
                                        }
                                        + "). During credential issuance, this key proves to the Credential Issuer "
                                        + "that the wallet software requesting credentials is an authentic, "
                                        + "untampered instance of ${BuildConfig.APP_NAME}."
                                    }
                                }
                            }

                            val keyKey = data.keyAttestation
                            if (keyKey != null && (keyKey.publicKeyPem.isNotEmpty() || keyKey.publicKeyJwkString.isNotEmpty())) {
                                renderKeySection(
                                    title = "Key Attestation Key",
                                    badgeText = "KEY ATTESTATION",
                                    badgeColor = "bg-indigo-500/10 text-indigo-400 border-indigo-500/20",
                                    keyInfo = keyKey,
                                    currentFormat = activeFormats["key"] ?: "PEM",
                                    copiedKey = copiedKey,
                                    onCopy = handleCopy,
                                    onFormatChange = handleFormatChange,
                                    sectionId = "key",
                                    showPublicKey = true
                                ) {
                                    p {
                                        className = ClassName("text-xs text-slate-400 leading-relaxed")
                                        +"Used by the backend server to issue JWT Key Attestations ("
                                        a {
                                            href = "https://github.com/openid/OpenID4VCI"
                                            (this.asDynamic()).target = "_blank"
                                            className = ClassName("text-indigo-400 hover:underline font-medium")
                                            +"OpenID4VCI"
                                        }
                                        + ") for cryptographic key pairs generated inside the user device's Secure "
                                        + "Area (e.g., Android KeyStore / StrongBox or iOS Secure Enclave). It "
                                        + "certifies to Credential Issuers that credential keys are securely "
                                        + "bound to hardware protection."
                                    }
                                }
                            }

                            val readerRootKey = data.readerRoot
                            if (readerRootKey != null && (readerRootKey.certificates.isNotEmpty() || readerRootKey.publicKeyPem.isNotEmpty())) {
                                renderKeySection(
                                    title = "Reader Root Certificate",
                                    badgeText = "READER ROOT",
                                    badgeColor = "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
                                    keyInfo = readerRootKey,
                                    currentFormat = activeFormats["reader"] ?: "PEM",
                                    copiedKey = copiedKey,
                                    onCopy = handleCopy,
                                    onFormatChange = handleFormatChange,
                                    sectionId = "reader",
                                    showPublicKey = false
                                ) {
                                    p {
                                        className = ClassName("text-xs text-slate-400 leading-relaxed")
                                        +"The Root Certificate Authority (CA) certificate used by external wallets to authenticate "
                                        +BuildConfig.APP_NAME
                                        +" when it acts as an mdoc reader or verifier ("
                                        a {
                                            href = "https://github.com/ISO-SC17-WG10/ISO-18013"
                                            (this.asDynamic()).target = "_blank"
                                            className = ClassName("text-indigo-400 hover:underline font-medium")
                                            +"ISO/IEC 18013-5"
                                        }
                                        +" mdoc reader authentication & "
                                        a {
                                            href = "https://github.com/openid/OpenID4VP"
                                            (this.asDynamic()).target = "_blank"
                                            className = ClassName("text-indigo-400 hover:underline font-medium")
                                            +"OpenID4VP"
                                        }
                                        +"). When ${BuildConfig.APP_NAME} requests identity attributes from a wallet, the holder's wallet uses this root certificate to verify ${BuildConfig.APP_NAME}'s reader certificate chain before disclosing sensitive personal data."
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.renderKeySection(
    title: String,
    badgeText: String,
    badgeColor: String,
    keyInfo: KeyInfo,
    currentFormat: String,
    copiedKey: String?,
    onCopy: (String, String) -> Unit,
    onFormatChange: (String, String) -> Unit,
    sectionId: String,
    showPublicKey: Boolean = true,
    descriptionBuilder: (ChildrenBuilder.() -> Unit)? = null
) {
    val keyTextToCopy = if (currentFormat == "JWK") keyInfo.publicKeyJwkString else keyInfo.publicKeyPem

    div {
        className = ClassName("w-full bg-slate-900/60 border border-slate-800/80 rounded-2xl p-6 space-y-4 text-left")

        div {
            className = ClassName("flex flex-wrap items-center justify-between gap-3")
            div {
                className = ClassName("flex items-center space-x-3")
                div {
                    className = ClassName("p-2 bg-indigo-500/10 border border-indigo-500/20 rounded-xl text-indigo-400")
                    svg {
                        val sd: dynamic = this
                        sd.className = "w-5 h-5"
                        sd.fill = "none"
                        sd.viewBox = "0 0 24 24"
                        sd.strokeWidth = 1.5
                        sd.stroke = "currentColor"
                        path {
                            val pd: dynamic = this
                            pd.strokeLinecap = "round"
                            pd.strokeLinejoin = "round"
                            pd.d = "M15.75 5.25a3 3 0 013 3m3 0a6 6 0 01-7.029 5.912c-.563-.097-1.159.026-1.563.43L10.5 17.25H8.25v2.25H6v2.25H2.25v-2.818c0-.597.237-1.17.659-1.591l6.499-6.499c.404-.404.527-1 .43-1.563A6 6 0 1121.75 8.25z"
                        }
                    }
                }
                div {
                    h2 {
                        className = ClassName("text-lg font-bold text-slate-100")
                        +title
                    }
                    span {
                        className = ClassName("inline-block text-[10px] font-bold tracking-wider px-2 py-0.5 rounded-full border $badgeColor mt-0.5")
                        +badgeText
                    }
                }
            }

            if (showPublicKey) {
                div {
                    className = ClassName("flex items-center space-x-2")

                    // Tab Switcher (PEM / JWK)
                    div {
                        className = ClassName("flex bg-slate-950 p-1 rounded-xl border border-slate-800")
                        button {
                            className = ClassName(
                                if (currentFormat == "PEM")
                                    "px-3 py-1 text-xs font-semibold rounded-lg bg-indigo-600 text-white shadow-sm border border-indigo-500/40 cursor-pointer transition-all"
                                else
                                    "px-3 py-1 text-xs font-medium rounded-lg text-slate-400 hover:text-slate-200 cursor-pointer transition-all"
                            )
                            onClick = { onFormatChange(sectionId, "PEM") }
                            +"PEM"
                        }
                        button {
                            className = ClassName(
                                if (currentFormat == "JWK")
                                    "px-3 py-1 text-xs font-semibold rounded-lg bg-indigo-600 text-white shadow-sm border border-indigo-500/40 cursor-pointer transition-all"
                                else
                                    "px-3 py-1 text-xs font-medium rounded-lg text-slate-400 hover:text-slate-200 cursor-pointer transition-all"
                            )
                            onClick = { onFormatChange(sectionId, "JWK") }
                            +"JWK"
                        }
                    }

                    // Copy Button
                    button {
                        className = ClassName("flex items-center space-x-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 active:bg-slate-900 border border-slate-700 text-xs text-slate-200 font-medium rounded-xl transition-all cursor-pointer")
                        onClick = { onCopy(keyTextToCopy, "${sectionId}_pubkey") }
                        svg {
                            val sd: dynamic = this
                            sd.className = "w-4 h-4 text-slate-400"
                            sd.fill = "none"
                            sd.viewBox = "0 0 24 24"
                            sd.strokeWidth = 1.5
                            sd.stroke = "currentColor"
                            path {
                                val pd: dynamic = this
                                pd.strokeLinecap = "round"
                                pd.strokeLinejoin = "round"
                                if (copiedKey == "${sectionId}_pubkey") {
                                    pd.d = "M4.5 12.75l6 6 9-13.5"
                                } else {
                                    pd.d = "M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125H3.375c-.621 0-1.125-.504-1.125-1.125V11.25c0-.621.504-1.125 1.125-1.125h3.375m1.5-1.5h11.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125H9.75c-.621 0-1.125-.504-1.125-1.125V8.625z"
                                }
                            }
                        }
                        span {
                            if (copiedKey == "${sectionId}_pubkey") +"Copied!" else +"Copy $currentFormat"
                        }
                    }
                }
            }
        }

        if (descriptionBuilder != null) {
            descriptionBuilder()
        }

        // Key Content Box (only when showPublicKey is true)
        if (showPublicKey) {
            div {
                className = ClassName("space-y-1.5")
                div {
                    className = ClassName("flex items-center justify-between text-xs font-semibold text-slate-400 tracking-wide uppercase")
                    span { +"Public Key ($currentFormat)" }
                }
                pre {
                    className = ClassName("p-4 bg-slate-950 border border-slate-800 rounded-xl font-mono text-xs text-indigo-200 overflow-x-auto whitespace-pre select-all leading-relaxed shadow-inner")
                    if (currentFormat == "JWK") {
                        +keyInfo.publicKeyJwkString
                    } else {
                        +keyInfo.publicKeyPem
                    }
                }
            }
        }

        // Certificate Chain / Root Certificate (if present)
        if (keyInfo.certificates.isNotEmpty()) {
            div {
                className = ClassName("space-y-3 pt-2")
                if (showPublicKey) {
                    span {
                        className = ClassName("text-xs font-semibold text-slate-400 tracking-wide uppercase")
                        +"Certificate Chain (${keyInfo.certificates.size} ${if (keyInfo.certificates.size == 1) "cert" else "certs"})"
                    }
                }
                keyInfo.certificates.forEachIndexed { idx, certPem ->
                    div {
                        className = ClassName("space-y-1.5")
                        div {
                            className = ClassName("flex items-center justify-between text-xs font-semibold text-slate-400 tracking-wide uppercase")
                            span {
                                if (!showPublicKey) {
                                    +"Root Certificate"
                                } else {
                                    +"Certificate #${idx + 1}"
                                }
                            }
                            button {
                                className = ClassName("flex items-center space-x-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 active:bg-slate-900 border border-slate-700 text-xs text-slate-200 font-medium rounded-xl transition-all cursor-pointer normal-case")
                                onClick = { onCopy(certPem, "${sectionId}_cert_$idx") }
                                svg {
                                    val sd: dynamic = this
                                    sd.className = "w-4 h-4 text-slate-400"
                                    sd.fill = "none"
                                    sd.viewBox = "0 0 24 24"
                                    sd.strokeWidth = 1.5
                                    sd.stroke = "currentColor"
                                    path {
                                        val pd: dynamic = this
                                        pd.strokeLinecap = "round"
                                        pd.strokeLinejoin = "round"
                                        if (copiedKey == "${sectionId}_cert_$idx") {
                                            pd.d = "M4.5 12.75l6 6 9-13.5"
                                        } else {
                                            pd.d = "M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125H3.375c-.621 0-1.125-.504-1.125-1.125V11.25c0-.621.504-1.125 1.125-1.125h3.375m1.5-1.5h11.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125H9.75c-.621 0-1.125-.504-1.125-1.125V8.625z"
                                        }
                                    }
                                }
                                span {
                                    if (copiedKey == "${sectionId}_cert_$idx") +"Copied!" else +"Copy Certificate"
                                }
                            }
                        }
                        pre {
                            className = ClassName("p-4 bg-slate-950 border border-slate-800 rounded-xl font-mono text-xs text-slate-300 overflow-x-auto whitespace-pre select-all leading-relaxed shadow-inner")
                            +certPem
                        }
                    }
                }
            }
        }
    }
}
