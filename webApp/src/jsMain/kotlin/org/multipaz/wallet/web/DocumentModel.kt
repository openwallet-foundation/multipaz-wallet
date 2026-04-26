package org.multipaz.wallet.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborMap
import org.multipaz.credential.Credential
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.document.Document
import org.multipaz.document.DocumentAdded
import org.multipaz.document.DocumentDeleted
import org.multipaz.document.DocumentEvent
import org.multipaz.document.DocumentStore
import org.multipaz.document.DocumentUpdated
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.claim.Claim
import org.multipaz.securearea.KeyInfo
import org.multipaz.util.Logger

private const val TAG = "DocumentModel"

/**
 * Information about a [Document].
 */
data class DocumentInfo(
    val document: Document,
    val credentialInfos: List<CredentialInfo>
)

/**
 * Information about a [Credential].
 */
data class CredentialInfo(
    val credential: Credential,
    val claims: List<Claim>,
    val keyInfo: KeyInfo?,
    val keyInvalidated: Boolean
)

private data class DocumentModelStorageData(
    var sortingOrder: Map<String, Int> = emptyMap()
) {
    fun toDataItem(): DataItem {
        return buildCborMap {
            putCborMap("documentOrder") {
                for((key, value) in sortingOrder) {
                    put(key, value)
                }
            }
        }
    }

    companion object {
        fun fromDataItem(dataItem: DataItem): DocumentModelStorageData {
            var sortingOrder = emptyMap<String, Int>()
            try {
                if (dataItem.hasKey("documentOrder")) {
                    sortingOrder = dataItem["documentOrder"].asMap.map { (key, value) ->
                        key.asTstr to value.asNumber.toInt()
                    }.toMap()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e(TAG, "Error decoding sortingOrder", e)
            }
            return DocumentModelStorageData(sortingOrder)
        }
    }
}

/**
 * Model that loads documents from a [DocumentStore] and keeps them updated.
 *
 * This is a React-friendly version of the Android DocumentModel.
 */
class DocumentModel private constructor(
    private val documentStore: DocumentStore,
    private val documentTypeRepository: DocumentTypeRepository?,
    private val documentOrderKey: String = "org.multipaz.DocumentModel.orderingKey",
) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    private val _documentInfos = MutableStateFlow<List<DocumentInfo>>(emptyList())
    private lateinit var storageData: DocumentModelStorageData

    /**
     * A list of [DocumentInfo] for the documents in [documentStore].
     */
    val documentInfos: StateFlow<List<DocumentInfo>> = _documentInfos.asStateFlow()

    private suspend fun initialize() {
        storageData = documentStore.getTags().get<ByteString>(documentOrderKey)?.let {
            DocumentModelStorageData.fromDataItem(Cbor.decode(it.toByteArray()))
        } ?: DocumentModelStorageData()

        val docIds = documentStore.listDocumentIds()
        val initialDocumentInfos = docIds.mapNotNull { id ->
            documentStore.lookupDocument(id)?.toDocumentInfo()
        }.toMutableList().sorted()
        _documentInfos.value = initialDocumentInfos

        documentStore.eventFlow
            .onEach { event -> updateDocumentInfo(event = event) }
            .launchIn(scope)
    }

    private suspend fun updateDocumentInfo(
        documentId: String? = null,
        event: DocumentEvent? = null,
    ) {
        val id = event?.documentId ?: documentId ?: return
        when (event) {
            is DocumentAdded -> {
                documentStore.lookupDocument(id)?.let { document ->
                    _documentInfos.update { current ->
                        current
                            .toMutableList()
                            .apply {
                                add(document.toDocumentInfo())
                            }.sorted()
                    }
                }
            }
            is DocumentDeleted -> {
                _documentInfos.update { current ->
                    current
                        .toMutableList()
                        .apply {
                            find { documentInfo -> documentInfo.document.identifier == id }?.let {
                                remove(it)
                            }
                        }.sorted()
                }
            }
            is DocumentUpdated -> {
                documentStore.lookupDocument(id)?.let { document ->
                    _documentInfos.update { current ->
                        current
                            .toMutableList()
                            .apply {
                                val existingDocumentInfo =
                                    find { documentInfo -> documentInfo.document.identifier == id }
                                if (existingDocumentInfo != null) {
                                    try {
                                        val newDocumentInfo = document.toDocumentInfo()
                                        if (newDocumentInfo != existingDocumentInfo) {
                                            remove(existingDocumentInfo)
                                            add(newDocumentInfo)
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        Logger.w(TAG, "Error generating DocumentInfo", e)
                                    }
                                }
                            }.sorted()
                    }
                }
            }
            null -> {}
        }
    }

    private fun List<DocumentInfo>.sorted(): List<DocumentInfo> {
        return this.sortedWith { a, b ->
            val sa = storageData.sortingOrder[a.document.identifier]
            val sb = storageData.sortingOrder[b.document.identifier]
            if (sa != null && sb != null) {
                if (sa != sb) {
                    return@sortedWith sa.compareTo(sb)
                }
            }
            return@sortedWith a.document.created.compareTo(b.document.created)
        }
    }

    /**
     * Sets the position of a document.
     */
    suspend fun setDocumentPosition(
        documentInfo: DocumentInfo,
        position: Int
    ) {
        val documentInfos = _documentInfos.value.toMutableList()
        if (!documentInfos.remove(documentInfo)) {
            throw IllegalArgumentException("Passed in documentInfo is not in list")
        }
        documentInfos.add(
            index = position,
            element = documentInfo,
        )
        val sortingOrder = mutableMapOf<String, Int>()
        documentInfos.forEachIndexed { index, info ->
            sortingOrder.put(info.document.identifier, index)
        }
        storageData.sortingOrder = sortingOrder
        documentStore.getTags().edit {
            set(documentOrderKey, ByteString(Cbor.encode(storageData.toDataItem())))
        }
        _documentInfos.value = documentInfos
    }

    private suspend fun Document.toDocumentInfo(): DocumentInfo {
        return DocumentInfo(
            document = this,
            credentialInfos = buildCredentialInfos(documentTypeRepository)
        )
    }

    companion object {
        /**
         * Creates a [DocumentModel] instance.
         */
        suspend fun create(
            documentStore: DocumentStore,
            documentTypeRepository: DocumentTypeRepository?,
            documentOrderKey: String = "org.multipaz.DocumentModel.orderingKey",
        ): DocumentModel {
            val documentModel = DocumentModel(
                documentStore,
                documentTypeRepository,
                documentOrderKey
            )
            documentModel.initialize()
            return documentModel
        }

        private suspend fun Document.buildCredentialInfos(
            documentTypeRepository: DocumentTypeRepository?
        ): List<CredentialInfo> {
            return getCredentials().map { credential ->
                val keyInfo = if (credential is SecureAreaBoundCredential) {
                    credential.secureArea.getKeyInfo(credential.alias)
                } else {
                    null
                }
                val keyInvalidated = if (credential is SecureAreaBoundCredential) {
                    credential.secureArea.getKeyInvalidated(credential.alias)
                } else {
                    false
                }
                val claims = if (credential.isCertified) {
                    credential.getClaims(documentTypeRepository)
                } else {
                    emptyList()
                }
                CredentialInfo(
                    credential = credential,
                    claims = claims,
                    keyInfo = keyInfo,
                    keyInvalidated = keyInvalidated
                )
            }
        }
    }
}
