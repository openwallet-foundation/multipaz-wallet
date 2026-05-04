package org.multipaz.wallet.android.ui.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.multipaz.cbor.Cbor
import org.multipaz.compose.decodeImage
import org.multipaz.compose.document.DocumentModel
import org.multipaz.documenttype.DocumentAttributeSensitivity
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.toBase64Url
import org.multipaz.wallet.android.R
import org.multipaz.wallet.client.DocumentPreconsentApprovedReader
import org.multipaz.wallet.client.DocumentPreconsentSetting
import org.multipaz.wallet.client.preconsentSetting
import org.multipaz.wallet.client.setPreconsentSetting

@Composable
fun ManageTrustedReadersAddReaderDialog(
    documentId: String,
    certData: String,
    documentModel: DocumentModel,
    readerTrustManager: CompositeTrustManager,
    imageLoader: ImageLoader,
    onDismissed: () -> Unit
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
    
    val selectedTrustPoint = remember(allTrustPoints, certData) {
        allTrustPoints.find { point ->
            Cbor.encode(point.certificate.toDataItem()).toBase64Url() == certData
        }
    }

    if (selectedTrustPoint == null) {
        if (allTrustPoints.isNotEmpty()) {
            onDismissed()
        }
        return
    }

    var selectedSensitivity by remember { mutableStateOf<DocumentAttributeSensitivity?>(DocumentAttributeSensitivity.PORTRAIT_IMAGE) }

    AlertDialog(
        onDismissRequest = onDismissed,
        icon = {
            selectedTrustPoint.metadata.displayIcon?.let {
                val bitmap = remember { decodeImage(it.toByteArray()) }
                Image(
                    modifier = Modifier.size(48.dp),
                    bitmap = bitmap,
                    contentDescription = null
                )
            } ?: selectedTrustPoint.metadata.displayIconUrl?.let {
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
        title = {
            Text(stringResource(R.string.preconsent_trusted_readers_add))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val readerName = selectedTrustPoint.metadata.displayName
                    ?: selectedTrustPoint.certificate.subject.toString()
                
                Text(
                    text = stringResource(R.string.preconsent_trusted_readers_add_dialog_question, readerName),
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensitivityRadioOption(
                        title = stringResource(R.string.preconsent_trusted_readers_add_age_checks_title),
                        description = stringResource(R.string.preconsent_trusted_readers_add_age_checks_description),
                        selected = selectedSensitivity == DocumentAttributeSensitivity.PORTRAIT_IMAGE,
                        onClick = { selectedSensitivity = DocumentAttributeSensitivity.PORTRAIT_IMAGE }
                    )
                    SensitivityRadioOption(
                        title = stringResource(R.string.preconsent_trusted_readers_add_any_info_title),
                        description = stringResource(R.string.preconsent_trusted_readers_add_any_info_description),
                        selected = selectedSensitivity == DocumentAttributeSensitivity.PII,
                        onClick = { selectedSensitivity = DocumentAttributeSensitivity.PII }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                coroutineScope.launch {
                    addReader(
                        documentInfo,
                        currentSetting,
                        selectedTrustPoint,
                        selectedSensitivity
                    )
                    onDismissed()
                }
            }) {
                Text(stringResource(R.string.preconsent_trusted_readers_add_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissed) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun SensitivityRadioOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // handled by row click
            modifier = Modifier.padding(end = 16.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private suspend fun addReader(
    documentInfo: org.multipaz.compose.document.DocumentInfo,
    currentSetting: DocumentPreconsentSetting.ReaderIdentityBased,
    trustPoint: TrustPoint,
    sensitivity: DocumentAttributeSensitivity?
) {
    val newRequester = DocumentPreconsentApprovedReader(
        metadata = trustPoint.metadata,
        certificate = trustPoint.certificate,
        approvedSensitivity = sensitivity
    )
    documentInfo.document.setPreconsentSetting(
        DocumentPreconsentSetting.ReaderIdentityBased(
            currentSetting.approvedReaders + newRequester
        )
    )
}
