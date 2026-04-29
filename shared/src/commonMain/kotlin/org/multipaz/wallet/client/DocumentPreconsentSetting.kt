package org.multipaz.wallet.client

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.documenttype.DocumentAttributeSensitivity

/**
 * Defines the rules for when a document can be presented without explicit user consent.
 *
 * This sealed class represents the various strategies for pre-consent. It ranges from
 * always requiring consent, to conditionally granting it based on the sensitivity of the
 * requested data or the identity of the reader, to never requiring consent.
 *
 * TODO: add support for pre-consent for transactions, for example pre=consent for payment
 *   confirmations smaller than $50 USD.
 */
@CborSerializable
sealed class DocumentPreconsentSetting(
) {
    /**
     * A pre-consent setting that mandates explicit user consent for every presentation
     * request involving this document.
     */
    data object AlwaysRequireConsent: DocumentPreconsentSetting()

    /**
     * A pre-consent setting that grants consent automatically if the sensitivity of the
     * requested claims does not exceed a specified threshold.
     *
     * @property approvedSensitivity The maximum [DocumentAttributeSensitivity] that is
     *                               allowed to be presented without user consent. If a request
     *                               contains claims with a higher sensitivity, or unclassified claims,
     *                               consent will be required.
     */
    data class RequestComplexityBased(
        val approvedSensitivity: DocumentAttributeSensitivity
    ): DocumentPreconsentSetting()

    /**
     * A pre-consent setting that grants consent automatically based on the verified identity
     * of the reader, combined with a sensitivity threshold specific to that reader.
     *
     * @property approvedReaders A list of [DocumentPreconsentApprovedReader] defining the
     *                           trusted entities and their corresponding data access limits.
     *                           A request is pre-consented if the reader's certificate chain
     *                           contains the public key of any approved reader's certificate,
     *                           AND the requested data's sensitivity does not exceed that
     *                           reader's [DocumentPreconsentApprovedReader.approvedSensitivity].
     */
    data class ReaderIdentityBased(
        val approvedReaders: List<DocumentPreconsentApprovedReader>
    ): DocumentPreconsentSetting()

    /**
     * A pre-consent setting that waives the requirement for explicit user consent for
     * any presentation request involving this document, regardless of the reader or
     * the requested data. Use with caution.
     */
    data object NeverRequireConsent: DocumentPreconsentSetting()

    companion object
}
