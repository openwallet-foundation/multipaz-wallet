package org.multipaz.wallet.android

import android.net.Uri
import androidx.core.content.FileProvider

class MultipazFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? {
        val fileName = uri.lastPathSegment
        if (fileName != null) {
            if (fileName.endsWith(".mpzpass", ignoreCase = true)) {
                return "application/vnd.multipaz.mpzpass"
            }
            if (fileName.endsWith(".mpzevent", ignoreCase = true)) {
                return "application/vnd.multipaz.mpzevent"
            }
        }
        return super.getType(uri)
    }
}
