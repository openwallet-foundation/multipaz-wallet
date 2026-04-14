package org.multipaz.wallet.android.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.multipaz.wallet.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Simple
import org.multipaz.compose.prompt.PresentmentActivity
import org.multipaz.compose.qrcode.generateQrCode
import org.multipaz.context.applicationContext
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.engagement.buildDeviceEngagement
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.presentment.Iso18013Presentment
import org.multipaz.presentment.PresentmentCanceledException
import org.multipaz.presentment.PresentmentModel
import org.multipaz.presentment.PresentmentSource
import org.multipaz.util.Logger
import org.multipaz.util.UUID
import org.multipaz.util.toBase64Url
import org.multipaz.wallet.android.App
import kotlin.time.Duration.Companion.seconds

private const val TAG = "DocumentQrPresentmentDialog"

@Composable
fun  DocumentQrPresentmentDialog(
    documentStore: DocumentStore,
    documentId: String,
    scope: CoroutineScope,
    onDismissed: () -> Unit,
    onTransactionUnderway: () -> Unit,
) {
    var qrCodeToShow by remember { mutableStateOf<String?>(null) }
    var transactionJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        val document = documentStore.lookupDocument(documentId)
        proximityPresentment(
            document = document,
            scope = scope,
            source = App.getPresentmentSource(),
            qrCodeReady = { qrCode ->
                qrCodeToShow = qrCode
            },
            onTransactionUnderway = onTransactionUnderway
        )
    }

    AlertDialog(
        onDismissRequest = {
            transactionJob?.cancel()
            onDismissed()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    transactionJob?.cancel()
                    onDismissed()
                }
            ) {
                Text(text = stringResource(R.string.qr_presentment_cancel))
            }
        },
        title = {
            Text(text = stringResource(R.string.qr_presentment_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Text(
                    text = stringResource(R.string.qr_presentment_usage_info)
                )

                Column(
                    modifier = Modifier
                        .dropShadow(
                            shape = RoundedCornerShape(16.dp),
                            shadow = Shadow(
                                radius = 10.dp,
                                spread = 7.5.dp,
                                color = Color.Black.copy(alpha = 0.05f),
                                offset = DpOffset(x = 0.dp, 2.dp)
                            )
                        )
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    if (qrCodeToShow != null) {
                        // Added 'qrCode' as a key to remember() so it recomposes correctly if the code ever changes
                        val qrCodeBitmap = remember(qrCodeToShow) { generateQrCode(qrCodeToShow!!) }
                        Image(
                            modifier = Modifier.fillMaxWidth(),
                            bitmap = qrCodeBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    )
}

private fun proximityPresentment(
    document: Document?,
    scope: CoroutineScope,
    source: PresentmentSource,
    qrCodeReady: (qrCode: String) -> Unit,
    onTransactionUnderway: () -> Unit
) {
    var listenForCancellationFromUiJob: Job? = null
    var transactionJob: Job? = null
    transactionJob = (scope + PresentmentActivity.promptModel).launch {
        try {
            val eDeviceKeyCurve = EcCurve.P256
            val transportFactory = MdocTransportFactory.Default
            val transportOptions = MdocTransportOptions(
                bleUseL2CAP = false,
                bleUseL2CAPInEngagement = true
            )
            val connectionMethods = mutableListOf<MdocConnectionMethod>()
            val bleUuid = UUID.randomUUID()
            connectionMethods.add(
                MdocConnectionMethodBle(
                    supportsPeripheralServerMode = true,
                    supportsCentralClientMode = false,
                    peripheralServerModeUuid = bleUuid,
                    centralClientModeUuid = null,
                )
            )
            val eDeviceKey = Crypto.createEcPrivateKey(eDeviceKeyCurve)
            val advertisedTransports = connectionMethods.advertise(
                role = MdocRole.MDOC,
                transportFactory = transportFactory,
                options = transportOptions
            )
            val deviceEngagement = buildDeviceEngagement(eDeviceKey = eDeviceKey.publicKey) {
                advertisedTransports.forEach { addConnectionMethod(it.connectionMethod) }
            }.toDataItem()
            val encodedDeviceEngagement = ByteString(Cbor.encode(deviceEngagement))
            val qrCode = "mdoc:" + encodedDeviceEngagement.toByteArray().toBase64Url()
            qrCodeReady(qrCode)

            val transport = advertisedTransports.waitForConnection(
                eSenderKey = eDeviceKey.publicKey,
            )

            onTransactionUnderway()

            PresentmentActivity.presentmentModel.reset(
                source = source,
                preselectedDocuments = document?.let { listOf(it) } ?: emptyList()
            )
            val intent = Intent(applicationContext, PresentmentActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            applicationContext.startActivity(intent)

            listenForCancellationFromUiJob = CoroutineScope(Dispatchers.Main).launch {
                PresentmentActivity.presentmentModel.state.collect { state ->
                    if (state == PresentmentModel.State.CanceledByUser) {
                        transactionJob?.cancel()
                        listenForCancellationFromUiJob?.cancel()
                        listenForCancellationFromUiJob = null
                    }
                }
            }

            Iso18013Presentment(
                transport = transport,
                eDeviceKey = eDeviceKey,
                deviceEngagement = deviceEngagement,
                handover = Simple.NULL,
                source = source,
                keyAgreementPossible = listOf(eDeviceKeyCurve),
                onWaitingForRequest = { PresentmentActivity.presentmentModel.setWaitingForReader() },
                onWaitingForUserInput = { PresentmentActivity.presentmentModel.setWaitingForUserInput() },
                onDocumentsInFocus = { documents ->
                    PresentmentActivity.presentmentModel.setDocumentsSelected(selectedDocuments = documents)
                },
                onSendingResponse = { PresentmentActivity.presentmentModel.setSending() }
            )

            PresentmentActivity.presentmentModel.setCompleted(null)
            Logger.i(TAG, "Transaction complete")
        } catch (e: Exception) {
            Logger.w(TAG, "Transaction failed", e)
            if (e is CancellationException) {
                PresentmentActivity.presentmentModel.setCompleted(
                    PresentmentCanceledException(
                        applicationContext.getString(R.string.qr_presentment_cancelled_error)
                    )
                )
            } else {
                PresentmentActivity.presentmentModel.setCompleted(e)
            }
        } finally {
            transactionJob = null
            listenForCancellationFromUiJob?.cancel()
            listenForCancellationFromUiJob = null
        }
    }
}
