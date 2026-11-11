package org.multipaz.wallet.client.verification

/**
 * A [Query] that requests user-defined data elements from an ISO/IEC 18013-5 mdoc credential.
 *
 * @property docType The ISO mdoc document type identifier (e.g., `org.iso.18013.5.1.mDL`).
 * @property namespaces Map from namespace name to the list of requested data element names within that namespace.
 * @property name A name chosen by the user to tell saved queries apart, or `null` if none was given.
 * @property documentQueries The list of [DocumentQuery] requirements, defaulting to a [UserDefinedDocumentQuery].
 */
data class UserDefinedQuery(
    val docType: String,
    val namespaces: Map<String, List<String>>,
    // Nullable rather than defaulting to an empty string since the generated CBOR decoder does not apply
    // default values, and queries saved before this field existed must still decode.
    val name: String? = null,
    override val documentQueries: List<DocumentQuery> = listOf(
        UserDefinedDocumentQuery(docType = docType, namespaces = namespaces)
    ),
) : Query(documentQueries = documentQueries)
