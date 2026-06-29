package org.multipaz.wallet.client.verification

import kotlinx.io.bytestring.ByteString
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult
import org.multipaz.verification.VerifiedPresentation

data class SimpleMdocDocumentQueryResult(
    override val trustResult: TrustResult,
    override val documentType: DocumentType?,
    override val issuingAuthority: String?,
    override val issuingCountryCode: String?,
    override val revocationStatus: RevocationStatus?,

    val verifiedPresentation: VerifiedPresentation
): DocumentQueryResult(trustResult, documentType, issuingAuthority, issuingCountryCode, revocationStatus)
