package org.multipaz.wallet.android.ui.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Business
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.multipaz.compose.decodeImage
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.eventlogger.SimpleEventLoggerModel
import org.multipaz.datetime.formatLocalized
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.eventlogger.Event
import org.multipaz.eventlogger.EventPresentment
import org.multipaz.eventlogger.EventProvisioning
import org.multipaz.eventlogger.EventSimple
import org.multipaz.eventlogger.EventVerification
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.getSharingType
import org.multipaz.wallet.android.isForDocumentId
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEventListScreen(
    eventLogger: SimpleEventLogger,
    documentId: String,
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
    val scrollState = rememberScrollState()

    val documentInfos by documentModel.documentInfos.collectAsState()
    val documentInfo = documentInfos.find { it.document.identifier == documentId }
    val documentEvents = events?.filter { it.isForDocumentId(documentId) }?.sortedByDescending { it.timestamp }

    val typeDisplayName = documentInfo?.document?.typeDisplayName.orEmpty()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.document_event_list_screen_title, typeDisplayName))
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    IconButton(
                        onClick = onDeleteAllEvents,
                        enabled = documentEvents?.isNotEmpty() ?: false
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
        // TODO: with many events Column might be too slow, consider using LazyColumn instead.
        //
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
            Note(
                markdownString = stringResource(R.string.document_event_list_screen_explainer)
            )
            FloatingItemList {
                if (documentEvents == null) {
                    CircularProgressIndicator()
                } else {
                    // Newest events at the top
                    if (documentEvents.isEmpty()) {
                        FloatingItemCenteredText(
                            text = stringResource(R.string.document_event_list_screen_no_events_text),
                        )
                    } else {
                        documentEvents.forEach { event ->
                            when (event) {
                                is EventPresentment -> {
                                    EventPresentmentForDocument(
                                        modifier = Modifier
                                            .clickable { onEventClicked(event) },
                                        event = event,
                                        imageLoader = imageLoader,
                                    )
                                }
                                is EventProvisioning -> {
                                    EventProvisioningForDocument(
                                        modifier = Modifier
                                            .clickable { onEventClicked(event) },
                                        event = event,
                                        imageLoader = imageLoader,
                                    )
                                }
                                is EventVerification -> {}
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
private fun EventProvisioningForDocument(
    event: EventProvisioning,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    imageSize: Dp = 24.dp,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val eventType = if (event.initialProvisioning) {
        "Document provisioning"
    } else {
        "Credential refresh"
    }

    val eventDateTimeString = event.timestamp.toLocalDateTime(timeZone = timeZone).formatLocalized()
    val text = "$eventDateTimeString • $eventType"

    FloatingItemText(
        modifier = modifier,
        showChevron = true,
        image = {
            event.issuerData.display.logo?.let {
                val bitmap = remember { decodeImage(it.toByteArray()) }
                Image(
                    modifier = Modifier.size(imageSize),
                    bitmap = bitmap,
                    contentDescription = null
                )
            } ?: Icon(
                modifier = Modifier.size(imageSize),
                imageVector = Icons.Outlined.AccountBalance,
                contentDescription = null
            )
        },
        text = event.issuerData.display.text,
        secondary = text,
    )
}

// Shown when this is on a list for a specific document
@Composable
private fun EventPresentmentForDocument(
    event: EventPresentment,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    imageSize: Dp = 24.dp,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val sharingType = event.getSharingType()
    val eventDateTimeString = event.timestamp.toLocalDateTime(timeZone = timeZone).formatLocalized()
    val text = stringResource(R.string.document_event_list_screen_time_and_type_text, eventDateTimeString, sharingType)
    FloatingItemText(
        modifier = modifier,
        showChevron = true,
        image = {
            event.presentmentData.trustMetadata?.displayIcon?.let {
                val bitmap = remember { decodeImage(it.toByteArray()) }
                Image(
                    modifier = Modifier.size(imageSize),
                    bitmap = bitmap,
                    contentDescription = null
                )
            } ?: event.presentmentData.trustMetadata?.displayIconUrl?.let {
                AsyncImage(
                    modifier = Modifier.size(imageSize),
                    model = it,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    contentDescription = null
                )
            } ?: Icon(
                modifier = Modifier.size(imageSize),
                imageVector = Icons.Outlined.Business,
                contentDescription = null
            )
        },
        text = event.presentmentData.trustMetadata?.displayName ?: stringResource(R.string.document_event_list_screen_unknown_requester_text),
        secondary = text,
    )
}
