package org.multipaz.wallet.android.ui.verification

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.multipaz.nfc.ExternalNfcReaderState
import org.multipaz.nfc.ExternalNfcReaderStore
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottiePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.compose.qrcode.QrCodeScanner
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.nfc.MdocReaderNfcHandoverOptions
import org.multipaz.mdoc.nfc.ScanMdocReaderResult
import org.multipaz.mdoc.nfc.scanMdocReader
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.nfc.NfcScanOptions
import org.multipaz.nfc.NfcTagReader
import org.multipaz.prompt.PromptModel
import org.multipaz.securearea.SecureArea
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import org.multipaz.util.fromHex
import org.multipaz.verification.Iso18013PresentmentRecord
import org.multipaz.verification.PresentmentRecord
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.getReaderAuthenticationKey
import org.multipaz.wallet.android.navigation.ProximityScanMode
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.verification.ProximityReaderModel

private const val TAG = "VerificationProximityTransferScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationProximityTransferScreen(
    proximityReaderModel: ProximityReaderModel,
    walletClient: WalletClient,
    secureArea: SecureArea,
    settingsModel: SettingsModel,
    externalNfcReaderStore: ExternalNfcReaderStore,
    promptModel: PromptModel,
    initialScanMode: ProximityScanMode = ProximityScanMode.NONE,
    nfcOnly: Boolean = false,
    onNfcHandover: (suspend (ScanMdocReaderResult) -> Unit)? = null,
    onQrCodeScanned: (suspend (String) -> Unit)? = null,
    onBackClicked: () -> Unit,
    onTransferComplete: (presentmentRecord: PresentmentRecord) -> Unit,
    onTransferError: (error: Throwable) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope { promptModel }
    val scrollState = rememberScrollState()
    val isDarkTheme = isSystemInDarkTheme()

    DisposableEffect(Unit) {
        onDispose {
            proximityReaderModel.reset()
        }
    }

    val state = proximityReaderModel.state.collectAsState().value

    LaunchedEffect(state) {
        when (state) {
            ProximityReaderModel.State.WAITING_FOR_DEVICE_REQUEST -> {
                try {
                    val (readerAuthKey, keyInfoToMarkUsed) = getReaderAuthenticationKey(
                        settingsModel = settingsModel,
                        walletClient = walletClient,
                        secureArea = secureArea
                    )
                    val query = settingsModel.readerQuery.value
                    val sessionTranscript = try {
                        proximityReaderModel.sessionTranscript
                    } catch (e: Exception) {
                        Logger.w(TAG, "Session transcript not available", e)
                        return@LaunchedEffect
                    }
                    val deviceRequest = query.generateDeviceRequest(
                        deviceEngagement = sessionTranscript.asArray[0].asTaggedEncodedCbor,
                        sessionTranscript = sessionTranscript,
                        readerAuthKey = readerAuthKey,
                        intentToRetain = settingsModel.verificationStoreResponse.value,
                        issuerIdentifiers = settingsModel.verificationIssuerIdentifiers.value
                    )
                    if (keyInfoToMarkUsed != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                walletClient.markReaderKeyAsUsed(
                                    keyInfo = keyInfoToMarkUsed
                                )
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Logger.w(TAG, "Error marking reader key as used", e)
                            }
                        }
                    }
                    proximityReaderModel.setDeviceRequest(
                        query = query,
                        deviceRequest = deviceRequest
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Logger.e(TAG, "Error creating device request", e)
                    onTransferError(e)
                }
            }
            ProximityReaderModel.State.WAITING_FOR_START -> {
                proximityReaderModel.start(coroutineScope)
            }
            ProximityReaderModel.State.COMPLETED -> {
                val error = proximityReaderModel.error
                val result = proximityReaderModel.result
                if (error != null) {
                    onTransferError(error)
                } else if (result != null) {
                    if (result.deviceResponse == null) {
                        onTransferError(IllegalStateException("No DeviceResponse message"))
                    } else if (result.deviceResponse!!.status != 0) {
                        onTransferError(IllegalStateException("DeviceResponse has non-zero status ${result.deviceResponse!!.status}"))
                    } else {
                        val presentmentRecord = Iso18013PresentmentRecord(
                            response = result.deviceResponse!!.toDataItem(),
                            sessionTranscript = result.sessionTranscript,
                            request = result.deviceRequest!!.toDataItem(),
                            eDeviceKey = result.eReaderKey,
                            encryptionInfo = null,
                            origin = null
                        )
                        onTransferComplete(presentmentRecord)
                    }
                }
            }
            else -> {}
        }
    }

    val selectedReaderId = settingsModel.selectedExternalNfcReaderId.collectAsState().value
    val externalReaders = externalNfcReaderStore.readers.collectAsState().value
    val externalReader = remember(selectedReaderId, externalReaders) {
        if (selectedReaderId != null) externalReaders.find { it.id == selectedReaderId } else null
    }

    val nfcTagReader = produceState<NfcTagReader?>(initialValue = null, key1 = externalReader) {
        value = if (externalReader != null) {
            try {
                when (externalReader.observeState().first()) {
                    ExternalNfcReaderState.NOT_CONNECTED -> {
                        Logger.w(TAG, "External reader is not connected")
                        NfcTagReader.getReaders().firstOrNull()
                    }
                    ExternalNfcReaderState.CONNECTED_NO_PERMISSION -> {
                        if (externalReader.requestPermission()) {
                            externalReader.getNfcTagReader()
                        } else {
                            Logger.w(TAG, "Permission denied for external reader")
                            NfcTagReader.getReaders().firstOrNull()
                        }
                    }
                    ExternalNfcReaderState.CONNECTED -> {
                        externalReader.getNfcTagReader()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e(TAG, "Error getting external NFC tag reader", e)
                NfcTagReader.getReaders().firstOrNull()
            }
        } else {
            NfcTagReader.getReaders().firstOrNull()
        }
    }.value

    val isExternalReader = (externalReader != null) || (nfcTagReader?.external == true)

    val nfcScanOptions = remember {
        if (nfcPollingFramesInsertionSupported) {
            NfcScanOptions(
                pollingFrameData = ByteString("6a0281030000".fromHex())
            )
        } else {
            NfcScanOptions()
        }
    }

    val useNfcV2 = settingsModel.useNfcV2.collectAsState().value

    LaunchedEffect(initialScanMode, nfcTagReader, nfcOnly, useNfcV2) {
        if (proximityReaderModel.state.value == ProximityReaderModel.State.IDLE && initialScanMode == ProximityScanMode.NFC && onNfcHandover != null) {
            if (nfcTagReader != null && !nfcTagReader.dialogAlwaysShown) {
                withContext(promptModel) {
                    while (isActive) {
                        try {
                            val scanResult = nfcTagReader.scanMdocReader(
                                message = null,
                                options = MdocTransportOptions(
                                    bleUseL2CAP = false,               // Doesn't work with Apple Wallet
                                    bleUseL2CAPInEngagement = true
                                ),
                                handoverOptions = MdocReaderNfcHandoverOptions(
                                    useNfcV2 = useNfcV2
                                ),
                                transportFactory = MdocTransportFactory.Default,
                                selectConnectionMethod = { connectionMethods -> connectionMethods.first() },
                                negotiatedHandoverConnectionMethods = if (nfcOnly) {
                                    emptyList()
                                } else {
                                    listOf(
                                        MdocConnectionMethodBle(
                                            supportsPeripheralServerMode = false,
                                            supportsCentralClientMode = true,
                                            peripheralServerModeUuid = null,
                                            centralClientModeUuid = UUID.randomUUID(),
                                        )
                                    )
                                },
                                nfcScanOptions = nfcScanOptions,
                                onHandover = { scanResult ->
                                    onNfcHandover(scanResult)
                                    scanResult
                                }
                            )
                            if (scanResult != null) {
                                break
                            }
                        } catch (e: Throwable) {
                            if (!isActive) {
                                Logger.e(TAG, "Caught exception while scanning and scope isn't active", e)
                                break
                            } else if (e is SecurityException) {
                                Logger.e(TAG, "SecurityException while scanning, stopping scan", e)
                                break
                            } else {
                                Logger.e(TAG, "Caught exception while scanning. Retrying", e)
                            }
                        }
                    }
                }
            }
        }
    }

    val isScanning = state == ProximityReaderModel.State.IDLE && initialScanMode != ProximityScanMode.NONE

    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    if (isScanning && initialScanMode == ProximityScanMode.QR) {
                        Text(stringResource(R.string.request_verification_scan_qr_code_to_verify))
                    } else if (isScanning && initialScanMode == ProximityScanMode.NFC) {
                        Text(stringResource(R.string.request_verification_scan_nfc_to_verify))
                    }
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = isScanning,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400))) togetherWith (fadeOut(animationSpec = tween(400)))
            },
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            label = "scanningToTransferTransition"
        ) { scanning ->
            if (scanning) {
                if (initialScanMode == ProximityScanMode.NFC) {
                    InPersonNfcView(
                        isDarkTheme = isDarkTheme,
                        isExternal = isExternalReader,
                        nfcOnly = nfcOnly
                    )
                } else if (initialScanMode == ProximityScanMode.QR) {
                    InPersonQrScannerView(
                        onQrCodeScanned = { qrCode ->
                            if (qrCode?.startsWith("mdoc:") == true && onQrCodeScanned != null) {
                                if (proximityReaderModel.state.value == ProximityReaderModel.State.IDLE) {
                                    coroutineScope.launch {
                                        onQrCodeScanned(qrCode)
                                    }
                                }
                            }
                        }
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space = 16.dp, alignment = Alignment.CenterVertically)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text = stringResource(R.string.verification_proximity_transfer_waiting_for_response),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InPersonNfcView(
    isDarkTheme: Boolean,
    isExternal: Boolean = false,
    nfcOnly: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
                .height(250.dp)
                .padding(16.dp),
            painter = rememberLottiePainter(
                composition = composition,
                progress = progressState.value,
            ),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (isExternal) R.string.request_verification_tap_on_external_nfc_reader
                else R.string.request_verification_hold_to_wallet
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (nfcOnly) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.request_verification_nfc_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InPersonQrScannerView(
    onQrCodeScanned: (qrCode: String?) -> Unit
) {
    val permissionScope = rememberCoroutineScope()
    val permissionState = rememberCameraPermissionState()

    if (permissionState.isGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
            QrCodeScanner(
                modifier = Modifier.fillMaxSize(),
                cameraSelection = CameraSelection.DEFAULT_BACK_CAMERA,
                captureResolution = CameraCaptureResolution.HIGH,
                showCameraPreview = true,
                onCodeScanned = { qrCode ->
                    onQrCodeScanned(qrCode)
                }
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
    }
}