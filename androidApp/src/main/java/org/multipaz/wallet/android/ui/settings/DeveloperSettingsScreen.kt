package org.multipaz.wallet.android.ui.settings

import android.accounts.Account
import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DoorBack
import androidx.compose.material.icons.outlined.NoEncryption
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.context.applicationContext
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.InfoNote
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.BuildConfig

private const val TAG = "DeveloperSettingsScreen"

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    onCorruptEncryptionKeyClicked: () -> Unit,
    onConfigureWalletBackendClicked: () -> Unit,
    onRunFirstTimeSetupClicked: () -> Unit,
    onClearAppDataClicked: () -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.dev_settings_screen_title))
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
            InfoNote(
                markdownString = stringResource(R.string.dev_settings_screen_warning),
            )

            FloatingItemList {
                FloatingItemHeadingAndText(
                    modifier = Modifier.clickable {
                        onCorruptEncryptionKeyClicked()
                    },
                    image = {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            imageVector = Icons.Outlined.NoEncryption,
                            contentDescription = null
                        )
                    },
                    heading = stringResource(R.string.dev_settings_corrupt_key_title),
                    text = stringResource(R.string.dev_settings_corrupt_key_text)
                )

                FloatingItemHeadingAndText(
                    modifier = Modifier.clickable {
                        val signedInData = walletClient.signedInUser.value
                        if (signedInData == null) {
                            showToast(context.getString(R.string.dev_settings_not_signed_in))
                        } else {
                            coroutineScope.launch {
                                val requestedScopes = listOf(Scope("https://www.googleapis.com/auth/drive.appdata"))

                                val request = RevokeAccessRequest.builder()
                                    .setAccount(Account(signedInData.id, "com.google"))
                                    .setScopes(requestedScopes)
                                    .build()

                                try {
                                    Identity.getAuthorizationClient(applicationContext)
                                        .revokeAccess(request)
                                        .await()
                                    Logger.i(TAG, "Successfully revoked Drive access")
                                    showToast(context.getString(R.string.dev_settings_revoke_drive_success))
                                } catch (e: Exception) {
                                    Logger.e(TAG, "Failed to revoke Drive access", e)
                                    showToast(context.getString(R.string.dev_settings_revoke_drive_failure, e.toString()))
                                }
                            }
                        }
                    },
                    image = {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            imageVector = Icons.Outlined.Remove,
                            contentDescription = null
                        )
                    },
                    heading = stringResource(R.string.dev_settings_revoke_drive_title),
                    text = stringResource(R.string.dev_settings_revoke_drive_text)
                )

                FloatingItemHeadingAndText(
                    modifier = Modifier.clickable {
                        settingsModel.explicitlySignedOut.value = false
                    },
                    image = {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            imageVector = Icons.AutoMirrored.Outlined.Login,
                            contentDescription = null
                        )
                    },
                    heading = stringResource(R.string.dev_settings_clear_signed_out_flag_title),
                    text = stringResource(R.string.dev_settings_clear_signed_out_flag_text)
                )

                val customWalletBackendUrl = settingsModel.walletBackendUrl.collectAsState().value
                val walletBackendText = if (customWalletBackendUrl == null) {
                    stringResource(R.string.dev_settings_using_backend_default, BuildConfig.BACKEND_URL)
                } else {
                    stringResource(R.string.dev_settings_using_backend_custom, customWalletBackendUrl)
                }
                FloatingItemHeadingAndText(
                    modifier = Modifier.clickable {
                        onConfigureWalletBackendClicked()
                    },
                    image = {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = null
                        )
                    },
                    heading = stringResource(R.string.dev_settings_set_backend_title),
                    text = walletBackendText
                )

                FloatingItemHeadingAndText(
                    modifier = Modifier.clickable {
                        onRunFirstTimeSetupClicked()
                    },
                    image = {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = null
                        )
                    },
                    heading = stringResource(R.string.dev_settings_run_setup_title),
                    text = stringResource(R.string.dev_settings_run_setup_text)
                )

                FloatingItemHeadingAndText(
                    modifier = Modifier.clickable {
                        onClearAppDataClicked()
                    },
                    image = {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = null
                        )
                    },
                    heading = stringResource(R.string.dev_settings_delete_app_data_title),
                    text = stringResource(R.string.dev_settings_delete_app_data_text)
                )

                FloatingItemHeadingAndText(
                    modifier = Modifier.clickable {
                        settingsModel.devMode.value = false
                        onBackClicked()
                    },
                    image = {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            imageVector = Icons.Outlined.DoorBack,
                            contentDescription = null
                        )
                    },
                    heading = stringResource(R.string.dev_settings_exit_dev_mode_title),
                    text = stringResource(R.string.dev_settings_exit_dev_mode_text)
                )
            }
        }
    }
}
