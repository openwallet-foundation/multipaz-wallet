package org.multipaz.wallet.android.ui.verification

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.multipaz.cbor.Cbor
import org.multipaz.compose.datetime.durationFromNowText
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.permissions.rememberNotificationPermissionState
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.eventlogger.EventVerification
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.wallet.client.verification.toCbor
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.nfc.ExternalNfcReaderState
import org.multipaz.nfc.ExternalNfcReaderStore
import org.multipaz.prompt.PromptModel
import org.multipaz.storage.Storage
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import org.multipaz.verification.PresentmentRecord
import org.multipaz.wallet.android.LinkVerification
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.VERIFICATION_LINK_EXPIRATION
import org.multipaz.wallet.android.checkVerificationResults
import org.multipaz.wallet.android.decryptResponse
import org.multipaz.wallet.android.deleteVerification
import org.multipaz.wallet.android.getCompletedVerifications
import org.multipaz.wallet.android.getDescription
import org.multipaz.wallet.android.getDisplayName
import org.multipaz.wallet.android.getPendingVerifications
import org.multipaz.wallet.android.postNotification
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.shareVerificationLink
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.ConfirmationDialog
import org.multipaz.wallet.android.ui.InfoNote
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.verification.Query
import org.multipaz.wallet.client.verification.Result
import kotlin.time.Instant

private data class CompletedVerificationData(
    val portrait: ImageBitmap?,
    val queryResult: Result,
    val presentmentRecord: PresentmentRecord
)

private const val TAG = "RequestVerificationScreen"

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun RequestVerificationScreen(
    walletClient: WalletClient,
    storage: Storage,
    settingsModel: SettingsModel,
    externalNfcReaderStore: ExternalNfcReaderStore,
    documentModel: DocumentModel,
    promptModel: PromptModel,
    documentTypeRepository: DocumentTypeRepository,
    zkSystemRepository: ZkSystemRepository,
    issuerTrustManager: CompositeTrustManager,
    eventLogger: SimpleEventLogger,
    onSelectVerificationTypeClicked: () -> Unit,
    onSelectNfcReaderClicked: () -> Unit,
    onRequestVerificationAdvancedOptionsClicked: () -> Unit,
    onScanQrClicked: () -> Unit,
    onScanNfcClicked: (nfcOnly: Boolean) -> Unit,
    onGenerateVerificationLinkClicked: () -> Unit,
    onViewVerificationClicked: (query: Query, presentmentRecord: PresentmentRecord, atTime: Instant, showNotTrusted: Boolean) -> Unit,
    onDeletePendingVerificationClicked: (requestId: String) -> Unit,
    refreshTrigger: Int,
    onBackClicked: () -> Unit,
    onVerificationHistoryClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope { promptModel }
    val context = LocalContext.current
    val isInPerson = settingsModel.verificationIsInPerson.collectAsState().value
    val devMode = settingsModel.devMode.collectAsState().value
    val notificationPermissionState = rememberNotificationPermissionState()
    var showPermissionDialog by remember { mutableStateOf(false) }

    val externalReaders = externalNfcReaderStore.readers.collectAsState().value
    val selectedReaderId = settingsModel.selectedExternalNfcReaderId.collectAsState().value
    if (showPermissionDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.request_verification_notification_permission_title),
            textMarkdown = stringResource(R.string.request_verification_notification_permission_text),
            confirmButtonText = stringResource(R.string.request_verification_notification_permission_confirm),
            onDismissed = { showPermissionDialog = false },
            onConfirmClicked = {
                showPermissionDialog = false
                coroutineScope.launch {
                    notificationPermissionState.launchPermissionRequest()
                }
            }
        )
    }

    val pendingList = remember { mutableStateOf<List<LinkVerification>>(emptyList()) }
    val completedList = remember { mutableStateOf<List<LinkVerification>>(emptyList()) }

    LaunchedEffect(isInPerson, refreshTrigger) {
        val pending = withContext(Dispatchers.Default) {
            getPendingVerifications(storage)
        }
        val completed = withContext(Dispatchers.Default) {
            getCompletedVerifications(storage)
        }
        pendingList.value = pending
        completedList.value = completed
        if (!isInPerson) {
            while (isActive) {
                val currentPending = withContext(Dispatchers.Default) {
                    getPendingVerifications(storage)
                }
                pendingList.value = currentPending
                if (currentPending.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        checkVerificationResults(
                            walletClient = walletClient,
                            storage = storage,
                            eventLogger = eventLogger,
                            documentTypeRepository = documentTypeRepository,
                            zkSystemRepository = zkSystemRepository,
                            issuerTrustManager = issuerTrustManager,
                            onResponseReceived = { verification ->
                                postNotification(
                                    context = context,
                                    verification = verification
                                )
                            }
                        )
                    }
                    val updatedPending = withContext(Dispatchers.Default) {
                        getPendingVerifications(storage)
                    }
                    pendingList.value = updatedPending
                }
                val updatedCompleted = withContext(Dispatchers.Default) {
                    getCompletedVerifications(storage)
                }
                completedList.value = updatedCompleted
                delay(3000)
            }
        }
    }
    val completedUiStates = remember { mutableStateOf<List<CompletedVerificationUiState>>(emptyList()) }
    LaunchedEffect(completedList.value) {
        val states = withContext(Dispatchers.Default) {
            completedList.value.map { item ->
                val presentmentRecord = try {
                    val decryptedResponse = item.decryptResponse()
                    val dcResponse = Json.parseToJsonElement(decryptedResponse).jsonObject
                    item.session.processDcResponse(dcResponse = dcResponse)
                } catch (e: Exception) {
                    null
                }
                val isTrusted = presentmentRecord?.let {
                    try {
                        val timeForChecking = item.responseReceivedAtMillis?.let { Instant.fromEpochMilliseconds(it) }
                            ?: Instant.fromEpochMilliseconds(item.creationTimeMillis)
                        val verifiedPresentations = it.verify(
                            atTime = timeForChecking,
                            documentTypeRepository = documentTypeRepository,
                            zkSystemRepository = zkSystemRepository
                        )
                        val queryResult = item.query.processVerifiedPresentations(
                            verifiedPresentation = verifiedPresentations,
                            issuerTrustManager = issuerTrustManager
                        )
                        queryResult.documents.firstOrNull()?.trustResult?.isTrusted ?: true
                    } catch (e: Exception) {
                        true
                    }
                } ?: true
                CompletedVerificationUiState(item, presentmentRecord, isTrusted)
            }
        }
        completedUiStates.value = states
    }

    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = { Text(stringResource(R.string.request_verification_screen_title)) },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    IconButton(onClick = onVerificationHistoryClicked) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = stringResource(R.string.verification_history_title)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                SegmentedButton(
                    modifier = Modifier.fillMaxHeight(),
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = !isInPerson,
                    onClick = { settingsModel.verificationIsInPerson.value = false },
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    label = {
                        Text(text = stringResource(R.string.request_verification_send_link))
                    }
                )
                SegmentedButton(
                    modifier = Modifier.fillMaxHeight(),
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = isInPerson,
                    onClick = { settingsModel.verificationIsInPerson.value = true },
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    label = {
                        Text(text = stringResource(R.string.request_verification_in_person))
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            FloatingItemList(title = stringResource(R.string.request_verification_what_to_request)) {
                val selectedQuery = settingsModel.readerQuery.collectAsState().value
                FloatingItemHeadingAndContent(
                    modifier = Modifier.clickable { onSelectVerificationTypeClicked() },
                    showChevron = true,
                    heading = selectedQuery.getDisplayName(),
                    content = {
                        Text(
                            text = selectedQuery.getDescription(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                val storeResponse = settingsModel.verificationStoreResponse.collectAsState().value
                FloatingItemHeadingAndContent(
                    heading = stringResource(R.string.request_verification_store_response),
                    content = {
                        Text(
                            text = stringResource(R.string.request_verification_store_response_content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = storeResponse,
                            onCheckedChange = { value -> settingsModel.verificationStoreResponse.value = value }
                        )
                    }
                )
                if (devMode) {
                    val issuerIdentifiers by settingsModel.verificationIssuerIdentifiers.collectAsState()
                    val advancedContent = when (issuerIdentifiers.size) {
                        0 -> stringResource(R.string.request_verification_advanced_content)
                        1 -> stringResource(R.string.request_verification_advanced_content_one)
                        else -> stringResource(
                            R.string.request_verification_advanced_content_many,
                            issuerIdentifiers.size
                        )
                    }
                    FloatingItemHeadingAndContent(
                        modifier = Modifier.clickable { onRequestVerificationAdvancedOptionsClicked() },
                        showChevron = true,
                        heading = stringResource(R.string.request_verification_advanced_heading),
                        content = {
                            Text(
                                text = advancedContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            if (!isInPerson) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !notificationPermissionState.isGranted
                        ) {
                            showPermissionDialog = true
                        } else {
                            onGenerateVerificationLinkClicked()
                        }
                    },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null
                        )
                        Text(
                            modifier = Modifier.padding(vertical = 8.dp),
                            text = stringResource(R.string.request_verification_generate_link),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (pendingList.value.isNotEmpty()) {
                    FloatingItemList(title = stringResource(R.string.request_verification_pending_heading)) {
                        for (item in pendingList.value) {
                            FloatingItemHeadingAndContent(
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        try {
                                            val origin = walletClient.getVerificationLinkOrigin()
                                            val link = "$origin/web/verify?request=${item.requestId}#${item.requestEncryptionKey.toByteArray().toBase64Url()}"
                                            shareVerificationLink(context, link)
                                        } catch (e: Exception) {
                                            Logger.e(TAG, "Failed to share pending link", e)
                                            showToast(context.getString(R.string.verification_link_share_failed, e.message ?: ""))
                                        }
                                    }
                                },
                                image = {
                                    Icon(
                                        modifier = Modifier.size(48.dp),
                                        imageVector = Icons.Outlined.Link,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        contentDescription = stringResource(R.string.content_description_link)
                                    )
                                },
                                heading = item.query.getDisplayName(),
                                content = {
                                    Text(
                                        text = stringResource(
                                            R.string.request_verification_link_expires,
                                            durationFromNowText(Instant.fromEpochMilliseconds(item.creationTimeMillis + VERIFICATION_LINK_EXPIRATION.inWholeMilliseconds))
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            onDeletePendingVerificationClicked(item.requestId)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            contentDescription = stringResource(R.string.content_description_delete)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (completedUiStates.value.isNotEmpty()) {
                    FloatingItemList(title = stringResource(R.string.request_verification_completed_heading)) {
                        for (state in completedUiStates.value) {
                            val item = state.item
                            val timeText = durationFromNowText(Instant.fromEpochMilliseconds(item.responseReceivedAtMillis ?: item.creationTimeMillis))
                            val presentmentRecord = state.presentmentRecord
                            val isTrusted = state.isTrusted

                            FloatingItemHeadingAndContent(
                                modifier = Modifier.clickable {
                                    if (presentmentRecord != null) {
                                        completedList.value = completedList.value.filter { it.requestId != item.requestId }
                                        CoroutineScope(Dispatchers.IO).launch {
                                            if (item.storeResponse && !item.logged) {
                                                try {
                                                    val event = EventVerification(
                                                        appData = mapOf("query" to Cbor.decode(item.query.toCbor())),
                                                        presentmentRecord = presentmentRecord
                                                    )
                                                    eventLogger.addEvent(event)
                                                } catch (e: Exception) {
                                                    if (e is CancellationException) throw e
                                                    Logger.e(TAG, "Failed to log verification event on review", e)
                                                }
                                            }
                                            try {
                                                deleteVerification(storage, item.requestId)
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                Logger.e(TAG, "Failed to delete verification on click", e)
                                            }
                                            try {
                                                walletClient.deleteVerificationRequest(item.requestId)
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                Logger.w(TAG, "Failed to delete verification request from server", e)
                                            }
                                            try {
                                                walletClient.deleteVerificationResponse(item.requestId)
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                // Already deleted when polled, ignore
                                            }
                                        }
                                        onViewVerificationClicked(
                                            item.query,
                                            presentmentRecord,
                                            Instant.fromEpochMilliseconds(item.responseReceivedAtMillis ?: item.creationTimeMillis),
                                            !isTrusted
                                        )
                                    }
                                },
                                showChevron = true,
                                image = {
                                    val icon = if (isTrusted) Icons.Outlined.CheckCircle else Icons.Outlined.Warning
                                    val tint = if (isTrusted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    Icon(
                                        modifier = Modifier.size(48.dp),
                                        imageVector = icon,
                                        tint = tint,
                                        contentDescription = null
                                    )
                                },
                                heading = item.query.getDisplayName(),
                                content = {
                                    val resId = if (isTrusted) R.string.request_verification_received else R.string.request_verification_received_unknown_issuer
                                    Text(
                                        text = stringResource(resId, timeText),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }

                if (pendingList.value.isEmpty() && completedList.value.isEmpty()) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        text = stringResource(R.string.request_verification_no_requests),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val activeReaderDisplayName = if (selectedReaderId != null) {
                    externalReaders.find { it.id == selectedReaderId }?.let { it.userDisplayName ?: it.displayName }
                        ?: stringResource(R.string.request_verification_internal_nfc_reader)
                } else {
                    stringResource(R.string.request_verification_internal_nfc_reader)
                }

                FloatingItemList(title = stringResource(R.string.request_verification_nfc_reader_heading)) {
                    FloatingItemHeadingAndContent(
                        modifier = Modifier.clickable { onSelectNfcReaderClicked() },
                        showChevron = true,
                        heading = activeReaderDisplayName,
                        content = {
                            Text(
                                text = if (selectedReaderId == null || externalReaders.none { it.id == selectedReaderId })
                                    stringResource(R.string.external_nfc_reader_type_builtin)
                                else
                                    stringResource(R.string.external_nfc_reader_type_external),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
                InfoNote(markdownString = stringResource(R.string.request_verification_nfc_reader_info))
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onScanQrClicked
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCode2,
                                contentDescription = null
                            )
                            Text(
                                modifier = Modifier.padding(vertical = 8.dp),
                                text = stringResource(R.string.request_verification_scan_qr_code),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = ButtonDefaults.MinHeight)
                            .combinedClickable(
                                onClick = { onScanNfcClicked(false) },
                                onLongClick = {
                                    if (devMode) {
                                        onScanNfcClicked(true)
                                    } else {
                                        onScanNfcClicked(false)
                                    }
                                }
                            ),
                        shape = ButtonDefaults.shape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(ButtonDefaults.ContentPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Contactless,
                                contentDescription = null
                            )
                            Text(
                                modifier = Modifier.padding(vertical = 8.dp),
                                text = stringResource(R.string.request_verification_scan_nfc),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

data class CompletedVerificationUiState(
    val item: LinkVerification,
    val presentmentRecord: PresentmentRecord?,
    val isTrusted: Boolean
)