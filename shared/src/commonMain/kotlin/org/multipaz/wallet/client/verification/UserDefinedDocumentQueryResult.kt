package org.multipaz.wallet.client.verification

import org.multipaz.cbor.DataItem
import org.multipaz.crypto.X509CertChain
import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

/**
 * Result of a user-defined document query.
 *
 * @property trustResult Trust verification result for the document signer certificate chain.
 * @property documentType The well-known [DocumentType] that satisfied the query, or `null` for custom types.
 * @property issuingAuthority The issuing authority of the document, if known.
 * @property issuingCountryCode The issuing country code of the issuer, if known.
 * @property revocationStatus Revocation status of the document, if available.
 * @property certificateChain Signer certificate chain of the document, if available.
 * @property docType The requested document type string.
 * @property elements Map of namespace name to a map of data element name to CBOR [DataItem].
 */
data class UserDefinedDocumentQueryResult(
    override val trustResult: TrustResult,
    override val documentType: DocumentType? = null,
    override val issuingAuthority: String? = null,
    override val issuingCountryCode: String? = null,
    override val revocationStatus: RevocationStatus?,
    override val certificateChain: X509CertChain? = null,

    val docType: String,
    val elements: Map<String, Map<String, DataItem>>,
) : DocumentQueryResult(
    trustResult = trustResult,
    documentType = documentType,
    issuingAuthority = issuingAuthority,
    issuingCountryCode = issuingCountryCode,
    revocationStatus = revocationStatus,
    certificateChain = certificateChain,
)
