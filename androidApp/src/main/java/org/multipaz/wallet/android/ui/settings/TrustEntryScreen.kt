package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.multipaz.compose.trustmanagement.TrustEntryViewer
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.crypto.X509CertChain
import org.multipaz.trustmanagement.TrustEntryRical
import org.multipaz.trustmanagement.TrustEntryVical
import org.multipaz.trustmanagement.TrustEntryX509Cert
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.wallet.android.R

@OptIn(ExperimentalResourceApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrustEntryScreen(
    trustManagerModel: TrustManagerModel,
    trustEntryId: String,
    justImported: Boolean,
    imageLoader: ImageLoader,
    onViewSignerCertificateChain: (certificateChain: X509CertChain) -> Unit,
    onViewVicalEntry: (vicalCertNum: Int) -> Unit,
    onViewRicalEntry: (ricalCertNum: Int) -> Unit,
    onEditClicked: () -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    val info = trustManagerModel.trustManagerInfos.collectAsState().value?.find {
        it.entry.identifier == trustEntryId
    } ?: return

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmationDialog = false }
                ) {
                    Text(text = stringResource(R.string.trust_entry_delete_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            showDeleteConfirmationDialog = false
                            (trustManagerModel.trustManager as? TrustManager)?.deleteEntry(info.entry)
                            onBackClicked()
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.trust_entry_delete_confirm))
                }
            },
            title = {
                Text(
                    text = when (info.entry) {
                        is TrustEntryX509Cert -> stringResource(R.string.trust_entry_delete_cert_title)
                        is TrustEntryVical -> stringResource(R.string.trust_entry_delete_vical_title)
                        is TrustEntryRical -> stringResource(R.string.trust_entry_delete_rical_title)
                    }
                )
            },
            text = {
                Text(
                    text = when (info.entry) {
                        is TrustEntryX509Cert -> stringResource(R.string.trust_entry_delete_cert_text)
                        is TrustEntryVical -> stringResource(R.string.trust_entry_delete_vical_text)
                        is TrustEntryRical -> stringResource(R.string.trust_entry_delete_rical_text)
                    }
                )
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
                    Text(
                        text = when (info.entry) {
                            is TrustEntryX509Cert -> stringResource(R.string.trust_entry_view_cert)
                            is TrustEntryVical -> stringResource(R.string.trust_entry_view_vical)
                            is TrustEntryRical -> stringResource(R.string.trust_entry_view_rical)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (trustManagerModel.trustManager is TrustManager) {
                        IconButton(
                            onClick = { onEditClicked() }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirmationDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null
                            )
                        }
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
                .padding(8.dp),
        ) {
            TrustEntryViewer(
                trustManagerModel = trustManagerModel,
                trustEntryId = trustEntryId,
                justImported = justImported,
                imageLoader = imageLoader,
                onViewSignerCertificateChain = onViewSignerCertificateChain,
                onViewVicalEntry = onViewVicalEntry,
                onViewRicalEntry = onViewRicalEntry,
            )
        }
    }
}