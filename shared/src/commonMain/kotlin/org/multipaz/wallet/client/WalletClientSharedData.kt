package org.multipaz.wallet.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
@CborSerializable(schemaHash = "baOfBCilzugVA7pWjYFlsTqC3mti3dfwpvQiuXSh0fI")
data class WalletClientSharedData(
    /**
     * A list of imported passes.
     */
    val encodedMpzPasses: List<ByteString>? = null,

    val provisionedDocuments: List<WalletClientProvisionedDocument>? = null,

    val documentOrder: List<String>? = null
) {
    /**
     * Gets all imported passes.
     *
     * @return a list of [MpzPass] instances.
     */
    suspend fun getMpzPasses(): List<MpzPass> {
        return encodedMpzPasses?.mapNotNull {
            try {
                MpzPass.fromDataItem(Cbor.decode(it.toByteArray()))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e(TAG, "Failed to decode MpzPass from CBOR", e)
                null
            }
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
        val newOrder = documentOrder?.let { order ->
            if (order.contains(pass.uniqueId)) order else order + pass.uniqueId
        }
        return copy(
            encodedMpzPasses = encodedMpzPasses.orEmpty() + ByteString(
                Cbor.encode(pass.toDataItem())
            ),
            documentOrder = newOrder
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
        val newOrder = documentOrder?.filter { it != pass.uniqueId }?.ifEmpty { null }
        return copy(
            encodedMpzPasses = encodedMpzPasses?.filter {
                try {
                    val p = MpzPass.fromDataItem(Cbor.decode(it.toByteArray()))
                    p.uniqueId != pass.uniqueId
                } catch (e: Exception) {
                    false
                }
            }?.ifEmpty { null },
            documentOrder = newOrder
        )
    }

    suspend fun addProvisionedDocument(provisionedDocument: WalletClientProvisionedDocument): WalletClientSharedData {
        val newOrder = documentOrder?.let { order ->
            if (order.contains(provisionedDocument.identifier)) order else order + provisionedDocument.identifier
        }
        return copy(
            provisionedDocuments = provisionedDocuments.orEmpty() + provisionedDocument,
            documentOrder = newOrder
        )
    }

    suspend fun removeProvisionedDocument(provisionedDocument: WalletClientProvisionedDocument): WalletClientSharedData {
        val newOrder = documentOrder?.filter { it != provisionedDocument.identifier }?.ifEmpty { null }
        return copy(
            provisionedDocuments = provisionedDocuments?.filter {
                it != provisionedDocument
            }?.ifEmpty { null },
            documentOrder = newOrder
        )
    }

    companion object
}

/**
 * Maps a list of shared document identifiers (such as [MpzPass.uniqueId] or
 * [WalletClientProvisionedDocument.identifier]) to local [Document.identifier]s.
 */
suspend fun DocumentStore.mapSharedDocumentOrderToLocal(
    sharedDocumentOrder: List<String>
): List<String> {
    val allDocs = listDocuments()
    return sharedDocumentOrder.map { syncId ->
        val doc = allDocs.find {
            it.mpzPassId == syncId || it.provisionedDocumentIdentifier == syncId || it.identifier == syncId
        }
        doc?.identifier ?: syncId
    }
}

/**
 * Maps a list of local [Document.identifier]s to shared document identifiers
 * (such as [MpzPass.uniqueId] or [WalletClientProvisionedDocument.identifier]).
 */
suspend fun DocumentStore.mapLocalDocumentOrderToShared(
    localDocumentOrder: List<String>
): List<String> {
    val allDocs = listDocuments()
    return localDocumentOrder.map { localId ->
        val doc = allDocs.find { it.identifier == localId }
        doc?.mpzPassId ?: doc?.provisionedDocumentIdentifier ?: localId
    }
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
 * @param walletClient optional [WalletClient] to auto-remove un-importable corrupt passes from cloud shared data.
 * @param initialPreconsentSetting optional initial preconsent setting for imported documents.
 * @param onDocumentOrderChanged optional callback invoked with the new document order mapped to local document IDs.
 */
@Throws(Exception::class)
suspend fun DocumentStore.syncWithSharedData(
    sharedData: WalletClientSharedData,
    mpzPassIsoMdocDomain: String,
    mpzPassSdJwtVcDomain: String,
    mpzPassKeylessSdJwtVcDomain: String,
    walletClient: WalletClient? = null,
    initialPreconsentSetting: DocumentPreconsentSetting? = null,
    onDocumentOrderChanged: ((documentOrder: List<String>) -> Unit)? = null,
) {
    syncMpzPasses(
        sharedData = sharedData,
        mpzPassIsoMdocDomain = mpzPassIsoMdocDomain,
        mpzPassSdJwtVcDomain = mpzPassSdJwtVcDomain,
        mpzPassKeylessSdJwtVcDomain = mpzPassKeylessSdJwtVcDomain,
        walletClient = walletClient,
        initialPreconsentSetting = initialPreconsentSetting,
    )
    syncProvisionedDocuments(
        sharedData = sharedData,
        walletClient = walletClient,
        initialPreconsentSetting = initialPreconsentSetting,
    )
    if (sharedData.documentOrder != null && onDocumentOrderChanged != null) {
        val mappedOrder = mapSharedDocumentOrderToLocal(sharedData.documentOrder)
        onDocumentOrderChanged(mappedOrder)
    }
}

private suspend fun DocumentStore.syncMpzPasses(
    sharedData: WalletClientSharedData,
    mpzPassIsoMdocDomain: String = "mdoc",
    mpzPassSdJwtVcDomain: String = "sdjwtvc",
    mpzPassKeylessSdJwtVcDomain: String = "sdjwtvc_keyless",
    walletClient: WalletClient? = null,
    initialPreconsentSetting: DocumentPreconsentSetting? = null,
) {
    val mpzPasses = sharedData.getMpzPasses()

    Logger.i(TAG, "syncMpzPasses: Running")
    val failedPasses = mutableListOf<MpzPass>()

    // First add / update passes
    mpzPasses.forEach { pass ->
        val documentForPass = listDocuments().find {
            it.mpzPassId == pass.uniqueId
        }
        try {
            if (documentForPass == null) {
                val document = importMpzPass(
                    mpzPass = pass,
                    isoMdocDomain = mpzPassIsoMdocDomain,
                    sdJwtVcDomain = mpzPassSdJwtVcDomain,
                    keylessSdJwtVcDomain = mpzPassKeylessSdJwtVcDomain
                )
                withContext(NonCancellable) {
                    document.setMpzPassData(ByteString(Cbor.encode(pass.toDataItem())))
                    if (initialPreconsentSetting != null) {
                        document.setPreconsentSetting(initialPreconsentSetting)
                    }
                }
                Logger.i(TAG, "syncMpzPasses: Imported pass ${pass.uniqueId} at version ${pass.version}")
            } else if (documentForPass.mpzPassVersion!! < pass.version) {
                val oldVersion = documentForPass.mpzPassVersion!!
                val existingPreconsent = documentForPass.preconsentSetting
                val document = importMpzPass(
                    mpzPass = pass,
                    isoMdocDomain = mpzPassIsoMdocDomain,
                    sdJwtVcDomain = mpzPassSdJwtVcDomain,
                    keylessSdJwtVcDomain = mpzPassKeylessSdJwtVcDomain
                )
                withContext(NonCancellable) {
                    document.setMpzPassData(ByteString(Cbor.encode(pass.toDataItem())))
                    document.setPreconsentSetting(existingPreconsent)
                }
                Logger.i(TAG, "syncMpzPasses: Updated pass ${pass.uniqueId} from version $oldVersion to ${pass.version}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "syncMpzPasses: Failed importing pass ${pass.uniqueId} at version ${pass.version}", e)
            failedPasses.add(pass)
        }
    }

    // Auto-remove failed passes from backend shared data if walletClient is provided
    if (failedPasses.isNotEmpty() && walletClient != null) {
        for (failedPass in failedPasses) {
            try {
                walletClient.refreshSharedData()
                walletClient.sharedData.value?.let { currentSharedData ->
                    if (currentSharedData.getMpzPasses().any { it.uniqueId == failedPass.uniqueId }) {
                        val updatedSharedData = currentSharedData.removeMpzPass(failedPass)
                        walletClient.setSharedData(updatedSharedData)
                        Logger.i(TAG, "syncMpzPasses: Auto-removed un-importable pass ${failedPass.uniqueId} from backend shared data")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.w(TAG, "syncMpzPasses: Failed to remove un-importable pass ${failedPass.uniqueId} from backend shared data", e)
            }
        }
    }

    // Then remove passes in DocumentStore which no longer exist in shared data (or failed import)
    for (document in listDocuments()) {
        if (document.mpzPassId == null) {
            continue
        }
        val pass = mpzPasses.find { it.uniqueId == document.mpzPassId }
        if (pass == null || failedPasses.any { it.uniqueId == document.mpzPassId }) {
            deleteDocument(document.identifier)
            Logger.i(TAG, "syncMpzPasses: Removed pass ${document.mpzPassId} at version ${document.mpzPassVersion}")
        }
    }
}

private suspend fun DocumentStore.syncProvisionedDocuments(
    sharedData: WalletClientSharedData,
    walletClient: WalletClient? = null,
    initialPreconsentSetting: DocumentPreconsentSetting? = null,
) {
    Logger.i(TAG, "syncProvisionedDocuments: Running")
    val failedProvisionedDocuments = mutableListOf<WalletClientProvisionedDocument>()

    // First add / update provisioned documents
    sharedData.provisionedDocuments?.forEach { provisionedDocument ->
        try {
            val documentForProvisionedDocument = listDocuments().find {
                it.provisionedDocumentIdentifier == provisionedDocument.identifier
            }
            if (documentForProvisionedDocument == null) {
                val document = createDocument(
                    displayName = provisionedDocument.displayName,
                    typeDisplayName = provisionedDocument.typeDisplayName,
                    cardArt = provisionedDocument.cardArt,
                )
                withContext(NonCancellable) {
                    document.setProvisionedDocumentIdentifier(provisionedDocument.identifier)
                    document.setProvisionedDocumentSetupNeeded(true)
                    if (initialPreconsentSetting != null) {
                        document.setPreconsentSetting(initialPreconsentSetting)
                    }
                }
                Logger.i(
                    TAG,
                    "syncProvisionedDocuments: Added placeholder document for provisioned document ${provisionedDocument.identifier}"
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "syncProvisionedDocuments: Failed adding document for ${provisionedDocument.identifier}", e)
            failedProvisionedDocuments.add(provisionedDocument)
        }
    }

    // Auto-remove failed provisioned documents from backend shared data if walletClient is provided
    if (failedProvisionedDocuments.isNotEmpty() && walletClient != null) {
        for (failedDoc in failedProvisionedDocuments) {
            try {
                walletClient.refreshSharedData()
                walletClient.sharedData.value?.let { currentSharedData ->
                    if (currentSharedData.provisionedDocuments?.any { it.identifier == failedDoc.identifier } == true) {
                        val updatedSharedData = currentSharedData.removeProvisionedDocument(failedDoc)
                        walletClient.setSharedData(updatedSharedData)
                        Logger.i(TAG, "syncProvisionedDocuments: Auto-removed un-creatable provisioned document ${failedDoc.identifier} from backend shared data")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.w(TAG, "syncProvisionedDocuments: Failed to remove provisioned document ${failedDoc.identifier} from backend shared data", e)
            }
        }
    }

    // Then remove provisioned documents in DocumentStore which no longer exist in shared data (or failed creation).
    for (document in listDocuments()) {
        if (document.provisionedDocumentIdentifier == null) {
            continue
        }

        val provisionedDocument = sharedData.provisionedDocuments?.find {
            it.identifier == document.provisionedDocumentIdentifier
        }
        if (provisionedDocument == null || failedProvisionedDocuments.any { it.identifier == document.provisionedDocumentIdentifier }) {
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

/**
 * Monitors [WalletClient.signedInUser] and deletes all documents in [this] [DocumentStore]
 * whenever [WalletClient.signedInUser] transitions from non-null to `null`.
 *
 * @receiver the [DocumentStore] to clean up.
 * @param walletClient the [WalletClient] whose sign-in state to observe.
 * @param coroutineScope the [CoroutineScope] in which to collect state changes.
 * @return a [Job] representing the observation task.
 */
fun DocumentStore.clearOnSignOut(
    walletClient: WalletClient,
    coroutineScope: CoroutineScope
): Job {
    return coroutineScope.launch {
        walletClient.signedInUser
            .scan(initial = null as WalletClientSignedInUser?) { previousUser, newUser ->
                if (previousUser != null && newUser == null) {
                    Logger.i(TAG, "User signed out, clearing all documents in DocumentStore")
                    try {
                        for (document in listDocuments()) {
                            deleteDocument(document.identifier)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.e(TAG, "Error clearing documents on sign-out", e)
                    }
                }
                newUser
            }
            .collect()
    }
}

