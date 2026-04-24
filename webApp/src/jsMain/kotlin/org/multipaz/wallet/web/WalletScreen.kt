package org.multipaz.wallet.web

import emotion.react.css
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientSignedInUser
import org.multipaz.wallet.shared.CredentialIssuer
import react.FC
import react.Props
import react.useEffectOnce
import react.useState
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import web.cssom.AlignItems
import web.cssom.Auto
import web.cssom.Border
import web.cssom.Color
import web.cssom.Cursor
import web.cssom.Display
import web.cssom.FontFamily
import web.cssom.JustifyContent
import web.cssom.LineStyle
import web.cssom.Margin
import web.cssom.Padding
import web.cssom.Position
import web.cssom.px
import web.cssom.vh
import web.cssom.vw

private const val TAG = "WalletScreen"

external interface WalletScreenProps : Props {
    var walletClient: WalletClient
    var signedInUser: WalletClientSignedInUser
}

val WalletScreen = FC<WalletScreenProps> { props ->
    val (issuers, setIssuers) = useState<List<CredentialIssuer>>(emptyList())
    val (status, setStatus) = useState("Connected to backend")

    useEffectOnce {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val issuersList = props.walletClient.getCredentialIssuers()
                setIssuers(issuersList)
            } catch (e: Exception) {
                Logger.e(TAG, "Error fetching issuers", e)
                setStatus("Error: ${e.message}")
            }
        }
    }

    div {
        css {
            padding = 40.px
            fontFamily = FontFamily.sansSerif
            color = Color("#333333")
            minHeight = 100.vh
            width = 100.vw
            position = Position.absolute
            top = 0.px
            left = 0.px
            backgroundColor = Color("#FFFFFF") // Wallet screen uses white background
        }

        div {
            css {
                maxWidth = 800.px
                margin = Margin(0.px, Auto.auto)
            }

            div {
                css {
                    display = Display.flex
                    justifyContent = JustifyContent.spaceBetween
                    alignItems = AlignItems.center
                    borderBottom = Border(1.px, LineStyle.solid, Color("#EEEEEE"))
                    paddingBottom = 20.px
                    marginBottom = 40.px
                }

                div {
                    h2 { +"Multipaz Wallet" }
                    p {
                        +"Signed in as: "
                        b { +(props.signedInUser.displayName ?: props.signedInUser.id) }
                        +" (${props.signedInUser.id})"
                    }
                }

                button {
                    +"Sign Out"
                    css {
                        padding = Padding(8.px, 16.px)
                        backgroundColor = Color("#FFFFFF")
                        border = Border(1.px, LineStyle.solid, Color("#CCCCCC"))
                        borderRadius = 4.px
                        cursor = Cursor.pointer
                    }
                    onClick = {
                        val scope = CoroutineScope(Dispatchers.Main)
                        scope.launch {
                            try {
                                props.walletClient.signOut()
                            } catch (e: Exception) {
                                Logger.e(TAG, "Error signing out", e)
                            }
                        }
                    }
                }
            }

            p { +status }

            h3 { +"Credential Issuers" }
            div {
                css {
                    display = Display.grid
                    this.asDynamic()["gridTemplateColumns"] = "repeat(auto-fill, minmax(200px, 1fr))"
                    gap = 20.px
                    marginTop = 20.px
                }
                
                issuers.forEach { issuer ->
                    div {
                        key = issuer.name
                        css {
                            padding = 20.px
                            border = Border(1.px, LineStyle.solid, Color("#EEEEEE"))
                            borderRadius = 8.px
                            backgroundColor = Color("#F9F9F9")
                        }
                        b { +issuer.name }
                    }
                }
            }
        }
    }
}
