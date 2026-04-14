package org.multipaz.wallet.shared

import kotlinx.io.bytestring.ByteString
import org.multipaz.rpc.annotation.RpcInterface
import org.multipaz.rpc.annotation.RpcMethod

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
     * Gets the EULA the user needs to accept.
     *
     * @param locale BCP-47 language tag for the user's language.
     * @return the EULA, as Markdown.
     */
    @RpcMethod
    suspend fun getEula(locale: String): String
}