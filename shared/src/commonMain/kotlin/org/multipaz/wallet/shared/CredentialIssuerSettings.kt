package org.multipaz.wallet.shared

import kotlinx.serialization.Serializable
import org.multipaz.cbor.annotation.CborSerializable

/**
 * Issuer settings.
 *
 * This class contains configuration settings specific to an issuer.
 *
 * @property secureAreaToUse the [CredentialIssuerSecureAreaType] indicating which secure area to use.
 * @property androidKeySettings optional [CredentialIssuerSettingsAndroidKeySettings] describing key settings on Android devices.
 */
@Serializable
@CborSerializable
class CredentialIssuerSettings(
    val secureAreaToUse: CredentialIssuerSecureAreaType = CredentialIssuerSecureAreaType.PLATFORM_SECURE_AREA,
    val androidKeySettings: CredentialIssuerSettingsAndroidKeySettings? = null
) {
    companion object
}
