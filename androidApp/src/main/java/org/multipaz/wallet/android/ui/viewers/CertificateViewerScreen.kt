package org.multipaz.wallet.android.ui.viewers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.multipaz.compose.certificateviewer.X509CertViewer
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.hazeTopAppBar

@Composable
fun CertificateViewerScreen(
    x509CertChain: X509CertChain,
    onBackClicked: () -> Unit,
) {
    CertificateViewerInternal(
        certificates = x509CertChain.certificates,
        onBackClicked = onBackClicked
    )
}

@Composable
fun CertificateViewerScreen(
    x509Cert: X509Cert,
    onBackClicked: () -> Unit,
) {
    CertificateViewerInternal(
        certificates = listOf(x509Cert),
        onBackClicked = onBackClicked
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CertificateViewerInternal(
    certificates: List<X509Cert>,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    check(certificates.isNotEmpty())

    val title = if (certificates.size == 1) {
        stringResource(R.string.cert_viewer_title)
    } else {
        stringResource(R.string.cert_viewer_chain_title)
    }

    var selectedCertIndex by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedCertIndex) {
        scrollState.scrollTo(0)
    }

    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeTopAppBar(hazeState)
            ) {
                AppMediumTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        AppBackButton(onClick = onBackClicked)
                    },
                    scrollBehavior = scrollBehavior
                )
                if (certificates.size > 1) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        certificates.forEachIndexed { index, _ ->
                            val label = when {
                                index == 0 -> stringResource(R.string.cert_tab_leaf)
                                index == certificates.size - 1 -> stringResource(R.string.cert_tab_root)
                                certificates.size == 3 -> stringResource(R.string.cert_tab_intermediate)
                                else -> stringResource(R.string.cert_tab_intermediate_n, index)
                            }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = certificates.size
                                ),
                                selected = selectedCertIndex == index,
                                onClick = { selectedCertIndex = index },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            val topSpacerHeight = if (certificates.size > 1) {
                innerPadding.calculateTopPadding() + 8.dp
            } else {
                innerPadding.calculateTopPadding() + 16.dp
            }
            Spacer(modifier = Modifier.height(topSpacerHeight))

            val currentCert = certificates.getOrNull(selectedCertIndex) ?: certificates[0]
            key(selectedCertIndex, currentCert) {
                X509CertViewer(
                    certificate = currentCert
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

