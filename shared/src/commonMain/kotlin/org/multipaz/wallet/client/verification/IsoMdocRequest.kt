package org.multipaz.wallet.client.verification

import org.multipaz.mdoc.response.MdocDocument
import org.multipaz.trustmanagement.TrustResult
import kotlin.time.Instant

data class IsoMdocDataElementRequest(
    val dataElementName: String,
    val alternativeDataElements: List<String> = emptyList(),
)

data class IsoMdocRequest(
    val docType: String,
    val namespaces: Map<String, List<IsoMdocDataElementRequest>>,
    val getResult: (
        document: MdocDocument,
        atTime: Instant,
        trustResult: TrustResult
    ) -> DocumentQueryResult
): Request()

