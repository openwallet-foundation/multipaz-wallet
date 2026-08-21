package org.multipaz.wallet.android.ui.provisioning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.multipaz.compose.items.FloatingItemContainer
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterIssuerUrlScreen(
    settingsModel: SettingsModel,
    onConnect: (issuerUrl: String) -> Unit,
    onBackClicked: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    var issuingServerUrl by remember {
        mutableStateOf(settingsModel.provisioningServerUrl.value)
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_title))
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))

            Note(stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_text))

            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = issuingServerUrl,
                onValueChange = { issuingServerUrl = it },
                singleLine = true,
                label = {
                    Text(stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_url_label))
                }
            )

            val resetToDefaultText = stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_reset)
            Text(
                text = AnnotatedString.fromMarkdown(
                    markdownString = "[$resetToDefaultText](reset://)",
                    linkInteractionListener = {
                        settingsModel.provisioningServerUrl.value = SettingsModel.DEFAULT_PROVISIONING_SERVER_URL
                        issuingServerUrl = SettingsModel.DEFAULT_PROVISIONING_SERVER_URL
                    }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = issuingServerUrl.isNotBlank(),
                onClick = {
                    settingsModel.provisioningServerUrl.value = issuingServerUrl
                    onConnect(issuingServerUrl)
                }
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 8.dp),
                    text = stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_connect),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
