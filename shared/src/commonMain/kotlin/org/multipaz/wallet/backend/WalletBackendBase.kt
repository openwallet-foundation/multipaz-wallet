package org.multipaz.wallet.backend

import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.crypto.X509Cert
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.getTable
import org.multipaz.storage.NoRecordStorageException
import org.multipaz.storage.StorageTableSpec
import org.multipaz.trustmanagement.TrustEntry
import org.multipaz.trustmanagement.TrustEntryX509Cert
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import org.multipaz.wallet.shared.CredentialIssuer
import org.multipaz.wallet.shared.CredentialIssuerOpenID4VCI
import org.multipaz.wallet.shared.GetSharedDataResult
import org.multipaz.wallet.shared.WalletClientPublicData
import org.multipaz.wallet.shared.WalletBackend
import org.multipaz.wallet.shared.WalletBackendEncryptionKeyMismatchException
import org.multipaz.wallet.shared.WalletBackendIdTokenException
import org.multipaz.wallet.shared.WalletBackendNonceException
import org.multipaz.wallet.shared.WalletBackendNotSignedInException
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val TAG = "WalletBackendBase"

abstract class WalletBackendBase: WalletBackend {
    /**
     * Validates a Google ID token.
     *
     * @param googleIdTokenString the ID token from Google, encoded as a string.
     * @throws WalletBackendIdTokenException if the ID token didn't verify
     * @return the nonce and the ID that was verified.
     */
    abstract suspend fun googleIdTokenVerifier(googleIdTokenString: String): Pair<String, String>

    abstract suspend fun getClientId(): String

    override suspend fun getNonce(): String {
        val nonceTable = BackendEnvironment.getTable(nonceTableSpec)
        val nonce = Random.nextBytes(16).toBase64Url()
        nonceTable.insert(
            key = nonce,
            data = ByteString(),
            expiration = Clock.System.now() + NONCE_EXPIRATION_TIME
        )
        return nonce
    }

    override suspend fun signIn(
        nonce: String,
        googleIdTokenString: String,
        walletServerEncryptionKeySha256: ByteString,
        resetSharedData: Boolean,
        initialSharedData: ByteString
    ) {
        val nonceTable = BackendEnvironment.getTable(nonceTableSpec)
        if (nonceTable.get(key = nonce) == null) {
            throw WalletBackendNonceException("Unknown nonce")
        }

        val (extractedNonce, googleUserId) = googleIdTokenVerifier(googleIdTokenString)
        if (extractedNonce != nonce) {
            throw WalletBackendNonceException("Nonce mismatch")
        }

        val walletClient = loadRemoteWalletClient()
        val signedInUser = GoogleSignedInUser(
            id = googleUserId
        )
        // Could be the user is already signed in, silently ignore that since failing
        // here could cause bugs where the wallet client stays signed out because this
        // method fails.
        //
        if (walletClient.signedInUser != null) {
            Logger.w(
                TAG, "Client is already signed in with ${walletClient.signedInUser}" +
                        " - we're ignoring that and signing them in with $signedInUser"
            )
        }
        val newWalletClient = walletClient.copy(
            signedInUser = signedInUser
        )

        if (resetSharedData) {
            Logger.i(TAG, "As requested, deleting shared data for $signedInUser")
            deleteSharedData(
                sharedDataKey = signedInUser.sharedDataKey,
            )
        }

        // This here is to check that the client passed in the right walletServerEncryptionKeySha256
        val sharedData = loadOrCreateSharedData(
            sharedDataKey = signedInUser.sharedDataKey,
            walletServerEncryptionKeySha256 = walletServerEncryptionKeySha256,
            initialSharedData = initialSharedData
        )

        saveRemoteWalletClient(newWalletClient)
    }

    override suspend fun signOut() {
        val walletClient = loadRemoteWalletClient()
        if (walletClient.signedInUser == null) {
            throw WalletBackendNotSignedInException("User isn't signed in")
        }
        val newWalletClient = walletClient.copy(
            signedInUser = null
        )
        saveRemoteWalletClient(newWalletClient)
    }

    private suspend fun loadRemoteWalletClient(): RemoteWalletClient {
        val clientId = getClientId()
        val walletClientsTable = BackendEnvironment.getTable(walletClientsTableSpec)
        val walletClientEncoded = walletClientsTable.get(
            key = clientId
        )
        if (walletClientEncoded != null) {
            return RemoteWalletClient.fromCbor(walletClientEncoded.toByteArray())
        }
        val remoteWalletClient = RemoteWalletClient(
            clientId = clientId,
            signedInUser = null
        )
        saveRemoteWalletClient(remoteWalletClient)
        return remoteWalletClient
    }

    private suspend fun saveRemoteWalletClient(remoteWalletClient: RemoteWalletClient) {
        val walletClientsTable = BackendEnvironment.getTable(walletClientsTableSpec)
        try {
            walletClientsTable.update(
                key = remoteWalletClient.clientId,
                data = ByteString(remoteWalletClient.toCbor())
            )
        } catch (_: NoRecordStorageException) {
            walletClientsTable.insert(
                key = remoteWalletClient.clientId,
                data = ByteString(remoteWalletClient.toCbor())
            )
        }
    }

    private suspend fun deleteSharedData(
        sharedDataKey: String,
    ): Boolean {
        val sharedDataTable = BackendEnvironment.getTable(sharedDataTableSpec)
        return sharedDataTable.delete(
            key = sharedDataKey
        )
    }

    private suspend fun loadOrCreateSharedData(
        sharedDataKey: String,
        walletServerEncryptionKeySha256: ByteString? = null,
        initialSharedData: ByteString? = null
    ): SharedData {
        val sharedDataTable = BackendEnvironment.getTable(sharedDataTableSpec)
        val sharedDataEncoded = sharedDataTable.get(
            key = sharedDataKey
        )
        if (sharedDataEncoded != null) {
            val sharedData = SharedData.fromCbor(sharedDataEncoded.toByteArray())
            if (walletServerEncryptionKeySha256 != null) {
                if (sharedData.walletServerEncryptionKeySha256 != walletServerEncryptionKeySha256) {
                    throw WalletBackendEncryptionKeyMismatchException("Wallet server encryption key isn't what was expected")
                }
            }
            return sharedData
        }
        check(walletServerEncryptionKeySha256 != null) {
            "Need walletServerEncryptionKeySha256 to create a new record"
        }
        check(initialSharedData != null) {
            "Need initialSharedData to create a new record"
        }
        val sharedData = SharedData(
            walletServerEncryptionKeySha256 = walletServerEncryptionKeySha256,
            version = 0L,
            data = initialSharedData,
        )
        saveSharedData(sharedDataKey, sharedData)
        return sharedData
    }

    private suspend fun saveSharedData(sharedDataKey: String, sharedData: SharedData) {
        val sharedDataTable = BackendEnvironment.getTable(sharedDataTableSpec)
        try {
            sharedDataTable.update(
                key = sharedDataKey,
                data = ByteString(sharedData.toCbor())
            )
        } catch (_: NoRecordStorageException) {
            sharedDataTable.insert(
                key = sharedDataKey,
                data = ByteString(sharedData.toCbor())
            )
        }
    }

    override suspend fun getSharedData(currentVersion: Long): GetSharedDataResult? {
        val walletClient = loadRemoteWalletClient()
        if (walletClient.signedInUser == null) {
            throw WalletBackendNotSignedInException("User is not signed in")
        }
        val sharedData = loadOrCreateSharedData(walletClient.signedInUser.sharedDataKey)
        if (currentVersion == sharedData.version) {
            return null
        }
        if (currentVersion > sharedData.version) {
            Logger.w(
                TAG,
                "Client claimed it has version $currentVersion but our latest version is ${sharedData.version}"
            )
        }
        Logger.i(
            TAG,
            "${walletClient.signedInUser.sharedDataKey}: " +
                    "Returning ${sharedData.data.size.asByteSize} bytes " +
                    "of data with version ${sharedData.version}"
        )
        return GetSharedDataResult(
            version = sharedData.version,
            data = sharedData.data
        )
    }

    override suspend fun putSharedData(data: ByteString): Long {
        val walletClient = loadRemoteWalletClient()
        if (walletClient.signedInUser == null) {
            throw WalletBackendNotSignedInException("User is not signed in")
        }
        val sharedData = loadOrCreateSharedData(walletClient.signedInUser.sharedDataKey)
        val newVersion = sharedData.version + 1L
        val newSharedData = sharedData.copy(
            version = newVersion,
            data = data
        )
        saveSharedData(walletClient.signedInUser.sharedDataKey, newSharedData)
        Logger.i(
            TAG,
            "${walletClient.signedInUser.sharedDataKey}: " +
                    "Storing ${newSharedData.data.size.asByteSize} bytes " +
                    "of data with version $newVersion"
        )
        return newVersion
    }

    private var latestPublicData: WalletClientPublicData? = null

    private suspend fun getPublicData(): WalletClientPublicData {
        latestPublicData?.let { return it }

        val configuration = BackendEnvironment.getInterface(Configuration::class)!!
        val publicData = configuration.getValue("public_data")?.let {
            Json.parseToJsonElement(it) as JsonObject
        } ?: throw IllegalStateException("Public data is not configured")

        val versionString = publicData.string("version")
            ?: throw IllegalStateException("No version for public_data")
        val version = versionString.replace("_", "").toLong(10)

        latestPublicData = WalletClientPublicData(
            version = version,
            trustedIssuers = publicData.trustEntries("trusted_issuers"),
            trustedReaders = publicData.trustEntries("trusted_readers"),
        )

        return latestPublicData!!
    }

    override suspend fun getPublicData(currentVersion: Long?): WalletClientPublicData? {
        val publicData = getPublicData()
        if (currentVersion == publicData.version) {
            return null
        }
        return publicData
    }

    override suspend fun getCredentialIssuers(): List<CredentialIssuer> {
        val configuration = BackendEnvironment.getInterface(Configuration::class)!!
        val provisioning = configuration.getValue("provisioning")?.let {
            Json.parseToJsonElement(it) as JsonObject
        } ?: throw IllegalStateException("Trust list not configured")
        return provisioning.credentialIssuers("credential_issuers")
    }

    override suspend fun getEula(locale: String): String {
        val configuration = BackendEnvironment.getInterface(Configuration::class)!!
        val eula = configuration.getValue("eula")?.let {
            Json.parseToJsonElement(it) as JsonObject
        } ?: throw IllegalStateException("EULA not configured")
        // TODO: support multiple locales
        return eula.string("en")
            ?: throw IllegalStateException("EULA not configured")
    }

    companion object {
        private val NONCE_EXPIRATION_TIME = 5.minutes

        private val nonceTableSpec = StorageTableSpec(
            name = "Nonces",
            supportPartitions = false,
            supportExpiration = true
        )

        private val walletClientsTableSpec = StorageTableSpec(
            name = "WalletClients",
            supportExpiration = false,
            supportPartitions = false
        )

        private val sharedDataTableSpec = StorageTableSpec(
            name = "SharedData",
            supportExpiration = false,
            supportPartitions = false
        )
    }

    private val Int.asByteSize: String
        get() = this.toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()

    private suspend fun JsonObject?.bool(name: String, default: Boolean = false): Boolean {
        val value = this?.get(name) ?: return default
        if (value !is JsonPrimitive || value.isString) {
            throw IllegalStateException("$name is not a boolean")
        }
        return when (value.content) {
            "true" -> true
            "false" -> false
            else -> throw IllegalStateException("$name is not a boolean")
        }
    }

    private suspend fun JsonObject?.string(name: String, default: String? = ""): String? {
        val value = this?.get(name) ?: return default
        if (value !is JsonPrimitive || !value.isString) {
            throw IllegalStateException("$name is not a string")
        }
        return value.content
    }

    private suspend fun JsonObject?.trustEntries(name: String): List<TrustEntry> {
        val value = this?.get(name) ?: return listOf()
        if (value !is JsonArray) {
            throw IllegalStateException("$name is not an array")
        }
        return buildList {
            value.forEachIndexed { index, item ->
                if (item !is JsonObject) {
                    throw IllegalStateException("$name must contain list of objects")
                }
                val displayName = item.string("display_name")
                    ?: throw IllegalStateException("$name must have display_name set")
                val testOnly = item.bool("test_only")
                val displayIconUrl = item.string("display_icon_url")
                val certificate = item.string("certificate")
                    ?: throw IllegalStateException("$name must have certificate set")
                // TODO: also support RICALs and VICALs in the future

                add(
                    TrustEntryX509Cert(
                        identifier = "${name}_${index}",
                        metadata = TrustMetadata(
                            // TODO: support reading more fields
                            displayName = displayName,
                            displayIconUrl = displayIconUrl,
                            testOnly = testOnly
                        ),
                        certificate = X509Cert.fromPem(certificate)
                    )
                )
            }
        }
    }

    private suspend fun JsonObject?.credentialIssuers(name: String): List<CredentialIssuer> {
        val value = this?.get(name) ?: return listOf()
        if (value !is JsonArray) {
            throw IllegalStateException("$name is not an array")
        }
        // TODO: support localization
        return buildList {
            value.forEachIndexed { index, item ->
                if (item !is JsonObject) {
                    throw IllegalStateException("$name must contain list of objects")
                }
                val type = item.string("type")
                val name = item.string("name")
                    ?: throw IllegalStateException("$name must have name set")
                val iconUrl = item.string("icon_url")
                    ?: throw IllegalStateException("$name must have icon_url set")
                val issuer = when (type) {
                    "openid4vci" -> {
                        CredentialIssuerOpenID4VCI(
                            name = name,
                            iconUrl = iconUrl,
                            url = item.string("url")
                                ?: throw IllegalStateException("OpenID4VCI issuer must have url set"),
                            id = item.string("id", null)
                        )
                    }

                    else -> {
                        throw IllegalStateException("Unexpected credential issuer with type $type")
                    }
                }
                add(issuer)
            }
        }
    }

    private suspend fun JsonObject?.byteStringSet(name: String): Set<ByteString> {
        val value = this?.get(name) ?: return setOf()
        if (value !is JsonArray) {
            throw IllegalStateException("$name is not an array")
        }
        return buildSet {
            for (item in value) {
                if (item !is JsonPrimitive || !item.isString) {
                    throw IllegalStateException("$name must contain list of strings")
                }
                // allow both base64url and hex encoding (common for certificate hashes)
                val bytes = if (item.content.contains(':')) {
                    item.jsonPrimitive.content.split(':').map { byteCode ->
                        byteCode.toInt(16).toByte()
                    }.toByteArray()
                } else {
                    item.content.fromBase64Url()
                }
                add(ByteString(bytes))
            }
        }
    }

    private suspend fun JsonObject?.stringSet(name: String): Set<String> {
        val value = this?.get(name) ?: return setOf()
        if (value !is JsonArray) {
            throw IllegalStateException("$name is not an array")
        }
        return buildSet {
            for (item in value) {
                if (item !is JsonPrimitive || !item.isString) {
                    throw IllegalStateException("$name must contain list of strings")
                }
                add(item.content)
            }
        }
    }
}