package org.multipaz.wallet.android

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quickaccesswallet.GetWalletCardsCallback
import android.service.quickaccesswallet.GetWalletCardsRequest
import android.service.quickaccesswallet.GetWalletCardsResponse
import android.service.quickaccesswallet.QuickAccessWalletService
import android.service.quickaccesswallet.SelectWalletCardRequest
import androidx.annotation.RequiresApi
import kotlinx.coroutines.runBlocking
import org.multipaz.compose.prompt.PresentmentActivity
import org.multipaz.context.applicationContext
import org.multipaz.presentment.PresentmentSource
import org.multipaz.util.Logger
import kotlin.time.Clock

private const val TAG = "WalletQuickAccessWalletService"

@RequiresApi(Build.VERSION_CODES.R)
class WalletQuickAccessWalletService: QuickAccessWalletService() {
    // Unused since we override getTargetActivityPendingIntent()
    override fun onWalletCardsRequested(
        request: GetWalletCardsRequest,
        callback: GetWalletCardsCallback
    ) {
        val response = GetWalletCardsResponse(
            listOf(),
            0
        )
        callback.onSuccess(response)
    }

    // Unused since we override getTargetActivityPendingIntent()
    override fun onWalletCardSelected(p0: SelectWalletCardRequest) {
    }

    // Unused since we override getTargetActivityPendingIntent()
    override fun onWalletDismissed() {
    }

    // Called when the user double-clicks the power button:
    //
    //   Launches PresentmentActivity in document-chooser mode
    //
    override fun getGestureTargetActivityPendingIntent(): PendingIntent {
        val source = runBlocking {
            val t0 = Clock.System.now()
            val source = App.getPresentmentSource()
            val t1 = Clock.System.now()
            Logger.i(TAG, "App initialized in ${(t1 - t0).inWholeMilliseconds} ms")
            source
        }
        return getPendingIntentForLaunchingQuickAccessWallet(
            source = source,
            initiallySelectedDocumentId = null // TODO
        )
    }

    // Called when the user taps the wallet tile in the Quick Settings shade:
    //
    //   Launches MainActivity
    //
    override fun getTargetActivityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            /* context = */ applicationContext,
            /* requestCode = */ 0,
            /* intent = */ Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            },
            /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPendingIntentForLaunchingQuickAccessWallet(
        source: PresentmentSource,
        initiallySelectedDocumentId: String?
    ): PendingIntent {
        return PresentmentActivity.getPendingIntent(
            source = source,
            initiallySelectedDocumentId = initiallySelectedDocumentId,
            openWalletAppPendingIntentFn = { document ->
                PendingIntent.getActivity(
                    /* context = */ applicationContext,
                    /* requestCode = */ 0,
                    /* intent = */ Intent(applicationContext, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                        action = App.ACTION_VIEW_DOCUMENT
                        putExtra("documentId", document.identifier)
                    },
                    /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            },
            preferredServices = listOf(
                ComponentName(applicationContext, WalletMdocNdefService::class.java)
            )
        )
    }
}
