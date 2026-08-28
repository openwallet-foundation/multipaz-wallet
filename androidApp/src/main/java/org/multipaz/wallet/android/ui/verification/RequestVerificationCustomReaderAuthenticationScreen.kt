package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.certificateviewer.X509CertViewer
import org.multipaz.compose.pickers.rememberFilePicker
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestVerificationCustomReaderAuthenticationScreen(
    settingsModel: SettingsModel,
    onImportPkcs12: (bytes: ByteString) -> Unit,
    onDeleteClicked: () -> Unit,
    onBackClicked: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val readerKey by settingsModel.customVerificationReaderKey.collectAsState()
    val readerCertChain by settingsModel.customVerificationReaderCertChain.collectAsState()

    val filePicker = rememberFilePicker(
        types = listOf(
            "application/x-pkcs12",
            "application/x-pkcs12-certificates",
            "application/octet-stream",
            "*/*"
        ),
        allowMultiple = false,
        onResult = { files ->
            if (files.isNotEmpty()) {
                onImportPkcs12(files[0])
            }
        }
    )

    var selectedCertIndex by remember(readerCertChain) { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = { Text(stringResource(R.string.request_verification_custom_reader_auth_screen_title)) },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    if (readerKey != null && readerCertChain != null) {
                        IconButton(onClick = onDeleteClicked) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.request_verification_custom_reader_auth_action_delete)
                            )
                        }
                    }
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
            val noteText = if (readerKey != null && readerCertChain != null) {
                stringResource(R.string.request_verification_custom_reader_auth_note_configured)
            } else {
                stringResource(R.string.request_verification_custom_reader_auth_note_not_configured)
            }
            Note(noteText)

            if (readerKey == null || readerCertChain == null) {
                Button(
                    onClick = { filePicker.launch() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.request_verification_custom_reader_auth_import_button))
                }
            } else {
                val certificates = readerCertChain!!.certificates
                if (certificates.size > 1) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        certificates.forEachIndexed { index, _ ->
                            val label = when {
                                index == 0 -> stringResource(R.string.request_verification_custom_reader_auth_cert_tab_leaf)
                                index == certificates.size - 1 -> stringResource(R.string.request_verification_custom_reader_auth_cert_tab_root)
                                certificates.size == 3 -> stringResource(R.string.request_verification_custom_reader_auth_cert_tab_intermediate)
                                else -> stringResource(R.string.request_verification_custom_reader_auth_cert_tab_intermediate_n, index)
                            }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = certificates.size),
                                selected = selectedCertIndex == index,
                                onClick = { selectedCertIndex = index },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                val currentCert = certificates.getOrNull(selectedCertIndex) ?: certificates[0]
                key(selectedCertIndex, currentCert) {
                    X509CertViewer(certificate = currentCert)
                }
            }
        }
    }
}
