package org.multipaz.wallet.android.ui.verification

import android.annotation.SuppressLint
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.multipaz.wallet.android.R

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SelectCustomAgeDialog(
    onConfirmed: (age: Int) -> Unit,
    onDismissed: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var age by remember { mutableIntStateOf(50) }

    AlertDialog(
        onDismissRequest = onDismissed,
        dismissButton = {
            TextButton(
                onClick = onDismissed
            ) {
                Text(text = stringResource(R.string.select_custom_age_dialog_cancel_button))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmed(age) }
            ) {
                Text(text = stringResource(R.string.select_custom_age_dialog_affirmative_button))
            }
        },
        title = {
            Text(text = stringResource(R.string.select_custom_age_dialog_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LegacyNumberPicker(
                    range = 0..99,
                    value = age,
                    onValueChange = { newValue -> age = newValue }
                )
            }
        }
    )
}

@Composable
private fun LegacyNumberPicker(
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    AndroidView(
        factory = { context ->
            NumberPicker(context).apply {
                minValue = range.first
                maxValue = range.last
                setOnValueChangedListener { _, _, newVal ->
                    onValueChange(newVal)
                }
            }
        },
        update = { view ->
            view.value = value
        }
    )
}
