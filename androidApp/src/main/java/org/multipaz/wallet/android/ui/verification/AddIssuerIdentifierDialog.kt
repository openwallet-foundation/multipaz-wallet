package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.io.bytestring.ByteString
import org.multipaz.util.fromHex
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel

@Composable
fun AddIssuerIdentifierDialog(
    settingsModel: SettingsModel,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val cleanInput = input.trim()
    val isValid = remember(cleanInput) {
        try {
            cleanInput.isNotEmpty() && cleanInput.length % 2 == 0 && cleanInput.fromHex().isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_issuer_identifier_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.add_issuer_identifier_dialog_description))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.add_issuer_identifier_dialog_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val ski = ByteString(cleanInput.fromHex())
                    val currentList = settingsModel.verificationIssuerIdentifiers.value
                    if (!currentList.contains(ski)) {
                        settingsModel.verificationIssuerIdentifiers.value = currentList + ski
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.add_issuer_identifier_dialog_add_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirmation_dialog_cancel))
            }
        }
    )
}
