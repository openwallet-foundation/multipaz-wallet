package org.multipaz.wallet.client.verification

data class UserDefinedQuery(
    val docType: String,
    val namespaces: Map<String, List<String>>,
    override val documentQueries: List<DocumentQuery> = listOf(
        UserDefinedDocumentQuery(docType = docType, namespaces = namespaces)
    ),
) : Query(documentQueries = documentQueries)
