package org.multipaz.wallet.web

import kotlinx.io.bytestring.ByteString
import org.khronos.webgl.Uint8Array
import org.multipaz.util.Logger
import react.Cleanup
import react.useEffect
import react.useMemo
import react.useRef
import react.useState
import web.blob.Blob
import web.blob.BlobPropertyBag
import web.file.FileReader
import web.events.EventHandler

private const val TAG = "Hooks"

/**
 * Creates a temporary data URL for a [ByteString] containing image data.
 */
fun useImageUri(byteString: ByteString?): String? {
    val (uri, setUri) = useState<String?>(null)
    val lastByteString = useRef<ByteString>()
    
    useEffect(byteString) {
        // Only proceed if the ByteString has actually changed (content-wise)
        if (byteString == lastByteString.current) {
            return@useEffect
        }
        lastByteString.current = byteString

        if (byteString == null) {
            setUri(null)
            return@useEffect
        }
        
        try {
            val bytes = byteString.toByteArray()
            val blob = Blob(arrayOf(bytes), BlobPropertyBag(type = ""))
            val reader = FileReader()
            reader.onload = EventHandler { _ ->
                val result = reader.result as String
                setUri(result)
            }
            reader.readAsDataURL(blob)
        } catch (e: Exception) {
            Logger.e(TAG, "Error creating data URL", e)
            setUri(null)
        }
    }
    
    return uri
}

fun Cleanup(block: () -> Unit): Cleanup? = block.unsafeCast<Cleanup?>()
