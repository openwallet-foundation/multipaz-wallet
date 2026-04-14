package org.multipaz.wallet.android.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

import androidx.compose.ui.res.stringResource
import org.multipaz.wallet.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignOutConfirmationDialog(
    onConfirmed: () -> Unit,
    onDismissed: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissed,
        dismissButton = {
            TextButton(
                onClick = onDismissed
            ) {
                Text(text = stringResource(R.string.sign_out_dialog_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmed
            ) {
                Text(text = stringResource(R.string.sign_out_dialog_confirm))
            }
        },
        title = {
            Text(text = stringResource(R.string.sign_out_dialog_title))
        },
        text = {
            Text(
                text = stringResource(R.string.sign_out_dialog_text)
            )
        }
    )
}