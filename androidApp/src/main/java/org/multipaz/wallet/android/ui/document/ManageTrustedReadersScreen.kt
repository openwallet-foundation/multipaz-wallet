package org.multipaz.wallet.android.ui.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Business
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.multipaz.cbor.Cbor
import org.multipaz.compose.decodeImage
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.crypto.X509Cert
import org.multipaz.documenttype.DocumentAttributeSensitivity
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.toBase64Url
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.DocumentPreconsentSetting
import org.multipaz.wallet.client.preconsentSetting
import org.multipaz.wallet.client.setPreconsentSetting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTrustedReadersScreen(
    documentId: String,
    documentModel: DocumentModel,
    readerTrustManager: CompositeTrustManager,
    imageLoader: ImageLoader,
    onBackClicked: () -> Unit,
    onAddReaderClicked: (certData: String) -> Unit,
    onViewCertificateClicked: (cert: X509Cert) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val documentInfo = documentModel.documentInfos.collectAsState().value.find {
        it.document.identifier == documentId
    } ?: return

    val currentSetting = documentInfo.document.preconsentSetting as? DocumentPreconsentSetting.ReaderIdentityBased
        ?: DocumentPreconsentSetting.ReaderIdentityBased(emptyList())

    val allTrustPoints by produceState<List<TrustPoint>>(initialValue = emptyList()) {
        value = readerTrustManager.getTrustPoints()
    }
    val availablePoints = allTrustPoints.filter { point ->
        currentSetting.approvedReaders.none { added ->
            added.certificate == point.certificate
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.preconsent_trusted_readers_title))
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
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Note(stringResource(R.string.manage_trusted_readers_blurb))

            FloatingItemList(title = stringResource(R.string.manage_trusted_readers_trusted_section)) {
                if (currentSetting.approvedReaders.isEmpty()) {
                    FloatingItemCenteredText(text = stringResource(R.string.preconsent_trusted_readers_no_readers))
                } else {
                    currentSetting.approvedReaders.forEach { reader ->
                        val sensitivityText = when (reader.approvedSensitivity) {
                            null -> stringResource(R.string.preconsent_trusted_readers_sensitivity_null)
                            DocumentAttributeSensitivity.PII -> stringResource(R.string.preconsent_trusted_readers_sensitivity_pii)
                            DocumentAttributeSensitivity.PORTRAIT_IMAGE -> stringResource(R.string.preconsent_trusted_readers_sensitivity_portrait_age)
                            DocumentAttributeSensitivity.AGE_INFORMATION -> stringResource(R.string.preconsent_trusted_readers_sensitivity_age)
                            DocumentAttributeSensitivity.VALIDITY -> stringResource(R.string.preconsent_trusted_readers_sensitivity_validity)
                            DocumentAttributeSensitivity.ISSUER -> stringResource(R.string.preconsent_trusted_readers_sensitivity_issuer)
                        }
                        FloatingItemText(
                            modifier = Modifier.clickable { onViewCertificateClicked(reader.certificate) },
                            image = {
                                reader.metadata.displayIcon?.let {
                                    val bitmap = remember { decodeImage(it.toByteArray()) }
                                    Image(
                                        modifier = Modifier.size(48.dp),
                                        bitmap = bitmap,
                                        contentDescription = null
                                    )
                                } ?: reader.metadata.displayIconUrl?.let {
                                    AsyncImage(
                                        modifier = Modifier.size(48.dp),
                                        model = it,
                                        imageLoader = imageLoader,
                                        contentScale = ContentScale.Crop,
                                        contentDescription = null
                                    )
                                } ?: Icon(
                                    modifier = Modifier.size(48.dp),
                                    imageVector = Icons.Outlined.Business,
                                    contentDescription = null
                                )
                            },
                            text = reader.metadata.displayName ?: reader.certificate.subject.toString(),
                            secondary = sensitivityText,
                            trailingContent = {
                                IconButton(onClick = {
                                    val newList = currentSetting.approvedReaders.filter { it != reader }
                                    coroutineScope.launch {
                                        documentInfo.document.setPreconsentSetting(
                                            DocumentPreconsentSetting.ReaderIdentityBased(newList)
                                        )
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.preconsent_trusted_readers_remove)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Note(stringResource(R.string.manage_trusted_readers_available_blurb))

            FloatingItemList(title = stringResource(R.string.manage_trusted_readers_available_section)) {
                if (availablePoints.isEmpty()) {
                    FloatingItemCenteredText(text = stringResource(R.string.manage_trusted_readers_no_available_readers))
                } else {
                    availablePoints.forEach { point ->
                        FloatingItemText(
                            modifier = Modifier.clickable {
                                val certData = Cbor.encode(point.certificate.toDataItem()).toBase64Url()
                                onAddReaderClicked(certData)
                            },
                            image = {
                                point.metadata.displayIcon?.let {
                                    val bitmap = remember { decodeImage(it.toByteArray()) }
                                    Image(
                                        modifier = Modifier.size(48.dp),
                                        bitmap = bitmap,
                                        contentDescription = null
                                    )
                                } ?: point.metadata.displayIconUrl?.let {
                                    AsyncImage(
                                        modifier = Modifier.size(48.dp),
                                        model = it,
                                        imageLoader = imageLoader,
                                        contentScale = ContentScale.Crop,
                                        contentDescription = null
                                    )
                                } ?: Icon(
                                    modifier = Modifier.size(48.dp),
                                    imageVector = Icons.Outlined.Business,
                                    contentDescription = null
                                )
                            },
                            text = point.metadata.displayName ?: point.certificate.subject.toString(),
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.preconsent_trusted_readers_add)
                                )
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
