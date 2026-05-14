package org.multipaz.wallet.android

import android.content.Context
import org.multipaz.compose.mdoc.MdocNfcDataTransferService

class WalletMdocNfcDataTransferService(
    applicationContext: Context,
    sendResponse: (ByteArray) -> Unit
): MdocNfcDataTransferService(applicationContext, sendResponse) {
}