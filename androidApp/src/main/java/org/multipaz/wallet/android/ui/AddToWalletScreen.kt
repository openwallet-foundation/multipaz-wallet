package org.multipaz.wallet.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.CredentialIssuer

private const val TAG = "AddToDocumentScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToWalletScreen(
    walletClient: WalletClient,
    imageLoader: ImageLoader,
    onCredentialIssuerClicked: (credentialIssuer: CredentialIssuer) -> Unit,
    onImportMpzPass: (encodedMpzPass: ByteString) -> Unit,
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
                    Text(stringResource(R.string.add_document_screen_title))
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // For now, just show a flat list. In the future we can have the wallet backend
            // return a more complicated layered structure with categories and issuers inside
            // them...
            FloatingItemList {
                if (errorLoading != null) {
                    FloatingItemCenteredText(stringResource(R.string.add_document_screen_error_loading))
                } else if (credentialIssuers == null) {
                    FloatingItemCenteredText(stringResource(R.string.add_document_screen_loading))
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
                                        .width(40*1.586.dp).height(40.dp),
                                    model = credentialIssuer.iconUrl,
                                    imageLoader = imageLoader,
                                    contentScale = ContentScale.Crop,
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
                    text = stringResource(R.string.add_document_screen_import_pass),
                    image = {
                        Icon(
                            modifier = Modifier
                                .width(40*1.586.dp).height(40.dp),
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}
