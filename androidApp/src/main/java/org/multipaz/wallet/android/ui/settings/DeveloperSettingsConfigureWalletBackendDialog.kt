package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.shared.BuildConfig

@Composable
fun DeveloperSettingsConfigureWalletBackendDialog(
    settingsModel: SettingsModel,
    onConfirmed: (walletBackendUrl: String?) -> Unit,
    onDismissed: () -> Unit,
) {
    var urlText by remember { mutableStateOf(
        TextFieldValue(settingsModel.walletBackendUrl.value ?: "")
    )}

    AlertDialog(
        onDismissRequest = onDismissed,
        dismissButton = {
            TextButton(
                onClick = onDismissed
            ) {
                Text(text = stringResource(R.string.dev_settings_config_backend_cancel))
            }
        },
        confirmButton = {
            val newUrl = urlText.text.ifEmpty { null }
            TextButton(
                enabled = newUrl != settingsModel.walletBackendUrl.collectAsState().value,
                onClick = { onConfirmed(newUrl) }
            ) {
                Text(text = stringResource(R.string.dev_settings_config_backend_confirm))
            }
        },
        title = {
            Text(text = stringResource(R.string.dev_settings_config_backend_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dev_settings_config_backend_text, BuildConfig.BACKEND_URL)
                )
                TextField(
                    value = urlText,
                    label = { Text(text = stringResource(R.string.dev_settings_config_backend_label)) },
                    onValueChange = {
                        urlText = it
                    },
                    singleLine = true,
                )

            }
        }
    )

}