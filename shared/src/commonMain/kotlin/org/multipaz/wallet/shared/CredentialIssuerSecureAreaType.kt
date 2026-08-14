package org.multipaz.wallet.shared

/**
 * Enumeration of secure area types that can be selected for an issuer's credentials.
 */
enum class CredentialIssuerSecureAreaType {
    /**
     * Use the platform's default hardware-backed secure area (e.g. Android Keystore, iOS Secure Enclave).
     */
    PLATFORM_SECURE_AREA,

    /**
     * Use cloud-backed secure area (not yet used).
     */
    CLOUD_SECURE_AREA
}
