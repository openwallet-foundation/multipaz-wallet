package org.multipaz.wallet.android.ui.provisioning

import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.multipaz.provisioning.ProvisioningMetadata
import org.multipaz.provisioning.ProvisioningModel
import org.multipaz.util.Logger
import org.multipaz.wallet.client.WalletClient

import coil3.ImageLoader
import org.multipaz.document.Document
import org.multipaz.provisioning.AuthorizationChallenge
import org.multipaz.util.toBase64Url
import org.multipaz.wallet.client.WalletClientProvisionedDocument
import org.multipaz.wallet.client.WalletClientProvisionedDocumentOpenID4VCI
import org.multipaz.wallet.shared.CredentialIssuer
import org.multipaz.wallet.shared.CredentialIssuerOpenID4VCI
import kotlin.random.Random

private const val TAG = "ProvisioningRoute"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningRoute(
    provisioningModel: ProvisioningModel,
    walletClient: WalletClient,
    imageLoader: ImageLoader,
    credentialIssuer: CredentialIssuer?,
    openID4VCICredentialOffer: String?,
    openID4VCIIssuerUrl: String?,
    openID4VCICredentialId: String?,
    onCloseClicked: () -> Unit,
    onComplete: (document: Document, provisionedDocument: WalletClientProvisionedDocument) -> Unit,
    onFailed: (error: Throwable) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val provisioningState = provisioningModel.state.collectAsState().value

    val issuerUrl = remember { mutableStateOf<String?>(null) }
    val issuerMetadata = remember { mutableStateOf<ProvisioningMetadata?>(null) }
    val error = remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(Unit) {
        if (credentialIssuer != null) {
            credentialIssuer as CredentialIssuerOpenID4VCI  // only one we support right now
            if (credentialIssuer.id != null) {
                try {
                    provisioningModel.launchOpenID4VCIProvisioning(
                        issuerUrl = credentialIssuer.url,
                        credentialId = credentialIssuer.id!!,
                        clientPreferences = walletClient.getOpenID4VCIClientPreferences(),
                        backend = walletClient.getOpenID4VCIBackend()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "Error launching provisioning from $credentialIssuer", e)
                    error.value = e
                }
            } else {
                issuerUrl.value = credentialIssuer.url
                try {
                    issuerMetadata.value = provisioningModel.getOpenID4VCIIssuerMetadata(
                        issuerUrl = credentialIssuer.url,
                        clientPreferences = walletClient.getOpenID4VCIClientPreferences()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "Error getting issuer metadata from $credentialIssuer", e)
                    error.value = e
                }
            }
        } else if (openID4VCIIssuerUrl != null) {
            issuerUrl.value = openID4VCIIssuerUrl
            if (openID4VCICredentialId != null) {
                try {
                    provisioningModel.launchOpenID4VCIProvisioning(
                        issuerUrl = issuerUrl.value!!,
                        credentialId = openID4VCICredentialId,
                        clientPreferences = walletClient.getOpenID4VCIClientPreferences(),
                        backend = walletClient.getOpenID4VCIBackend()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "Error provisioning from $openID4VCIIssuerUrl and $openID4VCICredentialId", e)
                    error.value = e
                }
            } else {
                try {
                    issuerMetadata.value = provisioningModel.getOpenID4VCIIssuerMetadata(
                        issuerUrl = openID4VCIIssuerUrl,
                        clientPreferences = walletClient.getOpenID4VCIClientPreferences()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(TAG, "Error getting issuer metadata from $openID4VCIIssuerUrl", e)
                    error.value = e
                }
            }
        } else if (openID4VCICredentialOffer != null) {
            try {
                provisioningModel.launchOpenID4VCIProvisioning(
                    offerUri = openID4VCICredentialOffer,
                    clientPreferences = walletClient.getOpenID4VCIClientPreferences(),
                    backend = walletClient.getOpenID4VCIBackend()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Error launching provisioning from offer", e)
                error.value = e
            }
        }
    }

    BackHandler {
        onCloseClicked()
    }

    if (error.value != null) {
        ProvisioningErrorScreen(
            onAnimationComplete = {
                onFailed(Exception(error.value))
            }
        )
    } else {
        var showProgress = false
        when (provisioningState) {
            ProvisioningModel.Idle -> {
                if (issuerMetadata.value != null) {
                    CredentialSelectionScreen(
                        metadata = issuerMetadata.value!!,
                        onCloseClicked = {
                            onCloseClicked()
                        },
                        onCredentialSelected = { selectedId ->
                            coroutineScope.launch {
                                provisioningModel.launchOpenID4VCIProvisioning(
                                    issuerUrl = issuerUrl.value!!,
                                    credentialId = selectedId,
                                    clientPreferences = walletClient.getOpenID4VCIClientPreferences(),
                                    backend = walletClient.getOpenID4VCIBackend()
                                )
                            }
                        }
                    )
                } else if (openID4VCICredentialOffer != null) {
                    showProgress = true // "Processing offer..."
                } else {
                    showProgress = true // "Starting..."
                }
            }
            ProvisioningModel.Initial -> showProgress = true // "Connecting to issuer..."
            ProvisioningModel.Connected -> showProgress = true // "Connected to issuer..."
            is ProvisioningModel.Authorizing -> {
                when (val challenge = provisioningState.authorizationChallenges.first()) {
                    is AuthorizationChallenge.OAuth -> {
                        AuthorizationScreenOAuth(
                            provisioningModel = provisioningModel,
                            walletClient = walletClient,
                            challenge = challenge,
                            onCloseClicked = {
                                onCloseClicked()
                            },
                        )
                    }
                    is AuthorizationChallenge.SecretText -> {
                        AuthorizationScreenSecretText(
                            provisioningModel = provisioningModel,
                            challenge = challenge,
                            onCloseClicked = {
                                onCloseClicked()
                            },
                        )
                    }
                }
            }
            ProvisioningModel.ProcessingAuthorization -> showProgress = true // "Verifying authorization..."
            ProvisioningModel.Authorized -> showProgress = true // "Authorized!"
            ProvisioningModel.RequestingCredentials -> showProgress = true // "Requesting your credentials..."
            is ProvisioningModel.CredentialsIssued -> {
                LaunchedEffect(Unit) {
                    val document = provisioningState.document
                    val provisionedDocument = WalletClientProvisionedDocumentOpenID4VCI(
                        identifier = Random.Default.nextBytes(16).toBase64Url(),
                        cardArt = document.cardArt,
                        displayName = document.displayName,
                        typeDisplayName = document.typeDisplayName,
                        url = provisioningModel.metadata.value!!.url,
                        credentialId = provisioningModel.metadata.value!!.credentials.keys.first()
                    )
                    onComplete(
                        provisioningState.document,
                        provisionedDocument
                    )
                }
            }
            is ProvisioningModel.Error -> {
                ProvisioningErrorScreen(
                    onAnimationComplete = {
                        onFailed(provisioningState.err)
                    },
                )
            }
        }
        if (showProgress) {
            ProvisioningProgressScreen(
                onCloseClicked = onCloseClicked
            )
        }
    }
}
