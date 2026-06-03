package org.multipaz.wallet.client.verification

import org.multipaz.trustmanagement.TrustResult
import org.multipaz.verification.MdocVerifiedPresentation
import org.multipaz.verification.VerifiedPresentation
import kotlin.time.Instant

data class IsoMdocDataElementRequest(
    val dataElementName: String,
    val alternativeDataElements: List<String> = emptyList(),
)

data class IsoMdocRequest(
    val docType: String,
    val namespaces: Map<String, List<IsoMdocDataElementRequest>>,
    val getResult: (
        verifiedPresentation: MdocVerifiedPresentation,
        atTime: Instant,
        trustResult: TrustResult
    ) -> DocumentQueryResult
): Request()

