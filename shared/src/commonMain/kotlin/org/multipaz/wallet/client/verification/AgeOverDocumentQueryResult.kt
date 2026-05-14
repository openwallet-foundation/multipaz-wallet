package org.multipaz.wallet.client.verification

import kotlinx.io.bytestring.ByteString
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

data class AgeOverDocumentQueryResult(
    override val trustResult: TrustResult,
    override val documentType: DocumentType,
    override val issuingAuthority: String,
    override val issuingCountryCode: String,
    override val revocationStatus: RevocationStatus?,

    val portrait: ByteString,
    val isAgeOver: Boolean
): DocumentQueryResult(trustResult, documentType, issuingAuthority, issuingCountryCode, revocationStatus)

