package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import kotlinx.coroutines.launch
import org.multipaz.compose.trustmanagement.TrustEntryEditor
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.wallet.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustEntryEditScreen(
    trustManagerModel: TrustManagerModel,
    trustEntryId: String,
    imageLoader: ImageLoader,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showConfirmationBeforeExiting by remember { mutableStateOf(false) }

    val info = trustManagerModel.trustManagerInfos.collectAsState().value?.find {
        it.entry.identifier == trustEntryId
    } ?: return

    val newMetadata = remember { mutableStateOf<TrustMetadata>(info.entry.metadata) }

    if (showConfirmationBeforeExiting) {
        AlertDialog(
            onDismissRequest = { showConfirmationBeforeExiting = false },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmationBeforeExiting = false }
                ) {
                    Text(text = stringResource(R.string.trust_entry_edit_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            showConfirmationBeforeExiting = false
                            onBackClicked()
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.trust_entry_edit_discard_confirm))
                }
            },
            title = {
                Text(text = stringResource(R.string.trust_entry_edit_discard_title))
            },
            text = {
                Text(text = stringResource(R.string.trust_entry_edit_discard_text))
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(text = stringResource(R.string.trust_entry_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (newMetadata.value != info.entry.metadata) {
                            showConfirmationBeforeExiting = true
                        } else {
                            onBackClicked()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.trust_entry_edit_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = (newMetadata.value != info.entry.metadata),
                        onClick = {
                            coroutineScope.launch {
                                (trustManagerModel.trustManager as TrustManager).updateMetadata(
                                    entry = info.entry,
                                    metadata = newMetadata.value
                                )
                                onBackClicked()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
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
                .verticalScroll(scrollState)
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            TrustEntryEditor(
                trustEntryInfo = info,
                imageLoader = imageLoader,
                newMetadata = newMetadata
            )
        }
    }
}
