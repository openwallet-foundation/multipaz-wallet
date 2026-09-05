package org.multipaz.wallet.client.verification

/**
 * A [Query] that requests identification information (name, birth date, portrait, and optionally street address)
 * from an identity credential.
 *
 * @property requestStreetAddress Whether to request the holder's resident address.
 * @property documentQueries The list of [DocumentQuery] requirements, defaulting to an [IdentificationDocumentQuery].
 */
data class IdentificationQuery(
    val requestStreetAddress: Boolean,
    override val documentQueries: List<DocumentQuery> = listOf(IdentificationDocumentQuery(requestStreetAddress)),
): Query(documentQueries = documentQueries)