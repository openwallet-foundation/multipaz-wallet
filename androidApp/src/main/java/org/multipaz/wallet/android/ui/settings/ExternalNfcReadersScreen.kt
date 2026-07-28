package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.nfc.ExternalNfcReaderState
import org.multipaz.nfc.ExternalNfcReaderStore
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalNfcReadersScreen(
    externalNfcReaderStore: ExternalNfcReaderStore,
    onBackClicked: () -> Unit,
    onReaderClicked: (readerId: String) -> Unit
) {
    val scrollState = rememberScrollState()
    val readers = externalNfcReaderStore.readers.collectAsState().value
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.external_nfc_readers_screen_title))
                },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Note(stringResource(R.string.external_nfc_readers_screen_info))

            FloatingItemList(
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
                title = stringResource(R.string.external_nfc_readers_configured_heading)
            ) {
                if (readers.isEmpty()) {
                    FloatingItemCenteredText(
                        text = stringResource(R.string.external_nfc_readers_screen_none),
                    )
                } else {
                    readers.forEach { reader ->
                        val state = reader.observeState().collectAsState(null).value
                        val isConnected = state != null && state != ExternalNfcReaderState.NOT_CONNECTED
                        val statusText = if (isConnected) {
                            stringResource(R.string.external_nfc_reader_status_connected)
                        } else {
                            stringResource(R.string.external_nfc_reader_status_not_connected)
                        }
                        FloatingItemHeadingAndText(
                            modifier = Modifier.clickable {
                                onReaderClicked(reader.id)
                            },
                            showChevron = true,
                            heading = reader.userDisplayName ?: reader.displayName,
                            text = statusText
                        )
                    }
                }
            }
        }
    }
}
