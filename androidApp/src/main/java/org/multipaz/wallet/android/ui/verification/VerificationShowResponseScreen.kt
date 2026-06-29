package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Science
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
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.decodeImage
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.datetime.FormatStyle
import org.multipaz.datetime.formatLocalized
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.Options
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.trustmanagement.TrustResult
import org.multipaz.util.Logger
import org.multipaz.verification.PresentmentRecord
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.verification.AgeOverDocumentQueryResult
import org.multipaz.wallet.client.verification.AgeOverQuery
import org.multipaz.wallet.client.verification.DocumentQueryResult
import org.multipaz.wallet.client.verification.IdentificationDocumentQueryResult
import org.multipaz.wallet.client.verification.IdentificationQuery
import org.multipaz.wallet.client.verification.Query
import org.multipaz.wallet.client.verification.Result
import kotlin.time.Clock

private const val TAG = "VerificationShowResponseScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationShowResponseScreen(
    query: Query,
    presentmentRecord: PresentmentRecord,
    documentTypeRepository: DocumentTypeRepository,
    zkSystemRepository: ZkSystemRepository,
    issuerTrustManager: CompositeTrustManager,
    builtInIssuerTrustManager: TrustManagerInterface,
    userIssuerTrustManagerManager: TrustManagerInterface,
    settingsModel: SettingsModel,
    imageLoader: ImageLoader,
    onDeveloperExtrasClicked: () -> Unit,
    onBackClicked: () -> Unit,
    showNotTrusted: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val queryResult = remember { mutableStateOf<Result?>(null) }
    val parsingResponseFailed = remember { mutableStateOf<Exception?>(null) }
    val showNotTrustedState = remember { mutableStateOf(showNotTrusted) }
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
                    coroutineScope.launch {
                        try {
                            val verifiedPresentations = presentmentRecord.verify(
                                atTime = Clock.System.now(),
                                documentTypeRepository = documentTypeRepository,
                                zkSystemRepository = zkSystemRepository
                            )
                            queryResult.value = query.processVerifiedPresentations(
                                verifiedPresentation = verifiedPresentations,
                                issuerTrustManager = issuerTrustManager
                            )
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Logger.e(TAG, "Error parsing response", e)
                            parsingResponseFailed.value = e
                        }
                    }
                }

                when (queryResult.value?.query) {
                    is AgeOverQuery -> {
                        val result = queryResult.value!!.documents.first()
                        if (result.trustResult.isTrusted || showNotTrustedState.value) {
                            ShowAgeOverResult(
                                query = queryResult.value!!.query as AgeOverQuery,
                                result = result as AgeOverDocumentQueryResult,
                                builtInIssuerTrustManager = builtInIssuerTrustManager,
                                userIssuerTrustManagerManager = userIssuerTrustManagerManager,
                                imageLoader = imageLoader
                            )
                        } else {
                            ShowNotTrusted(
                                trustResult = result.trustResult,
                                devModeEnabled = devModeEnabled,
                                showNotTrusted = showNotTrustedState
                            )
                        }
                    }

                    is IdentificationQuery -> {
                        val result = queryResult.value!!.documents.first()
                        if (result.trustResult.isTrusted || showNotTrustedState.value) {
                            ShowIdentificationResult(
                                query = queryResult.value!!.query as IdentificationQuery,
                                result = result as IdentificationDocumentQueryResult,
                                builtInIssuerTrustManager = builtInIssuerTrustManager,
                                userIssuerTrustManagerManager = userIssuerTrustManagerManager,
                                imageLoader = imageLoader
                            )
                        } else {
                            ShowNotTrusted(
                                trustResult = result.trustResult,
                                devModeEnabled = devModeEnabled,
                                showNotTrusted = showNotTrustedState
                            )
                        }
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
private fun ShowNotTrusted(
    trustResult: TrustResult,
    devModeEnabled: Boolean,
    showNotTrusted: MutableState<Boolean>
) {
    Column(
        modifier = Modifier.let {
            if (devModeEnabled) {
                it.combinedClickable(
                    onClick = {},
                    onDoubleClick = { showNotTrusted.value = true }
                )
            } else it
        },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
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
            text = stringResource(R.string.verification_show_response_unknown_issuer),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
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
            contentDescription = null,
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
private fun ShowStatement(
    success: Boolean,
    message: String
) {
    val painter = if (success) {
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

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(50.dp)
        )
        Text(
            text = message,
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

    metadata.displayIconUrl?.let {
        AsyncImage(
            modifier = modifier.size(size),
            model = it,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            contentDescription = null
        )
        return
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
    builtInIssuerTrustManager: TrustManagerInterface,
    userIssuerTrustManagerManager: TrustManagerInterface,
    imageLoader: ImageLoader
) {
    val trustPoint = result.trustResult.trustPoints.firstOrNull()
    val iconSize = 32.dp

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

        if (trustPoint?.metadata?.testOnly == true) {
            val composition by rememberLottieComposition(spec = LottieCompositionSpec.RawRes(R.raw.error_animation))
            val progressState = animateLottieCompositionAsState(composition = composition)
            FloatingItemHeadingAndText(
                image = {
                    Image(
                        painter = rememberLottiePainter(
                            composition = composition,
                            progress = progressState.value,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                    )
                },
                heading = stringResource(R.string.verification_show_response_test_only_heading),
                text = stringResource(R.string.verification_show_response_test_only_text)
            )
        }

        //Logger.i(TAG, "Revocation Status: ${result.revocationStatus}")
        //if (result.revocationStatus != null) {
        //}
    }
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun ShowAgeOverResult(
    query: AgeOverQuery,
    result: AgeOverDocumentQueryResult,
    builtInIssuerTrustManager: TrustManagerInterface,
    userIssuerTrustManagerManager: TrustManagerInterface,
    imageLoader: ImageLoader
) {
    ShowPortrait(result.portrait)

    if (result.isAgeOver) {
        ShowStatement(
            success = true,
            message = stringResource(R.string.verification_show_response_age_over_success, query.ageOver)
        )
    } else {
        ShowStatement(
            success = false,
            message = stringResource(R.string.verification_show_response_age_over_failure, query.ageOver)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    ShowSource(
        result = result,
        builtInIssuerTrustManager = builtInIssuerTrustManager,
        userIssuerTrustManagerManager = userIssuerTrustManagerManager,
        imageLoader = imageLoader
    )
}

@Composable
fun ShowIdentificationResult(
    query: IdentificationQuery,
    result: IdentificationDocumentQueryResult,
    builtInIssuerTrustManager: TrustManagerInterface,
    userIssuerTrustManagerManager: TrustManagerInterface,
    imageLoader: ImageLoader
) {
    ShowPortrait(result.portrait)

    ShowStatement(
        success = true,
        message = stringResource(R.string.verification_show_response_verified)
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
        builtInIssuerTrustManager = builtInIssuerTrustManager,
        userIssuerTrustManagerManager = userIssuerTrustManagerManager,
        imageLoader = imageLoader
    )
}