package org.multipaz.wallet.client.verification

import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509CertChain
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

data class AgeOverDocumentQueryResult(
    override val trustResult: TrustResult,
    override val documentType: DocumentType,
    override val issuingAuthority: String,
    override val issuingCountryCode: String,
    override val revocationStatus: RevocationStatus?,
    override val certificateChain: X509CertChain? = null,

    val portrait: ByteString,
    val isAgeOver: Boolean
): DocumentQueryResult(trustResult, documentType, issuingAuthority, issuingCountryCode, revocationStatus, certificateChain)
