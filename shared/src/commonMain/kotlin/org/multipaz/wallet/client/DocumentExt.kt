package org.multipaz.wallet.client

import org.multipaz.document.Document

private const val PROVISIONED_DOCUMENT_IDENTIFIER_TAG_KEY = "org.multipaz.wallet.provisionedDocumentIdentifier"

private const val PROVISIONED_DOCUMENT_SETUP_NEEDED_TAG_KEY = "org.multipaz.wallet.provisionedDocumentSetupNeeded"

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
