package org.multipaz.wallet.android.ui.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.crypto.X509CertChain
import org.multipaz.trustmanagement.TrustEntryRical
import org.multipaz.trustmanagement.TrustEntryVical
import org.multipaz.trustmanagement.TrustEntryX509Cert
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.trustmanagement.TrustEntryViewer
import org.multipaz.wallet.client.TrustEntryUpdateResult
import org.multipaz.wallet.client.updateTrustEntry

private const val TAG = "TrustEntryScreen"

@SuppressLint("LocalContextGetResourceValueCall")
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
    onShowInfoDialog: (title: String, textMarkdown: String) -> Unit,
    onShowErrorDialog: (title: String, textMarkdown: String) -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit,
) {
    val hazeState = remember { HazeState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var isCheckingForUpdate by remember { mutableStateOf(false) }

    val info = trustManagerModel.trustManagerInfos.collectAsState().value?.find {
        it.entry.identifier == trustEntryId
    } ?: return

    val updateUrl = when (info.entry) {
        is TrustEntryVical -> info.signedVical?.vical?.vicalUrl
        is TrustEntryRical -> info.signedRical?.rical?.latestRicalUrl
        else -> null
    }

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
            AppMediumTopAppBar(
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
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    if (trustManagerModel.trustManager is TrustManager) {
                        if (!updateUrl.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val trustManager = trustManagerModel.trustManager as? TrustManager ?: return@launch
                                        isCheckingForUpdate = true
                                        try {
                                            when (val result = trustManager.updateTrustEntry(entry = info.entry)) {
                                                is TrustEntryUpdateResult.AlreadyUpToDate -> {
                                                    onShowInfoDialog(
                                                        context.getString(R.string.trust_entry_update_already_latest_title),
                                                        context.getString(
                                                            R.string.trust_entry_update_already_latest_text,
                                                            result.listType
                                                        )
                                                    )
                                                }
                                                is TrustEntryUpdateResult.Updated -> {
                                                    val msg = if (result.issueId != null) {
                                                        context.getString(
                                                            R.string.trust_entry_update_success_text_with_issue,
                                                            result.listType,
                                                            result.issueId
                                                        )
                                                    } else {
                                                        context.getString(
                                                            R.string.trust_entry_update_success_text,
                                                            result.listType
                                                        )
                                                    }
                                                    onShowInfoDialog(
                                                        context.getString(R.string.trust_entry_update_success_title),
                                                        msg
                                                    )
                                                }
                                                is TrustEntryUpdateResult.NoUpdateUrl -> {}
                                            }
                                        } catch (e: Exception) {
                                            if (e is CancellationException) throw e
                                            Logger.w(TAG, "Error checking for update from $updateUrl", e)
                                            onShowErrorDialog(
                                                context.getString(R.string.trust_entry_update_failed_title),
                                                context.getString(
                                                    R.string.trust_entry_update_failed_text,
                                                    e.message ?: e.toString()
                                                )
                                            )
                                        } finally {
                                            isCheckingForUpdate = false
                                        }
                                    }
                                },
                                enabled = !isCheckingForUpdate
                            ) {
                                if (isCheckingForUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = stringResource(R.string.trust_entry_check_for_update)
                                    )
                                }
                            }
                        }
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
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null
                            )
                        }
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
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
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