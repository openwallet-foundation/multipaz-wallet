package org.multipaz.wallet.client.verification

import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509CertChain
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

/**
 * Base class for documents that are returned for a query.
 *
 * @property trustResult A [TrustResult] indicating whether the issuer of the document is trusted or not.
 * @property documentType The type of document returned, if known.
 * @property issuingAuthority The issuing authority of the document, if known.
 * @property issuingCountryCode The issuing country code of the issuer, if known.
 * @property revocationStatus The revocation status of the document, if known.
 * @property certificateChain The certificate chain of the document signer, if known.
 */
sealed class DocumentQueryResult(
    open val trustResult: TrustResult,
    open val documentType: DocumentType?,
    open val issuingAuthority: String?,
    open val issuingCountryCode: String?,
    open val revocationStatus: RevocationStatus?,
    open val certificateChain: X509CertChain? = null
)

/**
 * The portrait image for the document, if available.
 */
val DocumentQueryResult.portrait: ByteString?
    get() = when (this) {
        is AgeOverDocumentQueryResult -> portrait
        is IdentificationDocumentQueryResult -> portrait
        is DrivingPrivilegesDocumentQueryResult -> portrait
        is UserDefinedDocumentQueryResult -> extractPortraitImage()
    }

/**
 * Checks whether the given byte array represents a JPEG 2000 image.
 *
 * Checks for both JP2 file format header (`0x0000000C6A5020200D0A870A`) and J2K codestream header (`0xFF4FFF51`).
 *
 * @param bytes The raw bytes to inspect.
 * @return `true` if the bytes match a JPEG 2000 signature; `false` otherwise.
 */
fun isJpeg2000(bytes: ByteArray): Boolean {
    val isJpeg2000Jp2 = bytes.size >= 12 &&
            (bytes[0].toInt() and 0xFF == 0x00) &&
            (bytes[1].toInt() and 0xFF == 0x00) &&
            (bytes[2].toInt() and 0xFF == 0x00) &&
            (bytes[3].toInt() and 0xFF == 0x0C) &&
            (bytes[4].toInt() and 0xFF == 0x6A) &&
            (bytes[5].toInt() and 0xFF == 0x50) &&
            (bytes[6].toInt() and 0xFF == 0x20) &&
            (bytes[7].toInt() and 0xFF == 0x20) &&
            (bytes[8].toInt() and 0xFF == 0x0D) &&
            (bytes[9].toInt() and 0xFF == 0x0A) &&
            (bytes[10].toInt() and 0xFF == 0x87) &&
            (bytes[11].toInt() and 0xFF == 0x0A)
    val isJpeg2000J2k = bytes.size >= 4 &&
            (bytes[0].toInt() and 0xFF == 0xFF) &&
            (bytes[1].toInt() and 0xFF == 0x4F) &&
            (bytes[2].toInt() and 0xFF == 0xFF) &&
            (bytes[3].toInt() and 0xFF == 0x51)
    return isJpeg2000Jp2 || isJpeg2000J2k
}

/**
 * Checks whether the given byte array represents a supported image format (JPEG, PNG, or JPEG 2000).
 *
 * @param bytes The raw bytes to inspect.
 * @return `true` if the bytes match a JPEG, PNG, or JPEG 2000 header and meet the minimum size requirement; `false` otherwise.
 */
fun isJpegOrPng(bytes: ByteArray): Boolean {
    if (bytes.size < 500) return false
    val isJpeg = bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF == 0xFF) &&
            (bytes[1].toInt() and 0xFF == 0xD8) &&
            (bytes[2].toInt() and 0xFF == 0xFF)
    val isPng = bytes.size >= 8 &&
            (bytes[0].toInt() and 0xFF == 0x89) &&
            (bytes[1].toInt() and 0xFF == 0x50) &&
            (bytes[2].toInt() and 0xFF == 0x4E) &&
            (bytes[3].toInt() and 0xFF == 0x47) &&
            (bytes[4].toInt() and 0xFF == 0x0D) &&
            (bytes[5].toInt() and 0xFF == 0x0A) &&
            (bytes[6].toInt() and 0xFF == 0x1A) &&
            (bytes[7].toInt() and 0xFF == 0x0A)
    return isJpeg || isPng || isJpeg2000(bytes)
}

private fun UserDefinedDocumentQueryResult.extractPortraitImage(): ByteString? {
    for ((_, elements) in elements) {
        for ((dataElement, value) in elements) {
            if (dataElement.equals("portrait", ignoreCase = true)) {
                try {
                    val bytes = value.asBstr
                    if (isJpegOrPng(bytes)) {
                        return ByteString(bytes)
                    }
                } catch (_: Exception) {}
            }
        }
    }
    return null
}

