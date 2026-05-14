package org.multipaz.wallet.android.ui.verification

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.nfc.MdocReaderNfcHandoverOptions
import org.multipaz.mdoc.nfc.ScanMdocReaderResult
import org.multipaz.mdoc.nfc.scanMdocReader
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.nfc.NfcScanOptions
import org.multipaz.nfc.NfcTagReader
import org.multipaz.prompt.PromptModel
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import org.multipaz.util.fromHex
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.AgeOverQuery
import org.multipaz.wallet.client.IdentificationQuery
import org.multipaz.wallet.client.ReaderQuery
import org.multipaz.wallet.client.WalletClient

private const val TAG = "VerificationScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    promptModel: PromptModel,
    onNfcHandover: (result: ScanMdocReaderResult, query: ReaderQuery) -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope { promptModel }
    val scrollState = rememberScrollState()
    val signedInData = walletClient.signedInUser.collectAsState().value

    val queries = listOf(
        AgeOverQuery(18),
        AgeOverQuery(21),
        AgeOverQuery(65),
        IdentificationQuery
    )
    val selectedQuery = remember { mutableStateOf(
        settingsModel.readerSelectedQueryId.value?.let {
            queries.find { query -> query.id == it }?.id
        } ?: queries.first().id
    ) }

    val nfcTagReader = NfcTagReader.getReaders().firstOrNull()
    val nfcScanOptions = if (nfcPollingFramesInsertionSupported) {
        NfcScanOptions(
            pollingFrameData = ByteString("6a0281030000".fromHex())
        )
    } else {
        NfcScanOptions()
    }

    // On Platforms that support NFC scanning without a dialog, start scanning as soon
    // as we enter this screen. We'll get canceled when switched away because `coroutineScope`
    // will get canceled.
    //
    LaunchedEffect(Unit) {
        if (nfcTagReader != null && !nfcTagReader.dialogAlwaysShown) {
            coroutineScope.launch {
                while (true) {
                    try {
                        Logger.i(TAG, "Calling reader.scanMdocReader()")
                        val scanResult = nfcTagReader.scanMdocReader(
                            message = null,
                            options = MdocTransportOptions(
                                bleUseL2CAP = false,               // Doesn't work with Apple Wallet
                                bleUseL2CAPInEngagement = true
                            ),
                            handoverOptions = MdocReaderNfcHandoverOptions(
                                useNfcV2 = true
                            ),
                            transportFactory = MdocTransportFactory.Default,
                            selectConnectionMethod = { connectionMethods -> connectionMethods.first() },
                            negotiatedHandoverConnectionMethods = listOf(
                                MdocConnectionMethodBle(
                                    supportsPeripheralServerMode = false,
                                    supportsCentralClientMode = true,
                                    peripheralServerModeUuid = null,
                                    centralClientModeUuid = UUID.randomUUID(),
                                )
                            ),
                            nfcScanOptions = nfcScanOptions
                        )
                        if (scanResult != null) {
                            onNfcHandover(
                                scanResult,
                                queries.find { it.id == selectedQuery.value }!!
                            )
                        }
                        break
                    } catch (e: Throwable) {
                        if (!coroutineScope.isActive) {
                            Logger.e(TAG, "Caught exception while scanning and scope isn't active", e)
                            break
                        } else {
                            Logger.e(TAG, "Caught exception while scanning. Retrying", e)
                        }
                    }
                }
            }
            Logger.i(TAG, "Done1")
        }
    }


    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.verification_screen_title))
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.1f))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(
                    16.dp,
                    alignment = Alignment.CenterHorizontally
                )
            ) {
                for (query in queries) {
                    FilterChip(
                        selected = query.id == selectedQuery.value,
                        onClick = {
                            selectedQuery.value = query.id
                            settingsModel.readerSelectedQueryId.value = query.id
                        },
                        label = { Text(text = query.displayName) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            FloatingItemList {
                FloatingItemCenteredText("Verification functionality will be added in a future release. Stay tuned!")
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private val nfcPollingFramesInsertionSupported by lazy {
    // Use an allow-list until b/460804407 is resolved and used in Multipaz
    if (Build.MANUFACTURER == "Google" && (
                Build.MODEL.startsWith("Pixel 8") ||
                        Build.MODEL.startsWith("Pixel 9") ||
                        Build.MODEL.startsWith("Pixel 10") ||
                        Build.MODEL.startsWith("Pixel 11")
                )
    ) {
        Logger.i(TAG, "Device is on allow-list for nfcPollingFramesInsertionSupported")
        true
    } else {
        Logger.w(TAG, "Device is not allow-list for nfcPollingFramesInsertionSupported")
        false
    }
}