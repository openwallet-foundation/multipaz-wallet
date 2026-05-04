package org.multipaz.wallet.android.navigation

import kotlinx.serialization.Serializable
import org.multipaz.cbor.Cbor
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import org.multipaz.wallet.shared.CredentialIssuer
import org.multipaz.wallet.shared.fromCbor
import org.multipaz.wallet.shared.toCbor
import kotlin.time.Instant

@Serializable
sealed class Destination

@Serializable
data object SetupGraph: Destination()

@Serializable
data object MainGraph: Destination()

@Serializable
data object SetupWelcomeScreenDestination: Destination()

@Serializable
data object SetupDeviceCheckScreenDestination: Destination()

@Serializable
data object SetupScreenLockCheckScreenDestination: Destination()

@Serializable
data object SetupEulaScreenDestination: Destination()

@Serializable
data object SetupBlePermissionScreenDestination: Destination()

@Serializable
data object SetupSignInScreenDestination: Destination()

@Serializable
data object SetupActivityLoggingScreenDestination: Destination()

@Serializable
data object SetupActivityLoggingLocationScreenDestination: Destination()

@Serializable
data object SetupDefaultWalletScreenDestination: Destination()

@Serializable
data class ProvisioningDestination(
    val credentialIssuerEncoded: String? = null,
    val openID4VCICredentialOfferUri: String? = null,
    val openID4VCIIssuerUrl: String? = null,
    val openID4VCICredentialId: String? = null,
    val provisionedDocumentIdentifier: String? = null
): Destination() {

    constructor(
        credentialIssuer: CredentialIssuer? = null,
        openID4VCICredentialOfferUri: String? = null,
        openID4VCIIssuerUrl: String? = null,
        openID4VCICredentialId: String? = null,
    ): this(
        credentialIssuerEncoded = credentialIssuer?.toCbor()?.toBase64Url(),
        openID4VCICredentialOfferUri = openID4VCICredentialOfferUri,
        openID4VCIIssuerUrl = openID4VCIIssuerUrl,
        openID4VCICredentialId = openID4VCICredentialId
    )

    val credentialIssuer: CredentialIssuer?
        get() = credentialIssuerEncoded?.fromBase64Url()?.let { CredentialIssuer.fromCbor(it) }

}

@Serializable
data class WalletDestination(
    val documentId: String? = null,
    val justAddedAtMillis: Long? = null
): Destination()

@Serializable
data class DocumentQrPresentmentDialogDestination(
    val documentId: String
)

@Serializable
data object AboutDestination: Destination()

@Serializable
data object SettingsDestination: Destination()

@Serializable
data object ActivityLoggingSettingsDestination: Destination()

@Serializable
data class DocumentEventListDestination(
    val documentId: String
): Destination()

@Serializable
data object EventListDestination: Destination()

@Serializable
data object DeleteAllEventsConfirmationDialogDestination: Destination()

@Serializable
data class DeleteAllEventsForDocumentConfirmationDialogDestination(
    val documentId: String
): Destination()

@Serializable
data class DeleteEventConfirmationDialogDestination(
    val eventId: String
): Destination()

@Serializable
data class EventViewerDestination(
    val eventId: String
): Destination()

@Serializable
data object DeveloperSettingsDestination: Destination()

@Serializable
data object SettingsActivityLogDisableConfirmationDialogDestination: Destination() {
}

@Serializable
data object SignOutConfirmationDialogDestination: Destination()

@Serializable
data object DeveloperSettingsConfigureWalletBackendDialogDestination: Destination()

@Serializable
data object DeveloperSettingsClearAppDataDialogDestination: Destination()

@Serializable
data class ErrorDialogDestination(
    val title: String,
    val textMarkdown: String
): Destination()

@Serializable
data class InfoDialogDestination(
    val title: String,
    val textMarkdown: String
): Destination()

@Serializable
data class DeveloperSettingsConnectToWalletServerDialogDestination(
    val walletBackendUrl: String?
): Destination()

@Serializable
data object SignInClearEncryptionKeyDialogDestination: Destination()

@Serializable
data object TrustedIssuersDestination: Destination()

@Serializable
data object TrustedVerifiersDestination: Destination()

@Serializable
data class TrustEntryDestination(
    val trustManagerId: String,
    val trustEntryId: String,
    val justImported: Boolean = false
): Destination()

@Serializable
data class TrustEntryEditDestination(
    val trustManagerId: String,
    val trustEntryId: String
): Destination()

@Serializable
data class TrustEntryVicalEntryDestination(
    val trustManagerId: String,
    val trustEntryId: String,
    val vicalCertNumber: Int
): Destination()

@Serializable
data class TrustEntryRicalEntryDestination(
    val trustManagerId: String,
    val trustEntryId: String,
    val ricalCertNumber: Int
): Destination()

@ConsistentCopyVisibility
@Serializable
data class CertificateViewerDestination private constructor(
    // Base64url encoded CBOR data of either X509Cert or X509CertChain
    val certificateData: String,
) : Destination() {
    companion object {
        fun create(cert: X509Cert): CertificateViewerDestination {
            return CertificateViewerDestination(Cbor.encode(cert.toDataItem()).toBase64Url())
        }

        fun create(cert: X509CertChain): CertificateViewerDestination {
            return CertificateViewerDestination(Cbor.encode(cert.toDataItem()).toBase64Url())
        }

    }
}

@Serializable
data class RemoveDocumentConfirmationDialogDestination(
    val documentId: String,
    val isSyncing: Boolean
): Destination()

@Serializable
data class DocumentInfoDestination(
    val documentId: String,
): Destination()

@Serializable
data class DocumentInfoExtrasDestination(
    val documentId: String,
): Destination()

@Serializable
data class CredentialInfoDestination(
    val documentId: String,
    val credentialId: String,
): Destination()

@Serializable
data class PreconsentSettingsDestination(
    val documentId: String
): Destination()

@Serializable
data class ManageTrustedReadersDestination(
    val documentId: String
): Destination()

@Serializable
data class ManageTrustedReadersAddReaderDialogDestination(
    val documentId: String,
    val certData: String
): Destination()

@Serializable
data object AddToWalletDestination: Destination()
