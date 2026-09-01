package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.eventlogger.SimpleEventLoggerModel
import org.multipaz.compose.getOutlinedImageVector
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.LazyFloatingItemList
import org.multipaz.compose.items.floatingItemList
import org.multipaz.datetime.formatLocalized
import org.multipaz.eventlogger.Event
import org.multipaz.eventlogger.EventPresentment
import org.multipaz.eventlogger.EventProvisioning
import org.multipaz.eventlogger.EventSimple
import org.multipaz.eventlogger.EventVerification
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.verification.Iso18013PresentmentRecord
import org.multipaz.verification.OpenID4VPPresentmentRecord
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.getSharingType
import org.multipaz.wallet.android.isForDocumentId
import org.multipaz.wallet.android.isProximityPresentment
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.PeriodicBookkeepingEventDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    eventLogger: SimpleEventLogger,
    imageLoader: ImageLoader,
    documentModel: DocumentModel,
    onDeleteAllEvents: () -> Unit,
    onEventClicked: (event: Event) -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val hazeState = remember { HazeState() }
    val coroutineScope = rememberCoroutineScope()
    val model = remember(eventLogger) { SimpleEventLoggerModel(eventLogger, coroutineScope) }
    val events by model.events.collectAsState()

    val currentEvents = events?.sortedByDescending { it.timestamp }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.event_list_screen_title))
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    IconButton(
                        onClick = onDeleteAllEvents,
                        enabled = currentEvents?.isNotEmpty() ?: false
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        }
    ) { innerPadding ->
        if (currentEvents == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyFloatingItemList(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            ) {
                item {
                    Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
                }
                item {
                    Note(
                        markdownString = stringResource(R.string.event_list_screen_explainer)
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (currentEvents.isEmpty()) {
                    floatingItemList(count = 1) {
                        FloatingItemCenteredText(
                            text = stringResource(R.string.event_list_screen_no_events_text),
                        )
                    }
                } else {
                    floatingItemList(
                        items = currentEvents,
                        key = { it.identifier }
                    ) { event ->
                        when (event) {
                            is EventPresentment -> {
                                EventItemPresentment(
                                    modifier = Modifier
                                        .clickable { onEventClicked(event) },
                                    event = event,
                                    imageLoader = imageLoader,
                                    documentModel = documentModel
                                )
                            }
                            is EventProvisioning -> {
                                EventItemProvisioning(
                                    modifier = Modifier
                                        .clickable { onEventClicked(event) },
                                    event = event,
                                    imageLoader = imageLoader,
                                    documentModel = documentModel
                                )
                            }
                            is EventVerification -> {
                                EventItemVerification(
                                    modifier = Modifier
                                        .clickable { onEventClicked(event) },
                                    event = event
                                )
                            }
                            is EventSimple -> {
                                EventItemSimple(
                                    modifier = Modifier
                                        .clickable { onEventClicked(event) },
                                    event = event
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun EventItemProvisioning(
    event: EventProvisioning,
    imageLoader: ImageLoader,
    documentModel: DocumentModel,
    modifier: Modifier = Modifier,
    imageSize: Dp = 24.dp,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val docInfo = event.documentId.let { documentId ->
        documentModel.documentInfos.collectAsState().value.find {
            it.document.identifier == documentId
        }
    }

    val eventType = if (event.initialProvisioning) {
        stringResource(R.string.event_provisioning_type_initial)
    } else {
        stringResource(R.string.event_provisioning_type_refresh)
    }

    val eventDateTimeString = event.timestamp.toLocalDateTime(timeZone = timeZone).formatLocalized()
    val text = "$eventDateTimeString • $eventType"

    FloatingItemHeadingAndText(
        modifier = modifier,
        showChevron = true,
        image = {
            docInfo?.cardArt?.let {
                Image(
                    modifier = modifier.size(imageSize),
                    bitmap = it,
                    contentDescription = null
                )
            } ?: Spacer(modifier = Modifier.size(imageSize))
        },
        heading = docInfo?.document?.displayName ?: event.documentName ?: stringResource(R.string.event_unknown_document),
        text = text,
    )
}

@Composable
private fun EventItemPresentment(
    event: EventPresentment,
    imageLoader: ImageLoader,
    documentModel: DocumentModel,
    modifier: Modifier = Modifier,
    imageSize: Dp = 24.dp,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val sharingType = event.getSharingType()

    val firstDoc = event.presentmentData.requestedDocuments.firstOrNull()
    val firstDocInfo = firstDoc?.let { requestedDocument ->
        documentModel.documentInfos.collectAsState().value.find {
            it.document.identifier == requestedDocument.documentId
        }
    }

    val eventDateTimeString = event.timestamp.toLocalDateTime(timeZone = timeZone).formatLocalized()
    val text = stringResource(R.string.event_list_screen_time_and_type_text, eventDateTimeString, sharingType)
    FloatingItemHeadingAndText(
        modifier = modifier,
        showChevron = true,
        image = {
            firstDocInfo?.cardArt?.let {
                Image(
                    modifier = modifier.size(imageSize),
                    bitmap = it,
                    contentDescription = null
                )
            } ?: Spacer(modifier = Modifier.size(imageSize))
        },
        heading = firstDocInfo?.document?.displayName ?: firstDoc?.documentName ?: stringResource(R.string.event_list_screen_unknown_document_text),
        text = text,
    )
}

@Composable
private fun EventItemVerification(
    event: EventVerification,
    modifier: Modifier = Modifier,
    imageSize: Dp = 24.dp,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val eventDateTimeString = event.timestamp.toLocalDateTime(timeZone = timeZone).formatLocalized()
    val protocol = if (event.isProximityPresentment()) {
        "Verified in-person"
    } else {
        "Verified using link"
    }
    val text = "$eventDateTimeString • $protocol"

    FloatingItemHeadingAndText(
        modifier = modifier,
        showChevron = true,
        image = {
            Icon(
                modifier = Modifier.size(imageSize),
                imageVector = org.multipaz.documenttype.Icon.BADGE.getOutlinedImageVector(),
                contentDescription = null
            )
        },
        heading = "Verification",
        text = text,
    )
}

@Composable
private fun EventItemSimple(
    event: EventSimple,
    modifier: Modifier = Modifier,
    imageSize: Dp = 24.dp,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val eventDateTimeString = event.timestamp.toLocalDateTime(timeZone = timeZone).formatLocalized()
    val details: PeriodicBookkeepingEventDetails? = remember(event) {
        PeriodicBookkeepingEventDetails.fromEventSimple(event)
    }

    val title = if (details != null) {
        stringResource(R.string.event_simple_periodic_bookkeeping_title)
    } else {
        stringResource(R.string.event_simple_title)
    }

    val summary = if (details != null) {
        val triggerLabel = when (details.trigger) {
            "startup" -> stringResource(R.string.event_viewer_refresh_trigger_startup)
            "pull_to_refresh" -> stringResource(R.string.event_viewer_refresh_trigger_pull_to_refresh)
            "periodic_worker" -> stringResource(R.string.event_viewer_refresh_trigger_periodic_worker)
            "developer_settings" -> stringResource(R.string.event_viewer_refresh_trigger_developer_settings)
            else -> null
        }
        val summaryDetail = if (details.refreshedCredentialsCount > 0) {
            stringResource(
                R.string.event_simple_periodic_bookkeeping_summary,
                details.refreshedCredentialsCount,
                details.runtimeDurationMs / 1000.0
            )
        } else {
            stringResource(
                R.string.event_simple_periodic_bookkeeping_summary_up_to_date,
                details.runtimeDurationMs / 1000.0
            )
        }
        if (triggerLabel != null) {
            "$eventDateTimeString • $triggerLabel • $summaryDetail"
        } else {
            "$eventDateTimeString • $summaryDetail"
        }
    } else {
        eventDateTimeString
    }

    FloatingItemHeadingAndText(
        modifier = modifier,
        showChevron = true,
        image = {
            Icon(
                modifier = Modifier.size(imageSize),
                imageVector = Icons.Outlined.Sync,
                contentDescription = null
            )
        },
        heading = title,
        text = summary,
    )
}
