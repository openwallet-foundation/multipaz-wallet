package org.multipaz.wallet.web

import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.cbor.Cbor
import org.multipaz.document.DocumentStore
import org.multipaz.document.ImportMpzPassException
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mpzpass.MpzPass
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.deleteDocumentFromWalletBackend
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.Domains
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useEffect
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

private const val TAG = "App"

external interface AppProps : Props {
    var walletClient: WalletClient
    var googleSignIn: GoogleSignIn
    var documentTypeRepository: DocumentTypeRepository
    var documentStore: DocumentStore
    var documentModel: DocumentModel
    var settingsModel: SettingsModel
}

sealed class Screen {
    object Wallet : Screen()
    object AddToWallet : Screen()
    data class DocumentInfo(val documentId: String) : Screen()
}

val App = FC<AppProps> { props ->
    val signedInUser = useFlow(props.walletClient.signedInUser)
    val (screen, setScreen) = useState<Screen>(Screen.Wallet)
    val (launched, setLaunched) = useState(false)
    val darkMode = useFlow(props.settingsModel.darkMode)
    val (error, setError) = useState<String?>(null)
    val (deleteConfirmationId, setDeleteConfirmationId) = useState<String?>(null)

    useEffectOnce {
        window.document.title = BuildConfig.APP_NAME
    }

    // Dark Mode management
    useEffect(darkMode) {
        val root = window.document.documentElement!!
        
        fun updateTheme() {
            val isDark = when (darkMode) {
                DarkMode.DARK -> true
                DarkMode.LIGHT -> false
                DarkMode.AUTO -> window.matchMedia("(prefers-color-scheme: dark)").matches
            }
            if (isDark) {
                root.classList.add("dark")
            } else {
                root.classList.remove("dark")
            }
        }

        updateTheme()

        if (darkMode == DarkMode.AUTO) {
            val mediaQuery = window.matchMedia("(prefers-color-scheme: dark)")
            val listener: (dynamic) -> Unit = { _ -> updateTheme() }
            mediaQuery.addEventListener("change", listener)
            Cleanup {
                mediaQuery.removeEventListener("change", listener)
            }
        }
    }

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
            } else if (hash == "#add") {
                setScreen(Screen.AddToWallet)
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
        className = ClassName("min-h-screen bg-slate-100 dark:bg-slate-900 transition-colors duration-300")
        if (signedInUser != null) {
            when (val s = screen) {
                is Screen.Wallet -> {
                    WalletScreen {
                        this.walletClient = props.walletClient
                        this.documentTypeRepository = props.documentTypeRepository
                        this.documentStore = props.documentStore
                        this.documentModel = props.documentModel
                        this.settingsModel = props.settingsModel
                    }
                }
                is Screen.AddToWallet -> {
                    AddToWalletScreen {
                        this.walletClient = props.walletClient
                        this.settingsModel = props.settingsModel
                        this.onCredentialIssuerClicked = { issuer ->
                            setError("Provisioning credentials is not currently supported")
                        }
                        this.onImportMpzPass = { encodedMpzPass ->
                            val scope = CoroutineScope(Dispatchers.Main)
                            scope.launch {
                                try {
                                    val pass = MpzPass.fromDataItem(Cbor.decode(encodedMpzPass.toByteArray()))
                                    val existingDoc = props.documentStore.listDocuments().find { it.mpzPassId == pass.uniqueId }
                                    existingDoc?.mpzPassVersion?.let { existingVersion ->
                                        if (existingVersion >= pass.version) {
                                            setError("This pass is already in your wallet")
                                            return@launch
                                        }
                                    }
                                    walletClient.refreshSharedData()
                                    walletClient.setSharedData(
                                        walletClient.sharedData.value!!
                                            .removeMpzPass(pass)
                                            .addMpzPass(pass)
                                    )
                                    val document = props.documentStore.importMpzPass(
                                        mpzPass = pass,
                                        isoMdocDomain = Domains.DOMAIN_MDOC_SOFTWARE,
                                        sdJwtVcDomain = Domains.DOMAIN_SDJWT_SOFTWARE,
                                        keylessSdJwtVcDomain = Domains.DOMAIN_SDJWT_KEYLESS
                                    )
                                    window.location.hash = ""
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Logger.e(TAG, "Error importing .mpzpass", e)
                                    setError(e.message ?: "An unknown error occurred while importing the pass")
                                }

                            }
                        }
                        this.onCredentialIssuerUrl = { url ->
                            setError("Provisioning credentials is not currently supported")
                        }
                        this.onBack = {
                            window.location.hash = ""
                        }
                    }
                }
                is Screen.DocumentInfo -> {
                    DocumentInfoScreen {
                        this.documentId = s.documentId
                        this.documentModel = props.documentModel
                        this.settingsModel = props.settingsModel
                        this.onBack = {
                            window.location.hash = ""
                        }
                        this.onDelete = {
                            setDeleteConfirmationId(s.documentId)
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

    // Error Dialog
    if (error != null) {
        ErrorDialog {
            title = "Error"
            message = error
            onDismiss = { setError(null) }
        }
    }

    // Confirmation Dialog
    if (deleteConfirmationId != null) {
        ConfirmationDialog {
            title = "Delete pass?"
            message = "This pass will be removed from your wallet on all devices you are signed in to. If you " +
                    "want to add it again, you will need to start the process from the beginning."
            confirmButtonText = "Delete"
            onConfirm = {
                val scope = CoroutineScope(Dispatchers.Main)
                scope.launch {
                    try {
                        val document = props.documentStore.lookupDocument(deleteConfirmationId)
                        document?.let {
                            props.documentStore.deleteDocumentFromWalletBackend(
                                document = document,
                                walletClient = props.walletClient,
                            )
                        }
                        setDeleteConfirmationId(null)
                        window.location.hash = ""
                    } catch (e: Exception) {
                        setDeleteConfirmationId(null)
                        if (e is CancellationException) throw e
                        setError(e.message ?: "An unknown error occurred while deleting the pass")
                    }
                }
            }
            onCancel = {
                setDeleteConfirmationId(null)
            }
        }
    }
}
