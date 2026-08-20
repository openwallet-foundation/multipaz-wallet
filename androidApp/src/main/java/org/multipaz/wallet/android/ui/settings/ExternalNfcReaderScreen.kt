package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.nfc.ExternalNfcReaderState
import org.multipaz.nfc.ExternalNfcReaderStore
import org.multipaz.nfc.ExternalNfcReaderUsb
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalNfcReaderScreen(
    externalNfcReaderStore: ExternalNfcReaderStore,
    readerId: String,
    onBackClicked: () -> Unit,
    onEditNameClicked: () -> Unit,
    onRemoveReaderClicked: () -> Unit
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val reader = externalNfcReaderStore.readers.collectAsState().value.find { it.id == readerId }
    if (reader == null) {
        return
    }

    val hexFormat = HexFormat {
        number.prefix = "0x"
        number.minLength = 4
        number.removeLeadingZeros = true
    }

    val state = reader.observeState().collectAsState(initial = null).value

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.external_nfc_reader_screen_title))
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    IconButton(onClick = onEditNameClicked) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.external_nfc_reader_edit_name_button)
                        )
                    }
                    IconButton(onClick = onRemoveReaderClicked) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.external_nfc_reader_remove_button)
                        )
                    }
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
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            FloatingItemList(
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)
            ) {
                FloatingItemHeadingAndText(stringResource(R.string.external_nfc_reader_field_name), reader.userDisplayName ?: reader.displayName)
                if (reader.userDisplayName != null) {
                    FloatingItemHeadingAndText(stringResource(R.string.external_nfc_reader_field_original_name), reader.displayName)
                }
                if (reader is ExternalNfcReaderUsb) {
                    FloatingItemHeadingAndText(stringResource(R.string.external_nfc_reader_field_connection), stringResource(R.string.external_nfc_reader_connection_usb))
                    FloatingItemHeadingAndText(stringResource(R.string.external_nfc_reader_field_vendor_id), reader.vendorId.toHexString(hexFormat))
                    FloatingItemHeadingAndText(stringResource(R.string.external_nfc_reader_field_product_id), reader.productId.toHexString(hexFormat))
                    FloatingItemHeadingAndText(stringResource(R.string.external_nfc_reader_field_interface_index), reader.interfaceIndex.toString())
                }
                val isConnected = state != null && state != ExternalNfcReaderState.NOT_CONNECTED
                val statusText = if (isConnected) {
                    stringResource(R.string.external_nfc_reader_status_connected)
                } else {
                    stringResource(R.string.external_nfc_reader_status_not_connected)
                }
                FloatingItemHeadingAndText(stringResource(R.string.external_nfc_reader_field_state), statusText)
            }
        }
    }
}
