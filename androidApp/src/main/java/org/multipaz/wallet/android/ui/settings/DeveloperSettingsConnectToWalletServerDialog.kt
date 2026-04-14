package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.multipaz.wallet.android.R
import kotlinx.coroutines.CancellationException
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.BuildConfig

@Composable
fun DeveloperSettingsConnectToWalletServerDialog(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    newWalletBackendUrl: String?,
    onSuccess: () -> Unit,
    onFailed: (Throwable) -> Unit,
    onDismissed: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val newUrl = newWalletBackendUrl ?: BuildConfig.BACKEND_URL
        if (newUrl == walletClient.url) {
            onFailed(IllegalArgumentException(context.getString(R.string.dev_settings_connect_backend_already_using_url)))
        } else {
            try {
                walletClient.setBackendUrl(newUrl)
                settingsModel.walletBackendUrl.value = newWalletBackendUrl
                onSuccess()
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    onFailed(e)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissed,
        confirmButton = {
            TextButton(
                onClick = onDismissed
            ) {
                Text(text = stringResource(R.string.dev_settings_connect_backend_cancel))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.dev_settings_connect_backend_connecting))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    )
}
