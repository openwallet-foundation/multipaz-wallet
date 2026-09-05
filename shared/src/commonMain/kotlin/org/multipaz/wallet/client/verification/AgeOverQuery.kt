package org.multipaz.wallet.client.verification

/**
 * A [Query] that requests proof that the credential holder is older than a specified age.
 *
 * @property ageOver The threshold age in years that the holder must exceed (e.g. 18 or 21).
 * @property documentQueries The list of [DocumentQuery] requirements, defaulting to an [AgeOverDocumentQuery] for [ageOver].
 */
data class AgeOverQuery(
    val ageOver: Int,
    override val documentQueries: List<DocumentQuery> = listOf(AgeOverDocumentQuery(ageOver)),
): Query(documentQueries = documentQueries)

