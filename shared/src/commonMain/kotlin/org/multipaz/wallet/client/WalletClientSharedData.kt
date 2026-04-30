package org.multipaz.wallet.client

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.mpzpass.MpzPass
import org.multipaz.util.Logger

private const val TAG = "WalletClientSharedData"

/**
 * Data shared between multiple wallet apps signed in with the same account.
 *
 * This class is immutable.
 *
 * Because newer versions may be processed by older clients, any additions to this
 * data structure MUST be backwards compatible.
 */
@CborSerializable(schemaHash = "6EiYZfFZfzIVgXna7Cc7sX1VG86sXP5krZha8A27k_c")
data class WalletClientSharedData(
    /**
     * A list of imported passes.
     */
    val encodedMpzPasses: List<ByteString>? = null,

    val provisionedDocuments: List<WalletClientProvisionedDocument>? = null
) {
    /**
     * Gets all imported passes.
     *
     * @return a list of [MpzPass] instances.
     */
    suspend fun getMpzPasses(): List<MpzPass> {
        return encodedMpzPasses?.map {
            MpzPass.fromDataItem(Cbor.decode(it.toByteArray()))
        } ?: emptyList()
    }

    /**
     * Adds a new imported pass
     *
     * If the pass already exists, it's added a second time.
     *
     * @param pass the pass to add.
     * @return a new [org.multipaz.wallet.client.WalletClientSharedData] with the added pass.
     */
    suspend fun addMpzPass(pass: MpzPass): WalletClientSharedData {
        return copy(
            encodedMpzPasses = encodedMpzPasses.orEmpty() + ByteString(
                Cbor.encode(pass.toDataItem())
            )
        )
    }

    /**
     * Removes an imported pass
     *
     * If the pass doesn't exist the same shared data is returned.
     *
     * @param pass the pass to remove.
     * @return a new [org.multipaz.wallet.client.WalletClientSharedData] with the removed pass.
     */
    suspend fun removeMpzPass(pass: MpzPass): WalletClientSharedData {
        return copy(
            encodedMpzPasses = encodedMpzPasses?.filter {
                val p = MpzPass.fromDataItem(Cbor.decode(it.toByteArray()))
                p.uniqueId != pass.uniqueId
            }?.ifEmpty { null }
        )
    }

    suspend fun addProvisionedDocument(provisionedDocument: WalletClientProvisionedDocument): WalletClientSharedData {
        return copy(
            provisionedDocuments = provisionedDocuments.orEmpty() + provisionedDocument
        )
    }

    suspend fun removeProvisionedDocument(provisionedDocument: WalletClientProvisionedDocument): WalletClientSharedData {
        return copy(
            provisionedDocuments = provisionedDocuments?.filter {
                it != provisionedDocument
            }?.ifEmpty { null }
        )
    }

    companion object
}

/**
 * Synchronizes a local [DocumentStore] so it's in sync with data in a [WalletClientSharedData].
 *
 * This adds/removes documents for which [sharedData] is the source of truth.
 *
 * Also remember to use [DocumentStore.deleteDocumentFromWalletBackend] when deleting documents
 * to ensure that they are also deleted from [sharedData].
 *
 * @receiver the [DocumentStore] to sync.
 * @param sharedData the [WalletClientSharedData] data to sync to.
 * @param mpzPassIsoMdocDomain The domain string to use when creating ISO mdoc credentials.
 * @param mpzPassSdJwtVcDomain The domain string to use when creating SD-JWT VC credentials.
 * @param mpzPassKeylessSdJwtVcDomain the domain string to use when creating keyless SD-JWT VC credentials.
 */
@Throws(Exception::class)
suspend fun DocumentStore.syncWithSharedData(
    sharedData: WalletClientSharedData,
    mpzPassIsoMdocDomain: String,
    mpzPassSdJwtVcDomain: String,
    mpzPassKeylessSdJwtVcDomain: String
) {
    syncMpzPasses(
        sharedData = sharedData,
        mpzPassIsoMdocDomain = mpzPassIsoMdocDomain,
        mpzPassSdJwtVcDomain = mpzPassSdJwtVcDomain,
        mpzPassKeylessSdJwtVcDomain = mpzPassKeylessSdJwtVcDomain,
    )
    syncProvisionedDocuments(
        sharedData = sharedData,
    )
}

private suspend fun DocumentStore.syncMpzPasses(
    sharedData: WalletClientSharedData,
    mpzPassIsoMdocDomain: String = "mdoc",
    mpzPassSdJwtVcDomain: String = "sdjwtvc",
    mpzPassKeylessSdJwtVcDomain: String = "sdjwtvc_keyless"
) {
    val mpzPasses = sharedData.getMpzPasses()

    Logger.i(TAG, "syncMpzPasses: Running")
    // First add / update passes
    mpzPasses.forEach { pass ->
        val documentForPass = listDocuments().find {
            it.mpzPassId == pass.uniqueId
        }
        if (documentForPass == null) {
            importMpzPass(
                mpzPass = pass,
                isoMdocDomain = mpzPassIsoMdocDomain,
                sdJwtVcDomain = mpzPassSdJwtVcDomain,
                keylessSdJwtVcDomain = mpzPassKeylessSdJwtVcDomain
            )
            Logger.i(TAG, "syncMpzPasses: Imported pass ${pass.uniqueId} at version ${pass.version}")
        } else if (documentForPass.mpzPassVersion!! < pass.version) {
            val oldVersion = documentForPass.mpzPassVersion!!
            importMpzPass(
                mpzPass = pass,
                isoMdocDomain = mpzPassIsoMdocDomain,
                sdJwtVcDomain = mpzPassSdJwtVcDomain,
                keylessSdJwtVcDomain = mpzPassKeylessSdJwtVcDomain
            )
            Logger.i(TAG, "syncMpzPasses: Updated pass ${pass.uniqueId} from version $oldVersion to ${pass.version}")
        }
    }

    // Then remove passes in DocumentStore which no longer exists in shared data
    for (document in listDocuments()) {
        if (document.mpzPassId == null) {
            continue
        }
        val pass = mpzPasses.find { it.uniqueId == document.mpzPassId }
        if (pass == null) {
            deleteDocument(document.identifier)
            Logger.i(TAG, "syncMpzPasses: Removed pass ${document.mpzPassId} at version ${document.mpzPassVersion}")
        }
    }
}

private suspend fun DocumentStore.syncProvisionedDocuments(
    sharedData: WalletClientSharedData,
) {
    Logger.i(TAG, "syncProvisionedDocuments: Running")

    // First add / update provisioned documents
    sharedData.provisionedDocuments?.forEach { provisionedDocument ->
        val documentForProvisionedDocument = listDocuments().find {
            it.provisionedDocumentIdentifier == provisionedDocument.identifier
        }
        if (documentForProvisionedDocument == null) {
            val document = createDocument(
                displayName = provisionedDocument.displayName,
                typeDisplayName = provisionedDocument.typeDisplayName,
                cardArt = provisionedDocument.cardArt,
            )
            document.setProvisionedDocumentIdentifier(provisionedDocument.identifier)
            document.setProvisionedDocumentSetupNeeded(true)
            Logger.i(
                TAG,
                "syncProvisionedDocuments: Added placeholder document for provisioned document ${provisionedDocument.identifier}"
            )
        }
    }

    // Then remove provisioned documents in DocumentStore which no longer exists in shared data.
    for (document in listDocuments()) {
        if (document.provisionedDocumentIdentifier == null) {
            continue
        }

        val provisionedDocument = sharedData.provisionedDocuments?.find {
            it.identifier == document.provisionedDocumentIdentifier
        }
        if (provisionedDocument == null) {
            deleteDocument(document.identifier)
            Logger.i(TAG, "syncProvisionedDocuments: Removed document for provisioned document " +
                    "${document.identifier} since it no longer exists in shared data")
        }
    }
}

/**
 * Deletes a document from the [DocumentStore] and the backend, if applicable.
 *
 * Specifically, for documents that sync (see [Document.isSyncing]), this also deletes
 * the document from the [WalletClientSharedData] structure, which is stored encrypted
 * in the backend. Effectively this means that other clients will automatically delete
 * their copy of the document too, when they catch up to the latest version.
 *
 * Note that this can go wrong if e.g. we don't have an Internet connection or the wallet backend
 * is down. This means that you need to be online to delete a pass or provisioned document.
 * We may want to relax this requirement in the future.
 *
 * @receiver the [DocumentStore] to delete the document from.
 * @param document the [Document] to delete.
 * @param walletClient the [WalletClient] to use to delete the document.
 */
@Throws(Exception::class)
suspend fun DocumentStore.deleteDocumentFromWalletBackend(
    document: Document,
    walletClient: WalletClient,
) {
    if (document.mpzPassId != null && walletClient.sharedData.value != null) {
        walletClient.refreshSharedData()
        walletClient.sharedData.value?.let { sharedData ->
            val pass = sharedData.getMpzPasses().find { it.uniqueId == document.mpzPassId }
            if (pass != null) {
                walletClient.setSharedData(
                    sharedData.removeMpzPass(pass)
                )
                Logger.i(TAG, "deleteDocument: Removed mpzPass document from shared data")
            }
        }
    }
    if (document.provisionedDocumentIdentifier != null && walletClient.sharedData.value != null) {
        walletClient.refreshSharedData()
        walletClient.sharedData.value?.let { sharedData ->
            val provisionedDocument = sharedData.provisionedDocuments?.find {
                it.identifier == document.provisionedDocumentIdentifier
            }
            if (provisionedDocument != null) {
                walletClient.setSharedData(
                    sharedData.removeProvisionedDocument(provisionedDocument)
                )
                Logger.i(TAG, "deleteDocument: Removed provisioned document from shared data")
            }
        }
    }
    deleteDocument(document.identifier)
    Logger.i(TAG, "deleteDocument: Removed local document")
}

