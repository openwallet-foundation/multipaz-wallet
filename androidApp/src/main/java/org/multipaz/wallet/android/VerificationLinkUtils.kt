package org.multipaz.wallet.android

import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.mdoc.request.DeviceRequest
import org.multipaz.openid.OpenID4VP
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import org.multipaz.verification.VerificationSession
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.WalletClient
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.chooser.ChooserAction
import org.multipaz.wallet.client.verification.Query
import org.multipaz.verification.VerifierIdentity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private const val TAG = "VerificationLinkUtils"

private val linkVerificationsTableSpec = StorageTableSpec(
    name = "LinkVerifications4",
    supportPartitions = false,
    supportExpiration = false
)

val VERIFICATION_LINK_EXPIRATION = 1.hours

@CborSerializable
data class LinkVerification(
    val requestId: String,
    val query: Query,
    val session: VerificationSession,
    val requestEncryptionKey: ByteString,
    val creationTimeMillis: Long,
    val isPending: Boolean,
    val encryptedResponse: ByteString? = null,
    val responseReceivedAtMillis: Long? = null
) {
    companion object
}

suspend fun generateVerificationLink(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    storage: Storage,
    secureArea: SecureArea,
): String {

    val keyInfoAndCertification = try {
        walletClient.getReaderKey()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Logger.w(TAG, "Error getting reader key", e)
        null
    }
    val readerAuthKey = keyInfoAndCertification?.let { (keyInfo, certChain) ->
        AsymmetricKey.X509CertifiedSecureAreaBased(
            certChain = certChain,
            secureArea = secureArea,
            keyInfo = keyInfo,
        )
    }
    val query = settingsModel.readerQuery.value

    val origin = walletClient.getVerificationLinkOrigin()
    val nonce = ByteString(Random.nextBytes(16))
    val requestEncryptionKey = ByteString(Random.nextBytes(32))
    val responseEncryptionKey = Crypto.createEcPrivateKey(EcCurve.P256)

    val (deviceRequest, encryptionInfo) = query.generateDcRequest(
        nonce = nonce,
        origin = origin,
        responseEncryptionKey = responseEncryptionKey.publicKey,
        readerAuthKey = readerAuthKey,
        intentToRetain = settingsModel.verificationStoreResponse.value
    )

    val mdocApiRequest = VerificationSession.DcIso18013Request(
        origin = origin,
        responseEncryptionKey = responseEncryptionKey,
        deviceRequest = deviceRequest,
        encryptionInfo = encryptionInfo
    )

    fun getClientIdFromOrigin(origin: String): String {
        // Remove the http:// or https:// from the baseUrl.
        val startIndex = origin.findAnyOf(listOf("://"))?.first
        val ret = if (startIndex == null) origin else origin.removeRange(0, startIndex+3)
        return "x509_san_dns:$ret"
    }

    val verifierIdentities = if (readerAuthKey != null) {
        listOf(VerifierIdentity(readerAuthKey, getClientIdFromOrigin(origin)))
    } else {
        emptyList()
    }

    val openIdRequest = VerificationSession.DcOpenID4VPRequest(
        requestorId = origin,
        responseEncryptionKey = responseEncryptionKey,
        openID4VPRequest = OpenID4VP.generateRequest(
            version = OpenID4VP.Version.DRAFT_29,
            origin = origin,
            nonce = nonce.toByteArray().toBase64Url(),
            responseEncryptionKey = responseEncryptionKey.publicKey,
            verifierIdentities = verifierIdentities,
            responseMode = OpenID4VP.ResponseMode.DC_API,
            responseUri = null,
            dcqlQuery = DeviceRequest.fromDataItem(deviceRequest).toDcql(),
            jsonTransactionData = emptyList(),
            state = null
        ).toString()
    )

    val session = VerificationSession(
        requests = listOf(mdocApiRequest, openIdRequest),
    )

    val dcRequest = session.getDcRequest()

    val envelope = buildJsonObject {
        put("dcRequest", dcRequest)
    }
    val envelopeStr = Json.encodeToString(envelope)
    Logger.i(TAG, "Cleartext data before encrypting: $envelopeStr")
    val iv = Random.nextBytes(12)
    val encryptedData = Crypto.encrypt(
        algorithm = Algorithm.A256GCM,
        key = requestEncryptionKey.toByteArray(),
        nonce = iv,
        messagePlaintext = envelopeStr.toByteArray(),
        aad = byteArrayOf()
    )
    val cipherText = ByteString(iv + encryptedData)
 
    val result = walletClient.createVerificationLink(
        encryptedVerificationPayload = cipherText,
        expirationDurationAfterCreatedSeconds = VERIFICATION_LINK_EXPIRATION.inWholeSeconds
    )

    savePendingVerification(
        storage = storage,
        requestId = result.requestId,
        query = query,
        session = session,
        requestEncryptionKey = requestEncryptionKey,
    )

    val linkWithEncryptionKey = result.link + "#${requestEncryptionKey.toByteArray().toBase64Url()}"
    return linkWithEncryptionKey
}

private suspend fun savePendingVerification(
    storage: Storage,
    requestId: String,
    query: Query,
    session: VerificationSession,
    requestEncryptionKey: ByteString
) {
    val table = storage.getTable(linkVerificationsTableSpec)
    val entry = LinkVerification(
        requestId = requestId,
        query = query,
        session = session,
        requestEncryptionKey = requestEncryptionKey,
        creationTimeMillis = System.currentTimeMillis(),
        isPending = true
    )
    table.insert(requestId, ByteString(entry.toCbor()))
}

suspend fun getPendingVerifications(storage: Storage): List<LinkVerification> {
    val table = storage.getTable(linkVerificationsTableSpec)
    val now = Clock.System.now().toEpochMilliseconds()
    val all = table.enumerateWithData().map { (key, data) ->
        key to LinkVerification.fromCbor(data.toByteArray())
    }
    val pending = mutableListOf<LinkVerification>()
    for ((key, verification) in all) {
        if (verification.isPending) {
            if (verification.creationTimeMillis + VERIFICATION_LINK_EXPIRATION.inWholeMilliseconds <= now) {
                try {
                    table.delete(key)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Logger.e(TAG, "Failed to delete expired verification $key", e)
                }
            } else {
                pending.add(verification)
            }
        }
    }
    return pending.sortedByDescending { it.creationTimeMillis }
}

suspend fun deleteVerification(storage: Storage, requestId: String) {
    val table = storage.getTable(linkVerificationsTableSpec)
    table.delete(requestId)
}

suspend fun getCompletedVerifications(storage: Storage): List<LinkVerification> {
    val table = storage.getTable(linkVerificationsTableSpec)
    return table.enumerateWithData().map { (_, data) ->
        LinkVerification.fromCbor(data.toByteArray())
    }.filter { !it.isPending }
     .sortedByDescending { it.creationTimeMillis }
}

suspend fun checkVerificationResults(walletClient: WalletClient, storage: Storage) {
    val table = storage.getTable(linkVerificationsTableSpec)
    val pending = getPendingVerifications(storage)
    for (verification in pending) {
        try {
            val response = walletClient.getVerificationResponse(verification.requestId) ?: continue
            val updated = verification.copy(
                isPending = false,
                encryptedResponse = response,
                responseReceivedAtMillis = Clock.System.now().toEpochMilliseconds()
            )
            table.update(verification.requestId, ByteString(updated.toCbor()))
            try {
                walletClient.deleteVerificationResponse(verification.requestId)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to delete verification response from server for ${verification.requestId}", e)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check response for ${verification.requestId}", e)
        }
    }
}

suspend fun LinkVerification.decryptResponse(): String {
    val responseBytes = encryptedResponse?.toByteArray() ?: throw IllegalStateException("No response to decrypt")
    if (responseBytes.size < 12) throw IllegalStateException("Invalid encrypted response size")

    val iv = responseBytes.sliceArray(0 until 12)
    val ciphertext = responseBytes.sliceArray(12 until responseBytes.size)

    val plaintextBytes = Crypto.decrypt(
        algorithm = Algorithm.A256GCM,
        key = requestEncryptionKey.toByteArray(),
        nonce = iv,
        messageCiphertext = ciphertext,
        aad = byteArrayOf()
    )
    return plaintextBytes.decodeToString()
}

fun shareVerificationLink(context: Context, urlToShare: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, urlToShare)
    }

    val chooserIntent = Intent.createChooser(sendIntent, "Share link")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val qrIntent = Intent(context, VerificationLinkQrCodeDisplayActivity::class.java).apply {
            putExtra("URL", urlToShare)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            qrIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val qrAction = ChooserAction.Builder(
            Icon.createWithResource(context, R.drawable.ic_qr_code),
            context.getString(R.string.request_verification_share_qr_button),
            pendingIntent
        ).build()

        chooserIntent.putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, arrayOf(qrAction))
    }

    context.startActivity(chooserIntent)
}