package org.multipaz.wallet.backend;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.multipaz.cbor.annotation.CborSerializable;
import org.multipaz.rpc.annotation.RpcState;
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.WalletBackend
import org.multipaz.wallet.shared.WalletBackendIdTokenException

@RpcState(
    endpoint = "wallet_backend",
    creatable = true
)
@CborSerializable
class WalletBackendImpl: WalletBackendBase(), WalletBackend, RpcAuthInspector by RpcAuthBackendDelegate {

    override suspend fun googleIdTokenVerifier(googleIdTokenString: String): Pair<String, String> {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val verifier = GoogleIdTokenVerifier.Builder(
            transport,
            jsonFactory
        )
            .setAudience(listOf(BuildConfig.BACKEND_CLIENT_ID))
            .build()

        val idToken = verifier.verify(googleIdTokenString)
            ?: throw WalletBackendIdTokenException("Error validating ID token")

        val payload = idToken.payload
        return Pair(
            payload.nonce,
            payload.subject
        )
    }

    override suspend fun getClientId() = RpcAuthContext.getClientId()

    companion object
}
