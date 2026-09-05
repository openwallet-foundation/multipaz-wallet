package org.multipaz.wallet.client.verification

/**
 * A [Query] that requests user-defined data elements from an ISO/IEC 18013-5 mdoc credential.
 *
 * @property docType The ISO mdoc document type identifier (e.g., `org.iso.18013.5.1.mDL`).
 * @property namespaces Map from namespace name to the list of requested data element names within that namespace.
 * @property documentQueries The list of [DocumentQuery] requirements, defaulting to a [UserDefinedDocumentQuery].
 */
data class UserDefinedQuery(
    val docType: String,
    val namespaces: Map<String, List<String>>,
    override val documentQueries: List<DocumentQuery> = listOf(
        UserDefinedDocumentQuery(docType = docType, namespaces = namespaces)
    ),
) : Query(documentQueries = documentQueries)
