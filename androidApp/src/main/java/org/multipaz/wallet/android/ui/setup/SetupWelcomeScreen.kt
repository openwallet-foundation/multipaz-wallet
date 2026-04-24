package org.multipaz.wallet.android.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.settings.DeveloperSettingsConfigureWalletBackendDialog
import org.multipaz.wallet.android.ui.settings.DeveloperSettingsConnectToWalletServerDialog
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.BuildConfig

@Composable
fun SetupWelcomeScreen(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    onContinueClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    var showSetWalletBackendUrlDialog by remember { mutableStateOf(false) }
    var connectToWalletBackendDialog by remember { mutableStateOf<String?>(null) }

    if (showSetWalletBackendUrlDialog) {
        DeveloperSettingsConfigureWalletBackendDialog(
            settingsModel = settingsModel,
            onConfirmed = { walletBackendUrl ->
                showSetWalletBackendUrlDialog = false
                connectToWalletBackendDialog = walletBackendUrl
            },
            onDismissed = { showSetWalletBackendUrlDialog = false }
        )
    }

    connectToWalletBackendDialog?.let { walletBackendUrl ->
        DeveloperSettingsConnectToWalletServerDialog(
            walletClient = walletClient,
            settingsModel = settingsModel,
            newWalletBackendUrl = walletBackendUrl,
            onSuccess = {
                connectToWalletBackendDialog = null
            },
            onFailed = { error ->
                showToast("$error")
            },
            onDismissed = {
                connectToWalletBackendDialog = null
            }
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // For Multipaz Wallet Dev, we want to be able to set the wallet backend URL
            // before actually connecting to the wallet server. This is useful for when
            // testing the setup flow from scratch. Since this is uncommon, make it available
            // through clicking the icon on the screen so it's not discoverable at all. Also
            // don't make this available in production.
            val imageModifier = if (BuildConfig.ANDROID_APP_ID.endsWith(".dev")) {
                Modifier.clickable {
                    showSetWalletBackendUrlDialog = true
                }
            } else {
                Modifier
            }
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = null,
                modifier = imageModifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.setup_welcome_title, BuildConfig.APP_NAME),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.setup_welcome_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onContinueClicked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.setup_welcome_continue))
            }
        }
    }
}
