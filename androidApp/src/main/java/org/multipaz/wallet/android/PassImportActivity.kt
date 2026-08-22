package org.multipaz.wallet.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.multipaz.context.initializeApplication
import org.multipaz.util.Logger

private const val TAG = "PassImportActivity"

/**
 * Trampoline activity for importing .mpzpass files received from other apps (Files, Downloads, Quick Share, etc.).
 *
 * When an external app opens a file via [Intent.ACTION_VIEW], it does so without [Intent.FLAG_ACTIVITY_NEW_TASK],
 * which would place the activity inside the caller's task stack. This transparent activity reads the file,
 * forwards the bytes to [App.importMpzPass], brings [MainActivity] to the foreground in its own task with
 * [Intent.FLAG_ACTIVITY_NEW_TASK], and finishes immediately.
 */
class PassImportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)

        val uri: Uri? = intent.data
        if (uri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val fileContent = inputStream.readBytes()
                        val app = App.getInstance()
                        app.importMpzPass(fileContent)
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error reading file content from URI $uri", e)
                }
            }
        }

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        finish()
    }
}
