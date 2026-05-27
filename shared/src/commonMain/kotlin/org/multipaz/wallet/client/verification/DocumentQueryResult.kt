package org.multipaz.wallet.client.verification

import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

/**
 * Base class for documents that are returned as for a query.
 *
 * @property trustResult A [TrustResult] indicating whether the issuer of the document is trusted or not.
 * @property documentType The type of document returned.
 * @property issuingAuthority The issuing authority of the document.
 * @property issuingCountryCode The issuing country code of the issuer.
 * @property revocationStatus The revocation status of the document.
 */
sealed class DocumentQueryResult(
    open val trustResult: TrustResult,
    open val documentType: DocumentType,
    open val issuingAuthority: String,
    open val issuingCountryCode: String,
    open val revocationStatus: RevocationStatus?
)

