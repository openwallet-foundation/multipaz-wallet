package org.multipaz.wallet.android.ui.provisioning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.compose.qrcode.QrCodeScanner
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanCredentialOfferScreen(
    onCredentialOfferScanned: (offerUri: String) -> Unit,
    onBackClicked: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val permissionScope = rememberCoroutineScope()
    val permissionState = rememberCameraPermissionState()
    var scanned by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.scan_credential_offer_screen_title))
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            contentAlignment = Alignment.Center
        ) {
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
                            if (!scanned && !qrCode.isNullOrEmpty()) {
                                scanned = true
                                onCredentialOfferScanned(qrCode)
                            }
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
                        text = stringResource(R.string.scan_credential_offer_camera_permission_required),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        permissionScope.launch {
                            permissionState.launchPermissionRequest()
                        }
                    }) {
                        Text(text = stringResource(R.string.scan_credential_offer_grant_permission))
                    }
                }
            }
        }
    }
}
