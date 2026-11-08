package org.multipaz.wallet.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.context.initializeApplication
import org.multipaz.util.Logger

private const val TAG = "OAuthRedirectActivity"

/**
 * Trampoline activity for handling OAuth redirect AppLinks (`https://<applinkHost>/redirect`)
 * during OpenID4VCI provisioning.
 *
 * Forwards the redirect URL to [org.multipaz.wallet.client.WalletClient.processAppLinkInvocation]
 * and brings the activity that launched the Custom Tab ([MainActivity] or [ProvisioningActivity])
 * back to the top of its task stack with [Intent.FLAG_ACTIVITY_CLEAR_TOP], dismissing the Custom
 * Tab without switching to the wrong task.
 */
class OAuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)

        val url = intent.dataString
        if (url != null) {
            Logger.i(TAG, "Handling OAuth redirect URL $url")
            CoroutineScope(Dispatchers.Default).launch {
                val app = App.getInstance()
                if (url.startsWith(app.walletClient.appLinkBaseUrl)) {
                    app.walletClient.processAppLinkInvocation(url)
                } else {
                    Logger.e(TAG, "Unhandled redirect URL: '$url'")
                }
            }
        }

        targetActivityClass?.let { targetClass ->
            val targetIntent = Intent(this, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(targetIntent)
        }
        finish()
    }

    companion object {
        @Volatile
        var targetActivityClass: Class<out Activity>? = null
    }
}
