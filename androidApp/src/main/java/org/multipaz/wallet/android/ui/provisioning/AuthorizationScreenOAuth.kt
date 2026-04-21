package org.multipaz.wallet.android.ui.provisioning

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import org.multipaz.provisioning.AuthorizationChallenge
import org.multipaz.provisioning.AuthorizationResponse
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.provisioning.openid4vci.OpenID4VCIClientPreferences
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
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
    var preferences by remember { mutableStateOf<OpenID4VCIClientPreferences?>(null) }

    LaunchedEffect(Unit) {
        preferences = walletClient.getOpenID4VCIClientPreferences()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onCloseClicked) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.provisioning_auth_oauth_cancel_description)
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                    },
                    onTabClosed = {
                        onCloseClicked()
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
    onRedirectReceived: suspend (redirectUrk: String) -> Unit,
    onTabClosed: () -> Unit,
) {
    val context = LocalContext.current
    // Partial-height Custom Tabs (bottom sheet presentation) require a CustomTabsSession
    // obtained by binding to the browser's Custom Tabs service. Without a session,
    // setInitialActivityHeightPx() is silently ignored and the tab opens full screen.
    // The session starts as null and is set asynchronously once the service connects.
    var session by remember { mutableStateOf<CustomTabsSession?>(null) }
    var receivedRedirect by remember { mutableStateOf(false) }

    val callback = remember {
        object : CustomTabsCallback() {
            override fun onNavigationEvent(navigationEvent: Int, extras: Bundle?) {
                super.onNavigationEvent(navigationEvent, extras)
                if (navigationEvent == TAB_HIDDEN) {
                    if (!receivedRedirect) {
                        onTabClosed()
                    }
                }
            }
        }
    }

    // Connection callback for the Custom Tabs service binding. This is invoked
    // asynchronously by the system after bindCustomTabsService() is called:
    // - onCustomTabsServiceConnected: the browser service is ready. We call warmup()
    //   to pre-initialize the browser rendering engine and create a CustomTabsSession
    //   that enables partial-height presentation.
    // - onServiceDisconnected: the browser service process crashed or was killed.
    //   We null out the session to prevent using a stale reference.
    val connection = remember {
        object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                name: ComponentName,
                client: CustomTabsClient
            ) {
                client.warmup(0)
                session = client.newSession(callback)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                session = null
            }
        }
    }

    // Bind to the Custom Tabs service when this composable enters composition,
    // and unbind when it leaves. The binding is async: the connection callback
    // above will fire once the service is ready, populating the session state.
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
        receivedRedirect = true
        onRedirectReceived(redirectResult)
    }

    // Launch the Custom Tab once the session is available. This effect is keyed on
    // both url and session: it skips (returns) while session is null, and re-runs
    // once the service connection provides a session.
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
        activity.startActivityForResult(customTabsIntent.intent, 0)
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
