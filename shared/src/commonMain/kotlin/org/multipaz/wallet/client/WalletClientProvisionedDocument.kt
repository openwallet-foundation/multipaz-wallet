package org.multipaz.wallet.client

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable

/**
 * A provisioned document.
 *
 * This data structure is used to represent a document that has been provisioned on a client
 * and is designed to contain enough information so other clients can also provision the
 * document from the same issuer.
 *
 * The [identifier] property is intended to be used with [Document.provisionedDocumentIdentifier]
 *
 * - When a client provisions a document for the first time, it creates a [WalletClientProvisionedDocument]
 *   in [WalletClientSharedData.provisionedDocuments] and generates a new identifier. It then sets this
 *   identifier on its local [Document] instance using [Document.setProvisionedDocumentIdentifier].
 *
 * - When another client checks in, it goes through all entries in [WalletClientSharedData.provisionedDocuments]
 *   and checks if it already has a document with the same identifier set on [Document.provisionedDocumentIdentifier].
 *   If not, it creates a [Document], sets the identifier on it using [Document.setProvisionedDocumentIdentifier],
 *   and then asks the user to go through provisioning.
 *
 * @property identifier a unique identifier for the provisioned document.
 * @property cardArt card art for the document, if available.
 * @property displayName the display name of the document, if available.
 * @property typeDisplayName the type display name of the document, if available.
 */
@CborSerializable
sealed class WalletClientProvisionedDocument(
    open val identifier: String,
    open val cardArt: ByteString?,
    open val displayName: String?,
    open val typeDisplayName: String?
) {
    companion object
}
