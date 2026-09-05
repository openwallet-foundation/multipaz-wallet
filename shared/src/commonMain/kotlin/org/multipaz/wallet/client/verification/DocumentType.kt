package org.multipaz.wallet.client.verification

/**
 * Well-known document types recognized and used by [Query] and [DocumentQuery].
 */
enum class DocumentType {
    /**
     * ISO/IEC 18013-5 Mobile Driving License (mDL).
     */
    MOBILE_DRIVING_LICENSE,

    /**
     * ISO/IEC 23220-2 Photo ID.
     */
    PHOTO_ID,

    /**
     * European Union Personal Identification document (EU PID), supported in both ISO mdoc and SD-JWT VC formats.
     */
    EU_PID,

    /**
     * Indian Aadhaar identity document.
     */
    AADHAAR,

    /**
     * Google Wallet digital ID pass.
     */
    GOOGLE_WALLET_IDPASS,
}