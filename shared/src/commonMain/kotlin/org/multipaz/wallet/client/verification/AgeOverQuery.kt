package org.multipaz.wallet.client.verification

data class AgeOverQuery(
    val ageOver: Int,
    override val documentQueries: List<DocumentQuery> = listOf(AgeOverDocumentQuery(ageOver)),
): Query(documentQueries = documentQueries)

