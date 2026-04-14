package org.multipaz.wallet.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.multipaz.wallet.android.R
import org.multipaz.compose.text.fromMarkdown

@Composable
fun SignInClearEncryptionKeyDialog(
    onConfirm: () -> Unit,
    onDismissed: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissed,
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(text = stringResource(R.string.sign_in_clear_key_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissed
            ) {
                Text(text = stringResource(R.string.sign_in_clear_key_dialog_cancel))
            }
        },
        title = {
            Text(text = stringResource(R.string.sign_in_clear_key_dialog_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = AnnotatedString.fromMarkdown(
                    markdownString = stringResource(R.string.sign_in_clear_key_dialog_text)
                ))
            }
        }
    )
}
