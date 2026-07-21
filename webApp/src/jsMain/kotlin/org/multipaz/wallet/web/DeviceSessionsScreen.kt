package org.multipaz.wallet.web

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.ClientType
import org.multipaz.wallet.shared.Session
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useEffectOnce
import react.useState
import web.cssom.ClassName

private const val TAG = "DeviceSessionsScreen"

private fun formatLastSeen(lastSeenMillis: Long): String {
    val now = kotlin.js.Date.now()
    val diffSec = ((now - lastSeenMillis.toDouble()) / 1000.0).toLong()
    return when {
        diffSec < 60 -> "Active just now"
        diffSec < 3600 -> "Active ${diffSec / 60}m ago"
        diffSec < 86400 -> "Active ${diffSec / 3600}h ago"
        else -> "Active ${diffSec / 86400}d ago"
    }
}

external interface DeviceSessionsScreenProps : Props {
    var walletClient: WalletClient
    var settingsModel: SettingsModel
    var onBack: () -> Unit
}

val DeviceSessionsScreen = FC<DeviceSessionsScreenProps> { props ->
    val signedInUser = useFlow(props.walletClient.signedInUser)
    val (sessions, setSessions) = useState<List<Session>?>(null)
    val (currentClientId, setCurrentClientId) = useState<String?>(null)
    val (sessionToSignOut, setSessionToSignOut) = useState<Session?>(null)
    val (isLoading, setIsLoading) = useState(true)
    val (error, setError) = useState<String?>(null)

    val fetchSessions = {
        setIsLoading(true)
        setError(null)
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                if (currentClientId == null) {
                    try {
                        val id = props.walletClient.getClientId()
                        setCurrentClientId(id)
                    } catch (e: Exception) {
                        Logger.w(TAG, "Failed to get current clientId", e)
                    }
                }
                val list = props.walletClient.getSessions()
                setSessions(list)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load device sessions", e)
                setError(e.message ?: "Failed to load device sessions")
            } finally {
                setIsLoading(false)
            }
        }
    }

    useEffectOnce {
        fetchSessions()
    }

    div {
        className = ClassName("min-h-screen bg-slate-100 dark:bg-slate-900 flex flex-col transition-colors duration-300")

        AppBar {
            title = "Device sessions"
            settingsModel = props.settingsModel
            leftContent = FC {
                button {
                    className = ClassName("p-2 -ml-2 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-full transition-colors")
                    onClick = { props.onBack() }
                    BackIcon { }
                }
            }
            actions = FC {
                button {
                    className = ClassName("p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 rounded-full transition-all focus:outline-none ${if (isLoading) "animate-spin text-blue-500" else ""}")
                    title = "Refresh device sessions"
                    disabled = isLoading
                    onClick = { fetchSessions() }
                    RefreshIcon { }
                }
            }
        }

        main {
            className = ClassName("flex-grow max-w-2xl mx-auto px-4 sm:px-6 py-8 w-full space-y-6")

            if (signedInUser != null) {
                div {
                    className = ClassName("flex items-center justify-between px-1")
                    p {
                        className = ClassName("text-sm text-slate-500 dark:text-slate-400 font-medium")
                        +"Devices signed in with ${signedInUser.displayName ?: signedInUser.id}"
                    }
                    button {
                        className = ClassName("text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium flex items-center space-x-1")
                        onClick = { fetchSessions() }
                        +"Refresh"
                    }
                }
            }

            if (error != null) {
                div {
                    className = ClassName("p-4 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 text-sm")
                    +error
                }
            }

            if (isLoading && sessions == null) {
                div {
                    className = ClassName("flex flex-col items-center justify-center py-12 space-y-3")
                    div {
                        className = ClassName("animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500")
                    }
                    p {
                        className = ClassName("text-sm text-slate-400")
                        +"Loading device sessions..."
                    }
                }
            } else if (sessions != null) {
                if (sessions.isEmpty()) {
                    div {
                        className = ClassName("p-6 rounded-2xl bg-white dark:bg-slate-800 text-center text-slate-500 dark:text-slate-400")
                        +"No active device sessions found."
                    }
                } else {
                    FloatingItemList {
                        for (session in sessions) {
                            val deviceName = when (session.clientType) {
                                ClientType.WEB -> "Web Client"
                                ClientType.ANDROID -> "Android Device"
                                ClientType.IOS -> "iOS Device"
                            }
                            val deviceIcon = when (session.clientType) {
                                ClientType.WEB -> DevicesIcon
                                ClientType.ANDROID, ClientType.IOS -> PhoneIcon
                            }
                            val isCurrentDevice = (currentClientId != null && session.clientId == currentClientId)

                            div {
                                className = ClassName("w-full flex items-center justify-between px-6 py-4 hover:bg-slate-50/50 dark:hover:bg-slate-700/50 transition-colors")
                                div {
                                    className = ClassName("flex items-center space-x-4")
                                    div {
                                        className = ClassName("flex-shrink-0 w-10 h-10 flex items-center justify-center rounded-full bg-slate-100 dark:bg-slate-900 text-slate-600 dark:text-slate-300")
                                        deviceIcon { }
                                    }
                                    div {
                                        div {
                                            className = ClassName("text-sm font-semibold text-slate-900 dark:text-slate-100 flex items-center space-x-2")
                                            span { +deviceName }
                                            if (isCurrentDevice) {
                                                span {
                                                    className = ClassName("px-2 py-0.5 text-xs font-medium bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 rounded-full")
                                                    +"This device"
                                                }
                                            }
                                        }
                                        div {
                                            className = ClassName("text-xs text-slate-500 dark:text-slate-400 mt-0.5")
                                            +"${formatLastSeen(session.lastSeenMillis)} • ID: ${session.clientId.take(8)}"
                                        }
                                    }
                                }

                                if (!isCurrentDevice) {
                                    button {
                                        className = ClassName("px-3 py-1.5 text-xs font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 border border-red-200 dark:border-red-800/60 rounded-lg transition-colors focus:outline-none")
                                        onClick = {
                                            setSessionToSignOut(session)
                                        }
                                        +"Sign out"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            div { className = ClassName("h-8") }
        }

        if (sessionToSignOut != null) {
            val targetName = when (sessionToSignOut.clientType) {
                ClientType.WEB -> "Web Client"
                ClientType.ANDROID -> "Android Device"
                ClientType.IOS -> "iOS Device"
            }
            ConfirmationDialog {
                title = "Sign out $targetName?"
                message = "This device session will be signed out from your account and will lose access to synced wallet data."
                confirmButtonText = "Sign out"
                onCancel = { setSessionToSignOut(null) }
                onConfirm = {
                    val s = sessionToSignOut
                    setSessionToSignOut(null)
                    val scope = CoroutineScope(Dispatchers.Main)
                    scope.launch {
                        try {
                            props.walletClient.signOutSession(s.clientId)
                            fetchSessions()
                        } catch (e: Exception) {
                            Logger.e(TAG, "Failed to sign out session ${s.clientId}", e)
                            setError(e.message ?: "Failed to sign out session")
                        }
                    }
                }
            }
        }
    }
}
