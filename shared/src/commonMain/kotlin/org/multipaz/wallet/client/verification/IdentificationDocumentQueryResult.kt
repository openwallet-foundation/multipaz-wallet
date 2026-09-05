package org.multipaz.wallet.client.verification

import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509CertChain
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

/**
 * Result of verifying an [IdentificationDocumentQuery].
 *
 * @property trustResult Trust verification result for the document signer certificate chain.
 * @property documentType The well-known [DocumentType] that satisfied the query.
 * @property issuingAuthority The authority that issued the identity document.
 * @property issuingCountryCode The country code of the issuer.
 * @property revocationStatus Revocation status of the document, if available.
 * @property certificateChain Signer certificate chain of the document, if available.
 * @property portrait Portrait image bytes of the credential holder.
 * @property name Full name of the credential holder.
 * @property birthDate Birth date of the credential holder.
 * @property streetAddress The resident street address of the holder, or `null` if not requested or not provided.
 */
data class IdentificationDocumentQueryResult(
    override val trustResult: TrustResult,
    override val documentType: DocumentType,
    override val issuingAuthority: String,
    override val issuingCountryCode: String,
    override val revocationStatus: RevocationStatus?,
    override val certificateChain: X509CertChain? = null,

    val portrait: ByteString,
    val name: String,
    val birthDate: LocalDate,
    val streetAddress: String?,
): DocumentQueryResult(trustResult, documentType, issuingAuthority, issuingCountryCode, revocationStatus, certificateChain)
