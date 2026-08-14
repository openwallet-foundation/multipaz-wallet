package org.multipaz.wallet.shared

import kotlinx.serialization.Serializable
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.Algorithm

/**
 * Key settings to use on Android devices for credentials issued by an issuer.
 *
 * @property algorithm the signature algorithm to use for the device key, e.g. [Algorithm.ESP256] or [Algorithm.ED25519].
 * @property useStrongBox `true` to require StrongBox KeyMint if available, `false` otherwise.
 * @property userAuthenticationTimeoutMillis timeout in milliseconds for user authentication, or 0 to require authentication for every use.
 * @property userAuthenticationLskf `true` to allow device lock-screen knowledge factor (PIN, pattern, password) for authentication.
 * @property userAuthenticationBiometric `true` to allow biometric authentication (fingerprint, face).
 */
@CborSerializable
@Serializable
data class CredentialIssuerSettingsAndroidKeySettings(
    val algorithm: Algorithm = Algorithm.ESP256,
    val useStrongBox: Boolean = false,
    val userAuthenticationTimeoutMillis: Long = 0L,
    val userAuthenticationLskf: Boolean = true,
    val userAuthenticationBiometric: Boolean = true
) {
    companion object
}
