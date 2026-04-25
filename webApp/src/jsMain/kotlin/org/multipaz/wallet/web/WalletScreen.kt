package org.multipaz.wallet.web

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientSignedInUser
import org.multipaz.mpzpass.MpzPass
import org.multipaz.wallet.shared.BuildConfig
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
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

private const val TAG = "WalletScreen"

external interface WalletScreenProps : Props {
    var walletClient: WalletClient
    var signedInUser: WalletClientSignedInUser
    var documentTypeRepository: DocumentTypeRepository
}

val WalletScreen = FC<WalletScreenProps> { props ->
    val sharedData = useFlow(props.walletClient.sharedData)
    val (passes, setPasses) = useState<List<MpzPass>>(emptyList())
    val (status, setStatus) = useState("Connected to backend")
    val (isRefreshing, setIsRefreshing) = useState(false)
    val (selectedPass, setSelectedPass) = useState<MpzPass?>(null)

    useEffect(sharedData) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val passesList = sharedData?.getMpzPasses() ?: emptyList()
                setPasses(passesList)
                
                // Keep selected pass updated if it still exists
                selectedPass?.let { current ->
                    val updated = passesList.find { it.uniqueId == current.uniqueId }
                    if (updated != null) {
                        setSelectedPass(updated)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error fetching passes", e)
                setStatus("Error: ${e.message}")
            }
        }
    }

    div {
        className = ClassName("min-h-screen bg-slate-50 flex flex-col")

        WalletHeader {
            user = props.signedInUser
            refreshing = isRefreshing
            onRefresh = {
                val scope = CoroutineScope(Dispatchers.Main)
                scope.launch {
                    try {
                        setIsRefreshing(true)
                        setStatus("Refreshing...")
                        props.walletClient.refreshSharedData()
                        setStatus("Data updated")
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error refreshing data", e)
                        setStatus("Refresh failed: ${e.message}")
                    } finally {
                        setIsRefreshing(false)
                    }
                }
            }
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

        if (selectedPass != null) {
            PassDetailScreen {
                pass = selectedPass
                documentTypeRepository = props.documentTypeRepository
                onBack = { setSelectedPass(null) }
            }
        } else {
            main {
                className = ClassName("flex-grow max-w-2xl mx-auto px-4 sm:px-6 py-10 w-full")
                
                div {
                    className = ClassName("mb-8")
                    h1 {
                        className = ClassName("text-3xl font-bold text-slate-900")
                        +"Multipaz passes"
                    }
                    p {
                        className = ClassName("mt-2 text-slate-600")
                        +"A list of passes you've imported into your Multipaz Wallet."
                    }
                }

                if (passes.isEmpty()) {
                    EmptyPassesPlaceholder()
                } else {
                    FloatingItemList {
                        passes.forEach { pass ->
                            FloatingItemCard {
                                key = pass.uniqueId
                                title = pass.name ?: "Untitled Pass"
                                subtitle = pass.typeName ?: "Multipaz Pass"
                                cardArt = pass.cardArt
                                onClick = { setSelectedPass(pass) }
                            }
                        }
                    }
                }
            }
        }

        StatusFooter {
            this.status = status
        }
    }
}

// --- Extracted Components ---

external interface WalletHeaderProps : Props {
    var user: WalletClientSignedInUser
    var onSignOut: () -> Unit
    var onRefresh: () -> Unit
    var refreshing: Boolean
}

val WalletHeader = FC<WalletHeaderProps> { props ->
    val (isMenuOpen, setIsMenuOpen) = useState(false)
    val avatarUrl = useImageUri(props.user.profilePicture)

    // Close menu when clicking outside
    useEffect(isMenuOpen) {
        if (!isMenuOpen) return@useEffect
        val handler: (org.w3c.dom.events.Event) -> Unit = { setIsMenuOpen(false) }
        window.addEventListener("click", handler)
        Cleanup { window.removeEventListener("click", handler) }
    }

    header {
        className = ClassName("sticky top-0 bg-white border-b border-slate-200 z-50 h-16")
        div {
            className = ClassName("max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-full")
            div {
                className = ClassName("grid grid-cols-3 h-full items-center")
                
                // Left: Logo
                div {
                    className = ClassName("flex justify-start")
                    img {
                        src = "https://apps.multipaz.org/multipaz-logo-400x400.png"
                        className = ClassName("h-8 w-8 rounded-lg")
                    }
                }

                // Center: Title
                div {
                    className = ClassName("flex justify-center")
                    span {
                        className = ClassName("text-xl font-bold text-slate-900 tracking-tight")
                        +"Multipaz Wallet"
                    }
                }

                // Right: Actions & Avatar
                div {
                    className = ClassName("flex justify-end items-center space-x-3 relative")
                    
                    // Refresh Button
                    button {
                        className = ClassName("p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-50 rounded-full transition-all focus:outline-none")
                        title = "Refresh data"
                        onClick = { props.onRefresh() }
                        div {
                            className = ClassName(if (props.refreshing) "animate-spin" else "")
                            RefreshIcon()
                        }
                    }

                    // Avatar Button
                    button {
                        className = ClassName("h-9 w-9 rounded-full overflow-hidden border border-slate-200 hover:ring-2 hover:ring-blue-100 transition-all focus:outline-none")
                        onClick = { e ->
                            e.stopPropagation()
                            setIsMenuOpen(!isMenuOpen) 
                        }
                        
                        if (avatarUrl != null) {
                            img {
                                src = avatarUrl
                                className = ClassName("h-full w-full object-cover")
                            }
                        } else {
                            div {
                                className = ClassName("h-full w-full bg-blue-600 flex items-center justify-center text-white font-bold text-sm")
                                +(props.user.displayName?.take(1)?.uppercase() ?: "U")
                            }
                        }
                    }

                    // Popup Menu (Google 1P Style)
                    if (isMenuOpen) {
                        div {
                            className = ClassName("absolute right-0 top-14 w-80 bg-slate-100 rounded-[2.5rem] shadow-2xl border border-slate-200 p-3 z-50 animate-in fade-in zoom-in duration-200 origin-top-right")
                            onClick = { it.stopPropagation() }

                            // Identity Header (Centered on grey)
                            div {
                                className = ClassName("pt-8 pb-6 flex flex-col items-center")
                                div {
                                    className = ClassName("h-20 w-20 rounded-full overflow-hidden border-4 border-white shadow-sm mb-4")
                                    if (avatarUrl != null) {
                                        img {
                                            src = avatarUrl
                                            className = ClassName("h-full w-full object-cover")
                                        }
                                    } else {
                                        div {
                                            className = ClassName("h-full w-full bg-blue-600 flex items-center justify-center text-white font-bold text-3xl")
                                            +(props.user.displayName?.take(1)?.uppercase() ?: "U")
                                        }
                                    }
                                }
                                h2 {
                                    className = ClassName("text-lg font-bold text-slate-900")
                                    +(props.user.displayName ?: "User")
                                }
                                p {
                                    className = ClassName("text-sm text-slate-500")
                                    +props.user.id
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
                            
                            // Bottom padding for aesthetics
                            div { className = ClassName("h-2") }
                        }
                    }
                }
            }
        }
    }
}

val EmptyPassesPlaceholder = FC<Props> {
    div {
        className = ClassName("text-center py-20 bg-white rounded-3xl border-2 border-dashed border-slate-200")
        div {
            className = ClassName("mx-auto h-12 w-12 text-slate-300")
            TicketIcon()
        }
        h2 {
            className = ClassName("mt-4 text-lg font-medium text-slate-900")
            +"No passes found"
        }
        p {
            className = ClassName("mt-1 text-slate-500")
            +"You haven't imported any Multipaz passes yet."
        }
    }
}

external interface StatusFooterProps : Props {
    var status: String
}

val StatusFooter = FC<StatusFooterProps> { props ->
    div {
        className = ClassName("bg-white border-t border-slate-200 py-4")
        div {
            className = ClassName("max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center justify-between")
            div {
                className = ClassName("flex items-center space-x-2")
                div {
                    className = ClassName("h-2 w-2 rounded-full bg-green-500 animate-pulse")
                }
                span {
                    className = ClassName("text-xs font-medium text-slate-500 uppercase tracking-wider")
                    +props.status
                }
            }
            p {
                className = ClassName("text-xs text-slate-400")
                +"Multipaz Wallet v${BuildConfig.VERSION}"
            }
        }
    }
}

// --- Icon Components ---

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
