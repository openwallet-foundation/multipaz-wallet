package org.multipaz.wallet.android

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import org.multipaz.util.toHex
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.shared.BuildConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

private const val TAG = "VerificationLinkUtils"

suspend fun generateVerificationLink(
    walletClient: WalletClient,
    settingsModel: SettingsModel,
    storage: Storage,
    secureArea: SecureArea,
    httpClientEngineFactory: HttpClientEngineFactory<*>,
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

    val requestEncryptionKey = ByteString(Random.nextBytes(32))
    val responseEncryptionKey = Crypto.createEcPrivateKey(EcCurve.P256)
    val dcRequest = query.generateDcRequest(
        nonce = ByteString(Random.nextBytes(16)),
        origin = BuildConfig.BACKEND_URL,
        responseEncryptionKey = responseEncryptionKey.publicKey,
        readerAuthKey = readerAuthKey,
        intentToRetain = false
    )

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

    val result = walletClient.createVerificationLink(cipherText)

    // TODO: Store a blob with details locally so we can decrypt the response

    val linkWithEncryptionKey = result.link + "#${requestEncryptionKey.toByteArray().toBase64Url()}"
    return linkWithEncryptionKey
}