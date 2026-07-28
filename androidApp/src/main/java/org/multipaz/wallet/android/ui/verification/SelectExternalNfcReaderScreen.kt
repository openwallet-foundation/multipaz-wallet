package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz.compose.items.FloatingItemContainer
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.nfc.ExternalNfcReaderState
import org.multipaz.nfc.ExternalNfcReaderStore
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectExternalNfcReaderScreen(
    settingsModel: SettingsModel,
    externalNfcReaderStore: ExternalNfcReaderStore,
    onBackClicked: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val selectedReaderId = settingsModel.selectedExternalNfcReaderId.collectAsState().value
    val readers = externalNfcReaderStore.readers.collectAsState().value

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.select_external_nfc_reader_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Note(stringResource(R.string.select_external_nfc_reader_note))
            Spacer(modifier = Modifier.height(10.dp))
            FloatingItemList {
                // Built-in NFC reader option
                val isInternalSelected = selectedReaderId == null || readers.none { it.id == selectedReaderId }
                FloatingItemContainer(
                    modifier = Modifier.clickable {
                        settingsModel.selectedExternalNfcReaderId.value = null
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isInternalSelected,
                            onClick = {
                                settingsModel.selectedExternalNfcReaderId.value = null
                            }
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.request_verification_internal_nfc_reader),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.external_nfc_reader_type_builtin),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // External NFC readers options
                readers.forEach { reader ->
                    val isSelected = selectedReaderId == reader.id
                    val state = reader.observeState().collectAsState(initial = null).value
                    val isConnected = state != null && state != ExternalNfcReaderState.NOT_CONNECTED
                    val statusText = if (isConnected) {
                        stringResource(R.string.external_nfc_reader_status_connected)
                    } else {
                        stringResource(R.string.external_nfc_reader_status_not_connected)
                    }

                    FloatingItemContainer(
                        modifier = Modifier.clickable {
                            settingsModel.selectedExternalNfcReaderId.value = reader.id
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    settingsModel.selectedExternalNfcReaderId.value = reader.id
                                }
                            )
                            Column {
                                Text(
                                    text = reader.userDisplayName ?: reader.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.external_nfc_reader_type_external_status, statusText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
