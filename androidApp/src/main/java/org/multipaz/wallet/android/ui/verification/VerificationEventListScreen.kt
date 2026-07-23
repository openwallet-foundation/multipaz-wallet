package org.multipaz.wallet.android.ui.verification

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.multipaz.cbor.Cbor
import org.multipaz.compose.eventlogger.SimpleEventLoggerModel
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.datetime.durationFromNowText
import org.multipaz.datetime.formatLocalized
import org.multipaz.eventlogger.EventVerification
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.util.Logger
import org.multipaz.verification.PresentmentRecord
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.isProximityPresentment
import org.multipaz.wallet.android.getDisplayName
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.verification.AgeOverDocumentQueryResult
import org.multipaz.wallet.client.verification.AgeOverQuery
import org.multipaz.wallet.client.verification.DrivingPrivilegesDocumentQueryResult
import org.multipaz.wallet.client.verification.DrivingPrivilegesQuery
import org.multipaz.wallet.client.verification.IdentificationDocumentQueryResult
import org.multipaz.wallet.client.verification.IdentificationQuery
import org.multipaz.wallet.client.verification.Query

import org.multipaz.wallet.client.verification.Result
import org.multipaz.wallet.client.verification.fromCbor

private data class EventVerificationData(
    val portrait: ImageBitmap?,
    val queryResult: Result,
    val presentmentRecord: PresentmentRecord
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationEventListScreen(
    eventLogger: SimpleEventLogger,
    documentTypeRepository: DocumentTypeRepository,
    zkSystemRepository: ZkSystemRepository,
    issuerTrustManager: CompositeTrustManager,
    onDeleteAllEvents: () -> Unit,
    onEventClicked: (event: EventVerification) -> Unit,
    onBackClicked: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val model = remember(eventLogger) { SimpleEventLoggerModel(eventLogger, coroutineScope) }
    val events by model.events.collectAsState()
    val scrollState = rememberScrollState()

    val currentEvents = events
        ?.filterIsInstance<EventVerification>()
        ?.sortedByDescending { it.timestamp }

    val eventDataMap = remember { mutableStateMapOf<String, EventVerificationData>() }

    LaunchedEffect(currentEvents) {
        if (currentEvents != null) {
            for (event in currentEvents) {
                if (eventDataMap.containsKey(event.identifier)) continue
                launch(Dispatchers.Default) {
                    try {
                        val queryDataItem = event.appData["query"]
                        if (queryDataItem != null) {
                            val query = Query.fromCbor(Cbor.encode(queryDataItem))
                            val presentmentRecord = event.presentmentRecord
                            val verifiedPresentations = presentmentRecord.verify(
                                atTime = event.timestamp,
                                documentTypeRepository = documentTypeRepository,
                                zkSystemRepository = zkSystemRepository
                            )
                            val queryResult = query.processVerifiedPresentations(
                                verifiedPresentation = verifiedPresentations,
                                issuerTrustManager = issuerTrustManager
                            )
                            val docResult = queryResult.documents.firstOrNull()
                            val portraitBytes = when (docResult) {
                                is AgeOverDocumentQueryResult -> docResult.portrait
                                is IdentificationDocumentQueryResult -> docResult.portrait
                                is DrivingPrivilegesDocumentQueryResult -> docResult.portrait
                                else -> null
                            }
                            val portraitBitmap = portraitBytes?.let {
                                val array = it.toByteArray()
                                BitmapFactory.decodeByteArray(array, 0, array.size)?.asImageBitmap()
                            }
                            withContext(Dispatchers.Main) {
                                eventDataMap[event.identifier] = EventVerificationData(
                                    portrait = portraitBitmap,
                                    queryResult = queryResult,
                                    presentmentRecord = presentmentRecord
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.e("VerificationEventListScreen", "Failed to process event ${event.identifier}", e)
                    }
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.verification_history_title))
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Note(
                markdownString = stringResource(R.string.verification_history_note)
            )
            FloatingItemList {
                if (currentEvents == null) {
                    CircularProgressIndicator()
                } else {
                    if (currentEvents.isEmpty()) {
                        FloatingItemCenteredText(
                            text = stringResource(R.string.verification_history_empty),
                        )
                    } else {
                        currentEvents.forEach { event ->
                            val data = eventDataMap[event.identifier]
                            EventItemVerification(
                                modifier = Modifier
                                    .clickable { onEventClicked(event) },
                                event = event,
                                data = data
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EventItemVerification(
    event: EventVerification,
    data: EventVerificationData?,
    modifier: Modifier = Modifier,
    imageSize: Dp = 48.dp,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val isTrusted = data?.queryResult?.documents?.firstOrNull()?.trustResult?.isTrusted ?: true
    val isAgeOver = (data?.queryResult?.documents?.firstOrNull() as? AgeOverDocumentQueryResult)?.isAgeOver
    val durationText = durationFromNowText(event.timestamp)
    val timeText = if (!isTrusted) {
        stringResource(R.string.request_verification_received_unknown_issuer, durationText)
    } else {
        stringResource(R.string.request_verification_received, durationText)
    }
    val isProximity = event.isProximityPresentment()
    val protocol = if (isProximity) {
        stringResource(R.string.verification_history_in_person)
    } else {
        stringResource(R.string.verification_history_link)
    }
    val secondaryText = stringResource(R.string.verification_history_time_protocol, timeText, protocol)

    val defaultTitle = stringResource(R.string.verification_history_default_title)
    val headingText = if (data != null) {
        if (isTrusted) {
            val queryDataItem = event.appData["query"]
            val query = queryDataItem?.let { Query.fromCbor(Cbor.encode(it)) }
            when (query) {
                is AgeOverQuery -> {
                    if (isAgeOver == false) {
                        stringResource(R.string.verification_show_response_age_over_failure, query.ageOver)
                    } else {
                        stringResource(R.string.verification_show_response_age_over_success, query.ageOver)
                    }
                }
                is IdentificationQuery -> stringResource(R.string.request_verification_identified)
                is DrivingPrivilegesQuery -> stringResource(R.string.request_verification_driving_privileges_verified)

                else -> query?.getDisplayName() ?: defaultTitle
            }
        } else {
            val queryDataItem = event.appData["query"]
            val query = queryDataItem?.let { Query.fromCbor(Cbor.encode(it)) }
            query?.getDisplayName() ?: defaultTitle
        }
    } else {
        defaultTitle
    }

    FloatingItemHeadingAndContent(
        modifier = modifier,
        image = {
            if (!isTrusted) {
                Icon(
                    modifier = Modifier.size(imageSize),
                    imageVector = Icons.Outlined.ErrorOutline,
                    tint = MaterialTheme.colorScheme.error,
                    contentDescription = stringResource(R.string.content_description_error)
                )
            } else if (data?.portrait != null) {
                Image(
                    modifier = Modifier
                        .size(imageSize)
                        .clip(RoundedCornerShape(8.dp)),
                    bitmap = data.portrait,
                    contentDescription = stringResource(R.string.content_description_portrait),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    modifier = Modifier.size(imageSize),
                    imageVector = if (isProximity) Icons.Outlined.Contactless else Icons.Outlined.Link,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null
                )
            }
        },
        heading = headingText,
        content = {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
