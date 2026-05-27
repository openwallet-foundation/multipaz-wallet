package org.multipaz.wallet.client.verification

import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

data class IdentificationDocumentQueryResult(
    override val trustResult: TrustResult,
    override val documentType: DocumentType,
    override val issuingAuthority: String,
    override val issuingCountryCode: String,
    override val revocationStatus: RevocationStatus?,

    val portrait: ByteString,
    val name: String,
    val birthDate: LocalDate,
    val streetAddress: String?,
): DocumentQueryResult(trustResult, documentType, issuingAuthority, issuingCountryCode, revocationStatus)
