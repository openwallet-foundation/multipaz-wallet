package org.multipaz.wallet.client.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.multipaz.sdjwt.SdJwtKb
import org.multipaz.trustmanagement.TrustResult
import org.multipaz.verification.JsonVerifiedPresentation
import org.multipaz.verification.VerifiedPresentation
import kotlin.time.Instant

/**
 * Specification of a claim requested from an SD-JWT VC credential.
 *
 * @property claimPath Path of JSON element names or indices addressing the claim within the SD-JWT payload.
 * @property alternativeClaims List of alternative JSON paths that can satisfy the request if the primary claim is absent.
 *   The order of the claims indicates the order of preference, with the first element in the array indicating the highest preference.
 */
data class SdJwtVcClaimRequest(
    val claimPath: JsonArray,
    val alternativeClaims: List<JsonArray> = emptyList()
)

/**
 * A format-specific document request for an IETF SD-JWT Verifiable Credential (SD-JWT VC).
 *
 * @property vct The Verifiable Credential Type (vct) identifier string (e.g., `urn:eu.europa.ec.eudi:pid:1`).
 * @property claims The list of requested claims.
 * @property getResult Callback function that transforms a verified JSON presentation and trust evaluation into a [DocumentQueryResult].
 */
class SdJwtVcRequest(
    val vct: String,
    val claims: List<SdJwtVcClaimRequest>,
    val getResult: (
        verifiedPresentation: JsonVerifiedPresentation,
        atTime: Instant,
        trustResult: TrustResult
    ) -> DocumentQueryResult
): Request()