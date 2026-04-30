package org.multipaz.wallet.android.ui.provisioning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.compose.pickers.rememberFilePicker
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
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
    onCredentialIssuerUrl: (issuerUrl: String) -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
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

    val showOpenID4VCIServerUrlDialog = remember { mutableStateOf(false) }
    if (showOpenID4VCIServerUrlDialog.value) {
        val issuingServerUrl = remember { mutableStateOf(settingsModel.provisioningServerUrl.value) }
        AlertDialog(
            onDismissRequest = { showOpenID4VCIServerUrlDialog.value = false },
            title = { Text(stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_title)) },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOpenID4VCIServerUrlDialog.value = false
                    }
                ) {
                    Text(stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsModel.provisioningServerUrl.value = issuingServerUrl.value
                        showOpenID4VCIServerUrlDialog.value = false
                        onCredentialIssuerUrl(issuingServerUrl.value)
                    }) {
                    Text(stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_connect))
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextField(
                        modifier = Modifier.padding(4.dp),
                        value = issuingServerUrl.value,
                        onValueChange = { issuingServerUrl.value = it },
                        singleLine = true,
                        label = {
                            Text(stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_url_label))
                        }
                    )

                    val resetToDefaultText = stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_reset)
                    Text(
                        text = AnnotatedString.fromMarkdown(
                            markdownString = "[$resetToDefaultText](reset://)",
                            linkInteractionListener = {
                                settingsModel.provisioningServerUrl.value = SettingsModel.DEFAULT_PROVISIONING_SERVER_URL
                                issuingServerUrl.value = SettingsModel.DEFAULT_PROVISIONING_SERVER_URL
                            }
                        )
                    )
                }
            }
        )
    }


    val importMpzPassFilePicker = rememberFilePicker(
        types = listOf(
            "application/vnd.multipaz.mpzpass",
        ),
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
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.provisioning_add_to_wallet_screen_screen_title))
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState),
        ) {
            val iconSize = 24.dp
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                }

                if (settingsModel.devMode.collectAsState().value) {
                    FloatingItemList {
                        FloatingItemText(
                            modifier = Modifier.clickable {
                                showOpenID4VCIServerUrlDialog.value = true
                            },
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
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
