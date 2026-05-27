package org.multipaz.wallet.android

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.multipaz.compose.branding.Branding
import org.multipaz.context.initializeApplication
import org.multipaz.util.Logger
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "MdocUrlVerificationActivity"

class MdocUrlVerificationActivity : FragmentActivity() {

    private val mdocUrl = mutableStateOf<String?>(null)
    private val startFadeOut = mutableStateOf(false)
    private var isFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        disableActivityTransitions()
        super.onCreate(savedInstanceState)

        initializeApplication(this.applicationContext)

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        val url = intent.dataString
        if (url == null || !url.startsWith("mdoc:")) {
            Logger.e(TAG, "Invalid intent: $intent")
            finish()
            return
        }
        mdocUrl.value = url

        lifecycle.coroutineScope.launch {
            val app = App.getInstance()
            setContent {
                val currentBranding = Branding.Current.collectAsState().value
                currentBranding.theme {
                    mdocUrl.value?.let { url ->
                        key(url) {
                            MdocUrlVerificationActivityWrapper(
                                window = window,
                                startFadeOut = startFadeOut.value,
                                onFadeOutFinished = {
                                    isFinished = true
                                    finish()
                                }
                            ) {
                                app.MdocUrlVerificationContent(url)
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
        if (!isFinished && !startFadeOut.value) {
            startFadeOut.value = true
        } else {
            super.finish()
            disableActivityTransitions()
        }
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
        setIntent(intent)
        val url = intent.dataString
        if (url != null && url.startsWith("mdoc:")) {
            mdocUrl.value = url
        }
    }
}

@Composable
private fun MdocUrlVerificationActivityWrapper(
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
