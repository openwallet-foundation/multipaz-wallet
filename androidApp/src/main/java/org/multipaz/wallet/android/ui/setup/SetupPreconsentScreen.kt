package org.multipaz.wallet.android.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.compose.items.FloatingItemContainer
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.InfoNote

@Composable
fun SetupPreconsentScreen(
    settingsModel: SettingsModel,
    onContinueClicked: () -> Unit
) {
    val preconsentForNewDocuments = settingsModel.preconsentForNewDocuments.collectAsState().value

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.setup_preconsent_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.preconsent_defaults_screen_blurb),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            FloatingItemList {
                SettingRow(
                    title = stringResource(R.string.preconsent_defaults_option_enabled_title),
                    description = stringResource(R.string.preconsent_defaults_option_enabled_description),
                    selected = preconsentForNewDocuments,
                    onClick = {
                        settingsModel.preconsentForNewDocuments.value = true
                    }
                )

                SettingRow(
                    title = stringResource(R.string.preconsent_defaults_option_disabled_title),
                    description = stringResource(R.string.preconsent_defaults_option_disabled_description),
                    selected = !preconsentForNewDocuments,
                    onClick = {
                        settingsModel.preconsentForNewDocuments.value = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoNote(markdownString = stringResource(R.string.preconsent_defaults_per_document_note))

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onContinueClicked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.setup_preconsent_continue))
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FloatingItemContainer(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.padding(top = 2.dp, end = 16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
