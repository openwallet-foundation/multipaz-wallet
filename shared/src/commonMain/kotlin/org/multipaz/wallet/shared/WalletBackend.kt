package org.multipaz.wallet.shared

import kotlinx.io.bytestring.ByteString
import org.multipaz.rpc.annotation.RpcInterface
import org.multipaz.rpc.annotation.RpcMethod
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509CertChain
import org.multipaz.securearea.KeyAttestation

/**
 * Interface between the wallet backend and the implementation details
 * of [org.multipaz.wallet.client.WalletClient].
 *
 * Applications should use [org.multipaz.wallet.client.WalletClient] instead of this raw interface.
 */
@RpcInterface
interface WalletBackend {

    @RpcMethod
    suspend fun getNonce(): String

    @RpcMethod
    suspend fun signIn(
        nonce: String,
        googleIdTokenString:String,
        walletServerEncryptionKeySha256: ByteString,
        resetSharedData: Boolean,
        initialSharedData: ByteString
    )

    @RpcMethod
    suspend fun signInWithGoogleCode(
        nonce: String,
        authorizationCode: String,
        redirectUri: String,
        walletServerEncryptionKeySha256: ByteString,
        resetSharedData: Boolean,
        initialSharedData: ByteString
    ): String

    @RpcMethod
    suspend fun exchangeCodeForTokens(
        nonce: String,
        authorizationCode: String,
        redirectUri: String
    ): GoogleTokens

    @RpcMethod
    suspend fun signOut()

    /**
     * Gets the latest data shared among signed-in clients.
     *
     * @param currentVersion the current version of the shared data or [Long.MIN_VALUE] if no current version exists.
     * @return the shared data and its version or `null` if it's still at [currentVersion].
     * @throws WalletBackendNotSignedInException if not signed in.
     */
    @RpcMethod
    suspend fun getSharedData(currentVersion: Long): GetSharedDataResult?

    /**
     * Puts new data shared among signed-in clients.
     *
     * @param data data to put.
     * @return the assigned version.
     */
    @RpcMethod
    suspend fun putSharedData(data: ByteString): Long

    /**
     * Gets the latest public data.
     *
     * @param currentVersion the current version of the data or `null` if no current version exists.
     * @return the data and its version or `null` if it's still at [currentVersion].
     */
    @RpcMethod
    suspend fun getPublicData(currentVersion: Long?): WalletClientPublicData?

    /**
     * Gets a list of credential issuers.
     *
     * @return a list of credential issuers.
     */
    @RpcMethod
    suspend fun getCredentialIssuers(): List<CredentialIssuer>

    /**
     * Gets the EULA (End User License Agreement) for the wallet.
     *
     * @param locale the locale to get the EULA for.
     * @return the EULA text.
     */
    @RpcMethod
    suspend fun getEula(locale: String): String

    /**
     * Certifies reader authentication keys generated on the client.
     *
     * @param readerKeys a list of reader keys to certify.
     * @return a list of certifications.
     */
    @RpcMethod
    suspend fun certifyReaderKeys(
        readerKeys: List<KeyAttestation>
    ): List<X509CertChain>

    @RpcMethod
    suspend fun createVerificationLink(
        encryptedVerificationPayload: ByteString
    ): CreateVerificationLinkResult

    @RpcMethod
    suspend fun getVerificationPayload(
        requestId: String
    ): ByteString

    @RpcMethod
    suspend fun submitVerificationResponse(
        requestId: String,
        encryptedResponse: ByteString
    )

    @RpcMethod
    suspend fun getVerificationResponse(
        requestId: String
    ): ByteString?
}

@CborSerializable
data class CreateVerificationLinkResult(
    val requestId: String,
    val link: String
)

@CborSerializable
data class GoogleTokens(
    val idToken: String,
    val accessToken: String
) {
    companion object
}
