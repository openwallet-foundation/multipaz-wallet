package org.multipaz.wallet.client.verification

import org.multipaz.cbor.DataItem
import org.multipaz.verification.MdocVerifiedPresentation

data class UserDefinedDocumentQuery(
    val docType: String,
    val namespaces: Map<String, List<String>>,
) : DocumentQuery() {

    override fun getRequests(): List<Request> = listOf(
        IsoMdocRequest(
            docType = docType,
            namespaces = namespaces.mapValues { (_, elements) ->
                elements.map { IsoMdocDataElementRequest(dataElementName = it) }
            },
            getResult = { verifiedPresentation, _, trustResult ->
                verifiedPresentation as MdocVerifiedPresentation
                val elementsMap = mutableMapOf<String, MutableMap<String, DataItem>>()
                for (claim in verifiedPresentation.issuerSignedClaims) {
                    val ns = elementsMap.getOrPut(claim.namespaceName) { mutableMapOf() }
                    ns[claim.dataElementName] = claim.value
                }

                UserDefinedDocumentQueryResult(
                    trustResult = trustResult,
                    documentType = null,
                    issuingAuthority = null,
                    issuingCountryCode = null,
                    revocationStatus = verifiedPresentation.revocationStatus,
                    certificateChain = verifiedPresentation.documentSignerCertChain,
                    docType = docType,
                    elements = elementsMap,
                )
            }
        )
    )
}
