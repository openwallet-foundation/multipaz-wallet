package org.multipaz.wallet.android

import android.content.Context
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.offsetAt
import org.multipaz.cbor.Cbor
import org.multipaz.compose.sharemanager.ShareManager
import org.multipaz.eventlogger.Event
import org.multipaz.eventlogger.toDataItem
import org.multipaz.wallet.shared.BuildConfig
import kotlin.time.Clock

/**
 * Shares an event as a binary file, using a share sheet.
 *
 * @param context the [Context].
 * @param event the event to share.
 */
@OptIn(FormatStringsInDatetimeFormats::class)
suspend fun shareEvent(
    context: Context,
    event: Event
) {
    val format = DateTimeComponents.Format {
        byUnicodePattern("yyyyMMdd-HHmmss")
    }
    val timeStampString = event.timestamp.format(
        format = format,
        offset = TimeZone.currentSystemDefault().offsetAt(Clock.System.now())
    )
    val eventDataItem = event.toDataItem()
    val eventType = eventDataItem["type"].asTstr
    val eventBytes = Cbor.encode(eventDataItem)
    val shareManager = ShareManager()
    val appNameUnderscores = BuildConfig.APP_NAME.replace(" ", "-")
    shareManager.shareDocument(
        content = eventBytes,
        filename = "${appNameUnderscores}-Event-${eventType}-${timeStampString}.mpzevent",
        mimeType = "application/vnd.multipaz.mpzevent",
        title = context.getString(R.string.event_viewer_screen_file_name_text, BuildConfig.APP_NAME, eventType, event.timestamp))
}