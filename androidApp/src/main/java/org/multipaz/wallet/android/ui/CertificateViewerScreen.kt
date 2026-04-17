package org.multipaz.wallet.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz.compose.certificateviewer.X509CertViewer
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.wallet.android.R


private val PAGER_INDICATOR_HEIGHT = 30.dp
private val PAGER_INDICATOR_PADDING = 8.dp

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

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { Text(title) },
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
        Box(
            modifier = modifier
                .fillMaxHeight()
                .padding(innerPadding)
        ) {
            val listSize = certificates.size
            val pagerState = rememberPagerState(pageCount = { listSize })

            Column(
                modifier = Modifier.then(
                    if (listSize > 1)
                        Modifier.padding(bottom = PAGER_INDICATOR_HEIGHT + PAGER_INDICATOR_PADDING)
                    else // No pager, no padding.
                        Modifier
                )
            ) {
                HorizontalPager(
                    state = pagerState,
                ) { page ->
                    val scrollState = rememberScrollState()
                    X509CertViewer(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        certificate = certificates[page]
                    )
                }
            }

            if (listSize > 1) { // Don't show pager for single cert on the list.
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .wrapContentHeight()
                        .fillMaxWidth()
                        .height(PAGER_INDICATOR_HEIGHT)
                        .padding(PAGER_INDICATOR_PADDING),
                ) {
                    repeat(pagerState.pageCount) { iteration ->
                        val color =
                            if (pagerState.currentPage == iteration) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = .2f)
                            }
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(8.dp)
                        )
                    }
                }
            }
        }
    }
}

