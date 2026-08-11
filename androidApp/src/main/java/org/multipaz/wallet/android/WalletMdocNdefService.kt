package org.multipaz.wallet.android

import android.content.Context
import org.multipaz.compose.mdoc.MdocNdefService
import org.multipaz.compose.prompt.PresentmentActivity
import org.multipaz.crypto.EcCurve
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.util.Logger
import kotlin.time.Clock

private const val TAG = "WalletMdocNdefService"

class WalletMdocNdefService(
    applicationContext: Context,
    sendResponse: (ByteArray) -> Unit
): MdocNdefService(applicationContext, sendResponse) {

    override suspend fun getSettings(): Settings {
        // TODO: optimize initialization of App so we can just get settingsModel and presentmentSource() out
        val t0 = Clock.System.now()
        val source = App.getPresentmentSource()
        val t1 = Clock.System.now()
        Logger.i(TAG, "App initialized in ${(t1 - t0).inWholeMilliseconds} ms")

        val focusedDocumentId = App.getInstance().focusedDocumentId
        val focusedDocument = focusedDocumentId?.let { source.documentStore.lookupDocument(it) }
        Logger.i(TAG, "Focused document at tap-time: $focusedDocumentId: ${focusedDocument?.displayName}")

        if (!PresentmentActivity.presentmentModel.isActive) {
            PresentmentActivity.presentmentModel.reset(
                source = source,
                preselectedDocuments = focusedDocument?.let { listOf(it) } ?: emptyList()
            )
        }

        return Settings(
            source = source,
            promptModel = PresentmentActivity.promptModel,
            presentmentModel = PresentmentActivity.presentmentModel,
            activityClass = PresentmentActivity::class.java,
            sessionEncryptionCurve = EcCurve.P256,
            useNegotiatedHandover = true,
            negotiatedHandoverPreferredOrder = listOf(
                "ble:central_client_mode:",
                "ble:peripheral_server_mode:",
                "nfc:"
            ),
            staticHandoverBleCentralClientModeEnabled = true,
            staticHandoverBlePeripheralServerModeEnabled = true,
            staticHandoverNfcDataTransferEnabled = false,
            transportOptions = MdocTransportOptions(
                bleUseL2CAP = false,
                bleUseL2CAPInEngagement = true
            ),
        )
    }
}