package org.multipaz.wallet.web

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientSignedInUser
import org.multipaz.wallet.client.syncWithSharedData
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.Domains
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.useEffect
import react.useMemo
import react.useState
import web.cssom.ClassName

private const val TAG = "WalletScreen"

external interface WalletScreenProps : Props {
    var walletClient: WalletClient
    var documentTypeRepository: DocumentTypeRepository
    var documentStore: DocumentStore
    var documentModel: DocumentModel
    var settingsModel: SettingsModel
}

val WalletScreen = FC<WalletScreenProps> { props ->
    val (status, setStatus) = useState("Connected to backend")
    val (isRefreshing, setIsRefreshing) = useState(false)

    div {
        className = ClassName("min-h-screen bg-slate-100 dark:bg-slate-900 flex flex-col transition-colors duration-300")

        AppBar {
            title = BuildConfig.APP_NAME
            settingsModel = props.settingsModel
            leftContent = FC {
                img {
                    src = "https://apps.multipaz.org/multipaz-logo-400x400.png"
                    className = ClassName("h-8 w-8 rounded-lg")
                }
            }
            actions = FC {
                // Add Button
                button {
                    className = ClassName("p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 rounded-full transition-all focus:outline-none")
                    title = "Add to Wallet"
                    onClick = {
                        window.location.hash = "add"
                    }
                    PlusIcon { }
                }

                // Refresh Button
                button {
                    className = ClassName("p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 rounded-full transition-all focus:outline-none")
                    title = "Refresh data"
                    onClick = {
                        val scope = CoroutineScope(Dispatchers.Main)
                        scope.launch {
                            try {
                                setIsRefreshing(true)
                                setStatus("Refreshing...")
                                val newData = props.walletClient.refreshSharedData()
                                if (newData) {
                                    props.documentStore.syncWithSharedData(
                                        sharedData = props.walletClient.sharedData.value!!,
                                        mpzPassIsoMdocDomain = Domains.DOMAIN_MDOC_SOFTWARE,
                                        mpzPassSdJwtVcDomain = Domains.DOMAIN_SDJWT_SOFTWARE,
                                        mpzPassKeylessSdJwtVcDomain = Domains.DOMAIN_SDJWT_KEYLESS
                                    )
                                }
                                setStatus("Data updated")
                            } catch (e: Exception) {
                                Logger.e(TAG, "Error refreshing data", e)
                                setStatus("Refresh failed: ${e.message}")
                            } finally {
                                setIsRefreshing(false)
                            }
                        }
                    }
                    div {
                        className = ClassName(if (isRefreshing) "animate-spin" else "")
                        RefreshIcon()
                    }
                }

                // Avatar and Menu
                WalletAvatarMenu {
                    walletClient = props.walletClient
                    onSignOut = {
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
        }

        main {
            className = ClassName("flex-grow max-w-2xl mx-auto px-4 sm:px-6 py-10 w-full")

            DocumentList {
                documentModel = props.documentModel
            }
        }
    }
}

// --- Extracted Components ---

external interface DocumentListProps : Props {
    var documentModel: DocumentModel
}

val DocumentList = FC<DocumentListProps> { props ->
    val documentInfos = useFlow(props.documentModel.documentInfos)
    
    if (documentInfos.isEmpty()) {
        EmptyPassesPlaceholder()
    } else {
        FloatingItemList {
            documentInfos.forEach { documentInfo ->
                FloatingItemCard {
                    key = documentInfo.document.identifier
                    title = documentInfo.document.displayName ?: "Untitled Pass"
                    subtitle = documentInfo.document.typeDisplayName ?: "Pass"
                    cardArt = documentInfo.document.cardArt
                    onClick = { 
                        window.location.hash = "document/${documentInfo.document.identifier}"
                    }
                }
            }
        }
    }
}

external interface WalletAvatarMenuProps : Props {
    var walletClient: WalletClient
    var onSignOut: () -> Unit
}

val WalletAvatarMenu = FC<WalletAvatarMenuProps> { props ->
    val signedInUser = useFlow(props.walletClient.signedInUser)
    val (isMenuOpen, setIsMenuOpen) = useState(false)
    
    val profilePicture = useMemo(signedInUser) {
        signedInUser?.profilePicture
    }
    val avatarDataUrl = useImageUri(profilePicture)
    val effectiveAvatarUrl = signedInUser?.profilePictureUrl ?: avatarDataUrl

    // Close menu when clicking outside
    useEffect(isMenuOpen) {
        if (!isMenuOpen) return@useEffect
        val handler: (org.w3c.dom.events.Event) -> Unit = { setIsMenuOpen(false) }
        window.addEventListener("click", handler)
        Cleanup { window.removeEventListener("click", handler) }
    }

    div {
        className = ClassName("relative flex items-center")

        // Avatar Button
        button {
            className = ClassName("h-9 w-9 rounded-full overflow-hidden border border-slate-200 dark:border-slate-700 hover:ring-2 hover:ring-blue-100 dark:hover:ring-blue-900 transition-all focus:outline-none")
            onClick = { e ->
                e.stopPropagation()
                setIsMenuOpen(!isMenuOpen) 
            }

            if (effectiveAvatarUrl != null) {
                img {
                    src = effectiveAvatarUrl
                    className = ClassName("h-full w-full object-cover")
                }
            } else {
                div {
                    className = ClassName("h-full w-full bg-blue-600 flex items-center justify-center text-white font-bold text-sm")
                    +(signedInUser?.displayName?.take(1)?.uppercase() ?: "U")
                }
            }
        }

        // Popup Menu
        if (isMenuOpen && signedInUser != null) {
            div {
                className = ClassName("absolute right-0 top-14 w-80 bg-slate-100 dark:bg-slate-800 rounded-[2.5rem] shadow-balanced dark:shadow-balanced-dark border border-slate-200 dark:border-slate-700 p-3 z-50 animate-in fade-in zoom-in duration-200 origin-top-right")
                onClick = { it.stopPropagation() }

                // Identity Header (Centered on grey)
                div {
                    className = ClassName("pt-8 pb-6 flex flex-col items-center")
                    div {
                        className = ClassName("h-20 w-20 rounded-full overflow-hidden border-4 border-white dark:border-slate-700 shadow-sm mb-4")
                        if (effectiveAvatarUrl != null) {
                            img {
                                src = effectiveAvatarUrl
                                className = ClassName("h-full w-full object-cover")
                            }
                        } else {
                            div {
                                className = ClassName("h-full w-full bg-blue-600 flex items-center justify-center text-white font-bold text-3xl")
                                +(signedInUser.displayName?.take(1)?.uppercase() ?: "U")
                            }
                        }
                    }
                    h2 {
                        className = ClassName("text-lg font-bold text-slate-900 dark:text-white")
                        +(signedInUser.displayName ?: "User")
                    }
                    p {
                        className = ClassName("text-sm text-slate-500 dark:text-slate-400")
                        +signedInUser.id
                    }
                }

                // Actions (White floating list)
                FloatingItemList {
                    FloatingItemText {
                        icon = SettingsIcon
                        title = "Settings"
                        onClick = { /* Future: Navigate to settings */ }
                    }

                    FloatingItemText {
                        icon = LogoutIcon
                        title = "Sign out"
                        onClick = { props.onSignOut() }
                    }
                }
                
                // Version display
                div {
                    className = ClassName("mt-4 text-center")
                    p {
                        className = ClassName("text-xs text-slate-400 dark:text-slate-500")
                        +"${BuildConfig.APP_NAME} v${BuildConfig.VERSION}"
                    }
                }

                // Bottom padding for aesthetics
                div { className = ClassName("h-2") }
            }
        }
    }
}

val EmptyPassesPlaceholder = FC<Props> {
    div {
        className = ClassName("text-center py-20 bg-white dark:bg-slate-800 rounded-3xl border-2 border-dashed border-slate-200 dark:border-slate-700 transition-colors duration-300")
        h2 {
            className = ClassName("mt-4 text-lg font-medium text-slate-900 dark:text-white")
            +"No passes found"
        }
        p {
            className = ClassName("mt-1 text-slate-500 dark:text-slate-400")
            +"Passes you add will appear here"
        }
    }
}

// --- Icon Components ---

val SparklesIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-5 w-5"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"
        }
    }
}

val SunIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-5 w-5"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M12 3v1m0 16v1m9-9h-1M4 12H3m15.364-6.364l-.707.707M6.343 17.657l-.707.707m12.728 0l-.707-.707M6.343 6.343l-.707-.707M12 5a7 7 0 100 14 7 7 0 000-14z"
        }
    }
}

val MoonIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-5 w-5"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z"
        }
    }
}

val RefreshIcon = FC<Props> {
    svg {
        val d: dynamic = this
        d.className = "h-5 w-5"
        d.fill = "none"
        d.viewBox = "0 0 24 24"
        d.stroke = "currentColor"
        path {
            val pd: dynamic = this
            pd.strokeLinecap = "round"
            pd.strokeLinejoin = "round"
            pd.strokeWidth = 2.0
            pd.d = "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
        }
    }
}
