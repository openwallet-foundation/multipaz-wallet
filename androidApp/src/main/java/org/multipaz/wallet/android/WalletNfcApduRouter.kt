package org.multipaz.wallet.android

import kotlinx.io.bytestring.ByteString
import org.multipaz.mdoc.nfc.MdocNfcDataTransferService
import org.multipaz.nfc.Nfc
import org.multipaz.nfc.NfcApduRouter
import org.multipaz.nfc.NfcApduService

class WalletNfcApduRouter : NfcApduRouter() {
    override fun buildServices(): Map<ByteString, NfcApduService> {
        return mapOf(
            Nfc.NDEF_APPLICATION_ID to WalletMdocNdefService(),
            Nfc.ISO_MDOC_NFC_CCE_APPLICATION_ID to WalletMdocNfcCceService(),
            Nfc.ISO_MDOC_NFC_DATA_TRANSFER_APPLICATION_ID to MdocNfcDataTransferService()
        )
    }
}
