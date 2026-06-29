package org.multipaz.wallet.web

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.WalletBackendVerificationLinkExpiredException
import react.FC
import react.Props
import org.multipaz.wallet.shared.BuildConfig
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.useEffectOnce
import react.useState
import web.cssom.ClassName
import kotlin.random.Random

private const val TAG = "VerifyApp"

external interface VerifyAppProps : Props {
    var walletClient: WalletClient
}

enum class VerifyStatus {
    LOADING,
    READY,
    PRESENTING,
    SUCCESS,
    ERROR
}

val VerifyApp = FC<VerifyAppProps> { props ->
    val (status, setStatus) = useState(VerifyStatus.LOADING)
    val (errorMessage, setErrorMessage) = useState<String?>(null)
    val (dcRequest, setDcRequest) = useState<dynamic>(null)
    val (requestId, setRequestId) = useState<String?>(null)
    val (encryptionKey, setEncryptionKey) = useState<ByteString?>(null)

    useEffectOnce {
        val search = window.location.search
        val hash = window.location.hash

        val urlParams = js("new URLSearchParams(window.location.search)")
        val reqId = urlParams.get("request") as? String
        val keyB64 = if (hash.startsWith("#")) hash.substring(1) else ""

        if (reqId == null || keyB64.isEmpty()) {
            setErrorMessage("The link has expired.")
            setStatus(VerifyStatus.ERROR)
            return@useEffectOnce
        }

        val hasCrypto = js("typeof window.crypto !== 'undefined' && typeof window.crypto.subtle !== 'undefined'") as Boolean
        if (!hasCrypto) {
            setErrorMessage("Cryptography is unavailable. This page must be loaded over HTTPS or localhost (secure context) to access Web Crypto APIs.")
            setStatus(VerifyStatus.ERROR)
            return@useEffectOnce
        }

        setRequestId(reqId)

        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val key = ByteString(keyB64.fromBase64Url())
                setEncryptionKey(key)

                val encryptedPayload = props.walletClient.getVerificationPayload(reqId)
                val encryptedBytes = encryptedPayload.toByteArray()
                
                if (encryptedBytes.size < 12) {
                    throw Exception("Payload is too short")
                }

                val iv = encryptedBytes.copyOfRange(0, 12)
                val ciphertext = encryptedBytes.copyOfRange(12, encryptedBytes.size)

                val plaintextBytes = Crypto.decrypt(
                    algorithm = Algorithm.A256GCM,
                    key = key.toByteArray(),
                    nonce = iv,
                    messageCiphertext = ciphertext,
                    aad = byteArrayOf()
                )

                val plaintext = plaintextBytes.decodeToString()
                val jsonElement = Json.parseToJsonElement(plaintext)
                val jsonObject = jsonElement.jsonObject
                val reqObj = jsonObject["dcRequest"] ?: throw Exception("Missing dcRequest in payload")
                
                val dcRequestStr = Json.encodeToString(reqObj)
                val jsRequest = js("JSON.parse(dcRequestStr)")

                setDcRequest(jsRequest)
                setStatus(VerifyStatus.READY)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load/decrypt verification payload", e)
                setErrorMessage("The link has expired.")
                setStatus(VerifyStatus.ERROR)
            }
        }
    }

    val handlePresent = {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                setStatus(VerifyStatus.PRESENTING)
                
                val jsPromise = js("navigator.credentials.get({ digital: dcRequest })") as kotlin.js.Promise<dynamic>
                val credential = jsPromise.await()

                if (credential == null) {
                    throw Exception("No credential returned or user cancelled the request.")
                }

                val credentialStr = js("JSON.stringify(credential)") as String
                
                // Encrypt response using the same symmetric key
                val key = encryptionKey ?: throw Exception("Encryption key is missing")
                val iv = Random.nextBytes(12)
                val encryptedData = Crypto.encrypt(
                    algorithm = Algorithm.A256GCM,
                    key = key.toByteArray(),
                    nonce = iv,
                    messagePlaintext = credentialStr.encodeToByteArray(),
                    aad = byteArrayOf()
                )
                val encryptedResponse = ByteString(iv + encryptedData)

                val reqId = requestId ?: throw Exception("Request ID is missing")
                props.walletClient.submitVerificationResponse(reqId, encryptedResponse)

                setStatus(VerifyStatus.SUCCESS)
            } catch (e: Exception) {
                Logger.e(TAG, "Verification presentation failed", e)
                setErrorMessage("The link has expired.")
                setStatus(VerifyStatus.ERROR)
            }
        }
    }

    div {
        className = ClassName("flex flex-col items-center justify-center min-h-screen bg-slate-900 text-white px-4 py-8")

        // Animated gradient background accent
        div {
            className = ClassName("absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-80 h-80 bg-indigo-600/20 rounded-full blur-3xl -z-10 animate-pulse")
        }
        div {
            className = ClassName("absolute bottom-1/4 left-1/3 w-64 h-64 bg-purple-600/10 rounded-full blur-3xl -z-10")
        }

        div {
            className = ClassName("max-w-md w-full bg-slate-950/80 backdrop-blur-xl border border-slate-800/80 rounded-3xl p-8 shadow-2xl space-y-6 flex flex-col items-center text-center")

            when (status) {
                VerifyStatus.LOADING -> {
                    div {
                        className = ClassName("w-16 h-16 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin mb-4")
                    }
                    h1 {
                        className = ClassName("text-2xl font-bold text-slate-100")
                        +"Loading Verification"
                    }
                    p {
                        className = ClassName("text-sm text-slate-400 max-w-xs")
                        +"Fetching and decrypting credential request information safely..."
                    }
                }
                VerifyStatus.READY -> {
                    div {
                        className = ClassName("relative mb-4")
                        div {
                            className = ClassName("absolute -inset-1 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-full blur opacity-25")
                        }
                        img {
                            src = "https://apps.multipaz.org/multipaz-logo-400x400.png"
                            className = ClassName("relative h-32 w-32 rounded-full border-2 border-slate-800")
                        }
                    }
                    h1 {
                        className = ClassName("text-2xl font-extrabold text-slate-100 tracking-tight")
                        +"Verify with ${BuildConfig.APP_NAME}"
                    }
                    p {
                        className = ClassName("text-sm text-slate-400 leading-relaxed")
                        +"A verifier is requesting to verify your digital credentials securely."
                    }
                    div {
                        className = ClassName("w-full bg-slate-900/60 border border-slate-800/50 rounded-2xl p-5 text-left space-y-3 mt-2")
                        div {
                            className = ClassName("flex items-center space-x-2.5")
                            svg {
                                val sd: dynamic = this
                                sd.className = "w-5 h-5 text-indigo-400"
                                sd.fill = "none"
                                sd.viewBox = "0 0 24 24"
                                sd.strokeWidth = 1.5
                                sd.stroke = "currentColor"
                                path {
                                    val pd: dynamic = this
                                    pd.strokeLinecap = "round"
                                    pd.strokeLinejoin = "round"
                                    pd.d = "M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z"
                                }
                            }
                            span {
                                className = ClassName("text-xs font-bold text-slate-300 uppercase tracking-wider")
                                +"End-to-End Encrypted"
                            }
                        }
                        p {
                            className = ClassName("text-xs text-slate-400 leading-normal")
                            +"The requested data is decrypted strictly within your browser. The server only relays encrypted packets and cannot access your private data."
                        }
                    }
                    button {
                        className = ClassName("w-full py-4 bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 text-white font-semibold rounded-2xl transition-all duration-200 transform hover:scale-[1.02] shadow-lg shadow-indigo-600/30 cursor-pointer mt-4")
                        onClick = { handlePresent() }
                        +"Present Credentials"
                    }
                }
                VerifyStatus.PRESENTING -> {
                    div {
                        className = ClassName("relative flex items-center justify-center mb-4")
                        div {
                            className = ClassName("w-16 h-16 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin")
                        }
                        svg {
                            val sd: dynamic = this
                            sd.className = "w-6 h-6 text-indigo-400 absolute"
                            sd.fill = "none"
                            sd.viewBox = "0 0 24 24"
                            sd.strokeWidth = 1.5
                            sd.stroke = "currentColor"
                            path {
                                val pd: dynamic = this
                                pd.strokeLinecap = "round"
                                pd.strokeLinejoin = "round"
                                pd.d = "M10.5 1.5H8.25A2.25 2.25 0 006 3.75v16.5a2.25 2.25 0 002.25 2.25h7.5A2.25 2.25 0 0018 20.25V3.75a2.25 2.25 0 00-2.25-2.25H13.5m-3 0V3h3V1.5m-3 0h3m-3 18.75h3"
                            }
                        }
                    }
                    h1 {
                        className = ClassName("text-2xl font-bold text-slate-100")
                        +"Presenting Credential"
                    }
                    p {
                        className = ClassName("text-sm text-slate-400 max-w-xs")
                        +"Please follow your system prompt or wallet application prompts to authorize sharing."
                    }
                }
                VerifyStatus.SUCCESS -> {
                    svg {
                        val sd: dynamic = this
                        sd.className = "w-20 h-20 text-emerald-500 mb-2 animate-bounce"
                        sd.fill = "none"
                        sd.viewBox = "0 0 24 24"
                        sd.strokeWidth = 1.5
                        sd.stroke = "currentColor"
                        path {
                            val pd: dynamic = this
                            pd.strokeLinecap = "round"
                            pd.strokeLinejoin = "round"
                            pd.d = "M9 12.75L11.25 15 15 9.75M21 12c0 1.268-.63 2.39-1.593 3.068a3.745 3.745 0 01-1.043 3.296 3.745 3.745 0 01-3.296 1.043A3.745 3.745 0 0112 21c-1.268 0-2.39-.63-3.068-1.593a3.746 3.746 0 01-3.296-1.043 3.745 3.745 0 01-1.043-3.296A3.745 3.745 0 013 12c0-1.268.63-2.39 1.593-3.068a3.745 3.745 0 011.043-3.296 3.746 3.746 0 013.296-1.043A3.746 3.746 0 0112 3c1.268 0 2.39.63 3.068 1.593a3.746 3.746 0 013.296 1.043 3.746 3.746 0 011.043 3.296A3.745 3.745 0 0121 12z"
                        }
                    }
                    h1 {
                        className = ClassName("text-2xl font-extrabold text-slate-100 tracking-tight")
                        +"Verification Submitted"
                    }
                    p {
                        className = ClassName("text-sm text-slate-400 leading-relaxed max-w-xs")
                        +"Your credential response has been encrypted and securely sent. You may now return to the app or close this window."
                    }
                }
                VerifyStatus.ERROR -> {
                    svg {
                        val sd: dynamic = this
                        sd.className = "w-16 h-16 text-rose-500 mb-2"
                        sd.fill = "none"
                        sd.viewBox = "0 0 24 24"
                        sd.strokeWidth = 1.5
                        sd.stroke = "currentColor"
                        path {
                            val pd: dynamic = this
                            pd.strokeLinecap = "round"
                            pd.strokeLinejoin = "round"
                            pd.d = "M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z"
                        }
                    }
                    h1 {
                        className = ClassName("text-2xl font-bold text-slate-100")
                        +"Verification Failed"
                    }
                    p {
                        className = ClassName("text-sm text-rose-400 max-w-xs leading-normal")
                        +(errorMessage ?: "An unknown error occurred during presentation.")
                    }
                }
            }
        }
    }
}
