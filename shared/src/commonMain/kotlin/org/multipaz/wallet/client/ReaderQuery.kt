package org.multipaz.wallet.client

import org.multipaz.cbor.DataItem
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.mdoc.request.DeviceRequest.Builder
import org.multipaz.mdoc.request.DocRequestInfo

private const val TAG = "ReaderQuery"

sealed class ReaderQuery(
    val id: String,
    val displayName: String,
) {

    abstract suspend fun generateDeviceRequest(
        sessionTranscript: DataItem,
        readerAuthKey: AsymmetricKey.X509Compatible?,
        intentToRetain: Boolean
    ): DeviceRequest
}

// TODO: remove once in Multipaz proper
internal suspend fun DeviceRequest.Builder.addDocRequest(
    docType: String,
    nameSpaces: Map<String, Map<String, Boolean>>,
    docRequestInfo: DocRequestInfo? = null,
    readerKey: AsymmetricKey.X509Compatible? = null
): Builder {
    if (readerKey != null) {
        return addDocRequest(docType, nameSpaces, docRequestInfo, readerKey)
    }
    return addDocRequest(docType, nameSpaces, docRequestInfo)
}
