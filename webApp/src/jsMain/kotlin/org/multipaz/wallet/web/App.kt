package org.multipaz.wallet.web

import org.multipaz.wallet.client.WalletClient
import react.FC
import react.Props

external interface AppProps : Props {
    var walletClient: WalletClient
    var googleSignIn: GoogleSignIn
}

val App = FC<AppProps> { props ->
    val signedInUser = useFlow(props.walletClient.signedInUser)

    if (signedInUser != null) {
        WalletScreen {
            walletClient = props.walletClient
            this.signedInUser = signedInUser
        }
    } else {
        StartScreen {
            walletClient = props.walletClient
            googleSignIn = props.googleSignIn
        }
    }
}
