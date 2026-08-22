package org.multipaz.wallet.android

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.launch
import org.multipaz.nfc.handleUsbDeviceAttached
import org.multipaz.context.initializeApplication
import org.multipaz.util.Logger
import org.multipaz.wallet.shared.BuildConfig

private const val TAG = "MainActivity"


class MainActivity : FragmentActivity() {

    override fun onResume() {
        super.onResume()
        NfcAdapter.getDefaultAdapter(this)?.let { adapter ->
            val cardEmulation = CardEmulation.getInstance(adapter)
            val componentName = ComponentName(this, WalletCombinedNfcService::class.java)
            if (!cardEmulation.unsetPreferredService(this)) {
                Logger.w(TAG, "CardEmulation.unsetPreferredService() returned false")
            }
            if (!cardEmulation.setPreferredService(this, componentName)) {
                Logger.w(TAG, "CardEmulation.setPreferredService() returned false")
            }
            if (!cardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER)) {
                Logger.w(TAG, "CardEmulation.categoryAllowsForegroundPreference(CATEGORY_OTHER) returned false")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeApplication(this.applicationContext)

        enableEdgeToEdge()

        lifecycle.coroutineScope.launch {
            val app = App.getInstance()
            setContent {
                app.Content()
            }
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Logger.i(TAG, "intent: $intent")
        if (intent.action == App.ACTION_VIEW_DOCUMENT) {
            val documentId = intent.getStringExtra("documentId")
            if (documentId != null) {
                lifecycle.coroutineScope.launch {
                    val app = App.getInstance()
                    app.viewDocument(documentId)
                }
            }
        } else if (intent.action == App.ACTION_VIEW_EVENT) {
            val eventId = intent.getStringExtra("eventId").orEmpty()
            lifecycle.coroutineScope.launch {
                val app = App.getInstance()
                app.viewEvent(eventId)
            }
        } else if (intent.action == ACTION_VIEW_PENDING_VERIFICATION) {
            lifecycle.coroutineScope.launch {
                val app = App.getInstance()
                app.viewRequestVerificationScreen()
            }
        } else if (intent.action == Intent.ACTION_VIEW) {
            val url = intent.dataString
            if (url != null) {
                lifecycle.coroutineScope.launch {
                    val app = App.getInstance()
                    app.handleUrl(url)
                }
            }
        } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device = IntentCompat.getParcelableExtra(
                intent,
                UsbManager.EXTRA_DEVICE,
                UsbDevice::class.java
            )
            if (device != null) {
                lifecycle.coroutineScope.launch {
                    val app = App.getInstance()
                    app.externalNfcReaderStore.handleUsbDeviceAttached(device)
                }
            }
        }
    }

    companion object {
        val ACTION_VIEW_PENDING_VERIFICATION = "${BuildConfig.ANDROID_APP_ID}.action.viewPendingVerification"
    }

}
