package org.multipaz.wallet.android.ui.provisioning

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.multipaz.provisioning.AuthorizationChallenge
import org.multipaz.provisioning.AuthorizationResponse
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.provisioning.openid4vci.OpenID4VCIClientPreferences
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppTopAppBar
import org.multipaz.wallet.client.WalletClient

private const val TAG = "AuthorizationScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizationScreenOAuth(
    provisioningModel: ProvisioningModel,
    walletClient: WalletClient,
    challenge: AuthorizationChallenge.OAuth,
    onCloseClicked: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    var preferences by remember { mutableStateOf<OpenID4VCIClientPreferences?>(null) }

    LaunchedEffect(Unit) {
        preferences = walletClient.getOpenID4VCIClientPreferences()
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onCloseClicked) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.provisioning_auth_oauth_cancel_description)
                        )
                    }
                },
                hazeState = hazeState
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))

            preferences?.let {
                EvidenceRequestOAuthBrowser(
                    url = challenge.url,
                    waitForRedirect = { walletClient.waitForAppLinkInvocation(challenge.state) },
                    onRedirectReceived = { invokedUrl ->
                        provisioningModel.provideAuthorizationResponse(
                            response = AuthorizationResponse.OAuth(
                                id = challenge.id,
                                parameterizedRedirectUrl = invokedUrl
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EvidenceRequestOAuthBrowser(
    url: String,
    waitForRedirect: suspend () -> String,
    onRedirectReceived: suspend (redirectUrl: String) -> Unit,
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<CustomTabsSession?>(null) }

    val connection = remember {
        object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                name: ComponentName,
                client: CustomTabsClient
            ) {
                client.warmup(0)
                session = client.newSession(null)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                session = null
            }
        }
    }

    DisposableEffect(Unit) {
        val packageName = CustomTabsClient.getPackageName(context, null)
        if (packageName != null) {
            CustomTabsClient.bindCustomTabsService(context, packageName, connection)
        } else {
            Logger.w(TAG, "No Custom Tabs provider found")
        }
        onDispose {
            try {
                context.unbindService(connection)
            } catch (_: IllegalArgumentException) {
                // Service was not bound
            }
        }
    }

    // Wait for the redirect URL to arrive via the app's intent filter / deep link
    // pipeline.
    LaunchedEffect(url) {
        val redirectResult = waitForRedirect()
        onRedirectReceived(redirectResult)
    }

    // Launch the Custom Tab once the session is available.
    LaunchedEffect(url, session) {
        val currentSession = session ?: return@LaunchedEffect
        val activity = context.findActivity()
        if (activity == null) {
            Logger.w(TAG, "Could not find Activity in context chain, cannot launch Custom Tab")
            return@LaunchedEffect
        }

        val customTabsIntent = CustomTabsIntent.Builder(currentSession)
            .build()
        customTabsIntent.intent.data = url.toUri()
        activity.startActivity(customTabsIntent.intent)
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
