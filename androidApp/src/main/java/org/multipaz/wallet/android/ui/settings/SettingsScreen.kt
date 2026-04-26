package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.android.ui.InfoNote
import org.multipaz.wallet.android.ui.ProfilePicture
import org.multipaz.wallet.client.WalletClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    isSigningIn: MutableState<Boolean>,
    isSigningOut: MutableState<Boolean>,
    onBackClicked: () -> Unit,
    onUseWithoutGoogleAccountClicked: () -> Unit,
    onSignInToGoogleClicked: () -> Unit,
    onTrustedIssuersClicked: () -> Unit,
    onTrustedReadersClicked: () -> Unit,
    onActivityLoggingClicked: () -> Unit,
    onDeveloperSettingsClicked: () -> Unit,
    onAboutClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val signedInData = walletClient.signedInUser.collectAsState().value

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.settings_screen_title))
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
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingItemList(title = stringResource(R.string.settings_screen_account_title)) {
                if (signedInData != null) {
                    if (isSigningOut.value) {
                        FloatingItemCenteredText(
                            text = stringResource(R.string.settings_screen_signing_out),
                        )
                    } else {
                        FloatingItemText(
                            text = signedInData.displayName ?: stringResource(R.string.settings_screen_google_user_default_name),
                            secondary = signedInData.id,
                            image = {
                                signedInData.ProfilePicture()
                            }
                        )
                        FloatingItemText(
                            modifier = Modifier.clickable {
                                onUseWithoutGoogleAccountClicked()
                            },
                            text = stringResource(R.string.settings_screen_use_without_google),
                            image = {
                                Icon(Icons.Outlined.NoAccounts, contentDescription = null)
                            }
                        )
                    }
                } else {
                    if (isSigningIn.value) {
                        FloatingItemCenteredText(
                            text = stringResource(R.string.settings_screen_signing_in),
                        )
                    } else {
                        FloatingItemText(
                            modifier = Modifier.clickable {
                                onSignInToGoogleClicked()
                            },
                            text = stringResource(R.string.settings_screen_sign_in),
                            image = {
                                Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                            }
                        )
                    }
                }
            }
            val markdownString = if (signedInData != null) {
                stringResource(R.string.settings_screen_sync_info_signed_in)
            } else {
                stringResource(R.string.settings_screen_sync_info_signed_out)
            }
            InfoNote(markdownString = markdownString)

            Spacer(modifier = Modifier.height(10.dp))
            FloatingItemList {
                FloatingItemText(
                    modifier = Modifier.clickable {
                        onTrustedIssuersClicked()
                    },
                    text = stringResource(R.string.settings_screen_trusted_issuers),
                    image = {
                        Icon(Icons.Outlined.AccountBalance, contentDescription = null)
                    }
                )
                FloatingItemText(
                    modifier = Modifier.clickable {
                        onTrustedReadersClicked()
                    },
                    text = stringResource(R.string.settings_screen_trusted_verifiers),
                    image = {
                        Icon(Icons.Outlined.Business, contentDescription = null)
                    }
                )
                FloatingItemText(
                    modifier = Modifier.clickable { onActivityLoggingClicked() },
                    text = stringResource(R.string.settings_screen_activity_logging_button_text),
                    image = {
                        Icon(Icons.Outlined.History, contentDescription = null)
                    },
                )
                if (settingsModel.devMode.collectAsState().value) {
                    FloatingItemText(
                        modifier = Modifier.clickable { onDeveloperSettingsClicked() },
                        text = stringResource(R.string.settings_screen_developer_settings),
                        image = {
                            Icon(Icons.Outlined.Science, contentDescription = null)
                        }
                    )
                }
                FloatingItemText(
                    modifier = Modifier.clickable { onAboutClicked() },
                    text = stringResource(R.string.settings_screen_about, BuildConfig.APP_NAME),
                    image = {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}