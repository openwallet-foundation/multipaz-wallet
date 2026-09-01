package org.multipaz.wallet.android.ui.document

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
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Delete
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
        if (documentEvents == null) {
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
                        markdownString = stringResource(R.string.document_event_list_screen_explainer)
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (documentEvents.isEmpty()) {
                    floatingItemList(count = 1) {
                        FloatingItemCenteredText(
                            text = stringResource(R.string.document_event_list_screen_no_events_text),
                        )
                    }
                } else {
                    floatingItemList(
                        items = documentEvents,
                        key = { it.identifier }
                    ) { event ->
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
                item {
                    Spacer(modifier = Modifier.size(20.dp))
                }
            }
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

    FloatingItemHeadingAndText(
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
        heading = event.issuerData.display.text,
        text = text,
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
    FloatingItemHeadingAndText(
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
        heading = event.presentmentData.trustMetadata?.displayName ?: stringResource(R.string.document_event_list_screen_unknown_requester_text),
        text = text,
    )
}
