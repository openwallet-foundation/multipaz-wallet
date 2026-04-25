package org.multipaz.wallet.web

import kotlinx.io.bytestring.ByteString
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import org.multipaz.util.Logger
import react.useEffect
import react.useState
import web.blob.Blob
import web.blob.BlobPropertyBag
import web.url.URL

private const val TAG = "Hooks"

fun useImageUri(byteString: ByteString?): String? {
    val (uri, setUri) = useState<String?>(null)
    useEffect(byteString) {
        if (byteString == null) {
            setUri(null)
            return@useEffect
        }
        try {
            val bytes = byteString.toByteArray()
            val uint8Array = Uint8Array(bytes.size)
            for (i in bytes.indices) {
                uint8Array[i] = bytes[i]
            }
            val blob = Blob(arrayOf(uint8Array), BlobPropertyBag(type = "image/png"))
            val url = URL.createObjectURL(blob)
            setUri(url)
            Cleanup {
                URL.revokeObjectURL(url)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error creating object URL", e)
            setUri(null)
        }
    }
    return uri
}

fun Cleanup(block: () -> Unit): react.Cleanup = block.unsafeCast<react.Cleanup>()
