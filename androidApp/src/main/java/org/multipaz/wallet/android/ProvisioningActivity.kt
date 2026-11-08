package org.multipaz.wallet.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.multipaz.compose.branding.Branding
import org.multipaz.compose.digitalcredentials.CredentialManagerProvisioningActivity
import org.multipaz.context.initializeApplication
import org.multipaz.util.Logger
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ProvisioningActivity"

class ProvisioningActivity : FragmentActivity() {

    private data class OfferRequest(
        val offerUri: String,
        val fromCredentialManager: Boolean,
    )

    private sealed class CredmanOutcome {
        data object Success : CredmanOutcome()
        data object Canceled : CredmanOutcome()
        data class Error(val error: Throwable) : CredmanOutcome()
    }

    private val offerRequest = mutableStateOf<OfferRequest?>(null)
    private val invocationCount = mutableIntStateOf(0)
    private val startFadeOut = mutableStateOf(false)
    private var isFinished = false
    private var credmanOutcome: CredmanOutcome? = null
    private var credmanOutcomeDelivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        disableActivityTransitions()
        super.onCreate(savedInstanceState)

        initializeApplication(this.applicationContext)

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        val request = extractOfferRequest(intent)
        if (request == null) {
            Logger.e(TAG, "Invalid intent: $intent")
            finish()
            return
        }
        Logger.i(TAG, "Handling credential offer ${request.offerUri} (fromCredman=${request.fromCredentialManager})")
        startFadeOut.value = false
        isFinished = false
        credmanOutcome = null
        credmanOutcomeDelivered = false
        offerRequest.value = request
        invocationCount.intValue++

        lifecycle.coroutineScope.launch {
            val app = App.getInstance()
            setContent {
                val currentBranding = Branding.Current.collectAsState().value
                currentBranding.theme {
                    offerRequest.value?.let { req ->
                        key(invocationCount.intValue) {
                            ProvisioningActivityWrapper(
                                window = window,
                                startFadeOut = startFadeOut.value,
                                onFadeOutFinished = {
                                    isFinished = true
                                    deliverCredmanOutcomeIfNeeded()
                                    finish()
                                }
                            ) {
                                app.ProvisioningContent(
                                    offerUri = req.offerUri,
                                    onComplete = {
                                        credmanOutcome = CredmanOutcome.Success
                                        finish()
                                    },
                                    onCanceled = {
                                        credmanOutcome = CredmanOutcome.Canceled
                                        finish()
                                    },
                                    onFailed = { error ->
                                        credmanOutcome = CredmanOutcome.Error(error)
                                        finish()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        disableActivityTransitions()
    }

    override fun finish() {
        if (!isFinished && !startFadeOut.value && offerRequest.value != null) {
            startFadeOut.value = true
        } else {
            super.finish()
            disableActivityTransitions()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            deliverCredmanOutcomeIfNeeded()
        }
        super.onDestroy()
    }

    private fun deliverCredmanOutcomeIfNeeded() {
        val currentRequest = offerRequest.value ?: return
        if (!currentRequest.fromCredentialManager || credmanOutcomeDelivered) {
            return
        }
        credmanOutcomeDelivered = true
        val result = when (val outcome = credmanOutcome) {
            is CredmanOutcome.Success -> CredentialManagerProvisioningActivity.Result.Success
            is CredmanOutcome.Error -> CredentialManagerProvisioningActivity.Result.Error(outcome.error.message)
            is CredmanOutcome.Canceled, null -> CredentialManagerProvisioningActivity.Result.Canceled("Provisioning canceled")
        }
        pendingCredmanResult?.complete(result)
        pendingCredmanResult = null
    }

    private fun disableActivityTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val request = extractOfferRequest(intent)
        if (request != null) {
            Logger.i(TAG, "Handling new intent credential offer ${request.offerUri} (fromCredman=${request.fromCredentialManager})")
            if (offerRequest.value?.fromCredentialManager == true && !credmanOutcomeDelivered) {
                credmanOutcome = CredmanOutcome.Canceled
                deliverCredmanOutcomeIfNeeded()
            }
            setIntent(intent)
            startFadeOut.value = false
            isFinished = false
            credmanOutcome = null
            credmanOutcomeDelivered = false
            offerRequest.value = request
            invocationCount.intValue++
        }
    }

    private fun extractOfferRequest(intent: Intent): OfferRequest? {
        if (intent.hasExtra(EXTRA_OFFER_URI)) {
            val offerUri = intent.getStringExtra(EXTRA_OFFER_URI) ?: return null
            val fromCredman = intent.getBooleanExtra(EXTRA_FROM_CREDENTIAL_MANAGER, false)
            return OfferRequest(
                offerUri = offerUri,
                fromCredentialManager = fromCredman,
            )
        }
        if (intent.action == Intent.ACTION_VIEW) {
            val url = intent.dataString ?: return null
            if ((url.startsWith(OID4VCI_CREDENTIAL_OFFER_URL_SCHEME) || url.startsWith(HAIP_VCI_URL_SCHEME)) &&
                url.indexOf('?') >= 0
            ) {
                return OfferRequest(
                    offerUri = url,
                    fromCredentialManager = false,
                )
            }
        }
        return null
    }

    companion object {
        private const val OID4VCI_CREDENTIAL_OFFER_URL_SCHEME = "openid-credential-offer://"
        private const val HAIP_VCI_URL_SCHEME = "haip-vci://"
        private const val EXTRA_OFFER_URI = "offerUri"
        private const val EXTRA_FROM_CREDENTIAL_MANAGER = "fromCredentialManager"

        private var pendingCredmanResult: CompletableDeferred<CredentialManagerProvisioningActivity.Result>? = null

        suspend fun handleCredmanCredentialOffer(
            context: Context,
            offerUri: String,
        ): CredentialManagerProvisioningActivity.Result {
            val deferred = CompletableDeferred<CredentialManagerProvisioningActivity.Result>()
            pendingCredmanResult?.complete(
                CredentialManagerProvisioningActivity.Result.Canceled("Superseded by another provisioning request")
            )
            pendingCredmanResult = deferred
            val intent = Intent(context, ProvisioningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OFFER_URI, offerUri)
                putExtra(EXTRA_FROM_CREDENTIAL_MANAGER, true)
            }
            context.startActivity(intent)
            return deferred.await()
        }
    }
}

@Composable
private fun ProvisioningActivityWrapper(
    window: android.view.Window,
    startFadeOut: Boolean,
    onFadeOutFinished: () -> Unit,
    content: @Composable () -> Unit
) {
    var startFadeIn by remember { mutableStateOf(false) }
    val fadeInAlpha by animateFloatAsState(
        targetValue = if (startFadeIn) 1.0f else 0.0f,
        animationSpec = tween(
            durationMillis = 500
        )
    )
    val fadeOutAlpha by animateFloatAsState(
        targetValue = if (startFadeOut) 0.0f else 1.0f,
        animationSpec = tween(
            durationMillis = 300
        ),
        finishedListener = {
            if (startFadeOut) {
                onFadeOutFinished()
            }
        }
    )

    // Delay for 250ms to mask the system-animation which slides
    // the activity from the bottom to top.
    LaunchedEffect(Unit) {
        delay(250.milliseconds)
        startFadeIn = true
    }

    if (!startFadeIn) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0f)
        )
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        window.setBackgroundBlurRadius((80.0 * fadeOutAlpha * fadeInAlpha).roundToInt())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fadeOutAlpha * fadeInAlpha)
    ) {
        content()
    }
}
