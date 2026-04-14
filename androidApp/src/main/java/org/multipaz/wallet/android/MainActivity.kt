package org.multipaz.wallet.android

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.context.initializeApplication
import org.multipaz.util.Logger

private const val TAG = "MainActivity"


class MainActivity : FragmentActivity() {

    override fun onResume() {
        super.onResume()
        NfcAdapter.getDefaultAdapter(this)?.let { adapter ->
            val cardEmulation = CardEmulation.getInstance(adapter)
            val componentName = ComponentName(this, WalletMdocNdefService::class.java)
            if (!cardEmulation.setPreferredService(this, componentName)) {
                Logger.w(TAG, "CardEmulation.setPreferredService() returned false")
            }
            if (!cardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER)) {
                Logger.w(TAG, "CardEmulation.categoryAllowsForegroundPreference(CATEGORY_OTHER) returned false")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this)?.let {
            val cardEmulation = CardEmulation.getInstance(it)
            if (!cardEmulation.unsetPreferredService(this)) {
                Logger.w(TAG, "CardEmulation.unsetPreferredService() returned false")
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
        } else if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val mimeType = contentResolver.getType(uri) ?: intent.type
                val fileName = getFileNameFromUri(uri)
                if (mimeType == "application/vnd.multipaz.mpzpass" || fileName?.endsWith(".mpzpass", ignoreCase = true) == true) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                val fileContent: ByteArray = inputStream.readBytes()
                                val app = App.getInstance()
                                println("Importing pass")
                                app.importMpzPass(fileContent)
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "Error reading file content from URI", e)
                        }
                    }
                    return
                }
            }

            val url = intent.dataString
            if (url != null) {
                lifecycle.coroutineScope.launch {
                    val app = App.getInstance()
                    app.handleUrl(url)
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        // If it's a content URI, query the ContentResolver for the display name
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        // Fallback: If it's a file URI or the query failed, try to extract it from the path
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result
    }

}

