package org.multipaz.wallet.web

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.wallet.client.WalletClient
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useEffect
import react.useState
import web.cssom.ClassName

external interface AppProps : Props {
    var walletClient: WalletClient
    var googleSignIn: GoogleSignIn
    var documentTypeRepository: DocumentTypeRepository
    var documentStore: DocumentStore
    var documentModel: DocumentModel
}

sealed class Screen {
    object Wallet : Screen()
    data class DocumentInfo(val documentId: String) : Screen()
}

val App = FC<AppProps> { props ->
    val signedInUser = useFlow(props.walletClient.signedInUser)
    val (screen, setScreen) = useState<Screen>(Screen.Wallet)
    val (launched, setLaunched) = useState(false)

    // Run appJustLaunched when we have a signed in user, but only once.
    useEffect(signedInUser) {
        if (signedInUser != null && !launched) {
            setLaunched(true)
            val scope = CoroutineScope(Dispatchers.Main)
            scope.launch {
                appJustLaunched(
                    walletClient = props.walletClient,
                    documentStore = props.documentStore
                )
            }
        }
    }

    // Sync hash with state for routing
    useEffect(signedInUser) {
        if (signedInUser == null) return@useEffect

        val handleHashChange = {
            val hash = window.location.hash
            if (hash.startsWith("#document/")) {
                val documentId = hash.substring("#document/".length)
                setScreen(Screen.DocumentInfo(documentId))
            } else {
                setScreen(Screen.Wallet)
            }
        }

        window.addEventListener("hashchange", { handleHashChange() })
        handleHashChange() // Initial check

        Cleanup {
            window.removeEventListener("hashchange", { handleHashChange() })
        }
    }

    div {
        className = ClassName("min-h-screen bg-slate-100")
        if (signedInUser != null) {
            when (val s = screen) {
                is Screen.Wallet -> {
                    WalletScreen {
                        this.walletClient = props.walletClient
                        this.documentTypeRepository = props.documentTypeRepository
                        this.documentStore = props.documentStore
                        this.documentModel = props.documentModel
                    }
                }
                is Screen.DocumentInfo -> {
                    DocumentInfoScreen {
                        this.documentId = s.documentId
                        this.documentModel = props.documentModel
                        this.onBack = {
                            window.location.hash = ""
                        }
                    }
                }
            }
        } else {
            StartScreen {
                walletClient = props.walletClient
                googleSignIn = props.googleSignIn
            }
        }
    }
}
