package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.eventlogger.SimpleEventLoggerModel
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.datetime.formatLocalized
import org.multipaz.eventlogger.Event
import org.multipaz.eventlogger.EventPresentment
import org.multipaz.eventlogger.EventProvisioning
import org.multipaz.eventlogger.EventSimple
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.getSharingType
import org.multipaz.wallet.android.isForDocumentId
import org.multipaz.wallet.android.ui.Note

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
    val coroutineScope = rememberCoroutineScope()
    val model = remember(eventLogger) { SimpleEventLoggerModel(eventLogger, coroutineScope) }
    val events by model.events.collectAsState()
    val scrollState = rememberScrollState()

    val currentEvents = events?.sortedByDescending { it.timestamp }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.event_list_screen_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onDeleteAllEvents,
                        enabled = currentEvents?.isNotEmpty() ?: false
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        // TODO: with many events Column might be too slow, consider using LazyColumn instead.
        //
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Note(
                stringResource(R.string.event_list_screen_explainer)
            )
            FloatingItemList {
                if (currentEvents == null) {
                    CircularProgressIndicator()
                } else {
                    // Newest events at the top
                    if (currentEvents.isEmpty()) {
                        FloatingItemCenteredText(
                            text = stringResource(R.string.event_list_screen_no_events_text),
                        )
                    } else {
                        currentEvents.forEach { event ->
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
                                is EventSimple -> {}
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(20.dp))
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

    FloatingItemText(
        modifier = modifier,
        image = {
            docInfo?.cardArt?.let {
                Image(
                    modifier = modifier.size(imageSize),
                    bitmap = it,
                    contentDescription = null
                )
            } ?: Spacer(modifier = Modifier.size(imageSize))
        },
        text = docInfo?.document?.displayName ?: event.documentName ?: stringResource(R.string.event_unknown_document),
        secondary = text,
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
    FloatingItemText(
        modifier = modifier,
        image = {
            firstDocInfo?.cardArt?.let {
                Image(
                    modifier = modifier.size(imageSize),
                    bitmap = it,
                    contentDescription = null
                )
            } ?: Spacer(modifier = Modifier.size(imageSize))
        },
        text = firstDocInfo?.document?.displayName ?: firstDoc?.documentName ?: stringResource(R.string.event_list_screen_unknown_document_text),
        secondary = text,
    )
}
