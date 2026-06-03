package org.multipaz.wallet.client.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.multipaz.sdjwt.SdJwtKb
import org.multipaz.trustmanagement.TrustResult
import org.multipaz.verification.JsonVerifiedPresentation
import org.multipaz.verification.VerifiedPresentation
import kotlin.time.Instant

data class SdJwtVcClaimRequest(
    val claimPath: JsonArray,
    val alternativeClaims: List<JsonArray> = emptyList()
)

class SdJwtVcRequest(
    val vct: String,
    val claims: List<SdJwtVcClaimRequest>,
    val getResult: (
        verifiedPresentation: JsonVerifiedPresentation,
        atTime: Instant,
        trustResult: TrustResult
    ) -> DocumentQueryResult
): Request()