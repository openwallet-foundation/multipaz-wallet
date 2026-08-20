package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.nfc.ExternalNfcReaderState
import org.multipaz.nfc.ExternalNfcReaderStore
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalNfcReadersScreen(
    externalNfcReaderStore: ExternalNfcReaderStore,
    onBackClicked: () -> Unit,
    onReaderClicked: (readerId: String) -> Unit
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val readers = externalNfcReaderStore.readers.collectAsState().value
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.external_nfc_readers_screen_title))
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
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
