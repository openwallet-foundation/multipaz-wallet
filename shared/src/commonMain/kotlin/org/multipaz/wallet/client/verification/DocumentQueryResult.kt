package org.multipaz.wallet.client.verification

import org.multipaz.revocation.RevocationStatus
import org.multipaz.trustmanagement.TrustResult

/**
 * Base class for documents that are returned as for a query.
 *
 * @property trustResult A [TrustResult] indicating whether the issuer of the document is trusted or not.
 * @property documentType The type of document returned, if known.
 * @property issuingAuthority The issuing authority of the document, if known.
 * @property issuingCountryCode The issuing country code of the issuer, if known.
 * @property revocationStatus The revocation status of the document, if known.
 */
sealed class DocumentQueryResult(
    open val trustResult: TrustResult,
    open val documentType: DocumentType?,
    open val issuingAuthority: String?,
    open val issuingCountryCode: String?,
    open val revocationStatus: RevocationStatus?
)

