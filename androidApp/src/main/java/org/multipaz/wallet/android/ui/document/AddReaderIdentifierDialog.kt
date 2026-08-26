package org.multipaz.wallet.android.ui.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.document.DocumentModel
import org.multipaz.util.fromHex
import org.multipaz.wallet.android.R

@Composable
fun AddReaderIdentifierDialog(
    documentModel: DocumentModel,
    documentId: String,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val documentInfos by documentModel.documentInfos.collectAsState()
    val documentInfo = documentInfos.find { it.document.identifier == documentId }

    var input by remember { mutableStateOf("") }
    val cleanInput = input.trim()
    val isValid = remember(cleanInput) {
        try {
            cleanInput.isNotEmpty() && cleanInput.length % 2 == 0 && cleanInput.fromHex().isNotEmpty()
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_reader_identifier_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.add_reader_identifier_dialog_description))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.add_reader_identifier_dialog_label)) },
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
                    val doc = documentInfo?.document
                    if (doc != null) {
                        val currentList = doc.readerIdentifiers
                        if (!currentList.contains(ski)) {
                            coroutineScope.launch {
                                doc.edit {
                                    readerIdentifiers = currentList + ski
                                }
                            }
                        }
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.add_reader_identifier_dialog_add_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirmation_dialog_cancel))
            }
        }
    )
}
