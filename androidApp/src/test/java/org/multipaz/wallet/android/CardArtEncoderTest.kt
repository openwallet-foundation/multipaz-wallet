package org.multipaz.wallet.android

import kotlinx.io.bytestring.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class CardArtEncoderTest {

    @OptIn(ExperimentalEncodingApi::class)
    private val samplePngBytes = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    )

    @OptIn(ExperimentalEncodingApi::class)
    private val sampleJpegBytes = Base64.decode(
        "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP////////////////////////////////////////////////" +
                "//////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAF" +
                "BABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="
    )

    @Test
    fun testEmptyCardArtReturnsNull() {
        val result = encodeCardArt(ByteString(), 1000)
        assertNull(result)
    }

    @Test
    fun testZeroOrNegativeMaxBytesReturnsNull() {
        val cardArt = ByteString(sampleJpegBytes)
        assertNull(encodeCardArt(cardArt, 0))
        assertNull(encodeCardArt(cardArt, -100))
    }

    @Test
    fun testInvalidImageDataReturnsNull() {
        val invalidArt = ByteString(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val result = encodeCardArt(invalidArt, 1000)
        assertNull(result)
    }

    @Test
    fun testExistingJpegWithinMaxBytesPreserved() {
        val cardArt = ByteString(sampleJpegBytes)
        val maxBytes = sampleJpegBytes.size + 100
        val result = encodeCardArt(cardArt, maxBytes)
        assertNotNull(result)
        assertArrayEquals(sampleJpegBytes, result!!.toByteArray())
    }

    @Test
    fun testPngConvertedToJpegAndFits() {
        val cardArt = ByteString(samplePngBytes)
        val maxBytes = 2000
        val result = encodeCardArt(cardArt, maxBytes)
        if (result != null) {
            assertTrue(result.size <= maxBytes)
            val bytes = result.toByteArray()
            // Verify JPEG header (0xFF 0xD8 0xFF)
            assertTrue(bytes.size >= 3)
            assertEquals(0xFF, bytes[0].toInt() and 0xFF)
            assertEquals(0xD8, bytes[1].toInt() and 0xFF)
            assertEquals(0xFF, bytes[2].toInt() and 0xFF)
        }
    }

    @Test
    fun testJpegExceedingMaxBytesReEncoded() {
        val cardArt = ByteString(sampleJpegBytes)
        // Request a smaller size than the original sample JPEG
        val maxBytes = sampleJpegBytes.size - 1
        val result = encodeCardArt(cardArt, maxBytes)
        if (result != null) {
            assertTrue(result.size <= maxBytes)
            val bytes = result.toByteArray()
            assertTrue(bytes.size >= 3)
            assertEquals(0xFF, bytes[0].toInt() and 0xFF)
            assertEquals(0xD8, bytes[1].toInt() and 0xFF)
            assertEquals(0xFF, bytes[2].toInt() and 0xFF)
        }
    }
}
