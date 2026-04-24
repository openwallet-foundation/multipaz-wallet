package org.multipaz.wallet.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import kotlinx.browser.document
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.CredentialIssuer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import react.FC
import react.Props
import react.PropsWithChildren
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useState
import web.cssom.ClassName
import web.html.InputType
import web.file.FileReader

private const val TAG = "AddToWalletScreen"

external interface AddToWalletScreenProps : Props {
    var walletClient: WalletClient
    var settingsModel: SettingsModel
    var onCredentialIssuerClicked: (issuer: CredentialIssuer) -> Unit
    var onImportMpzPass: (encodedMpzPass: ByteString) -> Unit
    var onCredentialIssuerUrl: (url: String) -> Unit
    var onBack: () -> Unit
}

val AddToWalletScreen = FC<AddToWalletScreenProps> { props ->
    val (credentialIssuers, setCredentialIssuers) = useState<List<CredentialIssuer>?>(null)
    val (errorLoading, setErrorLoading) = useState<String?>(null)
    val devMode = useFlow(props.settingsModel.devMode)
    val provisioningServerUrl = useFlow(props.settingsModel.provisioningServerUrl)
    
    val (showUrlDialog, setShowUrlDialog) = useState(false)
    val (issuingServerUrl, setIssuingServerUrl) = useState(provisioningServerUrl)

    useEffect(Unit) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val issuers = props.walletClient.getCredentialIssuers()
                setCredentialIssuers(issuers)
            } catch (e: Exception) {
                Logger.w(TAG, "Error loading credential issuers", e)
                setErrorLoading(e.message ?: "Unknown error")
            }
        }
    }

    div {
        className = ClassName("min-h-screen bg-slate-100 dark:bg-slate-900 flex flex-col transition-colors duration-300")

        // Header
        AppBar {
            title = "Add to Wallet"
            settingsModel = props.settingsModel
            leftContent = FC {
                button {
                    className = ClassName("p-2 -ml-2 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-full transition-colors")
                    onClick = { props.onBack() }
                    BackIcon { }
                }
            }
        }

        main {
            className = ClassName("flex-grow max-w-2xl mx-auto px-4 sm:px-6 py-8 w-full space-y-8")

            div {
                className = ClassName("space-y-6")

                // Explainer text
                p {
                    className = ClassName("text-sm text-slate-600 dark:text-slate-400 px-2")
                    +"To add a new pass to your wallet, please select one of the available issuers below."
                }

                // Issuers List
                FloatingItemList {
                    if (errorLoading != null) {
                        div {
                            className = ClassName("px-6 py-8 text-center text-red-500")
                            +"Error loading issuers: $errorLoading"
                        }
                    } else if (credentialIssuers == null) {
                        div {
                            className = ClassName("px-6 py-8 text-center text-slate-400")
                            +"Loading issuers..."
                        }
                    } else {
                        credentialIssuers.forEach { issuer ->
                            FloatingItem {
                                key = issuer.name
                                title = issuer.name
                                onClick = {
                                    props.onCredentialIssuerClicked(issuer)
                                }
                                icon = FC {
                                    div {
                                        className = ClassName("w-full h-full flex items-center justify-center")
                                        img {
                                            className = ClassName("max-w-full max-h-full object-contain")
                                            src = issuer.iconUrl
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Import Pass
                div {
                    className = ClassName("space-y-2")
                    h2Section { +"More Options" }
                    FloatingItemList {
                        FloatingItem {
                            title = "Import from file"
                            subtitle = "Import a .mpzpass file"
                            icon = FileUploadIcon
                            onClick = {
                                val input: dynamic = document.createElement("input")
                                input.type = "file"
                                input.accept = ".mpzpass"
                                input.onchange = {
                                    val files = input.files
                                    if (files != null && files.length > 0) {
                                        val file = files[0]
                                        val reader: dynamic = FileReader()
                                        reader.onload = {
                                            val arrayBuffer = reader.result.unsafeCast<org.khronos.webgl.ArrayBuffer>()
                                            val uint8Array = Uint8Array(arrayBuffer, 0, arrayBuffer.byteLength)
                                            val byteArray = ByteArray(uint8Array.length)
                                            for (i in 0 until uint8Array.length) {
                                                byteArray[i] = uint8Array[i]
                                            }
                                            props.onImportMpzPass(ByteString(byteArray))
                                        }
                                        reader.readAsArrayBuffer(file)
                                    }
                                }
                                input.click()
                            }
                        }

                        if (devMode) {
                            FloatingItem {
                                title = "Enter Issuer URL"
                                subtitle = "Manually enter a provisioning server URL"
                                icon = AccountBalanceIcon
                                onClick = {
                                    setIssuingServerUrl(props.settingsModel.provisioningServerUrl.value)
                                    setShowUrlDialog(true)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom spacing
            div { className = ClassName("h-8") }
        }

        // Issuer URL Modal
        if (showUrlDialog) {
            div {
                className = ClassName("fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-in fade-in duration-200")
                onClick = { setShowUrlDialog(false) }

                div {
                    className = ClassName("bg-white dark:bg-slate-800 w-full max-w-md rounded-[2.5rem] shadow-2xl border border-slate-200 dark:border-slate-700 p-8 space-y-6 animate-in zoom-in-95 duration-200")
                    onClick = { it.stopPropagation() }

                    div {
                        className = ClassName("space-y-2")
                        h2 {
                            className = ClassName("text-2xl font-bold text-slate-900 dark:text-white")
                            +"Custom Issuer URL"
                        }
                        p {
                            className = ClassName("text-sm text-slate-500 dark:text-slate-400")
                            +"Enter the URL of the provisioning server you want to connect to."
                        }
                    }

                    div {
                        className = ClassName("space-y-4")
                        
                        div {
                            className = ClassName("space-y-1.5")
                            span {
                                className = ClassName("text-xs font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider ml-1")
                                +"Server URL"
                            }
                            input {
                                className = ClassName("w-full px-4 py-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all")
                                type = "url".unsafeCast<InputType>()
                                value = issuingServerUrl
                                onChange = { e -> setIssuingServerUrl(e.target.value) }
                                placeholder = "https://..."
                            }
                        }

                        button {
                            className = ClassName("text-sm text-blue-600 dark:text-blue-400 hover:underline ml-1 focus:outline-none")
                            onClick = {
                                val defaultUrl = "https://issuer.multipaz.org/issuer"
                                setIssuingServerUrl(defaultUrl)
                            }
                            +"Reset to default"
                        }
                    }

                    div {
                        className = ClassName("flex flex-col space-y-3 pt-2")
                        button {
                            className = ClassName("w-full py-4 bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white font-bold rounded-2xl shadow-lg shadow-blue-500/20 transition-all focus:outline-none")
                            onClick = {
                                props.settingsModel.provisioningServerUrl.value = issuingServerUrl
                                setShowUrlDialog(false)
                                props.onCredentialIssuerUrl(issuingServerUrl)
                            }
                            +"Connect"
                        }
                        button {
                            className = ClassName("w-full py-4 text-slate-600 dark:text-slate-400 font-semibold hover:bg-slate-50 dark:hover:bg-slate-700 rounded-2xl transition-all focus:outline-none")
                            onClick = { setShowUrlDialog(false) }
                            +"Cancel"
                        }
                    }
                }
            }
        }
    }
}

private val h2Section = FC<PropsWithChildren> { props ->
    div {
        className = ClassName("text-xs font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest px-2 mb-2")
        +props.children
    }
}
