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
fun ErrorDialog(
    title: String,
    textMarkdown: String,
    onDismissed: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissed,
        confirmButton = {
            TextButton(
                onClick = onDismissed
            ) {
                Text(text = stringResource(R.string.error_dialog_ok))
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
