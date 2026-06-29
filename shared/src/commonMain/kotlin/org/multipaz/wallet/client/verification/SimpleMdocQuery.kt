package org.multipaz.wallet.client.verification

import org.multipaz.request.MdocRequestedClaim

data class SimpleMdocQuery(
    val name: String,
    val description: String,
    val docType: String,
    val claims: List<MdocRequestedClaim>,
    override val documentQueries: List<DocumentQuery> = listOf(SimpleMdocDocumentQuery(docType, claims)),
): Query(documentQueries)
