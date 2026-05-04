package org.multipaz.wallet.client

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.X509Cert
import org.multipaz.documenttype.DocumentAttributeSensitivity
import org.multipaz.trustmanagement.TrustMetadata

/**
 * Defines the criteria for a specific reader to be granted pre-consent.
 *
 * This class encapsulates the identity of a trusted reader and the maximum
 * data sensitivity they are allowed to access without prompting the user. It is
 * used within [DocumentPreconsentSetting.ReaderIdentityBased].
 *
 * @property metadata Contextual information about the trusted entity.
 * @property certificate The X.509 certificate representing the identity of the approved
 *                       reader (e.g., a reader root certificate or leaf certificate).
 *                       A match occurs if this certificate's public key appears anywhere
 *                       in the actual reader's certificate chain.
 * @property approvedSensitivity The maximum [DocumentAttributeSensitivity] this reader
 *                               is pre-approved to access. If `null`, the reader is
 *                               approved for all requests, regardless of complexity or
 *                               if the request contains unclassified claims.
 */
@CborSerializable
data class DocumentPreconsentApprovedReader(
    val metadata: TrustMetadata,
    val certificate: X509Cert,
    val approvedSensitivity: DocumentAttributeSensitivity?
) {
    companion object
}