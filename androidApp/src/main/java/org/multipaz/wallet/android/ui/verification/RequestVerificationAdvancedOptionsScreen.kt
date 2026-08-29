package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestVerificationAdvancedOptionsScreen(
    settingsModel: SettingsModel,
    onIssuerIdentifiersClicked: () -> Unit,
    onCustomReaderAuthenticationClicked: () -> Unit,
    onBackClicked: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val issuerIdentifiers by settingsModel.verificationIssuerIdentifiers.collectAsState()
    val readerKey by settingsModel.customVerificationReaderKey.collectAsState()
    val readerCertChain by settingsModel.customVerificationReaderCertChain.collectAsState()
    val useNfcV2 by settingsModel.useNfcV2.collectAsState()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = { Text(stringResource(R.string.request_verification_advanced_options_title)) },
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
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            Note(stringResource(R.string.request_verification_advanced_options_note))
            FloatingItemList {
                val contentText = if (issuerIdentifiers.isEmpty()) {
                    stringResource(R.string.request_verification_advanced_no_issuer_identifiers)
                } else if (issuerIdentifiers.size == 1) {
                    stringResource(R.string.request_verification_advanced_issuer_identifiers_one)
                } else {
                    stringResource(
                        R.string.request_verification_advanced_issuer_identifiers_many,
                        issuerIdentifiers.size
                    )
                }
                FloatingItemHeadingAndContent(
                    modifier = Modifier.clickable { onIssuerIdentifiersClicked() },
                    showChevron = true,
                    heading = stringResource(R.string.request_verification_advanced_issuer_identifiers_heading),
                    content = {
                        Text(
                            text = contentText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                val readerAuthContentText = if (readerKey == null || readerCertChain == null) {
                    stringResource(R.string.request_verification_advanced_custom_reader_auth_not_configured)
                } else {
                    val leafCert = readerCertChain!!.certificates[0]
                    val cn = leafCert.subject.components["2.5.4.3"]?.value
                    val name = cn ?: leafCert.subject.name
                    stringResource(R.string.request_verification_advanced_custom_reader_auth_configured, name)
                }
                FloatingItemHeadingAndContent(
                    modifier = Modifier.clickable { onCustomReaderAuthenticationClicked() },
                    showChevron = true,
                    heading = stringResource(R.string.request_verification_advanced_custom_reader_auth_heading),
                    content = {
                        Text(
                            text = readerAuthContentText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                val useNfcV2ContentText = if (useNfcV2) {
                    stringResource(R.string.request_verification_advanced_use_nfc_v2_enabled)
                } else {
                    stringResource(R.string.request_verification_advanced_use_nfc_v2_disabled)
                }
                FloatingItemHeadingAndContent(
                    modifier = Modifier.clickable {
                        settingsModel.useNfcV2.value = !settingsModel.useNfcV2.value
                    },
                    heading = stringResource(R.string.request_verification_advanced_use_nfc_v2_heading),
                    content = {
                        Text(
                            text = useNfcV2ContentText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = useNfcV2,
                            onCheckedChange = { value ->
                                settingsModel.useNfcV2.value = value
                            }
                        )
                    }
                )
            }
        }
    }
}
