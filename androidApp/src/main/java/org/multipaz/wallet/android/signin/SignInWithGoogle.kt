package org.multipaz.wallet.android.signin

import android.accounts.Account
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.ByteString
import org.multipaz.context.applicationContext
import org.multipaz.util.Logger
import org.multipaz.util.toHex
import org.multipaz.wallet.shared.BuildConfig
import java.io.ByteArrayOutputStream
import kotlin.random.Random

private const val TAG = "SignInWithGoogle"

/**
 * Signs a user in, using their Google account.
 *
 * The app should first obtain a nonce from its backend before calling this function.
 *
 * Calling this function will prompt the user to sign in with a Google account.
 *
 * When this completes the app should the returned googleIdToken to the backend for verification.
 *
 * When the user signs out, [signInWithGoogleSignedOut] should be called.
 *
 * This is a wrapper around the platform native Credential Manager, see
 * [this article](https://developer.android.com/identity/sign-in/credential-manager-siwg)
 * for the Android implementation.
 *
 * @param explicitSignIn should be set to `true` if the user explicitly clicked a button to sign into Google. Set
 *   to false if opportunistically signing in an user e.g. the first time the application runs.
 * @param serverClientId the clientId of the server side.
 * @param nonce the nonce to include in the returned googleIdToken.
 * @param httpClientEngineFactory a [HttpClientEngineFactory] used for retrieving the profile photo.
 * @return a pair where the first member is the googleIdToken for the nonce, to be sent to a server for
 *   verification and the second being the sign-in data about the user which can be used in the user interface
 * @throws SignInWithGoogleDismissedException if the user dismissed the sign-in dialog.
 * @throws Throwable if an error occurs.
 */
suspend fun signInWithGoogle(
    explicitSignIn: Boolean,
    serverClientId: String,
    nonce: String,
    httpClientEngineFactory: HttpClientEngineFactory<*>,
): Pair<String, SignInWithGoogleUserData> {
    val credentialManager = CredentialManager.create(applicationContext)

    /*
    if (Build.VERSION.SDK_INT >= 28) {
        val pkg = applicationContext.packageManager
            .getPackageInfo(applicationContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        println("Num content signers: ${pkg.signingInfo!!.apkContentsSigners.size}")
        pkg.signingInfo!!.apkContentsSigners.forEach { signatureInfo ->
            println(
                "digest: ${Crypto.digest(Algorithm.INSECURE_SHA1, signatureInfo.toByteArray()).toHex(byteDivider = ":")}"
            )
        }
    }
     */

    val signInOption = if (explicitSignIn) {
        GetSignInWithGoogleOption.Builder(
            serverClientId = serverClientId
        ).setNonce(nonce)
            .build()
    } else {
        GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()
    }

    val request: GetCredentialRequest = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    try {
        val result = credentialManager.getCredential(
            request = request,
            context = applicationContext,
        )
        return handleSignIn(result, httpClientEngineFactory)
    } catch (e: GetCredentialCancellationException) {
        throw SignInWithGoogleDismissedException("User dismissed dialog", e)
    } catch (e: GetCredentialException) {
        throw IllegalStateException("Error signing in", e)
    }
}

private suspend fun handleSignIn(
    result: GetCredentialResponse,
    httpClientEngineFactory: HttpClientEngineFactory<*>,
): Pair<String, SignInWithGoogleUserData> {
    // Handle the successfully returned credential.
    val credential = result.credential
    val responseJson: String

    when (credential) {
        // GoogleIdToken credential
        is CustomCredential -> {
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    // Use googleIdTokenCredential and extract the ID to validate and
                    // authenticate on your server.
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val profilePicture = googleIdTokenCredential.profilePictureUri?.let {
                        val httpClient = HttpClient(httpClientEngineFactory) {
                            install(HttpTimeout)
                        }
                        val response = httpClient.get(it.toString())
                        ByteString(response.body<ByteArray>())
                    }
                    return Pair(
                        googleIdTokenCredential.idToken,
                        SignInWithGoogleUserData(
                            id = googleIdTokenCredential.id,
                            givenName = googleIdTokenCredential.givenName,
                            familyName = googleIdTokenCredential.familyName,
                            displayName = googleIdTokenCredential.displayName,
                            profilePicture = profilePicture
                        )
                    )
                } catch (e: GoogleIdTokenParsingException) {
                    Logger.e(TAG, "Received an invalid google id token response", e)
                    throw IllegalStateException("Received an invalid google id token response", e)
                }
            } else {
                Logger.e(TAG, "Unexpected type of credential")
                throw IllegalStateException("Unexpected type of credential returned")
            }
        }
        else -> {
            Logger.e(TAG, "Unexpected type of credential")
            throw IllegalStateException("Unexpected type of credential returned")
        }
    }
}

/**
 * Should be called when a user previously signed in via [signInWithGoogle] signs out.
 */
suspend fun signInWithGoogleSignedOut() {
    val credentialManager = CredentialManager.create(applicationContext)
    credentialManager.clearCredentialState(
        ClearCredentialStateRequest(
            requestType = ClearCredentialStateRequest.TYPE_CLEAR_CREDENTIAL_STATE
        )
    )
}

sealed class DriveAuthResult {
    data class Success(val accessToken: String) : DriveAuthResult()
    data class NeedsConsent(val intentSenderRequest: IntentSenderRequest) : DriveAuthResult()
    object Error : DriveAuthResult()
}

/**
 * Suspends to check or request authorization for the Google Drive App Data folder.
 *
 * @param accountEmail The user's Google account email, if known, to bypass the account picker.
 * @return [DriveAuthResult.Success] if permission is already granted, [DriveAuthResult.NeedsConsent]
 *   if the UI needs to prompt the user, or [DriveAuthResult.Error] if the request failed.
 */
suspend fun requestDriveAppDataScope(accountEmail: String? = null): DriveAuthResult {
    val requestedScopes = listOf(Scope("https://www.googleapis.com/auth/drive.appdata"))

    val requestBuilder = AuthorizationRequest.builder()
        .setRequestedScopes(requestedScopes)

    accountEmail?.let {
        requestBuilder.setAccount(Account(it, "com.google"))
    }

    return try {
        val result = Identity.getAuthorizationClient(applicationContext)
            .authorize(requestBuilder.build())
            .await()

        if (result.hasResolution() && result.pendingIntent != null) {
            // The user needs to explicitly grant permission via the consent UI
            val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build()
            DriveAuthResult.NeedsConsent(intentSenderRequest)
        } else {
            // The user has already granted this permission previously
            DriveAuthResult.Success(result.accessToken!!)
        }
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to authorize Drive scopes", e)
        DriveAuthResult.Error
    }
}

class SignInWithGoogle internal constructor(
    private val onSignIn: suspend (
        explicitSignIn: Boolean,
        serverClientId: String,
        nonce: String,
        httpClientEngineFactory: HttpClientEngineFactory<*>,
        resetEncryptionKey: Boolean
    ) -> SignInWithGoogleResult,

    private val onSignedOut: suspend () -> Unit,

    private val onCorruptEncryptionKey: suspend (accountEmail: String) -> Unit
) {
    suspend fun signIn(
        explicitSignIn: Boolean,
        serverClientId: String,
        nonce: String,
        httpClientEngineFactory: HttpClientEngineFactory<*>,
        resetEncryptionKey: Boolean
    ): SignInWithGoogleResult {
        return onSignIn(
            explicitSignIn,
            serverClientId,
            nonce,
            httpClientEngineFactory,
            resetEncryptionKey
        )
    }

    suspend fun corruptEncryptionKey(accountEmail: String) {
        return onCorruptEncryptionKey(accountEmail)
    }

    suspend fun signedOut() {
        onSignedOut()
    }
}

data class SignInWithGoogleResult(
    val googleIdTokenString: String,
    val signInData: SignInWithGoogleUserData,
    val walletServerEncryptionKey: ByteString
)

@Composable
fun rememberSignInWithGoogle(): SignInWithGoogle {
    val context = LocalContext.current

    class AuthDeferredHolder {
        var deferred: CompletableDeferred<String?>? = null
    }
    val deferredHolder = remember { AuthDeferredHolder() }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        var accessToken: String? = null

        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val authResult = Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(result.data)

                accessToken = authResult.accessToken
                Logger.i(TAG, "Drive permission granted by user. Token extracted.")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to extract Drive token from intent", e)
            }
        } else {
            Logger.w(TAG, "Drive permission denied or dialog canceled")
        }

        deferredHolder.deferred?.complete(accessToken)
        deferredHolder.deferred = null
    }

    return remember {
        SignInWithGoogle(
            onSignIn = { explicitSignIn, serverClientId, nonce, httpClientEngineFactory, resetEncryptionKey ->
                Logger.i(TAG, "launch resetEncryptionKey: $resetEncryptionKey")

                val (googleIdTokenString, signInData) = signInWithGoogle(
                    explicitSignIn = explicitSignIn,
                    serverClientId = serverClientId,
                    nonce = nonce,
                    httpClientEngineFactory = httpClientEngineFactory,
                )

                val authResult = requestDriveAppDataScope(
                    accountEmail = signInData.id
                )

                val driveAccessToken: String

                when (authResult) {
                    is DriveAuthResult.Success -> {
                        Logger.i(TAG, "Drive permission already granted")
                        driveAccessToken = authResult.accessToken
                    }
                    is DriveAuthResult.NeedsConsent -> {
                        // Create the deferred and store it
                        val deferred = CompletableDeferred<String?>()
                        deferredHolder.deferred = deferred

                        // Launch the system consent UI
                        driveAuthLauncher.launch(authResult.intentSenderRequest)

                        // Suspend until the launcher callback fires and gives us the token
                        val tokenFromIntent = deferred.await()

                        if (tokenFromIntent == null) {
                            Logger.w(TAG, "Aborting sign-in because Drive permission was denied")
                            signInWithGoogleSignedOut()
                            error("User denied Google Drive permission")
                        } else {
                            driveAccessToken = tokenFromIntent
                        }
                    }
                    is DriveAuthResult.Error -> {
                        Logger.e(TAG, "An error occurred checking Drive permissions")
                        signInWithGoogleSignedOut()
                        error("Drive auth error")
                    }
                }

                val encryptionKey = try {
                    retrieveOrCreateEncryptionKey(
                        accessToken = driveAccessToken,
                        resetEncryptionKey = resetEncryptionKey
                    )
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to retrieve or create the encryption key", e)
                    signInWithGoogleSignedOut()
                    error("Encryption key generation/retrieval failed")
                }

                return@SignInWithGoogle SignInWithGoogleResult(
                    googleIdTokenString = googleIdTokenString,
                    signInData = signInData,
                    walletServerEncryptionKey = ByteString(encryptionKey)
                )
            },

            onSignedOut = {
                signInWithGoogleSignedOut()
            },

            onCorruptEncryptionKey = { accountEmail ->
                val authResult = requestDriveAppDataScope(
                    accountEmail = accountEmail
                )

                val driveAccessToken: String
                when (authResult) {
                    is DriveAuthResult.Success -> {
                        Logger.i(TAG, "Drive permission already granted")
                        driveAccessToken = authResult.accessToken
                    }
                    is DriveAuthResult.NeedsConsent -> {
                        // Create the deferred and store it
                        val deferred = CompletableDeferred<String?>()
                        deferredHolder.deferred = deferred

                        // Launch the system consent UI
                        driveAuthLauncher.launch(authResult.intentSenderRequest)

                        // Suspend until the launcher callback fires and gives us the token
                        val tokenFromIntent = deferred.await()

                        if (tokenFromIntent == null) {
                            Logger.w(TAG, "Aborting sign-in because Drive permission was denied")
                            signInWithGoogleSignedOut()
                            error("User denied Google Drive permission")
                        } else {
                            driveAccessToken = tokenFromIntent
                        }
                    }
                    is DriveAuthResult.Error -> {
                        Logger.e(TAG, "An error occurred checking Drive permissions")
                        signInWithGoogleSignedOut()
                        error("Drive auth error")
                    }
                }

                val encryptionKey = try {
                    corruptEncryptionKey(
                        accessToken = driveAccessToken,
                    )
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to retrieve or create the encryption key", e)
                    signInWithGoogleSignedOut()
                    error("Encryption key generation/retrieval failed")
                }
            },
        )
    }
}

/**
 * Initializes the Google Drive API client using the access token obtained from AuthorizationClient.
 */
private fun buildDriveService(accessToken: String): Drive {
    val transport = NetHttpTransport()
    val jsonFactory = GsonFactory.getDefaultInstance()

    // Pass the bearer token into every request
    val requestInitializer = HttpRequestInitializer { request ->
        request.headers.authorization = "Bearer $accessToken"
    }

    return Drive.Builder(transport, jsonFactory, requestInitializer)
        .setApplicationName(BuildConfig.APP_NAME)
        .build()
}

/**
 * Retrieves the 32-byte symmetrical encryption key from Drive.
 * If it doesn't exist, generates a new one and saves it to the App Data folder.
 */
private suspend fun retrieveOrCreateEncryptionKey(
    accessToken: String,
    resetEncryptionKey: Boolean
): ByteArray = withContext(Dispatchers.IO) {
    val driveService = buildDriveService(accessToken) // The helper we wrote earlier
    val fileName = "WalletServerEncryptionKey"

    val fileList = driveService.files().list()
        .setSpaces("appDataFolder")
        .setQ("name='$fileName'")
        .setFields("files(id, name)")
        .execute()

    val existingFile = fileList.files.firstOrNull()
    if (resetEncryptionKey) {
        if (existingFile != null) {
            Logger.i(TAG, "Replacing existing encryption key in Drive.")
        }
    } else {
        if (existingFile != null) {
            Logger.i(TAG, "Found existing encryption key in Drive.")
            val outputStream = ByteArrayOutputStream()
            driveService.files().get(existingFile.id).executeMediaAndDownloadTo(outputStream)

            val keyBytes = outputStream.toByteArray()
            if (keyBytes.size == 32) {
                return@withContext keyBytes
            } else {
                Logger.w(TAG, "Existing key was not 32 bytes (size: ${keyBytes.size}). Regenerating.")
                // Fall through to regenerate
            }
        }
    }

    // 2. Generate a new 32-byte key
    Logger.i(TAG, "Creating new 32-byte encryption key.")
    val newKeyBytes = Random.nextBytes(32)

    val mediaContent = ByteArrayContent("application/octet-stream", newKeyBytes)

    // 3. Save it to Drive
    if (existingFile != null) {
        // Overwrite corrupted file
        driveService.files().update(existingFile.id, null, mediaContent).execute()
    } else {
        // Create brand new file
        val metadata = File().apply {
            name = fileName
            parents = listOf("appDataFolder")
        }
        driveService.files().create(metadata, mediaContent).execute()
    }
    Logger.i(TAG, "Woot resetEncryptionKey=$resetEncryptionKey newKeyBytes=${newKeyBytes.toHex()}")
    return@withContext newKeyBytes
}

/**
 * Generates a new 32-byte symmetrical encryption key and stores it in Drive.
 */
private suspend fun corruptEncryptionKey(
    accessToken: String,
) = withContext(Dispatchers.IO) {
    val driveService = buildDriveService(accessToken)
    val fileName = "WalletServerEncryptionKey"

    val fileList = driveService.files().list()
        .setSpaces("appDataFolder")
        .setQ("name='$fileName'")
        .setFields("files(id, name)")
        .execute()

    val existingFile = fileList.files.firstOrNull()

    Logger.i(TAG, "Creating new 32-byte encryption key.")
    val newKeyBytes = Random.nextBytes(32)

    val mediaContent = ByteArrayContent("application/octet-stream", newKeyBytes)

    // 3. Save it to Drive
    if (existingFile != null) {
        // Overwrite corrupted file
        driveService.files().update(existingFile.id, null, mediaContent).execute()
    } else {
        // Create brand new file
        val metadata = File().apply {
            name = fileName
            parents = listOf("appDataFolder")
        }
        driveService.files().create(metadata, mediaContent).execute()
    }
}
