package org.multipaz.wallet.android.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.Simple
import org.multipaz.claim.Claim
import org.multipaz.compose.decodeImage
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.eventlogger.SimpleEventLoggerModel
import org.multipaz.compose.getOutlinedImageVector
import org.multipaz.compose.items.FloatingItemContainer
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.rememberUiBoundCoroutineScope
import org.multipaz.compose.sharemanager.ShareManager
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.crypto.X509CertChain
import org.multipaz.datetime.FormatStyle
import org.multipaz.datetime.formatLocalized
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.Icon
import org.multipaz.eventlogger.Event
import org.multipaz.eventlogger.EventPresentment
import org.multipaz.eventlogger.EventPresentmentDigitalCredentialsMdocApi
import org.multipaz.eventlogger.EventPresentmentDigitalCredentialsOpenID4VP
import org.multipaz.eventlogger.EventPresentmentIso18013AnnexA
import org.multipaz.eventlogger.EventPresentmentIso18013Proximity
import org.multipaz.eventlogger.EventPresentmentUriSchemeOpenID4VP
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.eventlogger.toDataItem
import org.multipaz.prompt.PromptModel
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.RequestedClaim
import org.multipaz.util.Logger
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.multipaz.wallet.android.ui.OpenStreetMap
import org.multipaz.wallet.android.ui.getAddressFromCoordinates
import androidx.compose.ui.text.font.FontWeight.Companion
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.OpenStreetMap
import org.multipaz.wallet.shared.Location
import org.multipaz.wallet.shared.fromDataItem
import kotlin.time.Clock

private const val TAG = "EventViewerScreen"

@OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class)
@Composable
fun EventViewerScreen(
    eventLogger: SimpleEventLogger,
    eventId: String,
    documentTypeRepository: DocumentTypeRepository,
    documentModel: DocumentModel,
    imageLoader: ImageLoader,
    onEventDelete: () -> Unit,
    onViewCertificateChain: (certChain: X509CertChain) -> Unit,
    onBackClicked: () -> Unit,
    promptModel: PromptModel,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberUiBoundCoroutineScope { promptModel }
    val model = remember(eventLogger) { SimpleEventLoggerModel(eventLogger, coroutineScope) }
    val events by model.events.collectAsState()
    val scrollState = rememberScrollState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.event_viewer_screen_title_text))
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
                        onClick = {
                            coroutineScope.launch {
                                val event = eventLogger.getEvents().find { it.identifier == eventId }
                                if (event != null) {
                                    // Just do a text file for now. In the future we might define
                                    // a binary format and provide tools for offline analysis.
                                    val format = DateTimeComponents.Format {
                                        byUnicodePattern("yyyyMMdd-HHmmss")
                                    }
                                    val timeStampString = event.timestamp.format(
                                        format = format,
                                        offset = TimeZone.currentSystemDefault().offsetAt(Clock.System.now())
                                    )
                                    val shareManager = ShareManager()
                                    shareManager.shareDocument(
                                        content = Cbor.toDiagnostics(
                                            item = event.toDataItem(),
                                            options = setOf(DiagnosticOption.PRETTY_PRINT, DiagnosticOption.EMBEDDED_CBOR)
                                        ).encodeToByteArray(),
                                        filename = "mpzwallet-event-${timeStampString}.txt",
                                        mimeType = "text/plain",
                                        title = "Multipaz Wallet event recorded at ${event.timestamp}" // TODO
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null
                        )
                    }
                    IconButton(
                        onClick = onEventDelete
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
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (val currentEvents = events) {
                null -> {
                    CircularProgressIndicator()
                }

                else -> {
                    val event = currentEvents.find { it.identifier == eventId }
                    if (event != null) {
                        EventViewer(
                            event = event,
                            documentTypeRepository = documentTypeRepository,
                            documentModel = documentModel,
                            imageLoader = imageLoader,
                            onViewCertificateChain = onViewCertificateChain
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventViewer(
    event: Event,
    documentTypeRepository: DocumentTypeRepository,
    documentModel: DocumentModel,
    imageLoader: ImageLoader,
    onViewCertificateChain: (certChain: X509CertChain) -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val eventDateTime = event.timestamp.toLocalDateTime(timeZone = timeZone)
    val eventDateTimeString = eventDateTime.formatLocalized(
        dateStyle = FormatStyle.LONG,
        timeStyle = FormatStyle.LONG
    )

    // Right now the all events are presentment events. This will change in the future as we add
    // support for logging other events
    val presentmentData = (event as EventPresentment).presentmentData

    val protocol = when (event) {
        is EventPresentmentDigitalCredentialsMdocApi -> stringResource(R.string.event_viewer_screen_protocol_w3dc_18013_7_annex_c_text)
        is EventPresentmentDigitalCredentialsOpenID4VP -> stringResource(R.string.event_viewer_screen_protocol_w3dc_openid4v_text)
        is EventPresentmentUriSchemeOpenID4VP -> stringResource(R.string.event_viewer_screen_protocol_uri_openid4vp_text)
        is EventPresentmentIso18013AnnexA -> stringResource(R.string.event_viewer_screen_protocol_uri_18013_7_annex_a_text)
        is EventPresentmentIso18013Proximity -> stringResource(R.string.event_viewer_screen_protocol_18013_5_text)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val imageSize = 80.dp
        presentmentData.trustMetadata?.displayIcon?.let {
            val bitmap = remember { decodeImage(it.toByteArray()) }
            Image(
                modifier = Modifier.size(imageSize),
                bitmap = bitmap,
                contentDescription = null
            )
        } ?: presentmentData.trustMetadata?.displayIconUrl?.let {
            AsyncImage(
                modifier = Modifier.size(imageSize),
                model = it,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                contentDescription = null
            )
        }

        Text(
            text = presentmentData.requesterName ?: stringResource(R.string.event_viewer_screen_unknown_requester_text),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        FloatingItemList(title = null) {
            FloatingItemHeadingAndText(
                heading = stringResource(R.string.event_viewer_screen_date_and_time_text),
                text = eventDateTimeString
            )

            event.appData["location"]?.let {
                val location = Location.fromDataItem(it)

                var address by remember { mutableStateOf<String?>(null) }
                var isLoadingAddress by remember { mutableStateOf(true) }
                
                LaunchedEffect(location) {
                    isLoadingAddress = true
                    address = location.getAddressFromCoordinates()
                    isLoadingAddress = false
                }

                FloatingItemContainer {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.event_viewer_screen_location_text),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OpenStreetMap(
                            location = location,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        if (isLoadingAddress) {
                            Text(
                                text = stringResource(R.string.event_viewer_screen_location_looking_up_address),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            address?.let {
                                SelectionContainer {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } ?: Text(
                                text = stringResource(R.string.event_viewer_screen_location_address_not_found),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            when (event) {
                is EventPresentmentDigitalCredentialsMdocApi -> {
                    OriginAndAppIdItem(event.origin, event.appId)
                }
                is EventPresentmentDigitalCredentialsOpenID4VP -> {
                    OriginAndAppIdItem(event.origin, event.appId)
                }
                is EventPresentmentIso18013AnnexA -> {
                    OriginAndAppIdItem(event.origin, event.appId)
                }
                is EventPresentmentUriSchemeOpenID4VP -> {
                    OriginAndAppIdItem(event.origin, event.appId)
                }
                is EventPresentmentIso18013Proximity -> {
                    val handover = event.sessionTranscript.asArray[2]
                    val sharedInPerson = stringResource(R.string.event_viewer_screen_shared_in_person_text)
                    if (handover == Simple.NULL) {
                        FloatingItemHeadingAndText(
                            heading = sharedInPerson,
                            text = stringResource(R.string.event_viewer_screen_using_qr_code_text)
                        )
                    } else {
                        FloatingItemHeadingAndText(
                            heading = sharedInPerson,
                            text = stringResource(R.string.event_viewer_screen_using_nfc_text)
                        )
                    }
                }
            }

            FloatingItemHeadingAndText(
                heading = stringResource(R.string.event_viewer_screen_presentment_protocol_text),
                text =  protocol
            )

            val requesterTrusted = stringResource(R.string.event_viewer_screen_requester_trusted_text)
            if (presentmentData.trustMetadata != null) {
                FloatingItemHeadingAndText(
                    heading = requesterTrusted,
                    text =  stringResource(R.string.event_viewer_screen_requester_in_trust_list_text)
                )
            } else {
                FloatingItemHeadingAndText(
                    heading = requesterTrusted,
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                            append(stringResource(R.string.event_viewer_screen_requester_not_in_trust_list_text))
                        }
                    }
                )
            }

            val requesterCertificate = stringResource(R.string.event_viewer_screen_requester_certificate_text)
            presentmentData.requesterCertChain?.let {
                FloatingItemHeadingAndText(
                    heading = requesterCertificate,
                    text = stringResource(R.string.event_viewer_screen_certificate_click_to_view_text),
                    modifier = Modifier.clickable {
                        onViewCertificateChain(it)
                    }
                )
            } ?: run {
                FloatingItemHeadingAndText(
                    heading = requesterCertificate,
                    text = stringResource(R.string.event_viewer_screen_certificate_not_available_text),
                )
            }

            presentmentData.trustMetadata?.privacyPolicyUrl?.let {
                FloatingItemHeadingAndText(
                    heading = stringResource(R.string.event_viewer_screen_requester_privacy_policy_text),
                    text = AnnotatedString.fromMarkdown(
                        markdownString = "[$it]($it)"
                    )
                )
            }
        }

        presentmentData.requestedDocuments.forEach { requestedDocument ->
            val info = documentModel.documentInfos.collectAsState().value.find {
                it.document.identifier == requestedDocument.documentId
            }
            if (info != null) {
                Image(
                    modifier = Modifier.height(80.dp),
                    bitmap = info.cardArt,
                    contentDescription = null
                )
                Text(
                    text = info.document.displayName ?: stringResource(R.string.event_viewer_screen_unknown_document_text),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            val sharedClaims = requestedDocument.claims.filter { (requestedClaim, _) ->
                if (requestedClaim is MdocRequestedClaim) !requestedClaim.intentToRetain else true
            }
            if (sharedClaims.isNotEmpty()) {
                FloatingItemList(title = stringResource(R.string.event_viewer_screen_this_info_was_shared_text)) {
                    ExtractClaimsItems(sharedClaims, documentTypeRepository)
                }
            }

            val sharedAndStoredClaims = requestedDocument.claims.filter { (requestedClaim, _) ->
                if (requestedClaim is MdocRequestedClaim) requestedClaim.intentToRetain else false
            }
            if (sharedAndStoredClaims.isNotEmpty()) {
                FloatingItemList(title = stringResource(R.string.event_viewer_screen_this_info_was_shared_and_stored_text)) {
                    ExtractClaimsItems(sharedAndStoredClaims, documentTypeRepository)
                }
            }
        }
    }
}

@Composable
private fun OriginAndAppIdItem(
    origin: String?,
    appId: String?,
) {
    val sharedWithWebsite = stringResource(R.string.event_viewer_screen_shared_with_website_text)
    val sharedWithApp = stringResource(R.string.event_viewer_screen_shared_with_app_text)
    if (origin != null && origin.isNotEmpty() && (origin.startsWith("http://") || origin.startsWith("https://"))) {
        FloatingItemHeadingAndText(
            heading = sharedWithWebsite,
            text = AnnotatedString.fromMarkdown("[$origin]($origin)")
        )
    } else if (origin != null && origin.isNotEmpty()) {
        if (appId != null) {
            // TODO: look up details about the application
            FloatingItemHeadingAndText(
                heading = sharedWithApp,
                text = appId
            )
        } else {
            FloatingItemHeadingAndText(
                heading = sharedWithApp,
                text = stringResource(R.string.event_viewer_screen_unknown_app_text)
            )
        }
    } else {
        FloatingItemHeadingAndText(
            heading = sharedWithWebsite,
            text = stringResource(R.string.event_viewer_screen_unknown_website_text)
        )
    }
}

@Composable
private fun ExtractClaimsItems(
    requestedClaims: Map<RequestedClaim, Claim>,
    documentTypeRepository: DocumentTypeRepository
) {
    requestedClaims.forEach { (requestedClaim, claim) ->
        // Make sure claim.attribute is set, if we know the document type
        val claim = Claim.fromDataItem(
            dataItem = claim.toDataItem(),
            documentTypeRepository = documentTypeRepository
        )
        FloatingItemHeadingAndText(
            heading = claim.displayName,
            text = claim.render(),
            image = {
                val icon = claim.attribute?.icon ?: Icon.PERSON
                Icon(
                    imageVector = icon.getOutlinedImageVector(),
                    contentDescription = null
                )
            }
        )
    }
}