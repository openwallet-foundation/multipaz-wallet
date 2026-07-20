package org.multipaz.wallet.android.ui.verification

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottiePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.items.FloatingItemContainer
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.compose.qrcode.QrCodeScanner
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
import org.multipaz.wallet.android.getDescription
import org.multipaz.wallet.android.getDisplayName
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.InfoNote
import org.multipaz.wallet.android.ui.ConfirmationDialog
import org.multipaz.compose.permissions.rememberNotificationPermissionState
import org.multipaz.wallet.client.WalletClient
import android.Manifest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Switch
import org.multipaz.compose.items.FloatingItemText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.multipaz.storage.Storage
import org.multipaz.verification.PresentmentRecord
import org.multipaz.wallet.android.LinkVerification
import org.multipaz.wallet.android.shareVerificationLink
import org.multipaz.wallet.android.getPendingVerifications
import org.multipaz.wallet.android.getCompletedVerifications
import org.multipaz.wallet.android.VERIFICATION_LINK_EXPIRATION
import org.multipaz.wallet.android.checkVerificationResults
import org.multipaz.wallet.android.deleteVerification
import org.multipaz.wallet.android.postNotification
import org.multipaz.wallet.android.decryptResponse
import org.multipaz.cbor.Cbor
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.wallet.client.verification.Query
import org.multipaz.wallet.client.verification.toCbor
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import org.multipaz.util.toBase64Url
import org.multipaz.compose.datetime.durationFromNowText
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.trustmanagement.CompositeTrustManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import org.multipaz.wallet.client.verification.AgeOverQuery
import org.multipaz.wallet.client.verification.IdentificationQuery
import org.multipaz.wallet.client.verification.Result
import org.multipaz.wallet.client.verification.AgeOverDocumentQueryResult
import org.multipaz.wallet.client.verification.IdentificationDocumentQueryResult
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.mutableStateMapOf
import kotlin.time.Clock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private data class CompletedVerificationData(
    val portrait: ImageBitmap?,
    val queryResult: Result,
    val presentmentRecord: PresentmentRecord
)

private const val TAG = "RequestVerificationScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RequestVerificationScreen(
    walletClient: WalletClient,
    storage: Storage,
    settingsModel: SettingsModel,
    documentModel: DocumentModel,
    promptModel: PromptModel,
    documentTypeRepository: DocumentTypeRepository,
    zkSystemRepository: ZkSystemRepository,
    issuerTrustManager: CompositeTrustManager,
    eventLogger: SimpleEventLogger,
    onSelectVerificationTypeClicked: () -> Unit,
    onNfcHandover: (result: ScanMdocReaderResult) -> Unit,
    onQrCodeScanned: (qrCode: String?) -> Unit,
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
    val signedInData = walletClient.signedInUser.collectAsState().value
    val isDarkTheme = isSystemInDarkTheme()
    val isInPerson = settingsModel.verificationIsInPerson.collectAsState().value
    val notificationPermissionState = rememberNotificationPermissionState()
    var showPermissionDialog by remember { mutableStateOf(false) }

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
                    val updatedCompleted = withContext(Dispatchers.Default) {
                        getCompletedVerifications(storage)
                    }
                    pendingList.value = updatedPending
                    completedList.value = updatedCompleted
                }
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
                            onNfcHandover(scanResult)
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
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.request_verification_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onVerificationHistoryClicked) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "Verification History"
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
            ) {
                SegmentedButton(
                    modifier = Modifier
                        .fillMaxHeight(),
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = !isInPerson,
                    onClick = { settingsModel.verificationIsInPerson.value = false },
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    label = {
                        Text(text = stringResource(R.string.request_verification_send_link))
                    }
                )
                SegmentedButton(
                    modifier = Modifier
                        .fillMaxHeight(),
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
                    heading = selectedQuery.getDisplayName(),
                    content = {
                        Text(
                            text = selectedQuery.getDescription(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = Icons.Outlined.ChevronRight,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = null
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                                                showToast("Failed to share link: ${e.message}")
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
                                            coroutineScope.launch {
                                                try {
                                                    walletClient.deleteVerificationResponse(item.requestId)
                                                    deleteVerification(storage, item.requestId)
                                                } catch (e: Exception) {
                                                    Logger.e(TAG, "Failed to delete verification on click", e)
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
                }
            } else {
                Spacer(modifier = Modifier.height(5.dp))

                val showQrCodeScanner = remember { mutableStateOf(false) }
                val rotation by animateFloatAsState(
                    targetValue = if (showQrCodeScanner.value) 180f else 0f,
                    animationSpec = tween(durationMillis = 400),
                    label = "flipAnimation"
                )
                val density = LocalDensity.current.density

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 12f * density
                        }
                ) {
                    if (rotation <= 90f) {
                        InPersonNfcView(
                            isDarkTheme = isDarkTheme,
                            onScanQrClicked = { showQrCodeScanner.value = true }
                        )
                    } else {
                        InPersonQrScannerView(
                            onScanNfcClicked = { showQrCodeScanner.value = false },
                            onQrCodeScanned = onQrCodeScanned
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InPersonNfcView(
    isDarkTheme: Boolean,
    onScanQrClicked: () -> Unit
) {
    FloatingItemList(modifier = Modifier.fillMaxSize()) {
        FloatingItemContainer {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.5f))
                val composition by rememberLottieComposition(
                    spec = LottieCompositionSpec.RawRes(
                        resId = if (isDarkTheme) R.raw.nfc_animation_dark else R.raw.nfc_animation
                    )
                )
                val progressState = animateLottieCompositionAsState(
                    composition = composition,
                    iterations = LottieConstants.IterateForever
                )
                Image(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp).padding(16.dp),
                    painter = rememberLottiePainter(
                        composition = composition,
                        progress = progressState.value,
                    ),
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.request_verification_hold_to_wallet),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(0.5f))
                ScanQrButton(onScanQrClicked = onScanQrClicked)
            }
        }
    }
}

@Composable
private fun InPersonQrScannerView(
    onScanNfcClicked: () -> Unit,
    onQrCodeScanned: (qrCode: String?) -> Unit
) {
    val permissionScope = rememberCoroutineScope()
    val permissionState = rememberCameraPermissionState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = 180f
            }
    ) {
        if (permissionState.isGranted) {
            Column(
                modifier = Modifier
                    .dropShadow(
                        shape = RoundedCornerShape(16.dp),
                        shadow = Shadow(
                            radius = 10.dp,
                            spread = 7.5.dp,
                            color = Color.Black.copy(alpha = 0.15f),
                            offset = DpOffset(x = 0.dp, 2.dp)
                        )
                    )
                    .clip(RoundedCornerShape(16.dp))
            ) {
                QrScanner(
                    onScanNfcClicked = onScanNfcClicked,
                    onQrCodeScanned = onQrCodeScanned
                )
            }
        } else {
            FloatingItemList {
                FloatingItemContainer {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.weight(0.5f))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.request_verification_camera_permission_required),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                permissionScope.launch {
                                    permissionState.launchPermissionRequest()
                                }
                            }) {
                                Text(text = stringResource(R.string.request_verification_grant_permission))
                            }
                        }

                        Spacer(modifier = Modifier.weight(0.5f))
                        ScanNfcButton(
                            onScanNfcClicked = onScanNfcClicked
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanNfcButton(
    onScanNfcClicked: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onScanNfcClicked,
        colors = color?.let {
            ButtonDefaults.outlinedButtonColors(contentColor = color)
        } ?: ButtonDefaults.outlinedButtonColors(),
        border = color?.let {
            BorderStroke(1.dp, color)
        } ?: ButtonDefaults.outlinedButtonBorder()
    ) {
        Icon(
            imageVector = Icons.Outlined.Contactless,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = stringResource(R.string.request_verification_scan_nfc))
    }
}

@Composable
private fun ScanQrButton(
    onScanQrClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onScanQrClicked
    ) {
        Icon(
            imageVector = Icons.Outlined.QrCode2,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = stringResource(R.string.request_verification_scan_qr_code)
        )
    }
}

@Composable
private fun QrScanner(
    onScanNfcClicked: () -> Unit,
    onQrCodeScanned: (qrCode: String?) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        QrCodeScanner(
            modifier = Modifier.fillMaxSize(),
            cameraSelection = CameraSelection.DEFAULT_BACK_CAMERA,
            captureResolution = CameraCaptureResolution.HIGH,
            showCameraPreview = true,
            onCodeScanned = { qrCode ->
                onQrCodeScanned(qrCode)
            }
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.request_verification_scan_qr_code_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            ScanNfcButton(
                modifier = Modifier.padding(bottom = 16.dp),
                onScanNfcClicked = onScanNfcClicked,
                color = Color.White,
            )
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

data class CompletedVerificationUiState(
    val item: LinkVerification,
    val presentmentRecord: PresentmentRecord?,
    val isTrusted: Boolean
)