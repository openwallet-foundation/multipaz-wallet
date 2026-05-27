package org.multipaz.wallet.android.ui.verification

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCode2
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import org.multipaz.wallet.client.WalletClient

private const val TAG = "RequestVerificationScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestVerificationScreen(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    documentModel: DocumentModel,
    promptModel: PromptModel,
    onSelectVerificationTypeClicked: () -> Unit,
    onNfcHandover: (result: ScanMdocReaderResult) -> Unit,
    onQrCodeScanned: (qrCode: String?) -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope { promptModel }
    val signedInData = walletClient.signedInUser.collectAsState().value
    val isDarkTheme = isSystemInDarkTheme()
    val isInPerson = remember { mutableStateOf(true) } // TODO: from settings

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
                    selected = !isInPerson.value,
                    onClick = { isInPerson.value = false },
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    label = {
                        Text(text = stringResource(R.string.request_verification_send_link))
                    }
                )
                SegmentedButton(
                    modifier = Modifier
                        .fillMaxHeight(),
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = isInPerson.value,
                    onClick = { isInPerson.value = true },
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
            }

            if (!isInPerson.value) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showToast("Not yet implemented, stay tuned")  // Temp string, do not translate.
                    }
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
            androidx.compose.foundation.BorderStroke(1.dp, color)
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