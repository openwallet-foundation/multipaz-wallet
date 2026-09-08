package org.multipaz.wallet.android.ui.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sync
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.PeriodicBookkeepingEventDetails
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.Simple
import org.multipaz.util.fromBase64Url
import org.multipaz.verification.PresentmentRecord
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.verification.Query
import org.multipaz.wallet.client.verification.UserDefinedQuery
import org.multipaz.wallet.client.verification.fromCbor
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
import org.multipaz.eventlogger.EventProvisioning
import org.multipaz.eventlogger.EventProvisioningIssuerDataOpenID4VCI
import org.multipaz.eventlogger.EventSimple
import org.multipaz.eventlogger.EventVerification
import org.multipaz.eventlogger.EventVerificationDigitalCredentials
import org.multipaz.eventlogger.EventVerificationIso18013Proximity
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.eventlogger.toDataItem
import org.multipaz.mdoc.engagement.EngagementType
import org.multipaz.prompt.PromptModel
import org.multipaz.verification.Iso18013PresentmentRecord
import org.multipaz.verification.OpenID4VPPresentmentRecord
import org.multipaz.verification.VerifiedPresentation
import org.multipaz.verification.MdocVerifiedPresentation
import org.multipaz.verification.JsonVerifiedPresentation
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.trustmanagement.TrustResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.RequestedClaim
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.MapView
import org.multipaz.wallet.android.ui.getAddressFromCoordinates
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.Location
import org.multipaz.wallet.shared.fromDataItem
import org.multipaz.wallet.android.isProximityPresentment
import org.multipaz.wallet.android.shareEvent
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.time.Clock
import org.multipaz.wallet.client.verification.VerificationTelemetry
import org.multipaz.wallet.client.verification.formatDuration
import org.multipaz.wallet.client.verification.toTelemetry

private const val TAG = "EventViewerScreen"

@SuppressLint("LocalContextGetResourceValueCall")
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
    showToast: (message: String) -> Unit,
    zkSystemRepository: ZkSystemRepository,
    issuerTrustManager: CompositeTrustManager,
    settingsModel: SettingsModel? = null,
    onDeveloperExtrasClicked: ((presentmentRecord: PresentmentRecord, query: Query, atTime: Instant, telemetry: VerificationTelemetry?) -> Unit)? = null,
    onViewCbor: ((title: String, cborBytes: ByteArray) -> Unit)? = null,
    onViewJson: ((title: String, jsonString: String) -> Unit)? = null,
    onViewJwt: ((title: String, jwtString: String) -> Unit)? = null,
) {
    val hazeState = remember { HazeState() }
    val localContext = LocalContext.current
    val coroutineScope = rememberUiBoundCoroutineScope { promptModel }
    val model = remember(eventLogger) { SimpleEventLoggerModel(eventLogger, coroutineScope) }
    val events by model.events.collectAsState()
    val scrollState = rememberScrollState()
    val devModeEnabled = settingsModel?.devMode?.collectAsState()?.value ?: false
    val currentEvent = events?.find { it.identifier == eventId }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            val onTitleClick: (() -> Unit)? = if (devModeEnabled && currentEvent is EventVerification && onDeveloperExtrasClicked != null) {
                {
                    val query = currentEvent.appData["query"]?.let {
                        try {
                            Query.fromCbor(Cbor.encode(it))
                        } catch (_: Throwable) {
                            null
                        }
                    } ?: UserDefinedQuery(docType = "", namespaces = emptyMap())
                    onDeveloperExtrasClicked(currentEvent.presentmentRecord, query, currentEvent.timestamp, currentEvent.toTelemetry())
                }
            } else null
            AppMediumTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.event_viewer_screen_title_text),
                        modifier = if (onTitleClick != null) Modifier.clickable { onTitleClick() } else Modifier
                    )
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val event = eventLogger.getEvents().find { it.identifier == eventId }
                                if (event != null) {
                                    shareEvent(
                                        context = localContext,
                                        event = event
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
            when (val currentEvents = events) {
                null -> {
                    CircularProgressIndicator()
                }

                else -> {
                    val event = currentEvents.find { it.identifier == eventId }
                    when (event) {
                        null -> {}
                        is EventPresentment -> {
                            EventViewerPresentment(
                                event = event,
                                documentTypeRepository = documentTypeRepository,
                                documentModel = documentModel,
                                imageLoader = imageLoader,
                                onViewCertificateChain = onViewCertificateChain,
                                devModeEnabled = devModeEnabled,
                                onViewCbor = onViewCbor,
                                onViewJson = onViewJson,
                                onViewJwt = onViewJwt,
                            )
                        }
                        is EventProvisioning -> {
                            EventViewerProvisioning(
                                event = event,
                                documentTypeRepository = documentTypeRepository,
                                documentModel = documentModel,
                                imageLoader = imageLoader,
                                onViewCertificateChain = onViewCertificateChain
                            )
                        }
                        is EventVerification -> {
                            EventViewerVerification(
                                event = event,
                                documentTypeRepository = documentTypeRepository,
                                imageLoader = imageLoader,
                                onViewCertificateChain = onViewCertificateChain,
                                zkSystemRepository = zkSystemRepository,
                                issuerTrustManager = issuerTrustManager,
                                devModeEnabled = devModeEnabled,
                                onDeveloperExtrasClicked = onDeveloperExtrasClicked,
                                onViewJson = onViewJson,
                            )
                        }
                        is EventSimple -> {
                            EventViewerSimple(
                                event = event
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventViewerProvisioning(
    event: EventProvisioning,
    documentTypeRepository: DocumentTypeRepository,
    documentModel: DocumentModel,
    imageLoader: ImageLoader,
    onViewCertificateChain: (certChain: X509CertChain) -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val docInfo = documentModel.documentInfos.collectAsState().value.find {
        it.document.identifier == event.documentId
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (docInfo != null) {
            Image(
                modifier = Modifier.height(80.dp),
                bitmap = docInfo.cardArt,
                contentDescription = null
            )
        }
        Text(
            text = docInfo?.document?.displayName ?: event.documentName ?: stringResource(R.string.event_unknown_document),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        val eventDateTime = event.timestamp.toLocalDateTime(timeZone = timeZone)
        val eventDateTimeString = eventDateTime.formatLocalized(
            dateStyle = FormatStyle.LONG,
            timeStyle = FormatStyle.LONG
        )

        FloatingItemList(
            modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)
        ) {
            FloatingItemHeadingAndText(
                heading = stringResource(R.string.event_viewer_date_time),
                text = eventDateTimeString
            )
            FloatingItemHeadingAndText(
                heading = stringResource(R.string.event_viewer_issuer),
                text = event.issuerData.display.text
            )
            when (event.issuerData) {
                is EventProvisioningIssuerDataOpenID4VCI -> {
                    FloatingItemHeadingAndText(
                        heading = stringResource(R.string.event_viewer_openid4vci_server),
                        text = (event.issuerData as EventProvisioningIssuerDataOpenID4VCI).url
                    )
                }
            }
            FloatingItemHeadingAndText(
                heading = stringResource(R.string.event_viewer_type),
                text = if (event.initialProvisioning) {
                    stringResource(R.string.event_provisioning_type_initial)
                } else {
                    stringResource(R.string.event_provisioning_type_refresh)
                }
            )
            var numCredentials = 0
            event.credentialsFetched.forEach { (domain, credentials) -> numCredentials += credentials.size }
            FloatingItemHeadingAndText(
                heading = stringResource(R.string.event_viewer_credentials_heading),
                text = if (numCredentials == 1) {
                    stringResource(R.string.event_viewer_credentials_singular)
                } else {
                    stringResource(R.string.event_viewer_credentials_plural, numCredentials)
                }
            )
        }
    }
}

@Composable
private fun EventViewerPresentment(
    event: EventPresentment,
    documentTypeRepository: DocumentTypeRepository,
    documentModel: DocumentModel,
    imageLoader: ImageLoader,
    onViewCertificateChain: (certChain: X509CertChain) -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    devModeEnabled: Boolean = false,
    onViewCbor: ((title: String, cborBytes: ByteArray) -> Unit)? = null,
    onViewJson: ((title: String, jsonString: String) -> Unit)? = null,
    onViewJwt: ((title: String, jwtString: String) -> Unit)? = null,
) {
    val eventDateTime = event.timestamp.toLocalDateTime(timeZone = timeZone)
    val eventDateTimeString = eventDateTime.formatLocalized(
        dateStyle = FormatStyle.LONG,
        timeStyle = FormatStyle.LONG
    )

    val protocol = when (event) {
        is EventPresentmentDigitalCredentialsMdocApi -> stringResource(R.string.event_viewer_screen_protocol_w3dc_18013_7_annex_c_text)
        is EventPresentmentDigitalCredentialsOpenID4VP -> stringResource(R.string.event_viewer_screen_protocol_w3dc_openid4v_text)
        is EventPresentmentUriSchemeOpenID4VP -> stringResource(R.string.event_viewer_screen_protocol_uri_openid4vp_text)
        is EventPresentmentIso18013AnnexA -> stringResource(R.string.event_viewer_screen_protocol_uri_18013_7_annex_a_text)
        is EventPresentmentIso18013Proximity -> stringResource(R.string.event_viewer_screen_protocol_18013_5_text)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val imageSize = 80.dp
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
        }

        Text(
            text = event.presentmentData.requesterName ?: stringResource(R.string.event_viewer_screen_unknown_requester_text),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.size(16.dp))

        FloatingItemList(title = null) {
            FloatingItemHeadingAndText(
                heading = stringResource(R.string.event_viewer_screen_date_and_time_text),
                text = eventDateTimeString
            )

            event.appData["location"]?.let {
                val location = Location.fromDataItem(it)

                var address by remember { mutableStateOf<String?>(null) }
                var isLoadingAddress by remember { mutableStateOf(true) }
                
                val localContext = LocalContext.current
                LaunchedEffect(location) {
                    isLoadingAddress = true
                    address = location.getAddressFromCoordinates(localContext)
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
                        MapView(
                            location = location,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        val coordinates = "${location.latitude}, ${location.longitude}"
                        if (isLoadingAddress) {
                            Text(
                                text = stringResource(R.string.event_viewer_screen_location_looking_up_address) + " ($coordinates)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val displayText = address ?: coordinates
                            SelectionContainer {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    ),
                                    modifier = Modifier.clickable {
                                        val geoUri = if (address != null) {
                                            "geo:${location.latitude},${location.longitude}?q=${Uri.encode(address)}"
                                        } else {
                                            "geo:${location.latitude},${location.longitude}"
                                        }
                                        localContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)))
                                    }
                                )
                            }
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
                    val sessionTranscriptBytes = Cbor.encode(event.sessionTranscript)
                    val onTranscriptClick: (() -> Unit)? = if (onViewCbor != null) {
                        { onViewCbor("Session transcript", sessionTranscriptBytes) }
                    } else null
                    FloatingItemHeadingAndText(
                        heading = "Session transcript",
                        text = "${"%,d".format(sessionTranscriptBytes.size)} bytes of CBOR",
                        showChevron = onTranscriptClick != null,
                        modifier = if (onTranscriptClick != null) Modifier.clickable { onTranscriptClick() } else Modifier
                    )
                }
            }

            FloatingItemHeadingAndText(
                heading = stringResource(R.string.event_viewer_screen_presentment_protocol_text),
                text =  protocol
            )

            val requesterTrusted = stringResource(R.string.event_viewer_screen_requester_trusted_text)
            if (event.presentmentData.trustMetadata != null) {
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
            event.presentmentData.requesterCertChain?.let {
                FloatingItemHeadingAndText(
                    showChevron = true,
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

            event.presentmentData.trustMetadata?.privacyPolicyUrl?.let {
                FloatingItemHeadingAndText(
                    heading = stringResource(R.string.event_viewer_screen_requester_privacy_policy_text),
                    text = AnnotatedString.fromMarkdown(
                        markdownString = "[$it]($it)"
                    )
                )
            }
        }
        Spacer(modifier = Modifier.size(20.dp))

        event.presentmentData.requestedDocuments.forEach { requestedDocument ->
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
                Spacer(modifier = Modifier.size(16.dp))
                FloatingItemList(title = stringResource(R.string.event_viewer_screen_this_info_was_shared_text)) {
                    ExtractClaimsItems(sharedClaims, documentTypeRepository)
                }
                Spacer(modifier = Modifier.size(20.dp))
            }

            val sharedAndStoredClaims = requestedDocument.claims.filter { (requestedClaim, _) ->
                if (requestedClaim is MdocRequestedClaim) requestedClaim.intentToRetain else false
            }
            if (sharedAndStoredClaims.isNotEmpty()) {
                Spacer(modifier = Modifier.size(16.dp))
                FloatingItemList(title = stringResource(R.string.event_viewer_screen_this_info_was_shared_and_stored_text)) {
                    ExtractClaimsItems(sharedAndStoredClaims, documentTypeRepository)
                }
                Spacer(modifier = Modifier.size(20.dp))
            }
        }

        if (devModeEnabled) {
            FloatingItemList(
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
                title = "Developer extras"
            ) {
                when (event) {
                    is EventPresentmentIso18013Proximity -> {
                        val requestBytes = Cbor.encode(event.request)
                        val onRequestClick: (() -> Unit)? = if (onViewCbor != null) {
                            { onViewCbor("Device request", requestBytes) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "Device request",
                            text = "${"%,d".format(requestBytes.size)} bytes of CBOR",
                            showChevron = onRequestClick != null,
                            modifier = if (onRequestClick != null) Modifier.clickable { onRequestClick() } else Modifier
                        )

                        val responseBytes = Cbor.encode(event.response)
                        val onResponseClick: (() -> Unit)? = if (onViewCbor != null) {
                            { onViewCbor("Device response", responseBytes) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "Device response",
                            text = "${"%,d".format(responseBytes.size)} bytes of CBOR",
                            showChevron = onResponseClick != null,
                            modifier = if (onResponseClick != null) Modifier.clickable { onResponseClick() } else Modifier
                        )
                    }
                    is EventPresentmentDigitalCredentialsMdocApi -> {
                        val onRequestClick: (() -> Unit)? = if (onViewJson != null) {
                            { onViewJson("W3C DC Request", event.requestJson) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "W3C DC Request",
                            text = "${"%,d".format(event.requestJson.length)} chars of JSON",
                            showChevron = onRequestClick != null,
                            modifier = if (onRequestClick != null) Modifier.clickable { onRequestClick() } else Modifier
                        )

                        val onResponseClick: (() -> Unit)? = if (onViewJson != null) {
                            { onViewJson("W3C DC Response", event.responseJson) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "W3C DC Response",
                            text = "${"%,d".format(event.responseJson.length)} chars of JSON",
                            showChevron = onResponseClick != null,
                            modifier = if (onResponseClick != null) Modifier.clickable { onResponseClick() } else Modifier
                        )

                        val deviceResponseBytes = Cbor.encode(event.deviceResponse)
                        val onDeviceResponseClick: (() -> Unit)? = if (onViewCbor != null) {
                            { onViewCbor("Device response", deviceResponseBytes) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "Device response",
                            text = "${"%,d".format(deviceResponseBytes.size)} bytes of CBOR",
                            showChevron = onDeviceResponseClick != null,
                            modifier = if (onDeviceResponseClick != null) Modifier.clickable { onDeviceResponseClick() } else Modifier
                        )
                    }
                    is EventPresentmentDigitalCredentialsOpenID4VP -> {
                        val onRequestClick: (() -> Unit)? = if (onViewJson != null) {
                            { onViewJson("W3C DC Request", event.requestJson) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "W3C DC Request",
                            text = "${"%,d".format(event.requestJson.length)} chars of JSON",
                            showChevron = onRequestClick != null,
                            modifier = if (onRequestClick != null) Modifier.clickable { onRequestClick() } else Modifier
                        )

                        val onResponseClick: (() -> Unit)? = if (onViewJson != null) {
                            { onViewJson("W3C DC Response", event.responseJson) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "W3C DC Response",
                            text = "${"%,d".format(event.responseJson.length)} chars of JSON",
                            showChevron = onResponseClick != null,
                            modifier = if (onResponseClick != null) Modifier.clickable { onResponseClick() } else Modifier
                        )

                        val vpToken = event.vpToken
                        val onVpTokenClick: (() -> Unit)? = if (onViewJwt != null || onViewJson != null || onViewCbor != null) {
                            { viewVpToken(vpToken, onViewJwt, onViewJson, onViewCbor) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "VP Token",
                            text = formatVpTokenSummary(vpToken),
                            showChevron = onVpTokenClick != null,
                            modifier = if (onVpTokenClick != null) Modifier.clickable { onVpTokenClick() } else Modifier
                        )
                    }
                    is EventPresentmentUriSchemeOpenID4VP -> {
                        val onRequestClick: (() -> Unit)? = if (onViewJwt != null) {
                            { onViewJwt("Authorization Request", event.requestJwt) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "Authorization Request",
                            text = "${"%,d".format(event.requestJwt.length)} chars (JWT)",
                            showChevron = onRequestClick != null,
                            modifier = if (onRequestClick != null) Modifier.clickable { onRequestClick() } else Modifier
                        )

                        val vpToken = event.vpToken
                        val onVpTokenClick: (() -> Unit)? = if (onViewJwt != null || onViewJson != null || onViewCbor != null) {
                            { viewVpToken(vpToken, onViewJwt, onViewJson, onViewCbor) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "VP Token",
                            text = formatVpTokenSummary(vpToken),
                            showChevron = onVpTokenClick != null,
                            modifier = if (onVpTokenClick != null) Modifier.clickable { onVpTokenClick() } else Modifier
                        )
                    }
                    is EventPresentmentIso18013AnnexA -> {
                        val sessionTranscriptBytes = Cbor.encode(event.sessionTranscript)
                        val onTranscriptClick: (() -> Unit)? = if (onViewCbor != null) {
                            { onViewCbor("Session transcript", sessionTranscriptBytes) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "Session transcript",
                            text = "${"%,d".format(sessionTranscriptBytes.size)} bytes of CBOR",
                            showChevron = onTranscriptClick != null,
                            modifier = if (onTranscriptClick != null) Modifier.clickable { onTranscriptClick() } else Modifier
                        )

                        val requestBytes = Cbor.encode(event.request)
                        val onRequestClick: (() -> Unit)? = if (onViewCbor != null) {
                            { onViewCbor("Device request", requestBytes) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "Device request",
                            text = "${"%,d".format(requestBytes.size)} bytes of CBOR",
                            showChevron = onRequestClick != null,
                            modifier = if (onRequestClick != null) Modifier.clickable { onRequestClick() } else Modifier
                        )

                        val responseBytes = Cbor.encode(event.response)
                        val onResponseClick: (() -> Unit)? = if (onViewCbor != null) {
                            { onViewCbor("Device response", responseBytes) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "Device response",
                            text = "${"%,d".format(responseBytes.size)} bytes of CBOR",
                            showChevron = onResponseClick != null,
                            modifier = if (onResponseClick != null) Modifier.clickable { onResponseClick() } else Modifier
                        )

                        val reBytes = Cbor.encode(event.readerEngagement)
                        val onReClick: (() -> Unit)? = if (onViewCbor != null) {
                            { onViewCbor("Reader engagement", reBytes) }
                        } else null
                        FloatingItemHeadingAndText(
                            heading = "Reader engagement",
                            text = "${"%,d".format(reBytes.size)} bytes of CBOR",
                            showChevron = onReClick != null,
                            modifier = if (onReClick != null) Modifier.clickable { onReClick() } else Modifier
                        )
                    }
                }
            }
        }
    }
}

private fun formatVpTokenSummary(vpToken: String): String {
    return if (vpToken.contains('~') || (vpToken.contains('.') && !vpToken.startsWith("{"))) {
        "${"%,d".format(vpToken.length)} chars (SD-JWT)"
    } else if (vpToken.startsWith("{")) {
        "${"%,d".format(vpToken.length)} chars of JSON"
    } else {
        "${"%,d".format(vpToken.length)} chars"
    }
}

private fun viewVpToken(
    vpToken: String,
    onViewJwt: ((title: String, jwtString: String) -> Unit)?,
    onViewJson: ((title: String, jsonString: String) -> Unit)?,
    onViewCbor: ((title: String, cborBytes: ByteArray) -> Unit)?
) {
    if ((vpToken.contains('~') || (vpToken.contains('.') && !vpToken.startsWith("{"))) && onViewJwt != null) {
        onViewJwt("VP Token", vpToken)
    } else if (vpToken.startsWith("{") && onViewJson != null) {
        onViewJson("VP Token", vpToken)
    } else if (onViewCbor != null) {
        try {
            val bytes = vpToken.fromBase64Url()
            onViewCbor("VP Token", bytes)
        } catch (_: Throwable) {
            onViewJson?.invoke("VP Token", vpToken)
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

private data class VerifiedPresentationResult(
    val vp: VerifiedPresentation,
    val trustResult: TrustResult?
)

@Composable
private fun EventViewerVerification(
    event: EventVerification,
    documentTypeRepository: DocumentTypeRepository,
    imageLoader: ImageLoader,
    onViewCertificateChain: (certChain: X509CertChain) -> Unit,
    zkSystemRepository: ZkSystemRepository,
    issuerTrustManager: CompositeTrustManager,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    devModeEnabled: Boolean = false,
    onDeveloperExtrasClicked: ((presentmentRecord: PresentmentRecord, query: Query, atTime: Instant, telemetry: VerificationTelemetry?) -> Unit)? = null,
    onViewJson: ((title: String, jsonString: String) -> Unit)? = null,
) {
    val eventDateTime = event.timestamp.toLocalDateTime(timeZone = timeZone)
    val eventDateTimeString = eventDateTime.formatLocalized(
        dateStyle = FormatStyle.LONG,
        timeStyle = FormatStyle.LONG
    )

    val protocol = when (event.presentmentRecord) {
        is Iso18013PresentmentRecord -> "ISO 18013-5"
        is OpenID4VPPresentmentRecord -> "OpenID4VP"
    }

    val onDevExtras: (() -> Unit)? = if (devModeEnabled && onDeveloperExtrasClicked != null) {
        {
            val query = event.appData["query"]?.let {
                try {
                    Query.fromCbor(Cbor.encode(it))
                } catch (_: Throwable) {
                    null
                }
            } ?: UserDefinedQuery(docType = "", namespaces = emptyMap())
            onDeveloperExtrasClicked(event.presentmentRecord, query, event.timestamp, event.toTelemetry())
        }
    } else null

    var verifiedPresentationsResult by remember { mutableStateOf<List<VerifiedPresentationResult>?>(null) }
    var verificationError by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(event) {
        try {
            val presentations = withContext(Dispatchers.Default) {
                event.presentmentRecord.verify(
                    atTime = event.timestamp,
                    documentTypeRepository = documentTypeRepository,
                    zkSystemRepository = zkSystemRepository
                )
            }
            val mappedResults = presentations.map { vp ->
                val certChain = vp.documentSignerCertChain
                val trustResult = certChain?.let {
                    issuerTrustManager.verify(it.certificates, event.timestamp)
                }
                VerifiedPresentationResult(vp, trustResult)
            }
            verifiedPresentationsResult = mappedResults
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            verificationError = t
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val imageSize = 80.dp
        Icon(
            imageVector = Icon.BADGE.getOutlinedImageVector(),
            contentDescription = null,
            modifier = Modifier.size(imageSize)
        )

        Text(
            text = "Verification Result",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = if (onDevExtras != null) Modifier.clickable { onDevExtras() } else Modifier
        )

        FloatingItemList(
            modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)
        ) {
            FloatingItemHeadingAndText(
                heading = "Date and time",
                text = eventDateTimeString
            )

            when (event) {
                is EventVerificationDigitalCredentials -> {
                    OriginAndAppIdItem(event.origin, event.appId)
                    FloatingItemHeadingAndText(
                        heading = "Presentment protocol",
                        text = protocol
                    )
                    event.durationRequestSentToResponseReceived?.let {
                        FloatingItemHeadingAndText(
                            heading = "Request sent to response received",
                            text = it.formatDuration()
                        )
                    }
                    val onRequestClick: (() -> Unit)? = if (onViewJson != null) {
                        { onViewJson("W3C DC Request", event.requestJson) }
                    } else null
                    FloatingItemHeadingAndText(
                        heading = "Request JSON",
                        text = "${"%,d".format(event.requestJson.length)} chars of JSON",
                        showChevron = onRequestClick != null,
                        modifier = if (onRequestClick != null) Modifier.clickable { onRequestClick() } else Modifier
                    )
                    val onResponseClick: (() -> Unit)? = if (onViewJson != null) {
                        { onViewJson("W3C DC Response", event.responseJson) }
                    } else null
                    FloatingItemHeadingAndText(
                        heading = "Response JSON",
                        text = "${"%,d".format(event.responseJson.length)} chars of JSON",
                        showChevron = onResponseClick != null,
                        modifier = if (onResponseClick != null) Modifier.clickable { onResponseClick() } else Modifier
                    )
                }
                is EventVerificationIso18013Proximity -> {
                    val engagementText = when (event.engagementType) {
                        EngagementType.QR_CODE -> "QR code"
                        EngagementType.NFC_STATIC_HANDOVER -> "NFC static handover"
                        EngagementType.NFC_NEGOTIATED_HANDOVER -> "NFC negotiated handover"
                        EngagementType.NFC_CONCURRENT_CHANNEL_ENGAGEMENT -> "NFC concurrent channel engagement"
                    }
                    FloatingItemHeadingAndText(
                        heading = "Engagement channel",
                        text = engagementText
                    )
                    FloatingItemHeadingAndText(
                        heading = "Presentment protocol",
                        text = protocol
                    )
                    event.durationNfcTapToEngagement?.let {
                        FloatingItemHeadingAndText(
                            heading = "NFC tap to engagement",
                            text = it.formatDuration()
                        )
                    }
                    event.durationEngagementReceivedToRequestSent?.let {
                        FloatingItemHeadingAndText(
                            heading = "Engagement to request sent",
                            text = it.formatDuration()
                        )
                    }
                    event.durationRequestSentToResponseReceived?.let {
                        FloatingItemHeadingAndText(
                            heading = "Request sent to response received",
                            text = it.formatDuration()
                        )
                    }
                    event.durationScanningTime?.let {
                        FloatingItemHeadingAndText(
                            heading = "Transport scanning time",
                            text = it.formatDuration()
                        )
                    }
                    event.nfcHybridTransportStats?.let { stats ->
                        FloatingItemHeadingAndText(
                            heading = "NFC hybrid transport stats",
                            text = "Sent: ${stats.numSent} (${stats.numSentViaNfc} NFC, ${stats.numSentViaTransport} transport)\nReceived: ${stats.numReceived} (${stats.numReceivedFirstOnNfc} NFC, ${stats.numReceivedFirstOnTransport} transport)"
                        )
                    }
                }
                else -> {
                    FloatingItemHeadingAndText(
                        heading = "Presentment protocol",
                        text = protocol
                    )
                    event.durationRequestSentToResponseReceived?.let {
                        FloatingItemHeadingAndText(
                            heading = "Request sent to response received",
                            text = it.formatDuration()
                        )
                    }
                }
            }

            if (onDevExtras != null) {
                FloatingItemHeadingAndText(
                    heading = "Developer extras",
                    text = "View raw requests, responses, and transcripts",
                    showChevron = true,
                    modifier = Modifier.clickable { onDevExtras() }
                )
            }

            if (verificationError != null) {
                FloatingItemHeadingAndText(
                    heading = "Verification status",
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                            append("Failed: ${verificationError?.message}")
                        }
                    }
                )
            } else if (verifiedPresentationsResult == null) {
                FloatingItemHeadingAndText(
                    heading = "Verification status",
                    text = "Verifying..."
                )
            } else {
                FloatingItemHeadingAndText(
                    heading = "Verification status",
                    text = "Verified successfully"
                )
            }

            event.appData["location"]?.let {
                val location = Location.fromDataItem(it)

                var address by remember { mutableStateOf<String?>(null) }
                var isLoadingAddress by remember { mutableStateOf(true) }
                
                val localContext = LocalContext.current
                LaunchedEffect(location) {
                    isLoadingAddress = true
                    address = location.getAddressFromCoordinates(localContext)
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
                        MapView(
                            location = location,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        val coordinates = "${location.latitude}, ${location.longitude}"
                        if (isLoadingAddress) {
                            Text(
                                text = stringResource(R.string.event_viewer_screen_location_looking_up_address) + " ($coordinates)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val displayText = address ?: coordinates
                            SelectionContainer {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    ),
                                    modifier = Modifier.clickable {
                                        val geoUri = if (address != null) {
                                            "geo:${location.latitude},${location.longitude}?q=${Uri.encode(address)}"
                                        } else {
                                            "geo:${location.latitude},${location.longitude}"
                                        }
                                        localContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        verifiedPresentationsResult?.forEachIndexed { index, result ->
            val vp = result.vp
            val formatText = when (vp) {
                is MdocVerifiedPresentation -> "ISO mdoc"
                is JsonVerifiedPresentation -> "IETF SD-JWT VC"
            }
            val titleText = when (vp) {
                is MdocVerifiedPresentation -> "Document ${index + 1}: ${vp.docType} ($formatText)"
                is JsonVerifiedPresentation -> "Document ${index + 1}: ${vp.vct} ($formatText)"
            }
            FloatingItemList(
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
                title = titleText
            ) {
                vp.documentSignerCertChain?.let { certChain ->
                    FloatingItemHeadingAndText(
                        showChevron = true,
                        heading = "Issuer certificate chain",
                        text = "Click to view",
                        modifier = Modifier.clickable {
                            onViewCertificateChain(certChain)
                        }
                    )

                    val trustResult = result.trustResult
                    if (trustResult != null && trustResult.isTrusted) {
                        val tpName = " (${trustResult.trustPoints.first().metadata.displayName})"
                        FloatingItemHeadingAndText(
                            heading = "Issuer Trusted",
                            text = "Yes$tpName"
                        )
                    } else {
                        FloatingItemHeadingAndText(
                            heading = "Issuer Trusted",
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                    append("No, not in a trust list")
                                }
                            }
                        )
                    }
                }

                if (vp.zkpUsed) {
                    FloatingItemHeadingAndText(
                        heading = "ZK proof",
                        text = "Successfully verified 🪄"
                    )
                }

                for (n in listOf(0, 1)) {
                    val (claims, suffix) = if (n == 0) {
                        Pair(vp.issuerSignedClaims, "")
                    } else {
                        Pair(vp.deviceSignedClaims, " (Device Signed)")
                    }
                    claims.forEach { claim ->
                        val typedClaim = Claim.fromDataItem(
                            dataItem = claim.toDataItem(),
                            documentTypeRepository = documentTypeRepository
                        )
                        val textValue = typedClaim.render()
                        FloatingItemHeadingAndText(
                            heading = typedClaim.displayName + suffix,
                            text = textValue,
                            image = {
                                val icon = typedClaim.attribute?.icon ?: Icon.PERSON
                                Icon(
                                    imageVector = icon.getOutlinedImageVector(),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventViewerSimple(
    event: EventSimple,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val eventDateTime = event.timestamp.toLocalDateTime(timeZone = timeZone)
    val eventDateTimeString = eventDateTime.formatLocalized(
        dateStyle = FormatStyle.LONG,
        timeStyle = FormatStyle.LONG
    )

    val details: PeriodicBookkeepingEventDetails? = remember(event) {
        PeriodicBookkeepingEventDetails.fromEventSimple(event)
    }

    val title = if (details != null) {
        stringResource(R.string.event_simple_periodic_bookkeeping_title)
    } else {
        stringResource(R.string.event_simple_title)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            modifier = Modifier.size(64.dp),
            imageVector = Icons.Outlined.Sync,
            contentDescription = null,
            tint = if (details?.success == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        if (details != null) {
            Note(
                markdownString = stringResource(R.string.event_viewer_periodic_refresh_explanation)
            )
        }

        FloatingItemList(
            modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)
        ) {
            FloatingItemHeadingAndText(
                heading = stringResource(R.string.event_viewer_date_time),
                text = eventDateTimeString
            )
            if (details != null) {
                val triggerText = when (details.trigger) {
                    "startup" -> stringResource(R.string.event_viewer_refresh_trigger_startup)
                    "pull_to_refresh" -> stringResource(R.string.event_viewer_refresh_trigger_pull_to_refresh)
                    "periodic_worker" -> stringResource(R.string.event_viewer_refresh_trigger_periodic_worker)
                    "developer_settings" -> stringResource(R.string.event_viewer_refresh_trigger_developer_settings)
                    else -> stringResource(R.string.event_viewer_refresh_trigger_unknown)
                }
                FloatingItemHeadingAndText(
                    heading = stringResource(R.string.event_viewer_refresh_trigger),
                    text = triggerText
                )
                FloatingItemHeadingAndText(
                    heading = stringResource(R.string.event_viewer_refresh_status),
                    text = if (details.success) {
                        stringResource(R.string.event_viewer_refresh_status_success)
                    } else {
                        stringResource(R.string.event_viewer_refresh_status_failed)
                    }
                )
                val publicDataText = if (details.publicDataError != null) {
                    "${stringResource(R.string.event_viewer_failed)} (${details.publicDataError})"
                } else if (details.publicDataRefreshed) {
                    stringResource(R.string.event_viewer_yes)
                } else {
                    stringResource(R.string.event_viewer_no)
                }
                FloatingItemHeadingAndText(
                    heading = stringResource(R.string.event_viewer_public_data_refreshed),
                    text = publicDataText
                )
                val sharedDataText = if (details.sharedDataError != null) {
                    "${stringResource(R.string.event_viewer_failed)} (${details.sharedDataError})"
                } else if (details.sharedDataRefreshed) {
                    stringResource(R.string.event_viewer_yes)
                } else {
                    stringResource(R.string.event_viewer_no)
                }
                FloatingItemHeadingAndText(
                    heading = stringResource(R.string.event_viewer_shared_data_refreshed),
                    text = sharedDataText
                )
                val credsText = if (details.totalDocumentsChecked > 0) {
                    "${details.refreshedCredentialsCount} (${details.refreshedDocumentsCount}/${details.totalDocumentsChecked} passes)"
                } else {
                    details.refreshedCredentialsCount.toString()
                }
                FloatingItemHeadingAndText(
                    heading = stringResource(R.string.event_viewer_refreshed_credentials_count),
                    text = credsText
                )
                val readerKeysText = if (details.readerKeysError != null) {
                    "${stringResource(R.string.event_viewer_failed)} (${details.readerKeysError})"
                } else if (details.readerKeysRefreshedCount > 0) {
                    details.readerKeysRefreshedCount.toString()
                } else {
                    stringResource(R.string.event_viewer_no)
                }
                FloatingItemHeadingAndText(
                    heading = stringResource(R.string.event_viewer_reader_keys_refreshed),
                    text = readerKeysText
                )
                if (details.trustManagersChecked.isNotEmpty() || details.updatedTrustEntriesCount > 0) {
                    FloatingItemHeadingAndText(
                        heading = stringResource(R.string.event_viewer_trust_entries_updated),
                        text = details.updatedTrustEntriesCount.toString()
                    )
                }
                val allErrors = details.credentialRefreshErrors + details.trustManagerErrors
                if (allErrors.isNotEmpty()) {
                    FloatingItemHeadingAndText(
                        heading = stringResource(R.string.event_viewer_errors),
                        text = allErrors.joinToString("\n")
                    )
                }
                FloatingItemHeadingAndText(
                    heading = stringResource(R.string.event_viewer_runtime_duration),
                    text = stringResource(R.string.event_viewer_runtime_duration_seconds, details.runtimeDurationMs / 1000.0)
                )
            }
        }
    }
}
