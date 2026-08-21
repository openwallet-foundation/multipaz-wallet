package org.multipaz.wallet.android.ui.provisioning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.compose.pickers.rememberFilePicker
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.CredentialIssuer

private const val TAG = "AddToDocumentScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToWalletScreen(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    imageLoader: ImageLoader,
    onCredentialIssuerClicked: (credentialIssuer: CredentialIssuer) -> Unit,
    onImportMpzPass: (encodedMpzPass: ByteString) -> Unit,
    onScanCredentialOfferClicked: () -> Unit,
    onEnterIssuerUrlClicked: () -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val hazeState = remember { HazeState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var credentialIssuers by remember { mutableStateOf<List<CredentialIssuer>?>(null) }
    var errorLoading by remember { mutableStateOf<Exception?>(null) }

    LaunchedEffect(Unit) {
        try {
            credentialIssuers = walletClient.getCredentialIssuers()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Error loading credential issuers", e)
            errorLoading = e
        }
    }

    val importMpzPassFilePicker = rememberFilePicker(
        // Android derives a file's MIME type from its extension, and `.mpzpass` is not in the OS
        // MIME map — so a pass file from Downloads / Drive / email is tagged
        // `application/octet-stream`, and a strict `application/vnd.multipaz.mpzpass` filter hides
        // it (greyed out / unselectable in the picker). Accept any file; the bytes are validated
        // on import (AppNavHost: `MpzPass.fromDataItem` + the "error importing pass" dialog), which
        // is stricter than a MIME/extension check anyway.
        types = listOf("*/*"),
        allowMultiple = false,
        onResult = { files ->
            if (files.isNotEmpty()) {
                coroutineScope.launch {
                    onImportMpzPass(ByteString(files.first().toByteArray()))
                }
            }
        }
    )

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.provisioning_add_to_wallet_screen_screen_title))
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState),
        ) {
            val iconSize = 24.dp
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
                Note(
                    stringResource(R.string.provisioning_add_to_wallet_screen_explainer)
                )
                // For now, just show a flat list. In the future we can have the wallet backend
                // return a more complicated layered structure with categories and issuers inside
                // them...
                FloatingItemList {
                    if (errorLoading != null) {
                        FloatingItemCenteredText(stringResource(R.string.provisioning_add_to_wallet_screen_error_loading))
                    } else if (credentialIssuers == null) {
                        FloatingItemCenteredText(stringResource(R.string.provisioning_add_to_wallet_screen_loading))
                    } else {
                        credentialIssuers?.forEach { credentialIssuer ->
                            FloatingItemText(
                                modifier = Modifier.clickable {
                                    onCredentialIssuerClicked(credentialIssuer)
                                },
                                showChevron = true,
                                text = credentialIssuer.name,
                                image = {
                                    AsyncImage(
                                        modifier = Modifier
                                            .width(1.586 * iconSize).height(iconSize),
                                        model = credentialIssuer.iconUrl,
                                        imageLoader = imageLoader,
                                        contentScale = ContentScale.Fit,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }

                FloatingItemList {
                    FloatingItemText(
                        modifier = Modifier.clickable { importMpzPassFilePicker.launch() },
                        showChevron = true,
                        text = stringResource(R.string.provisioning_add_to_wallet_screen_import_pass),
                        image = {
                            Icon(
                                modifier = Modifier
                                    .width(1.586 * iconSize).height(iconSize),
                                imageVector = Icons.Outlined.FileUpload,
                                contentDescription = null
                            )
                        }
                    )
                    FloatingItemText(
                        modifier = Modifier.clickable { onScanCredentialOfferClicked() },
                        showChevron = true,
                        text = stringResource(R.string.provisioning_add_to_wallet_screen_scan_credential_offer),
                        image = {
                            Icon(
                                modifier = Modifier
                                    .width(1.586 * iconSize).height(iconSize),
                                imageVector = Icons.Outlined.QrCode2,
                                contentDescription = null
                            )
                        }
                    )
                    FloatingItemText(
                        modifier = Modifier.clickable { onEnterIssuerUrlClicked() },
                        showChevron = true,
                        text = stringResource(R.string.provisioning_add_to_wallet_screen_enter_issuer_url_item),
                        image = {
                            Icon(
                                modifier = Modifier
                                    .width(1.586 * iconSize).height(iconSize),
                                imageVector = Icons.Outlined.AccountBalance,
                                contentDescription = null
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
