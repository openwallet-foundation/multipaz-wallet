package org.multipaz.wallet.android.ui.verification


import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Science
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
import kotlin.time.Clock
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottiePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.branding.Branding
import org.multipaz.compose.decodeImage
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.rememberUiBoundCoroutineScope
import org.multipaz.datetime.FormatStyle
import org.multipaz.datetime.formatLocalized
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.Options
import org.multipaz.eventlogger.EventVerification
import org.multipaz.eventlogger.SimpleEventLogger
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.prompt.PromptModel
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.Logger
import org.multipaz.verification.PresentmentRecord
import org.multipaz.cbor.toCdn
import org.multipaz.wallet.android.R

import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.shareEvent
import org.multipaz.wallet.android.ui.MapView
import org.multipaz.wallet.android.ui.getAddressFromCoordinates
import org.multipaz.revocation.RevocationStatus
import org.multipaz.wallet.client.verification.AgeOverDocumentQueryResult
import org.multipaz.wallet.client.verification.AgeOverQuery
import org.multipaz.wallet.client.verification.DocumentQueryResult
import org.multipaz.wallet.client.verification.DrivingPrivilege
import org.multipaz.wallet.client.verification.DrivingPrivilegesDocumentQueryResult
import org.multipaz.crypto.X509CertChain
import org.multipaz.wallet.client.verification.DrivingPrivilegesQuery
import org.multipaz.wallet.client.verification.IdentificationDocumentQueryResult
import org.multipaz.wallet.client.verification.IdentificationQuery
import org.multipaz.wallet.client.verification.Query
import org.multipaz.wallet.client.verification.Result
import org.multipaz.wallet.client.verification.RevocationChecker
import org.multipaz.wallet.client.verification.RevocationCheckResult
import org.multipaz.wallet.client.verification.RevocationCheckState
import org.multipaz.wallet.shared.Location
import org.multipaz.wallet.shared.fromDataItem
import kotlin.time.Instant

import androidx.compose.foundation.clickable
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.trustmanagement.TrustEntryRical
import org.multipaz.trustmanagement.TrustEntryVical
import org.multipaz.trustmanagement.TrustEntryX509Cert

private const val TAG = "VerificationShowResponseScreen"

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class)
@Composable
fun VerificationShowResponseScreen(
    query: Query,
    presentmentRecord: PresentmentRecord,
    atTime: Instant,
    documentTypeRepository: DocumentTypeRepository,
    zkSystemRepository: ZkSystemRepository,
    issuerTrustManager: CompositeTrustManager,
    builtInIssuerTrustManagerModel: TrustManagerModel,
    userIssuerTrustManagerModel: TrustManagerModel,
    settingsModel: SettingsModel,
    imageLoader: ImageLoader,
    promptModel: PromptModel,
    onDeveloperExtrasClicked: () -> Unit,
    onBackClicked: () -> Unit,
    eventLogger: SimpleEventLogger,
    eventIdentifier: String? = null,
    onEventDelete: (() -> Unit)? = null,
    revocationChecker: RevocationChecker? = null,
    onTrustEntryClicked: ((trustManagerId: String, trustEntryId: String) -> Unit)? = null
) {
    val localContext = LocalContext.current
    val coroutineScope = rememberUiBoundCoroutineScope { promptModel }
    val scrollState = rememberScrollState()
    val queryResult = remember { mutableStateOf<Result?>(null) }
    
    var event by remember { mutableStateOf<EventVerification?>(null) }
    LaunchedEffect(eventIdentifier) {
        if (eventIdentifier != null) {
            event = eventLogger.getEvents().find { it.identifier == eventIdentifier } as? EventVerification
        }
    }
    val verificationLocation = event?.appData?.get("location")?.let { Location.fromDataItem(it) }
    val verificationTime = event?.timestamp
    val parsingResponseFailed = remember { mutableStateOf<Exception?>(null) }
    val devModeEnabled = settingsModel.devMode.collectAsState().value

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.verification_show_response_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (devModeEnabled) {
                        IconButton(onClick = onDeveloperExtrasClicked) {
                            Icon(
                                imageVector = Icons.Outlined.Science,
                                contentDescription = null
                            )
                        }
                    }
                    if (eventIdentifier != null) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val eventToShare = eventLogger.getEvents().find { it.identifier == eventIdentifier }
                                    if (eventToShare != null) {
                                        shareEvent(
                                            context = localContext,
                                            event = eventToShare
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
                    }
                    if (onEventDelete != null) {
                        IconButton(onClick = onEventDelete) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (parsingResponseFailed.value != null) {
                ShowParsingFailedError(parsingResponseFailed.value!!)
            } else {
                LaunchedEffect(Unit) {
                    try {
                        val verifiedPresentations = withContext(Dispatchers.Default) {
                            presentmentRecord.verify(
                                atTime = atTime,
                                documentTypeRepository = documentTypeRepository,
                                zkSystemRepository = zkSystemRepository
                            )
                        }
                        val result = withContext(Dispatchers.Default) {
                            query.processVerifiedPresentations(
                                verifiedPresentation = verifiedPresentations,
                                issuerTrustManager = issuerTrustManager
                            )
                        }
                        queryResult.value = result
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.e(TAG, "Error parsing response", e)
                        parsingResponseFailed.value = e
                    }
                }

                when (queryResult.value?.query) {
                    is AgeOverQuery -> {
                        val result = queryResult.value!!.documents.first()
                        ShowAgeOverResult(
                            query = queryResult.value!!.query as AgeOverQuery,
                            result = result as AgeOverDocumentQueryResult,
                            builtInIssuerTrustManagerModel = builtInIssuerTrustManagerModel,
                            userIssuerTrustManagerModel = userIssuerTrustManagerModel,
                            imageLoader = imageLoader,
                            verificationLocation = verificationLocation,
                            verificationTime = verificationTime,
                            revocationChecker = revocationChecker,
                            onTrustEntryClicked = onTrustEntryClicked
                        )
                    }

                    is IdentificationQuery -> {
                        val result = queryResult.value!!.documents.first()
                        ShowIdentificationResult(
                            query = queryResult.value!!.query as IdentificationQuery,
                            result = result as IdentificationDocumentQueryResult,
                            builtInIssuerTrustManagerModel = builtInIssuerTrustManagerModel,
                            userIssuerTrustManagerModel = userIssuerTrustManagerModel,
                            imageLoader = imageLoader,
                            verificationLocation = verificationLocation,
                            verificationTime = verificationTime,
                            revocationChecker = revocationChecker,
                            onTrustEntryClicked = onTrustEntryClicked
                        )
                    }

                    is DrivingPrivilegesQuery -> {
                        val result = queryResult.value!!.documents.first()
                        ShowDrivingPrivilegesResult(
                            query = queryResult.value!!.query as DrivingPrivilegesQuery,
                            result = result as DrivingPrivilegesDocumentQueryResult,
                            builtInIssuerTrustManagerModel = builtInIssuerTrustManagerModel,
                            userIssuerTrustManagerModel = userIssuerTrustManagerModel,
                            imageLoader = imageLoader,
                            verificationLocation = verificationLocation,
                            verificationTime = verificationTime,
                            revocationChecker = revocationChecker,
                            onTrustEntryClicked = onTrustEntryClicked
                        )
                    }

                    is org.multipaz.wallet.client.verification.UserDefinedQuery -> {
                        val result = queryResult.value!!.documents.first()
                        ShowUserDefinedResult(
                            query = queryResult.value!!.query as org.multipaz.wallet.client.verification.UserDefinedQuery,
                            result = result as org.multipaz.wallet.client.verification.UserDefinedDocumentQueryResult,
                            builtInIssuerTrustManagerModel = builtInIssuerTrustManagerModel,
                            userIssuerTrustManagerModel = userIssuerTrustManagerModel,
                            imageLoader = imageLoader,
                            verificationLocation = verificationLocation,
                            verificationTime = verificationTime,
                            revocationChecker = revocationChecker,
                            onTrustEntryClicked = onTrustEntryClicked
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun ShowParsingFailedError(
    error: Exception
) {
    val composition by rememberLottieComposition(spec = LottieCompositionSpec.RawRes(R.raw.error_animation))
    val progressState = animateLottieCompositionAsState(composition = composition)
    Image(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp).padding(16.dp),
        painter = rememberLottiePainter(
            composition = composition,
            progress = progressState.value,
        ),
        contentDescription = null,
    )
    Text(
        text = stringResource(R.string.verification_show_response_error_processing),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ShowPortrait(portrait: ByteString) {
    val portraitBitmap = remember { decodeImage(portrait.toByteArray()) }

    val imageRatio = portraitBitmap.width.toFloat() / portraitBitmap.height.toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = portraitBitmap,
            contentDescription = stringResource(R.string.content_description_portrait),
            modifier = Modifier
                .height(200.dp)
                .aspectRatio(imageRatio)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                    clip = true
                )
        )
    }
}


@Composable
private fun rememberRevocationCheckResult(
    result: DocumentQueryResult,
    revocationChecker: RevocationChecker?,
    atTime: Instant
): RevocationCheckResult? {
    var checkResult by remember(result.revocationStatus) { mutableStateOf<RevocationCheckResult?>(null) }
    LaunchedEffect(result.revocationStatus) {
        val status = result.revocationStatus
        if (revocationChecker != null && status != null && status !is RevocationStatus.Unknown) {
            checkResult = revocationChecker.check(
                revocationStatus = status,
                issuerCertChain = result.certificateChain,
                atTime = atTime
            )
        } else {
            checkResult = RevocationCheckResult(
                state = RevocationCheckState.UNKNOWN,
                error = null
            )
        }
    }
    return checkResult
}

@Composable
private fun ShowStatement(
    result: DocumentQueryResult,
    success: Boolean,
    message: String,
    revocationCheckResult: RevocationCheckResult? = null
) {
    val trustPoint = result.trustResult.trustPoints.firstOrNull()
    val isUnknownIssuer = trustPoint == null

    val (effectiveSuccess, effectiveMessage) = when {
        isUnknownIssuer -> Pair(
            false,
            stringResource(R.string.verification_show_response_unknown_issuer)
        )
        revocationCheckResult?.state == RevocationCheckState.INVALID -> Pair(
            false,
            stringResource(R.string.verification_show_response_credential_revoked)
        )
        revocationCheckResult?.state == RevocationCheckState.SUSPENDED -> Pair(
            false,
            stringResource(R.string.verification_show_response_credential_suspended)
        )
        else -> Pair(
            success,
            message
        )
    }

    val hasRevocationStatus = result.revocationStatus != null && result.revocationStatus !is RevocationStatus.Unknown

    val singleSuccessComposition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(R.raw.success_animation)
    )
    val singleSuccessProgressState = animateLottieCompositionAsState(
        composition = singleSuccessComposition,
        isPlaying = effectiveSuccess && !hasRevocationStatus
    )

    val doubleFirstComposition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(R.raw.success_double_first_animation)
    )
    val doubleFirstProgressState = animateLottieCompositionAsState(
        composition = doubleFirstComposition,
        isPlaying = effectiveSuccess && hasRevocationStatus
    )

    val isFirstAnimationDone = doubleFirstProgressState.isAtEnd || doubleFirstProgressState.value >= 1.0f
    val isRevocationValid = revocationCheckResult?.state == RevocationCheckState.VALID
    val playSecondAnimation = effectiveSuccess && hasRevocationStatus && isFirstAnimationDone && isRevocationValid

    val doubleSecondComposition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(R.raw.success_double_second_animation)
    )
    val doubleSecondProgressState = animateLottieCompositionAsState(
        composition = doubleSecondComposition,
        isPlaying = playSecondAnimation
    )

    val errorComposition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(R.raw.error_animation)
    )
    val errorProgressState = animateLottieCompositionAsState(
        composition = errorComposition,
        isPlaying = !effectiveSuccess
    )

    val painter = when {
        !effectiveSuccess -> rememberLottiePainter(
            composition = errorComposition,
            progress = errorProgressState.value
        )
        !hasRevocationStatus -> rememberLottiePainter(
            composition = singleSuccessComposition,
            progress = singleSuccessProgressState.value
        )
        playSecondAnimation -> rememberLottiePainter(
            composition = doubleSecondComposition,
            progress = doubleSecondProgressState.value
        )
        else -> rememberLottiePainter(
            composition = doubleFirstComposition,
            progress = doubleFirstProgressState.value
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.height(50.dp)
        )
        Text(
            text = effectiveMessage,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun TrustPoint.RenderImage(
    size: Dp,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    metadata.displayIcon?.let {
        val bitmap = remember { decodeImage(it.toByteArray()) }
        Image(
            modifier = modifier.size(size),
            bitmap = bitmap,
            contentDescription = null
        )
        return
    }

    metadata.displayIconUrl?.let { displayIconUrl ->
        if (displayIconUrl.isNotEmpty()) {
            AsyncImage(
                modifier = modifier.size(size),
                model = displayIconUrl,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                contentDescription = null
            )
            return
        }
    }

    metadata.displayName?.let { displayName ->
        if (displayName.isNotEmpty()) {
Branding.Current.collectAsState().value.AvatarIcon(
                size = size,
                name = displayName,
                additionalData = certificate.subjectKeyIdentifier
            )
            return
        }
    }

    Image(
        modifier = modifier.size(size),
        imageVector = Icons.Outlined.AccountBalance,
        contentDescription = null
    )
}

@Composable
private fun ShowSource(
    result: DocumentQueryResult,
    builtInIssuerTrustManagerModel: TrustManagerModel,
    userIssuerTrustManagerModel: TrustManagerModel,
    imageLoader: ImageLoader,
    revocationChecker: RevocationChecker? = null,
    revocationCheckResult: RevocationCheckResult? = null,
    onTrustEntryClicked: ((trustManagerId: String, trustEntryId: String) -> Unit)? = null
) {
    val trustPoint = result.trustResult.trustPoints.firstOrNull()
    val iconSize = 32.dp
    val builtInIssuerTrustManager = builtInIssuerTrustManagerModel.trustManager
    val userIssuerTrustManagerManager = userIssuerTrustManagerModel.trustManager

    val (trustManagerId, targetModel) = if (trustPoint?.trustManager == builtInIssuerTrustManager) {
        Pair("backendIssuerTrustManager", builtInIssuerTrustManagerModel)
    } else if (trustPoint?.trustManager == userIssuerTrustManagerManager) {
        Pair("userIssuerTrustManager", userIssuerTrustManagerModel)
    } else {
        Pair(null, null)
    }

    val infos = targetModel?.trustManagerInfos?.collectAsState()?.value
    val trustEntryId = remember(infos, trustPoint) {
        if (infos == null || trustPoint == null) null
        else {
            infos.find { info ->
                val entry = info.entry
                when (entry) {
                    is TrustEntryX509Cert -> entry.certificate == trustPoint.certificate
                    is TrustEntryVical -> info.signedVical?.vical?.certificateInfos?.any { it.certificate == trustPoint.certificate } == true
                    is TrustEntryRical -> info.signedRical?.rical?.certificateInfos?.any { it.certificate == trustPoint.certificate } == true
                    else -> false
                }
            }?.entry?.identifier
                ?: infos.find { it.entry.metadata.displayName == trustPoint.metadata.displayName }?.entry?.identifier
        }
    }

    val canClickTrustAnchor = trustManagerId != null && trustEntryId != null && onTrustEntryClicked != null

    FloatingItemList(title = stringResource(R.string.verification_show_response_source_title)) {
        if (result.documentType != null && result.issuingAuthority != null && result.issuingCountryCode != null) {
            val issuingCountry = Options.COUNTRY_ISO_3166_1_ALPHA_2.find {
                it.value == result.issuingCountryCode
            }?.displayName ?: result.issuingCountryCode
            FloatingItemHeadingAndText(
                image = {
                    if (trustPoint != null) {
                        trustPoint.RenderImage(
                            size = iconSize,
                            imageLoader = imageLoader,
                        )
                    } else {
                        Image(
                            modifier = Modifier.size(iconSize),
                            imageVector = Icons.Outlined.AccountBalance,
                            contentDescription = null
                        )
                    }
                },
                heading = stringResource(R.string.verification_show_response_issuer_heading),
                text = stringResource(
                    R.string.verification_show_response_issuer_text,
                    result.documentType!!.getDisplayName(),
                    result.issuingAuthority!!,
                    issuingCountry!!
                )
            )
        }

        val (message, isTrusted) = if (trustPoint?.trustManager == builtInIssuerTrustManager) {
            Pair(
                AnnotatedString(stringResource(R.string.verification_show_response_issuer_builtin_trust)),
                true
            )
        } else if (trustPoint?.trustManager == userIssuerTrustManagerManager) {
            Pair(
                AnnotatedString(stringResource(R.string.verification_show_response_issuer_user_trust)),
                true
            )
        } else {
            Pair(
                buildAnnotatedString {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                        append(stringResource(R.string.verification_show_response_no_trust_anchor))
                    }
                },
                false
            )
        }

        val trustAnchorPainter = if (isTrusted) {
            val composition by rememberLottieComposition(spec = LottieCompositionSpec.RawRes(R.raw.success_animation))
            val progressState = animateLottieCompositionAsState(composition = composition)
            rememberLottiePainter(
                composition = composition,
                progress = progressState.value,
            )
        } else {
            val composition by rememberLottieComposition(spec = LottieCompositionSpec.RawRes(R.raw.error_animation))
            val progressState = animateLottieCompositionAsState(composition = composition)
            rememberLottiePainter(
                composition = composition,
                progress = progressState.value,
            )
        }

        FloatingItemHeadingAndText(
            modifier = if (canClickTrustAnchor) {
                Modifier.clickable {
                    onTrustEntryClicked(trustManagerId, trustEntryId)
                }
            } else {
                Modifier
            },
            showChevron = canClickTrustAnchor,
            image = {
                Image(
                    painter = trustAnchorPainter,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
            heading = stringResource(R.string.verification_show_response_trust_anchor_heading),
            text = message,
        )

        val isResolving = revocationCheckResult == null && revocationChecker != null && result.revocationStatus != null && result.revocationStatus !is RevocationStatus.Unknown

        val revocationState = revocationCheckResult?.state ?: RevocationCheckState.UNKNOWN
        val isRevocationValid = revocationState == RevocationCheckState.VALID

        val revocationPainter = if (isRevocationValid) {
            val composition by rememberLottieComposition(spec = LottieCompositionSpec.RawRes(R.raw.success_animation))
            val progressState = animateLottieCompositionAsState(composition = composition)
            rememberLottiePainter(
                composition = composition,
                progress = progressState.value,
            )
        } else {
            val composition by rememberLottieComposition(spec = LottieCompositionSpec.RawRes(R.raw.error_animation))
            val progressState = animateLottieCompositionAsState(composition = composition)
            rememberLottiePainter(
                composition = composition,
                progress = progressState.value,
            )
        }

        val revocationMessage = if (isResolving) {
            AnnotatedString(stringResource(R.string.verification_show_response_revocation_status_checking))
        } else {
            when (revocationState) {
                RevocationCheckState.VALID -> AnnotatedString(
                    stringResource(R.string.verification_show_response_revocation_status_valid)
                )
                RevocationCheckState.INVALID -> buildAnnotatedString {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                        append(stringResource(R.string.verification_show_response_revocation_status_invalid))
                    }
                }
                RevocationCheckState.SUSPENDED -> buildAnnotatedString {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                        append(stringResource(R.string.verification_show_response_revocation_status_suspended))
                    }
                }
                RevocationCheckState.UNKNOWN -> AnnotatedString(
                    if (revocationCheckResult?.error != null) {
                        stringResource(R.string.verification_show_response_revocation_status_error)
                    } else {
                        stringResource(R.string.verification_show_response_revocation_status_unknown)
                    }
                )
            }
        }

        FloatingItemHeadingAndText(
            image = {
                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(iconSize)
                            .padding(4.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Image(
                        painter = revocationPainter,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                    )
                }
            },
            heading = stringResource(R.string.verification_show_response_revocation_status_heading),
            text = revocationMessage,
        )

        if (trustPoint?.metadata?.testOnly == true) {
            FloatingItemHeadingAndText(
                image = {
                    Icon(
                        imageVector = Icons.Outlined.Science,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                },
                heading = stringResource(R.string.verification_show_response_test_only_heading),
                text = AnnotatedString(stringResource(R.string.verification_show_response_test_only_text)),
            )
        }
    }
}

@Composable
private fun ShowAgeOverResult(
    query: AgeOverQuery,
    result: AgeOverDocumentQueryResult,
    builtInIssuerTrustManagerModel: TrustManagerModel,
    userIssuerTrustManagerModel: TrustManagerModel,
    imageLoader: ImageLoader,
    verificationLocation: Location?,
    verificationTime: Instant?,
    revocationChecker: RevocationChecker? = null,
    onTrustEntryClicked: ((trustManagerId: String, trustEntryId: String) -> Unit)? = null
) {
    val atTime = verificationTime ?: Clock.System.now()
    val revocationCheckResult = rememberRevocationCheckResult(result, revocationChecker, atTime)

    ShowPortrait(result.portrait)

    if (result.isAgeOver) {
        ShowStatement(
            result = result,
            success = true,
            message = stringResource(R.string.verification_show_response_age_over_success, query.ageOver),
            revocationCheckResult = revocationCheckResult
        )
    } else {
        ShowStatement(
            result = result,
            success = false,
            message = stringResource(R.string.verification_show_response_age_over_failure, query.ageOver),
            revocationCheckResult = revocationCheckResult
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    ShowSource(
        result = result,
        builtInIssuerTrustManagerModel = builtInIssuerTrustManagerModel,
        userIssuerTrustManagerModel = userIssuerTrustManagerModel,
        imageLoader = imageLoader,
        revocationChecker = revocationChecker,
        revocationCheckResult = revocationCheckResult,
        onTrustEntryClicked = onTrustEntryClicked
    )
    ShowEventDetails(verificationTime, verificationLocation)
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
fun ShowIdentificationResult(
    query: IdentificationQuery,
    result: IdentificationDocumentQueryResult,
    builtInIssuerTrustManagerModel: TrustManagerModel,
    userIssuerTrustManagerModel: TrustManagerModel,
    imageLoader: ImageLoader,
    verificationLocation: Location?,
    verificationTime: Instant?,
    revocationChecker: RevocationChecker? = null,
    onTrustEntryClicked: ((trustManagerId: String, trustEntryId: String) -> Unit)? = null
) {
    val atTime = verificationTime ?: Clock.System.now()
    val revocationCheckResult = rememberRevocationCheckResult(result, revocationChecker, atTime)

    ShowPortrait(result.portrait)

    ShowStatement(
        result = result,
        success = true,
        message = stringResource(R.string.verification_show_response_verified),
        revocationCheckResult = revocationCheckResult
    )

    Spacer(modifier = Modifier.height(20.dp))
    FloatingItemList {
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.verification_show_response_name_heading),
            text = result.name
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.verification_show_response_birth_date_heading),
            text = result.birthDate.formatLocalized(FormatStyle.LONG)
        )
        result.streetAddress?.let {
            FloatingItemHeadingAndText(
                heading = stringResource(R.string.verification_show_response_street_address_heading),
                text = it
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    ShowSource(
        result = result,
        builtInIssuerTrustManagerModel = builtInIssuerTrustManagerModel,
        userIssuerTrustManagerModel = userIssuerTrustManagerModel,
        imageLoader = imageLoader,
        revocationChecker = revocationChecker,
        revocationCheckResult = revocationCheckResult,
        onTrustEntryClicked = onTrustEntryClicked
    )
    ShowEventDetails(verificationTime, verificationLocation)
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
fun ShowDrivingPrivilegesResult(
    query: DrivingPrivilegesQuery,
    result: DrivingPrivilegesDocumentQueryResult,
    builtInIssuerTrustManagerModel: TrustManagerModel,
    userIssuerTrustManagerModel: TrustManagerModel,
    imageLoader: ImageLoader,
    verificationLocation: Location?,
    verificationTime: Instant?,
    revocationChecker: RevocationChecker? = null,
    onTrustEntryClicked: ((trustManagerId: String, trustEntryId: String) -> Unit)? = null
) {
    val atTime = verificationTime ?: Clock.System.now()
    val revocationCheckResult = rememberRevocationCheckResult(result, revocationChecker, atTime)

    ShowPortrait(result.portrait)


    ShowStatement(
        result = result,
        success = true,
        message = stringResource(R.string.request_verification_driving_privileges_verified),
        revocationCheckResult = revocationCheckResult
    )

    Spacer(modifier = Modifier.height(20.dp))
    FloatingItemList {
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.verification_show_response_name_heading),
            text = result.name
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.verification_show_response_birth_date_heading),
            text = result.birthDate.formatLocalized(FormatStyle.LONG)
        )
    }

    if (result.drivingPrivilegesList.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        ShowDrivingPrivileges(result.drivingPrivilegesList)
    }

    Spacer(modifier = Modifier.height(20.dp))
    ShowSource(
        result = result,
        builtInIssuerTrustManagerModel = builtInIssuerTrustManagerModel,
        userIssuerTrustManagerModel = userIssuerTrustManagerModel,
        imageLoader = imageLoader,
        revocationChecker = revocationChecker,
        revocationCheckResult = revocationCheckResult,
        onTrustEntryClicked = onTrustEntryClicked
    )
    ShowEventDetails(verificationTime, verificationLocation)
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
fun ShowDrivingPrivileges(privileges: List<DrivingPrivilege>) {
    FloatingItemList(title = stringResource(R.string.verification_show_response_driving_privileges_heading)) {
        for (privilege in privileges) {
            val details = mutableListOf<String>()
            privilege.issueDate?.let {
                details.add("Issue: ${it.formatLocalized(FormatStyle.LONG)}")
            }
            privilege.expiryDate?.let {
                details.add("Expiry: ${it.formatLocalized(FormatStyle.LONG)}")
            }
            if (privilege.codes.isNotEmpty()) {
                val formattedCodes = privilege.codes.joinToString(", ") { code ->
                    buildString {
                        append(code.code)
                        code.sign?.let { append(" ").append(it) }
                        code.value?.let { append(" ").append(it) }
                    }
                }
                details.add("Codes: $formattedCodes")
            }
            FloatingItemHeadingAndText(
                heading = "Category ${privilege.vehicleCategoryCode}",
                text = if (details.isNotEmpty()) details.joinToString("\n") else "Valid"
            )
        }
    }
}



@Composable
private fun ShowEventDetails(
    verificationTime: Instant?,
    verificationLocation: Location?
) {
    if (verificationTime != null || verificationLocation != null) {
        Spacer(modifier = Modifier.height(20.dp))
        FloatingItemList(title = stringResource(R.string.verification_show_response_verified_at)) {
            if (verificationTime != null) {
                val eventDateTime = verificationTime.toLocalDateTime(timeZone = TimeZone.currentSystemDefault())
                val eventDateTimeString = eventDateTime.formatLocalized(
                    dateStyle = FormatStyle.LONG,
                    timeStyle = FormatStyle.LONG
                )
                FloatingItemHeadingAndText(
                    heading = "Time",
                    text = eventDateTimeString
                )
            }
            if (verificationLocation != null) {
                var address by remember { mutableStateOf<String?>(null) }
                var isLoadingAddress by remember { mutableStateOf(true) }
                val localContext = LocalContext.current
                LaunchedEffect(verificationLocation) {
                    address = verificationLocation.getAddressFromCoordinates(localContext)
                    isLoadingAddress = false
                }
                val coordinates = "${verificationLocation.latitude}, ${verificationLocation.longitude}"
                FloatingItemHeadingAndContent(
                    heading = "Location",
                    content = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MapView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                location = verificationLocation
                            )
                            if (isLoadingAddress) {
                                Text(
                                    text = stringResource(R.string.verification_show_response_looking_up_address_fmt, coordinates),
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
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        modifier = Modifier.clickable {
                                            val geoUri = if (address != null) {
                                                "geo:${verificationLocation.latitude},${verificationLocation.longitude}?q=${Uri.encode(address)}"
                                            } else {
                                                "geo:${verificationLocation.latitude},${verificationLocation.longitude}"
                                            }
                                            localContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)))
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ShowUserDefinedResult(
    query: org.multipaz.wallet.client.verification.UserDefinedQuery,
    result: org.multipaz.wallet.client.verification.UserDefinedDocumentQueryResult,
    builtInIssuerTrustManagerModel: TrustManagerModel,
    userIssuerTrustManagerModel: TrustManagerModel,
    imageLoader: ImageLoader,
    verificationLocation: Location?,
    verificationTime: Instant?,
    revocationChecker: RevocationChecker? = null,
    onTrustEntryClicked: ((trustManagerId: String, trustEntryId: String) -> Unit)? = null
) {
    val atTime = verificationTime ?: Clock.System.now()
    val revocationCheckResult = rememberRevocationCheckResult(result, revocationChecker, atTime)

    val portrait = remember(result) { extractPortraitImage(result) }
    if (portrait != null) {
        ShowPortrait(portrait)
    }

    ShowStatement(
        result = result,
        success = true,
        message = stringResource(R.string.reader_query_user_defined),
        revocationCheckResult = revocationCheckResult
    )

    Spacer(modifier = Modifier.height(20.dp))
    FloatingItemList(
        title = stringResource(R.string.select_user_defined_query_dialog_doc_type)
    ) {
        FloatingItemText(
            text = result.docType
        )
    }

    result.elements.forEach { (namespace, elements) ->
        Spacer(modifier = Modifier.height(20.dp))
        FloatingItemList(
            title = stringResource(R.string.verification_show_response_developer_extras_namespace, namespace)
        ) {
            elements.forEach { (dataElement, value) ->
                val displayValue = try {
                    val bstr = value.asBstr
                    if (isJpegOrPng(bstr)) {
                        "Image (${bstr.size} bytes)"
                    } else if (bstr.size > 256) {
                        "Binary (${bstr.size} bytes)"
                    } else {
                        try {
                            value.toCdn()
                        } catch (_: Throwable) {
                            value.toString()
                        }
                    }
                } catch (_: Throwable) {
                    try {
                        value.toCdn()
                    } catch (_: Throwable) {
                        value.toString()
                    }
                }
                FloatingItemHeadingAndText(
                    heading = dataElement,
                    text = displayValue
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    ShowSource(
        result = result,
        builtInIssuerTrustManagerModel = builtInIssuerTrustManagerModel,
        userIssuerTrustManagerModel = userIssuerTrustManagerModel,
        imageLoader = imageLoader,
        onTrustEntryClicked = onTrustEntryClicked
    )
    ShowEventDetails(verificationTime, verificationLocation)
    Spacer(modifier = Modifier.height(20.dp))
}

private fun isJpeg2000(bytes: ByteArray): Boolean {
    val isJpeg2000Jp2 = bytes.size >= 12 &&
            (bytes[0].toInt() and 0xFF == 0x00) &&
            (bytes[1].toInt() and 0xFF == 0x00) &&
            (bytes[2].toInt() and 0xFF == 0x00) &&
            (bytes[3].toInt() and 0xFF == 0x0C) &&
            (bytes[4].toInt() and 0xFF == 0x6A) &&
            (bytes[5].toInt() and 0xFF == 0x50) &&
            (bytes[6].toInt() and 0xFF == 0x20) &&
            (bytes[7].toInt() and 0xFF == 0x20) &&
            (bytes[8].toInt() and 0xFF == 0x0D) &&
            (bytes[9].toInt() and 0xFF == 0x0A) &&
            (bytes[10].toInt() and 0xFF == 0x87) &&
            (bytes[11].toInt() and 0xFF == 0x0A)
    val isJpeg2000J2k = bytes.size >= 4 &&
            (bytes[0].toInt() and 0xFF == 0xFF) &&
            (bytes[1].toInt() and 0xFF == 0x4F) &&
            (bytes[2].toInt() and 0xFF == 0xFF) &&
            (bytes[3].toInt() and 0xFF == 0x51)
    return isJpeg2000Jp2 || isJpeg2000J2k
}

private fun isJpegOrPng(bytes: ByteArray): Boolean {
    if (bytes.size < 500) return false
    val isJpeg = bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF == 0xFF) &&
            (bytes[1].toInt() and 0xFF == 0xD8) &&
            (bytes[2].toInt() and 0xFF == 0xFF)
    val isPng = bytes.size >= 8 &&
            (bytes[0].toInt() and 0xFF == 0x89) &&
            (bytes[1].toInt() and 0xFF == 0x50) &&
            (bytes[2].toInt() and 0xFF == 0x4E) &&
            (bytes[3].toInt() and 0xFF == 0x47) &&
            (bytes[4].toInt() and 0xFF == 0x0D) &&
            (bytes[5].toInt() and 0xFF == 0x0A) &&
            (bytes[6].toInt() and 0xFF == 0x1A) &&
            (bytes[7].toInt() and 0xFF == 0x0A)
    return isJpeg || isPng || isJpeg2000(bytes)
}

private fun extractPortraitImage(result: org.multipaz.wallet.client.verification.UserDefinedDocumentQueryResult): ByteString? {
    for ((_, elements) in result.elements) {
        for ((dataElement, value) in elements) {
            if (dataElement.equals("portrait", ignoreCase = true)) {
                try {
                    val bytes = value.asBstr
                    if (isJpegOrPng(bytes)) {
                        return ByteString(bytes)
                    }
                } catch (_: Exception) {}
            }
        }
    }
    return null
}