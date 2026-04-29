package org.multipaz.wallet.client

import kotlinx.io.bytestring.ByteString
import org.multipaz.document.Document

private const val PROVISIONED_DOCUMENT_IDENTIFIER_TAG_KEY = "org.multipaz.wallet.provisionedDocumentIdentifier"
private const val PROVISIONED_DOCUMENT_SETUP_NEEDED_TAG_KEY = "org.multipaz.wallet.provisionedDocumentSetupNeeded"
private const val PRECONSENT_SETTING_TAG_KEY = "org.multipaz.wallet.preconsentSetting"

/**
 * Returns `true` if the document is synced to the backend and available on other devices.
 */
val Document.isSyncing: Boolean
    get() = provisionedDocumentIdentifier != null || mpzPassId != null

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
 * @return the pre-consent setting, or `null` if none is configured.
 */
val Document.preconsentSetting: DocumentPreconsentSetting?
    get() = tags.getByteString(PRECONSENT_SETTING_TAG_KEY)?.let {
        DocumentPreconsentSetting.fromCbor(it.toByteArray())
    }

/**
 * Sets or clears the [DocumentPreconsentSetting] for this [Document].
 *
 * This configures the rules under which credentials belonging to this document
 * can be presented without requiring explicit user consent.
 *
 * @receiver the [Document] to configure.
 * @param value the new [DocumentPreconsentSetting] to apply, or `null` to remove
 *              the existing setting.
 */
suspend fun Document.setPreconsentSetting(value: DocumentPreconsentSetting?) {
    edit {
        value?.let {
            tags.setByteString(PRECONSENT_SETTING_TAG_KEY, ByteString(value.toCbor()))
        } ?: tags.remove(PRECONSENT_SETTING_TAG_KEY)
    }
}
