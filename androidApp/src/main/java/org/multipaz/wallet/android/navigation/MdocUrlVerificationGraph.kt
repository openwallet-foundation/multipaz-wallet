package org.multipaz.wallet.android.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import coil3.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.Simple
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.securearea.SecureArea
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.CertificateViewerScreen
import org.multipaz.wallet.android.ui.verification.RequestVerificationFromMdocUrlScreen
import org.multipaz.wallet.android.ui.verification.SelectCustomAgeDialog
import org.multipaz.wallet.android.ui.verification.SelectVerificationTypeScreen
import org.multipaz.wallet.android.ui.verification.VerificationProximityTransferErrorScreen
import org.multipaz.wallet.android.ui.verification.VerificationProximityTransferScreen
import org.multipaz.wallet.android.ui.verification.VerificationShowResponseDeveloperExtrasScreen
import org.multipaz.wallet.android.ui.verification.VerificationShowResponseScreen
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.verification.AgeOverQuery
import org.multipaz.wallet.client.verification.ProximityReaderModel

private const val TAG = "MdocUrlVerificationGraph"

fun mdocUrlVerificationGraph(
    backStack: MutableList<NavKey>,
    walletClient: WalletClient,
    secureArea: SecureArea,
    documentModel: DocumentModel,
    settingsModel: SettingsModel,
    documentTypeRepository: DocumentTypeRepository,
    zkSystemRepository: ZkSystemRepository,
    proximityReaderModel: ProximityReaderModel,
    imageLoader: ImageLoader,
    coroutineScope: CoroutineScope,
    showToast: (message: String) -> Unit,
    backendIssuerTrustManagerModel: TrustManagerModel,
    userIssuerTrustManagerModel: TrustManagerModel,
    readerTrustManager: CompositeTrustManager,
    issuerTrustManager: CompositeTrustManager,
    onFinish: () -> Unit
): (NavKey) -> NavEntry<NavKey>? {
    return { key ->
        when (key) {
            is RequestVerificationFromMdocUrlDestination -> NavEntry(key) {
                RequestVerificationFromMdocUrlScreen(
                    walletClient = walletClient,
                    settingsModel = settingsModel,
                    documentModel = documentModel,
                    onSelectVerificationTypeClicked = { backStack.add(SelectVerificationTypeDestination) },
                    onContinueClicked = {
                        coroutineScope.launch {
                            handleQrCodeScanned(
                                backStack = backStack,
                                mdocUrl = key.mdocUrl,
                                walletClient = walletClient,
                                secureArea = secureArea,
                                settingsModel = settingsModel,
                                proximityReaderModel = proximityReaderModel
                            )
                            // In verification graph, we just remove the URL screen.
                            // There is no WalletDestination to replace it with.
                            backStack.removeAll { it is RequestVerificationFromMdocUrlDestination }
                        }
                    },
                    onBackClicked = {
                        onFinish()
                    }
                )
            }
            is SelectVerificationTypeDestination -> NavEntry(key) {
                SelectVerificationTypeScreen(
                    settingsModel = settingsModel,
                    onCustomAgeClicked = {
                        backStack.add(SelectCustomAgeDestination)
                    },
                    onBackClicked = { backStack.removeAt(backStack.size - 1) }
                )
            }
            is SelectCustomAgeDestination -> NavEntry(
                key = key,
                metadata = DialogSceneStrategy.dialog()
            ) {
                SelectCustomAgeDialog(
                    onDismissed = { backStack.removeAt(backStack.size - 1) },
                    onConfirmed = { age ->
                        settingsModel.readerQuery.value = AgeOverQuery(age)
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is VerificationProximityTransferDestination -> NavEntry(key) {
                VerificationProximityTransferScreen(
                    proximityReaderModel = proximityReaderModel,
                    onBackClicked = {
                        backStack.removeAt(backStack.size - 1)
                    },
                    onTransferComplete = { presentmentRecord ->
                        backStack.removeAt(backStack.size - 1)
                        backStack.add(VerificationShowResponseDestination(
                            query = settingsModel.readerQuery.value,
                            presentmentRecord = presentmentRecord,
                            showNotTrusted = false
                        ))
                    },
                    onTransferError = {
                        backStack.removeAt(backStack.size - 1)
                        backStack.add(VerificationProximityTransferErrorDestination)
                    },
                )
            }
            is VerificationProximityTransferErrorDestination -> NavEntry(key) {
                VerificationProximityTransferErrorScreen(
                    onAnimationComplete = {
                        backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            is VerificationShowResponseDestination -> NavEntry(key) {
                VerificationShowResponseScreen(
                    query = key.query,
                    presentmentRecord = key.presentmentRecord,
                    documentTypeRepository = documentTypeRepository,
                    zkSystemRepository = zkSystemRepository,
                    issuerTrustManager = issuerTrustManager,
                    builtInIssuerTrustManager = backendIssuerTrustManagerModel.trustManager,
                    userIssuerTrustManagerManager = userIssuerTrustManagerModel.trustManager,
                    settingsModel = settingsModel,
                    imageLoader = imageLoader,
                    showNotTrusted = key.showNotTrusted,
                    onDeveloperExtrasClicked = {
                        backStack.add(VerificationShowResponseDeveloperExtrasDestination(
                            query = key.query,
                            presentmentRecord = key.presentmentRecord
                        ))
                    },
                    onBackClicked = {
                        onFinish()
                    }
                )
            }
            is VerificationShowResponseDeveloperExtrasDestination -> NavEntry(key) {
                VerificationShowResponseDeveloperExtrasScreen(
                    query = key.query,
                    presentmentRecord = key.presentmentRecord,
                    issuerTrustManager = issuerTrustManager,
                    settingsModel = settingsModel,
                    documentTypeRepository = documentTypeRepository,
                    zkSystemRepository = zkSystemRepository,
                    onBackClicked = {
                        backStack.removeAt(backStack.size - 1)
                    },
                    onViewCertChain = { certChain ->
                        backStack.add(CertificateViewerDestination.create(certChain))
                    }
                )
            }
            is CertificateViewerDestination -> NavEntry(key) {
                when (val dataItem = Cbor.decode(key.certificateData.fromBase64Url())) {
                    is CborArray -> CertificateViewerScreen(
                        x509CertChain = X509CertChain.fromDataItem(dataItem),
                        onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    )
                    else -> CertificateViewerScreen(
                        x509Cert = X509Cert.fromDataItem(dataItem),
                        onBackClicked = { backStack.removeAt(backStack.size - 1) },
                    )
                }
            }
            else -> null
        }
    }
}

internal suspend fun handleQrCodeScanned(
    backStack: MutableList<NavKey>,
    mdocUrl: String,
    walletClient: WalletClient,
    secureArea: SecureArea,
    settingsModel: SettingsModel,
    proximityReaderModel: ProximityReaderModel,
) {
    check(mdocUrl.startsWith("mdoc:"))
    try {
        val deviceEngagement = Cbor.decode(mdocUrl.substringAfter("mdoc:").fromBase64Url())
        proximityReaderModel.reset()
        proximityReaderModel.setMdocTransportOptions(
            MdocTransportOptions(
                bleUseL2CAP = true,
                bleUseL2CAPInEngagement = true
            )
        )
        proximityReaderModel.setConnectionEndpoint(
            deviceEngagement = deviceEngagement,
            handover = Simple.NULL,
            existingTransport = null,
            nfcHandoverType = null,
            durationNfcTapToEngagement = null
        )
        val keyInfoAndCertification = try {
            walletClient.getReaderKey()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Error getting reader key", e)
            null
        }
        val query = settingsModel.readerQuery.value
        proximityReaderModel.setDeviceRequest(
            query = query,
            deviceRequest = query.generateDeviceRequest(
                deviceEngagement = proximityReaderModel.sessionTranscript.asArray[0].asTaggedEncodedCbor,
                sessionTranscript = proximityReaderModel.sessionTranscript,
                readerAuthKey = keyInfoAndCertification?.let {
                    AsymmetricKey.X509CertifiedSecureAreaBased(
                        certChain = keyInfoAndCertification.second,
                        secureArea = secureArea,
                        keyInfo = keyInfoAndCertification.first,
                    )
                },
                intentToRetain = false // TODO
            )
        )
        if (keyInfoAndCertification != null) {
            walletClient.markReaderKeyAsUsed(
                keyInfo = keyInfoAndCertification.first
            )
        }
        backStack.add(VerificationProximityTransferDestination)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Error parsing QR code", e)
    }
}
