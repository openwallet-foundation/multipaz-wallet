package org.multipaz.wallet.client.verification

import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.RequestedClaim

data class SimpleMdocDocumentQuery(
    val docType: String,
    val claims: List<RequestedClaim>,
): DocumentQuery() {
    override fun getRequests(): List<Request> = listOf(IsoMdocRequest(
        docType = docType,
        namespaces = buildMap {
            val claimsByNamespace = mutableMapOf<String, MutableList<MdocRequestedClaim>>()
            claims.forEach { claim ->
                claim as MdocRequestedClaim
                claimsByNamespace.getOrPut(claim.namespaceName, { mutableListOf() }).add(claim)
            }
            for ((namespace, claims) in claimsByNamespace) {
                put(namespace, claims.map { claim ->
                    IsoMdocDataElementRequest(
                        dataElementName = claim.dataElementName,
                        alternativeDataElements = emptyList()
                    )
                })
            }
        },
        getResult = { verifiedPresentation, atTime, trustResult ->
            return@IsoMdocRequest SimpleMdocDocumentQueryResult(
                trustResult = trustResult,
                documentType = null,
                issuingAuthority = null,
                issuingCountryCode = null,
                revocationStatus = null,
                verifiedPresentation = verifiedPresentation
            )
        }
    ))
}