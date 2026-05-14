package org.multipaz.wallet.backend;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.annotation.CborSerializable;
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.rpc.annotation.RpcState;
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.securearea.KeyAttestation
import org.multipaz.server.enrollment.ServerIdentity
import org.multipaz.server.enrollment.getServerIdentity
import org.multipaz.util.Logger
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.WalletBackend
import org.multipaz.wallet.shared.WalletBackendIdTokenException
import org.multipaz.wallet.shared.WalletBackendNonceException
import org.multipaz.wallet.shared.GoogleTokens
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private const val TAG = "WalletBackendImpl"

@RpcState(
    endpoint = "wallet_backend",
    creatable = true
)
@CborSerializable
class WalletBackendImpl: WalletBackendBase(), WalletBackend, RpcAuthInspector by RpcAuthBackendDelegate {

    override suspend fun googleIdTokenVerifier(googleIdTokenString: String, expectedNonce: String): String {
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
        // Only verify nonce if it's present in the ID token.
        // GIS initCodeClient (Web Code Flow) doesn't include it.
        if (payload.nonce != null && payload.nonce != expectedNonce) {
            throw WalletBackendNonceException("Nonce mismatch in ID token")
        }
        
        return payload.subject
    }

    override suspend fun googleCodeExchanger(authorizationCode: String, redirectUri: String, expectedNonce: String): Pair<String, String> {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        
        if (BuildConfig.BACKEND_CLIENT_SECRET.isEmpty()) {
            throw Exception("Google Client Secret is not configured on the backend. " +
                "Please set MULTIPAZ_WALLET_BACKEND_CLIENT_SECRET environment variable.")
        }

        val response = GoogleAuthorizationCodeTokenRequest(
            transport,
            jsonFactory,
            BuildConfig.BACKEND_CLIENT_ID,
            BuildConfig.BACKEND_CLIENT_SECRET,
            authorizationCode,
            redirectUri
        ).execute()

        val idTokenString = response.idToken
            ?: throw WalletBackendIdTokenException("No ID token in response")
        
        val userId = googleIdTokenVerifier(idTokenString, expectedNonce)
        return Pair(userId, response.accessToken)
    }

    override suspend fun exchangeCodeForTokensInternal(
        authorizationCode: String,
        redirectUri: String
    ): GoogleTokens {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()

        if (BuildConfig.BACKEND_CLIENT_SECRET.isEmpty()) {
            throw Exception("Google Client Secret is not configured on the backend. " +
                "Please set MULTIPAZ_WALLET_BACKEND_CLIENT_SECRET environment variable.")
        }

        val response = GoogleAuthorizationCodeTokenRequest(
            transport,
            jsonFactory,
            BuildConfig.BACKEND_CLIENT_ID,
            BuildConfig.BACKEND_CLIENT_SECRET,
            authorizationCode,
            redirectUri
        ).execute()

        return GoogleTokens(
            idToken = response.idToken ?: throw WalletBackendIdTokenException("No ID token in response"),
            accessToken = response.accessToken ?: throw Exception("No access token in response")
        )
    }

    override suspend fun getClientId() = RpcAuthContext.getClientId()

    override suspend fun certifyReaderKeys(readerKeys: List<KeyAttestation>): List<X509CertChain> {
        // TODO: if dealing with Android client, verify attestations
        val identity = getServerIdentity(ServerIdentity.READER_ROOT) as AsymmetricKey.X509CertifiedExplicit
        return certifyReaderKeys(
            readerKeys = readerKeys,
            readerRootKey = identity,
        )
    }

    companion object
}
