package org.multipaz.wallet.client.verification

data class IdentificationQuery(
    val requestStreetAddress: Boolean,
    override val documentQueries: List<DocumentQuery> = listOf(IdentificationDocumentQuery(requestStreetAddress)),
): Query(documentQueries = documentQueries)