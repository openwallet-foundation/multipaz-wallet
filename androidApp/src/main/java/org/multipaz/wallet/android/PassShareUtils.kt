package org.multipaz.wallet.android

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.multipaz.cbor.Cbor
import org.multipaz.document.Document
import org.multipaz.wallet.client.WalletClient
import java.io.File

suspend fun shareMpzPass(
    context: Context,
    document: Document,
    walletClient: WalletClient
) {
    val mpzPassId = document.mpzPassId ?: throw IllegalStateException("Document is not an MpzPass")
    val sharedData = walletClient.sharedData.value ?: throw IllegalStateException("No shared data available")
    val pass = sharedData.getMpzPasses().find { it.uniqueId == mpzPassId }
        ?: throw IllegalStateException("Pass not found in shared data")

    val passBytes = Cbor.encode(pass.toDataItem())

    val sharedFolder = File(context.cacheDir, "shared_docs").apply { mkdirs() }
    val rawName = document.displayName ?: document.typeDisplayName ?: "pass"
    val sanitizedName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val file = File(sharedFolder, "$sanitizedName.mpzpass")
    file.writeBytes(passBytes)

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
