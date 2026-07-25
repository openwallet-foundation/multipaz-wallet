package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Instant
import org.multipaz.compose.datetime.durationFromNowText
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemContainer
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.ClientType
import org.multipaz.wallet.shared.Session

private const val TAG = "DeviceSessionsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSessionsScreen(
    walletClient: WalletClient,
    onBackClicked: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val signedInData = walletClient.signedInUser.collectAsState().value

    // Pop back if user signs out
    LaunchedEffect(signedInData) {
        if (signedInData == null) {
            onBackClicked()
        }
    }

    val sessions = remember { mutableStateOf<List<Session>?>(null) }
    val currentClientId = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val error = remember { mutableStateOf<String?>(null) }
    val sessionToSignOut = remember { mutableStateOf<Session?>(null) }

    fun fetchSessions() {
        coroutineScope.launch {
            isLoading.value = true
            error.value = null
            try {
                if (currentClientId.value == null) {
                    try {
                        currentClientId.value = walletClient.getClientId()
                    } catch (e: Exception) {
                        Logger.w(TAG, "Failed to get current clientId", e)
                    }
                }
                sessions.value = walletClient.getSessions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e(TAG, "Failed to load device sessions", e)
                error.value = e.message ?: "Failed to load device sessions"
            } finally {
                isLoading.value = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchSessions()
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.device_sessions_screen_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { fetchSessions() },
                        enabled = !isLoading.value
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val accountIdentifier = signedInData?.id ?: stringResource(R.string.device_sessions_screen_your_account)
            Note(stringResource(R.string.device_sessions_screen_blurb, accountIdentifier))

            FloatingItemList {
                val sessionList = sessions.value
                if (isLoading.value && sessionList == null) {
                    FloatingItemContainer {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(4.dp),
                                strokeWidth = 1.dp
                            )
                            Text(
                                text = stringResource(R.string.device_sessions_screen_loading_sessions),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.secondary,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                } else if (sessionList != null) {
                    if (sessionList.isEmpty()) {
                        FloatingItemCenteredText(
                            text = stringResource(R.string.device_sessions_screen_no_sessions)
                        )
                    } else {
                        val sortedSessions = sessionList.sortedWith(
                            compareByDescending<Session> { currentClientId.value != null && it.clientId == currentClientId.value }
                                .thenByDescending { it.lastSeenMillis }
                        )
                        for (session in sortedSessions) {
                            val deviceName = when (session.clientType) {
                                ClientType.WEB -> stringResource(R.string.device_sessions_screen_device_web)
                                ClientType.ANDROID -> stringResource(R.string.device_sessions_screen_device_android)
                                ClientType.IOS -> stringResource(R.string.device_sessions_screen_device_ios)
                            }
                            val deviceIcon = when (session.clientType) {
                                ClientType.WEB -> Icons.Outlined.Computer
                                ClientType.ANDROID, ClientType.IOS -> Icons.Outlined.Smartphone
                            }
                            val isCurrentDevice = (currentClientId.value != null && session.clientId == currentClientId.value)
                            val lastSeenText = durationFromNowText(Instant.fromEpochMilliseconds(session.lastSeenMillis))
                            val secondaryText = if (isCurrentDevice) {
                                "${stringResource(R.string.device_sessions_screen_this_device)} • $lastSeenText"
                            } else {
                                lastSeenText
                            }

                            FloatingItemText(
                                text = deviceName,
                                secondary = secondaryText,
                                image = {
                                    Icon(deviceIcon, contentDescription = null)
                                },
                                trailingContent = {
                                    if (!isCurrentDevice) {
                                        IconButton(
                                            onClick = { sessionToSignOut.value = session }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                                contentDescription = stringResource(R.string.device_sessions_screen_sign_out_session),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            error.value?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    sessionToSignOut.value?.let { targetSession ->
        val targetName = when (targetSession.clientType) {
            ClientType.WEB -> stringResource(R.string.device_sessions_screen_device_web)
            ClientType.ANDROID -> stringResource(R.string.device_sessions_screen_device_android)
            ClientType.IOS -> stringResource(R.string.device_sessions_screen_device_ios)
        }
        AlertDialog(
            onDismissRequest = { sessionToSignOut.value = null },
            title = {
                Text(stringResource(R.string.device_sessions_screen_sign_out_session_dialog_title, targetName))
            },
            text = {
                Text(stringResource(R.string.device_sessions_screen_sign_out_session_dialog_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = targetSession
                        sessionToSignOut.value = null
                        coroutineScope.launch {
                            try {
                                walletClient.signOutSession(s.clientId)
                                fetchSessions()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Logger.e(TAG, "Failed to sign out session ${s.clientId}", e)
                                error.value = e.message ?: "Failed to sign out session"
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.device_sessions_screen_sign_out_session_dialog_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToSignOut.value = null }) {
                    Text(stringResource(R.string.device_sessions_screen_sign_out_session_dialog_cancel))
                }
            }
        )
    }
}
