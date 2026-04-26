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
}

val WalletScreen = FC<WalletScreenProps> { props ->
    val (status, setStatus) = useState("Connected to backend")
    val (isRefreshing, setIsRefreshing) = useState(false)

    div {
        className = ClassName("min-h-screen bg-slate-100 flex flex-col")

        WalletHeader {
            walletClient = props.walletClient
            refreshing = isRefreshing
            onRefresh = {
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

        main {
            className = ClassName("flex-grow max-w-2xl mx-auto px-4 sm:px-6 py-10 w-full")

            DocumentList {
                documentModel = props.documentModel
            }
        }

        StatusFooter {
            this.status = status
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

external interface WalletHeaderProps : Props {
    var walletClient: WalletClient
    var onSignOut: () -> Unit
    var onRefresh: () -> Unit
    var refreshing: Boolean
}

val WalletHeader = FC<WalletHeaderProps> { props ->
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
                        +BuildConfig.APP_NAME
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
                            className = ClassName("absolute right-0 top-14 w-80 bg-slate-100 rounded-[2.5rem] shadow-2xl border border-slate-200 p-3 z-50 animate-in fade-in zoom-in duration-200 origin-top-right")
                            onClick = { it.stopPropagation() }

                            // Identity Header (Centered on grey)
                            div {
                                className = ClassName("pt-8 pb-6 flex flex-col items-center")
                                div {
                                    className = ClassName("h-20 w-20 rounded-full overflow-hidden border-4 border-white shadow-sm mb-4")
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
                                    className = ClassName("text-lg font-bold text-slate-900")
                                    +(signedInUser.displayName ?: "User")
                                }
                                p {
                                    className = ClassName("text-sm text-slate-500")
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
        h2 {
            className = ClassName("mt-4 text-lg font-medium text-slate-900")
            +"No passes found"
        }
        p {
            className = ClassName("mt-1 text-slate-500")
            +"Passes you add will appear here"
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
                +"${BuildConfig.APP_NAME} v${BuildConfig.VERSION}"
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
