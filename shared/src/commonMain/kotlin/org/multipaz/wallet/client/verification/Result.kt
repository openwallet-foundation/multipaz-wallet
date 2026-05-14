package org.multipaz.wallet.client.verification

data class Result(
    val query: Query,
    val documents: List<DocumentQueryResult>
)