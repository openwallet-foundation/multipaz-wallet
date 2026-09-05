package org.multipaz.wallet.client.verification

import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509CertChain
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

/**
 * Result of verifying an [AgeOverDocumentQuery].
 *
 * @property trustResult Trust verification result for the document signer certificate chain.
 * @property documentType The well-known [DocumentType] that satisfied the query.
 * @property issuingAuthority The authority that issued the document.
 * @property issuingCountryCode The country code of the issuer.
 * @property revocationStatus Revocation status of the document, if available.
 * @property certificateChain Signer certificate chain of the document, if available.
 * @property portrait The portrait image bytes of the credential holder.
 * @property isAgeOver `true` if the holder was verified to be older than the requested age; `false` otherwise.
 */
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
