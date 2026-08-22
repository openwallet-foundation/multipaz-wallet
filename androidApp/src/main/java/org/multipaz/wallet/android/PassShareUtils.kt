package org.multipaz.wallet.android

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.multipaz.document.Document
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.mpzPassData
import java.io.File

suspend fun shareMpzPass(
    context: Context,
    document: Document,
    walletClient: WalletClient? = null
) {
    val passData = document.mpzPassData
        ?: throw IllegalStateException("Document does not have MpzPass data")

    val sharedFolder = File(context.cacheDir, "shared_docs").apply { mkdirs() }
    val rawName = document.displayName ?: document.typeDisplayName ?: "pass"
    val sanitizedName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val file = File(sharedFolder, "$sanitizedName.mpzpass")
    file.writeBytes(passData.toByteArray())

    val authority = "${context.packageName}.multipaz.fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, file)

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.multipaz.mpzpass"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooserIntent = Intent.createChooser(
        sendIntent,
        context.getString(R.string.share_pass_chooser_title)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(chooserIntent)
}
