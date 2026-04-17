package org.multipaz.wallet.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.wallet.android.R

@Composable
fun ConfirmationDialog(
    title: String,
    textMarkdown: String,
    confirmButtonText: String,
    onDismissed: () -> Unit,
    onConfirmClicked: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissed,
        dismissButton = {
            TextButton(
                onClick = onDismissed
            ) {
                Text(text = stringResource(R.string.confirmation_dialog_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmClicked
            ) {
                Text(text = confirmButtonText)
            }
        },
        title = {
            Text(text = title)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = AnnotatedString.fromMarkdown(markdownString = textMarkdown))
            }
        }
    )
}
