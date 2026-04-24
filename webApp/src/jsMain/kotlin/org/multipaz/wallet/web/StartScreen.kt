package org.multipaz.wallet.web

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientSignedInUser
import org.multipaz.wallet.shared.BuildConfig
import react.FC
import react.Props
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

private const val TAG = "StartScreen"

external interface StartScreenProps : Props {
    var walletClient: WalletClient
    var googleSignIn: GoogleSignIn
}

val StartScreen = FC<StartScreenProps> { props ->
    val (status, setStatus) = useState("")
    val (isSigningIn, setIsSigningIn) = useState(false)
    val (preFetchedNonce, setPreFetchedNonce) = useState<String?>(null)

    useEffectOnce {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val nonce = props.walletClient.getNonce()
                setPreFetchedNonce(nonce)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to pre-fetch nonce", e)
            }
        }
    }

    div {
        className = ClassName("flex flex-col items-center justify-center min-h-screen bg-slate-950 text-white p-4")

        div {
            className = ClassName("max-w-sm w-full space-y-8 flex flex-col items-center text-center")

            div {
                className = ClassName("relative")
                div {
                    className = ClassName("absolute -inset-1 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-full blur opacity-25")
                }
                img {
                    src = "https://apps.multipaz.org/multipaz-logo-400x400.png"
                    className = ClassName("relative h-32 w-32 rounded-full border-2 border-slate-800")
                }
            }

            div {
                h1 {
                    className = ClassName("text-4xl font-extrabold tracking-tight sm:text-5xl bg-clip-text text-transparent bg-gradient-to-b from-white to-slate-400")
                    +BuildConfig.APP_NAME
                }
                p {
                    className = ClassName("mt-4 text-lg text-slate-400 font-medium")
                    +"Securely store and share your credentials"
                }
            }

            div {
                className = ClassName("w-full pt-4")
                if (isSigningIn) {
                    div {
                        className = ClassName("flex flex-col items-center space-y-4")
                        div {
                            className = ClassName("animate-spin rounded-full h-10 w-10 border-b-2 border-blue-500")
                        }
                        p {
                            className = ClassName("text-sm text-blue-400 animate-pulse")
                            +status
                        }
                    }
                } else {
                    button {
                        className = ClassName("group relative w-full flex justify-center items-center py-3 px-4 border border-transparent text-lg font-semibold rounded-xl text-white bg-blue-600 hover:bg-blue-500 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-slate-900 focus:ring-blue-500 transition-all duration-200 shadow-lg shadow-blue-500/20")
                        disabled = isSigningIn
                        onClick = {
                            val nonce = preFetchedNonce
                            if (nonce == null) {
                                setStatus("Initializing... please wait.")
                            } else {
                                setIsSigningIn(true)
                                setStatus("Connecting to Google...")

                                val codePromise = props.googleSignIn.requestCode(
                                    scope = "openid profile email https://www.googleapis.com/auth/drive.appdata"
                                )

                                val scope = CoroutineScope(Dispatchers.Main)
                                scope.launch {
                                    try {
                                        val code = codePromise.await()
                                        setStatus("Authenticating...")
                                        val tokens = props.walletClient.exchangeCodeForTokens(nonce, code, "postmessage")
                                        
                                        setStatus("Retrieving user info...")
                                        val user = decodeUserFromIdToken(tokens.idToken) ?: throw Exception("Failed to get user info")
                                        
                                        setStatus("Retrieving encryption key...")
                                        val googleDrive = GoogleDrive(tokens.accessToken)
                                        val encryptionKey = googleDrive.retrieveOrCreateEncryptionKey(false)
                                        
                                        setStatus("Completing sign-in...")
                                        props.walletClient.signInWithGoogle(
                                            nonce = nonce,
                                            googleIdTokenString = tokens.idToken,
                                            signedInUser = user,
                                            walletBackendEncryptionKey = ByteString(encryptionKey),
                                            resetSharedData = false
                                        )
                                    } catch (e: Exception) {
                                        Logger.e(TAG, "Sign-in error", e)
                                        setStatus("Error: ${e.message}")
                                        setIsSigningIn(false)
                                    }
                                }
                            }
                        }

                        span {
                            className = ClassName("mr-3")
                            svg {
                                val sd: dynamic = this
                                sd.width = 20.0
                                sd.height = 20.0
                                sd.viewBox = "0 0 24 24"
                                path {
                                    val pd: dynamic = this
                                    pd.fill = "currentColor"
                                    pd.d = "M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                                }
                                path {
                                    val pd: dynamic = this
                                    pd.fill = "currentColor"
                                    pd.d = "M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                                }
                                path {
                                    val pd: dynamic = this
                                    pd.fill = "currentColor"
                                    pd.d = "M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                                }
                                path {
                                    val pd: dynamic = this
                                    pd.fill = "currentColor"
                                    pd.d = "M12 5.38c1.62 0 3.06.56 4.21 1.66l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 12-4.53z"
                                }
                            }
                        }
                        +"Sign In with Google"
                    }
                }
            }

            if (status.isNotEmpty() && !isSigningIn) {
                p {
                    className = ClassName("mt-4 text-sm text-red-400 bg-red-400/10 py-2 px-4 rounded-lg border border-red-400/20")
                    +status
                }
            }
        }
    }
}

private suspend fun decodeUserFromIdToken(idToken: String): WalletClientSignedInUser? {
    try {
        val payloadBase64Url = idToken.split(".")[1]
        var payloadBase64 = payloadBase64Url.replace("-", "+").replace("_", "/")
        while (payloadBase64.length % 4 != 0) {
            payloadBase64 += "="
        }
        val payloadJson = window.atob(payloadBase64)
        val payload = JSON.parse<dynamic>(payloadJson)
        
        val email = payload.email as String
        val name = payload.name as String
        val pictureUrl = payload.picture as String?
        
        return WalletClientSignedInUser(
            id = email,
            displayName = name,
            profilePicture = null,
            profilePictureUrl = pictureUrl
        )
    } catch (e: Exception) {
        return null
    }
}
