package org.multipaz.wallet.client.verification

import org.multipaz.trustmanagement.TrustResult
import org.multipaz.verification.MdocVerifiedPresentation
import org.multipaz.verification.VerifiedPresentation
import kotlin.time.Instant

/**
 * Specification of a data element requested from an ISO/IEC 18013-5 mdoc namespace.
 *
 * @property dataElementName The name of the requested data element (e.g., `portrait`, `given_name`).
 * @property alternativeDataElements Alternative data element names that may satisfy this request if the primary element is absent.
 *   The order of the claims indicates the order of preference, with the first element in the array indicating the highest preference.
 */
data class IsoMdocDataElementRequest(
    val dataElementName: String,
    val alternativeDataElements: List<String> = emptyList(),
)

/**
 * A format-specific document request for an ISO/IEC 18013-5 mobile document (mdoc).
 *
 * @property docType The ISO mdoc document type identifier (e.g., `org.iso.18013.5.1.mDL`).
 * @property namespaces Map from namespace name to the list of data elements requested within that namespace.
 * @property getResult Callback function that transforms a verified mdoc presentation and trust evaluation into a [DocumentQueryResult].
 */
data class IsoMdocRequest(
    val docType: String,
    val namespaces: Map<String, List<IsoMdocDataElementRequest>>,
    val getResult: (
        verifiedPresentation: MdocVerifiedPresentation,
        atTime: Instant,
        trustResult: TrustResult
    ) -> DocumentQueryResult
): Request()

