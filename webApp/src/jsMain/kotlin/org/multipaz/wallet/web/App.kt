package org.multipaz.wallet.web

import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.wallet.client.WalletClient
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

external interface AppProps : Props {
    var walletClient: WalletClient
    var googleSignIn: GoogleSignIn
    var documentTypeRepository: DocumentTypeRepository
}

val App = FC<AppProps> { props ->
    val signedInUser = useFlow(props.walletClient.signedInUser)

    div {
        className = ClassName("min-h-screen bg-slate-50")
        if (signedInUser != null) {
            WalletScreen {
                walletClient = props.walletClient
                this.signedInUser = signedInUser
                documentTypeRepository = props.documentTypeRepository
            }
        } else {
            StartScreen {
                walletClient = props.walletClient
                googleSignIn = props.googleSignIn
            }
        }
    }
}
