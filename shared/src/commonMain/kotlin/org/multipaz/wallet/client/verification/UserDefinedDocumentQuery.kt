package org.multipaz.wallet.client.verification

import org.multipaz.cbor.DataItem
import org.multipaz.verification.MdocVerifiedPresentation

/**
 * A [DocumentQuery] requesting user-specified data elements from an ISO/IEC 18013-5 mdoc credential.
 *
 * @property docType The ISO mdoc document type identifier (e.g., `org.iso.18013.5.1.mDL`).
 * @property namespaces Map from namespace name to the list of requested data element names within that namespace.
 */
data class UserDefinedDocumentQuery(
    val docType: String,
    val namespaces: Map<String, List<String>>,
) : DocumentQuery() {

    /**
     * Returns the [IsoMdocRequest] configured for the requested document type and namespaces.
     *
     * @return A list containing the [IsoMdocRequest].
     */
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
