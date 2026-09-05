package org.multipaz.wallet.client.verification

/**
 * Result of executing a [Query] against digital credential presentations.
 *
 * @property query The original [Query] that was executed.
 * @property documents The list of [DocumentQueryResult]s produced for the presented credentials.
 */
data class Result(
    val query: Query,
    val documents: List<DocumentQueryResult>
)