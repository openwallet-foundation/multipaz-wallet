package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.multipaz.nfc.ExternalNfcReaderStore
import org.multipaz.wallet.android.R

@Composable
fun EditExternalNfcReaderNameDialog(
    externalNfcReaderStore: ExternalNfcReaderStore,
    readerId: String,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val reader = externalNfcReaderStore.readers.collectAsState().value.find { it.id == readerId }
    if (reader == null) {
        onDismiss()
        return
    }

    var nameInput by remember(reader.userDisplayName) { mutableStateOf(reader.userDisplayName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_nfc_reader_edit_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.external_nfc_reader_hardware_name, reader.displayName),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.external_nfc_reader_custom_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newName = nameInput.trim().ifEmpty { null }
                    coroutineScope.launch {
                        reader.setUserDisplayName(newName)
                        onDismiss()
                    }
                }
            ) {
                Text(stringResource(R.string.edit_external_nfc_reader_name_save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirmation_dialog_cancel))
            }
        }
    )
}
