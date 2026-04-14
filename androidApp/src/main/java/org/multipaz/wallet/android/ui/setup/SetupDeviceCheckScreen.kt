package org.multipaz.wallet.android.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.wallet.android.R
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.WalletClientAuthorizationException
import org.multipaz.wallet.client.WalletClientBackendUnreachableException
import kotlin.system.exitProcess

@Composable
fun SetupDeviceCheckScreen(
    walletClient: WalletClient,
    onContinueClicked: () -> Unit
) {
    var isChecking by remember { mutableStateOf(true) }
    var isUnreachable by remember { mutableStateOf(false) }
    var isUnauthorized by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryCount) {
        isChecking = true
        isUnreachable = false
        isUnauthorized = false
        try {
            walletClient.refreshPublicData()
            onContinueClicked()
        } catch (e: WalletClientAuthorizationException) {
            isChecking = false
            isUnauthorized = true
        } catch (e: WalletClientBackendUnreachableException) {
            isChecking = false
            isUnreachable = true
        } catch (e: Exception) {
            // Treat unknown exceptions as unreachable so the user can at least retry
            e.printStackTrace()
            isChecking = false
            isUnreachable = true
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.setup_device_check_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else if (isUnreachable) {
                Icon(
                    imageVector = Icons.Outlined.SignalWifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.setup_device_check_unreachable_text),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { retryCount++ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.setup_device_check_unreachable_retry))
                }
            } else if (isUnauthorized) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = AnnotatedString.fromMarkdown(
                        stringResource(R.string.setup_device_check_unauthorized_text)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { exitProcess(0) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.setup_device_check_unauthorized_exit))
                }
            }
        }
    }
}
