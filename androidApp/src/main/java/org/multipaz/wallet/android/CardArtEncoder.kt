package org.multipaz.wallet.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.decodeImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Encodes card art image data into JPEG format such that the resulting output fits within [maxBytes].
 *
 * The input [cardArt] is expected to contain encoded image bytes in one of the supported formats:
 * JPEG, JPEG 2000 (JP2 / J2K), or PNG.
 *
 * If the input [cardArt] is already a JPEG image and its size is less than or equal to [maxBytes],
 * it is returned unmodified to preserve 100% of the original visual quality.
 *
 * Otherwise, the image is decoded into a bitmap and compressed into JPEG format using a binary
 * search on compression quality (`0..100`). If even the minimum compression quality exceeds
 * [maxBytes], the image resolution is iteratively downscaled until an encoding fitting within
 * [maxBytes] is achieved.
 *
 * @param cardArt the input card art image data encoded in JPEG, JPEG 2000, or PNG format.
 * @param maxBytes the maximum allowed size of the returned JPEG image in bytes.
 * @return a [ByteString] containing the JPEG-encoded image fitting within [maxBytes],
 *   or `null` if [cardArt] is empty, [maxBytes] is non-positive, the image cannot be decoded,
 *   or it is impossible to fit the image within [maxBytes].
 */
fun encodeCardArt(
    cardArt: ByteString,
    maxBytes: Int
): ByteString? {
    if (maxBytes <= 0 || cardArt.size == 0) {
        return null
    }

    val bytes = cardArt.toByteArray()

    // If it's already a JPEG and fits within maxBytes, return it as-is to preserve maximum quality.
    if (isJpeg(bytes) && bytes.size <= maxBytes) {
        return cardArt
    }

    // Decode the image bytes into a Bitmap
    val bitmap = decodeBitmap(bytes) ?: return null

    var currentBitmap = bitmap
    while (currentBitmap.width >= 1 && currentBitmap.height >= 1) {
        val jpegBytes = findBestQualityJpeg(currentBitmap, maxBytes)
        if (jpegBytes != null) {
            if (currentBitmap != bitmap && !currentBitmap.isRecycled) {
                currentBitmap.recycle()
            }
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            return ByteString(jpegBytes)
        }

        // Downscale the bitmap resolution
        val nextWidth = (currentBitmap.width * 0.8).toInt()
        val nextHeight = (currentBitmap.height * 0.8).toInt()
        if (nextWidth < 1 || nextHeight < 1) {
            break
        }

        val scaled = Bitmap.createScaledBitmap(currentBitmap, nextWidth, nextHeight, true)
        if (currentBitmap != bitmap && !currentBitmap.isRecycled) {
            currentBitmap.recycle()
        }
        currentBitmap = scaled
    }

    if (!bitmap.isRecycled) {
        bitmap.recycle()
    }
    if (currentBitmap != bitmap && !currentBitmap.isRecycled) {
        currentBitmap.recycle()
    }

    return null
}

private fun isJpeg(bytes: ByteArray): Boolean {
    return bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF == 0xFF) &&
            (bytes[1].toInt() and 0xFF == 0xD8) &&
            (bytes[2].toInt() and 0xFF == 0xFF)
}

private fun decodeBitmap(bytes: ByteArray): Bitmap? {
    var bitmap: Bitmap? = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Throwable) {
        null
    }

    if (bitmap == null) {
        try {
            val imageBitmap = decodeImage(bytes)
            bitmap = imageBitmap.asAndroidBitmap()
        } catch (_: Throwable) {
            // Ignore
        }
    }

    if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bytes.isNotEmpty()) {
        try {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } catch (_: Throwable) {
            // Ignore
        }
    }

    return bitmap
}

private fun findBestQualityJpeg(bitmap: Bitmap, maxBytes: Int): ByteArray? {
    // Check quality 0
    val minStream = ByteArrayOutputStream()
    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 0, minStream)) {
        return null
    }
    val minBytes = minStream.toByteArray()
    if (minBytes.size > maxBytes) {
        return null
    }

    // Check quality 100
    val maxStream = ByteArrayOutputStream()
    if (bitmap.compress(Bitmap.CompressFormat.JPEG, 100, maxStream)) {
        val maxBytesResult = maxStream.toByteArray()
        if (maxBytesResult.size <= maxBytes) {
            return maxBytesResult
        }
    }

    // Binary search for highest quality in range 0..100
    var low = 0
    var high = 100
    var bestResult: ByteArray? = minBytes

    while (low <= high) {
        val mid = (low + high) / 2
        val bos = ByteArrayOutputStream()
        if (bitmap.compress(Bitmap.CompressFormat.JPEG, mid, bos)) {
            val compressed = bos.toByteArray()
            if (compressed.size <= maxBytes) {
                bestResult = compressed
                low = mid + 1
            } else {
                high = mid - 1
            }
        } else {
            high = mid - 1
        }
    }

    return bestResult
}
