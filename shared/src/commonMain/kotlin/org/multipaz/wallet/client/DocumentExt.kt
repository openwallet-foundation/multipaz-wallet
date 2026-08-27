package org.multipaz.wallet.client

import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.Tagged
import org.multipaz.cose.CoseSign1
import org.multipaz.document.Document
import org.multipaz.mpzpass.MpzPass
import org.multipaz.util.Logger
import org.multipaz.util.inflate

private const val TAG = "DocumentExt"

private const val PROVISIONED_DOCUMENT_IDENTIFIER_TAG_KEY = "org.multipaz.wallet.provisionedDocumentIdentifier"
private const val PROVISIONED_DOCUMENT_SETUP_NEEDED_TAG_KEY = "org.multipaz.wallet.provisionedDocumentSetupNeeded"
private const val PRECONSENT_SETTING_TAG_KEY = "org.multipaz.wallet.preconsentSetting"

/**
 * Returns `true` if the document is synced to the backend and available on other devices.
 */
val Document.isSyncing: Boolean
    get() = provisionedDocumentIdentifier != null

/**
 * The identifier of the provisioned document, if any.
 *
 * @receiver a [Document].
 * @return the identifier of the provisioned document, if any.
 */
val Document.provisionedDocumentIdentifier: String?
    get() = tags.getString(PROVISIONED_DOCUMENT_IDENTIFIER_TAG_KEY)

/**
 * Setter for [Document.provisionedDocumentIdentifier].
 *
 * @receiver a [Document].
 * @param identifier the identifier of the provisioned document.
 */
suspend fun Document.setProvisionedDocumentIdentifier(identifier: String) {
    edit {
        tags.setString(PROVISIONED_DOCUMENT_IDENTIFIER_TAG_KEY, identifier)
    }
}

/**
 * If true, it means the document is a placeholder document for a provisioned document.
 *
 * @receiver a [Document].
 * @return whether the document is a placeholder document for a provisioned document.
 */
val Document.provisionedDocumentSetupNeeded: Boolean
    get() = tags.getBoolean(PROVISIONED_DOCUMENT_SETUP_NEEDED_TAG_KEY) ?: false

/**
 * Setter for [Document.provisionedDocumentSetupNeeded].
 *
 * @receiver a [Document].
 * @param value whether the document is a placeholder document for a provisioned document.
 */
suspend fun Document.setProvisionedDocumentSetupNeeded(value: Boolean) {
    edit {
        tags.setBoolean(PROVISIONED_DOCUMENT_SETUP_NEEDED_TAG_KEY, value)
    }
}


/**
 * Gets the configured [DocumentPreconsentSetting] for this [Document].
 *
 * This setting dictates the rules under which credentials belonging to this document
 * can be presented without requiring explicit user consent.
 *
 * @receiver the [Document] to check.
 * @return the pre-consent setting, defaulting to [DocumentPreconsentSetting.NeverRequireConsent] if none is configured.
 */
val Document.preconsentSetting: DocumentPreconsentSetting
    get() = tags.getByteString(PRECONSENT_SETTING_TAG_KEY)?.let {
        DocumentPreconsentSetting.fromCbor(it.toByteArray())
    } ?: DocumentPreconsentSetting.NeverRequireConsent

/**
 * Sets the [DocumentPreconsentSetting] for this [Document].
 *
 * This configures the rules under which credentials belonging to this document
 * can be presented without requiring explicit user consent.
 *
 * @receiver the [Document] to configure.
 * @param value the new [DocumentPreconsentSetting] to apply.
 */
suspend fun Document.setPreconsentSetting(value: DocumentPreconsentSetting) {
    edit {
        tags.setByteString(PRECONSENT_SETTING_TAG_KEY, ByteString(value.toCbor()))
    }
}

private const val MPZ_PASS_DATA_TAG_KEY = "org.multipaz.wallet.mpzPassData"
private const val MPZ_PASS_SHAREABLE_TAG_KEY = "org.multipaz.wallet.mpzPassShareable"

/**
 * The raw `.mpzpass` CBOR data for this document, if it was imported from an [org.multipaz.mpzpass.MpzPass].
 *
 * @receiver a [Document].
 * @return the raw `.mpzpass` CBOR bytes, or `null` if none is configured.
 */
val Document.mpzPassData: ByteString?
    get() = tags.getByteString(MPZ_PASS_DATA_TAG_KEY)

/**
 * Sets the raw `.mpzpass` CBOR data for this document, automatically extracting and storing the shareable flag.
 *
 * @receiver a [Document].
 * @param data the raw `.mpzpass` CBOR bytes to associate with this document.
 */
suspend fun Document.setMpzPassData(data: ByteString) {
    val isShareable = try {
        val dataItem = Cbor.decode(data.toByteArray())
        if (dataItem is CborArray && dataItem.items.size >= 2 && dataItem[0].asTstr == "MpzPass") {
            val secondElement = dataItem[1]
            val compressedBytes = when {
                secondElement is Bstr -> secondElement.asBstr
                secondElement is Tagged && secondElement.tagNumber == Tagged.COSE_SIGN1 -> {
                    val cose = CoseSign1.fromDataItem(secondElement.taggedItem)
                    cose.payload
                }
                else -> null
            }
            if (compressedBytes != null) {
                val credentialDataBytes = compressedBytes.inflate()
                val credentialData = Cbor.decode(credentialDataBytes)
                credentialData.getOrNull("shareable")?.asBoolean ?: false
            } else {
                false
            }
        } else {
            false
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.e(TAG, "Failed to parse MpzPass shareable flag", e)
        false
    }
    edit {
        tags.setByteString(MPZ_PASS_DATA_TAG_KEY, data)
        tags.setBoolean(MPZ_PASS_SHAREABLE_TAG_KEY, isShareable)
    }
}

/**
 * Returns whether this document is an [org.multipaz.mpzpass.MpzPass] and is configured as shareable.
 *
 * @receiver a [Document].
 * @return `true` if the document is an [MpzPass] with [MpzPass.shareable] set to `true`, `false` otherwise.
 */
val Document.isMpzPassShareable: Boolean
    get() = mpzPassId != null && (tags.getBoolean(MPZ_PASS_SHAREABLE_TAG_KEY) ?: false)

/**
 * Decodes and returns the [MpzPass] for this document, if it was imported from an [MpzPass]
 * and the raw data is available in [mpzPassData].
 *
 * @receiver a [Document].
 * @param disableSignatureVerification whether to skip cryptographic signature verification if the pass is signed.
 * @return the decoded [MpzPass], or `null` if the document is not an [MpzPass] or decoding fails.
 */
suspend fun Document.getMpzPass(disableSignatureVerification: Boolean = false): MpzPass? {
    val data = mpzPassData ?: return null
    return try {
        MpzPass.fromDataItem(
            dataItem = Cbor.decode(data.toByteArray()),
            disableSignatureVerification = disableSignatureVerification
        )
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.e(TAG, "Failed to decode MpzPass from mpzPassData", e)
        null
    }
}


