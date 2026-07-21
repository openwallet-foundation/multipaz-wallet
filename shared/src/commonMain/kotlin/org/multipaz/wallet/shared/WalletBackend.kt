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

    /**
     * Signs in the client using a Google ID token string.
     *
     * @param nonce the nonce obtained from [getNonce].
     * @param googleIdTokenString the Google ID token string.
     * @param walletServerEncryptionKeySha256 SHA-256 hash of the shared wallet server encryption key.
     * @param resetSharedData if `true`, resets any existing server-side shared data for the user.
     * @param initialSharedData initial data to store if creating shared data.
     * @param clientType the platform type of the client.
     */
    @RpcMethod
    suspend fun signIn(
        nonce: String,
        googleIdTokenString: String,
        walletServerEncryptionKeySha256: ByteString,
        resetSharedData: Boolean,
        initialSharedData: ByteString,
        clientType: ClientType
    )

    /**
     * Signs in the client using a Google authorization code.
     *
     * @param nonce the nonce obtained from [getNonce].
     * @param authorizationCode OAuth 2.0 authorization code from Google.
     * @param redirectUri redirect URI used during authorization code request.
     * @param walletServerEncryptionKeySha256 SHA-256 hash of the shared wallet server encryption key.
     * @param resetSharedData if `true`, resets any existing server-side shared data for the user.
     * @param initialSharedData initial data to store if creating shared data.
     * @param clientType the platform type of the client.
     * @return the access token for Drive access.
     */
    @RpcMethod
    suspend fun signInWithGoogleCode(
        nonce: String,
        authorizationCode: String,
        redirectUri: String,
        walletServerEncryptionKeySha256: ByteString,
        resetSharedData: Boolean,
        initialSharedData: ByteString,
        clientType: ClientType
    ): String

    @RpcMethod
    suspend fun exchangeCodeForTokens(
        nonce: String,
        authorizationCode: String,
        redirectUri: String
    ): GoogleTokens

    /**
     * Gets the client identifier for the current calling device session.
     *
     * @return the client ID string.
     */
    @RpcMethod
    suspend fun getClientId(): String

    @RpcMethod
    suspend fun signOut()

    /**
     * Gets all active device sessions for the signed-in user.
     *
     * @return a list of active [Session] instances.
     * @throws WalletBackendNotSignedInException if not signed in.
     */
    @RpcMethod
    suspend fun getSessions(): List<Session>

    /**
     * Signs out a specific device session by its [clientId].
     *
     * @param clientId the identifier of the client session to sign out.
     * @throws WalletBackendNotSignedInException if not signed in.
     */
    @RpcMethod
    suspend fun signOutSession(clientId: String)

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
        encryptedVerificationPayload: ByteString,
        expirationDurationAfterCreatedSeconds: Long? = null
    ): CreateVerificationLinkResult

    @RpcMethod
    suspend fun getVerificationLinkOrigin(): String

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
    suspend fun deleteVerificationRequest(
        requestId: String
    )

    @RpcMethod
    suspend fun deleteVerificationResponse(
        requestId: String
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
