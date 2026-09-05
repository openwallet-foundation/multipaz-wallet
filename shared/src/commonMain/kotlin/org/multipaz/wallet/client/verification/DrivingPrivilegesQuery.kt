package org.multipaz.wallet.client.verification

/**
 * A [Query] that requests driving privileges and driver identity information from a mobile driving license.
 *
 * @property documentQueries The list of [DocumentQuery] requirements, defaulting to a [DrivingPrivilegesDocumentQuery].
 */
data class DrivingPrivilegesQuery(
    override val documentQueries: List<DocumentQuery> = listOf(DrivingPrivilegesDocumentQuery()),
): Query(documentQueries = documentQueries)
