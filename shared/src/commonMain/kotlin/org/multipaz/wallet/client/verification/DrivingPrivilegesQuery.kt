package org.multipaz.wallet.client.verification

data class DrivingPrivilegesQuery(
    override val documentQueries: List<DocumentQuery> = listOf(DrivingPrivilegesDocumentQuery()),
): Query(documentQueries = documentQueries)
