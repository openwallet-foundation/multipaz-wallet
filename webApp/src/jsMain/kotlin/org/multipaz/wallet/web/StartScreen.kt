package org.multipaz.wallet.web

import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientSignedInUser
import react.FC
import react.Props
import react.StateSetter
import react.useEffectOnce
import react.useState
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import web.cssom.AlignItems
import web.cssom.Color
import web.cssom.Cursor
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FontFamily
import web.cssom.JustifyContent
import web.cssom.None
import web.cssom.Padding
import web.cssom.Position
import web.cssom.TextAlign
import web.cssom.px
import web.cssom.rem
import web.cssom.vh
import web.cssom.vw
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.call.body
import kotlinx.io.bytestring.ByteString

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
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            minHeight = 100.vh
            width = 100.vw
            position = Position.fixed
            top = 0.px
            left = 0.px
            backgroundColor = Color("#0D1B2A")
            color = Color("#FFFFFF")
            fontFamily = FontFamily.sansSerif
        }

        img {
            src = "https://apps.multipaz.org/multipaz-logo-400x400.png"
            css {
                width = 200.px
                marginBottom = 24.px
            }
        }

        h1 {
            +"Multipaz Wallet"
            css {
                marginBottom = 8.px
                fontSize = 2.5.rem
            }
        }

        p {
            +"Securely store and share your credentials"
            css {
                marginBottom = 40.px
                color = Color("#A0A0A0")
            }
        }

        button {
            +"Sign In with Google"
            disabled = isSigningIn
            css {
                padding = Padding(12.px, 24.px)
                fontSize = 18.px
                backgroundColor = if (isSigningIn) Color("#A0A0A0") else Color("#4285F4")
                color = Color("#FFFFFF")
                border = None.none
                borderRadius = 4.px
                cursor = if (isSigningIn) Cursor.default else Cursor.pointer
            }
            onClick = {
                val nonce = preFetchedNonce
                if (nonce == null) {
                    setStatus("Initializing... please wait.")
                } else {
                    setIsSigningIn(true)
                    setStatus("Connecting to Google...")

                    // 1. Request Code (Synchronous to satisfy Safari/Firefox popup requirements)
                    val codePromise = props.googleSignIn.requestCode(
                        scope = "openid profile email https://www.googleapis.com/auth/drive.appdata"
                    )

                    val scope = CoroutineScope(Dispatchers.Main)
                    scope.launch {
                        try {
                            val code = codePromise.await()
                            
                            setStatus("Authenticating...")
                            
                            // 2. Exchange Code for Tokens via Backend
                            val tokens = props.walletClient.exchangeCodeForTokens(nonce, code, "postmessage")
                            
                            setStatus("Retrieving user info...")
                            val user = decodeUserFromIdToken(tokens.idToken) ?: throw Exception("Failed to get user info")
                            
                            setStatus("Retrieving encryption key...")
                            val googleDrive = GoogleDrive(tokens.accessToken)
                            val encryptionKey = googleDrive.retrieveOrCreateEncryptionKey(false)
                            
                            setStatus("Completing sign-in with backend...")
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
        }

        p {
            +status
            css {
                marginTop = 20.px
                color = Color("#4285F4")
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
        
        val profilePicture = if (pictureUrl != null) {
            try {
                val httpClient = HttpClient(Js)
                val response = httpClient.get(pictureUrl)
                ByteString(response.body<ByteArray>())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        return WalletClientSignedInUser(
            id = email,
            displayName = name,
            profilePicture = profilePicture
        )
    } catch (e: Exception) {
        return null
    }
}
