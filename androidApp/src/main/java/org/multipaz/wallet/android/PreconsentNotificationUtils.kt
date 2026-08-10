package org.multipaz.wallet.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

fun postPreconsentNotification(
    context: Context,
    documentNames: List<String>,
    verifierName: String?,
    eventId: String?,
    cardArtBytes: ByteArray? = null
) {
    val channelId = "preconsent_presentations"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = context.getString(R.string.preconsent_notification_channel_name)
        val descriptionText = context.getString(R.string.preconsent_notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        action = App.ACTION_VIEW_EVENT
        putExtra("eventId", eventId)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val notificationId = eventId?.hashCode() ?: System.currentTimeMillis().toInt()
    val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val docNamesFormatted = if (documentNames.isNotEmpty()) {
        documentNames.joinToString(", ")
    } else {
        context.getString(R.string.event_unknown_document)
    }

    val bodyText = if (!verifierName.isNullOrBlank()) {
        context.getString(R.string.preconsent_notification_text, docNamesFormatted, verifierName)
    } else {
        context.getString(R.string.preconsent_notification_text_unknown_verifier, docNamesFormatted)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_stat_name)
        .setContentTitle(context.getString(R.string.preconsent_notification_title))
        .setContentText(bodyText)
        .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    cardArtBytes?.let { bytes ->
        if (bytes.isNotEmpty()) {
            val bitmap = try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        }
    }

    val notificationManager = NotificationManagerCompat.from(context)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        notificationManager.notify(notificationId, builder.build())
    }
}
