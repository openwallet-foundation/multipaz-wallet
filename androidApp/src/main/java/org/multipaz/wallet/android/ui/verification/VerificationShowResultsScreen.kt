package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottiePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.multipaz.compose.decodeImage
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.datetime.FormatStyle
import org.multipaz.datetime.formatLocalized
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.trustmanagement.TrustResult
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.AgeOverQuery
import org.multipaz.wallet.client.IdentificationQuery
import org.multipaz.wallet.client.ProximityReaderModel

private const val TAG = "VerificationShowResultsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationShowResultsScreen(
    proximityReaderModel: ProximityReaderModel,
    issuerTrustManager: TrustManagerInterface,
    settingsModel: SettingsModel,
    onBackClicked: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val result = proximityReaderModel.result!!
    val ageOverQueryResult = remember { mutableStateOf<AgeOverQuery.Result?>(null) }
    val identificationQueryResult = remember { mutableStateOf<IdentificationQuery.Result?>(null) }
    val parsingResponseFailed = remember { mutableStateOf<Exception?>(null) }
    val showNotTrusted = remember { mutableStateOf(false) }
    val devModeEnabled = settingsModel.devMode.collectAsState().value

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
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
            verticalArrangement = Arrangement.Center
        ) {
            if (parsingResponseFailed.value != null) {
                ShowParsingFailedError(parsingResponseFailed.value!!)
            } else {
                when (result.readerQuery!!) {
                    is AgeOverQuery -> {
                        LaunchedEffect(Unit) {
                            coroutineScope.launch {
                                try {
                                    ageOverQueryResult.value = (result.readerQuery as AgeOverQuery).processResponse(
                                        deviceResponse = result.deviceResponse!!,
                                        sessionTranscript = result.sessionTranscript,
                                        eReaderKey = AsymmetricKey.AnonymousExplicit(result.eReaderKey),
                                        issuerTrustManager = issuerTrustManager
                                    )
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Logger.e(TAG, "Error parsing response", e)
                                    parsingResponseFailed.value = e
                                }
                            }
                        }
                        ageOverQueryResult.value?.let { result ->
                            if (result.trustResult.isTrusted || showNotTrusted.value) {
                                ShowAgeOverResult(result)
                            } else {
                                ShowNotTrusted(
                                    trustResult = result.trustResult,
                                    devModeEnabled = devModeEnabled,
                                    showNotTrusted = showNotTrusted
                                )
                            }
                        }
                    }

                    is IdentificationQuery -> {
                        LaunchedEffect(Unit) {
                            coroutineScope.launch {
                                try {
                                    identificationQueryResult.value = (result.readerQuery as IdentificationQuery).processResponse(
                                        deviceResponse = result.deviceResponse!!,
                                        sessionTranscript = result.sessionTranscript,
                                        eReaderKey = AsymmetricKey.AnonymousExplicit(result.eReaderKey),
                                        issuerTrustManager = issuerTrustManager
                                    )
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Logger.e(TAG, "Error parsing response", e)
                                    parsingResponseFailed.value = e
                                }
                            }
                        }
                        identificationQueryResult.value?.let { result ->
                            if (result.trustResult.isTrusted || showNotTrusted.value) {
                                ShowIdentificationResult(result)
                            } else {
                                ShowNotTrusted(
                                    trustResult = result.trustResult,
                                    devModeEnabled = devModeEnabled,
                                    showNotTrusted = showNotTrusted
                                )
                            }
                        }
                    }
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
        text = "Error processing response",
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
            text = "The returned document is from an unknown issuer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ShowAgeOverResult(
    result: AgeOverQuery.Result
) {
    val portraitBitmap = remember { decodeImage(result.portrait.toByteArray()) }

    Image(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp).padding(16.dp),
        bitmap = portraitBitmap,
        contentDescription = null
    )

    val message = if (result.isAgeOver) {
        "This person is older that ${result.query.ageOver}"
    } else {
        "This person is NOT older than ${result.query.ageOver}"
    }

    val painter = if (result.isAgeOver) {
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
fun ShowIdentificationResult(
    result: IdentificationQuery.Result
) {
    val portraitBitmap = remember { decodeImage(result.portrait.toByteArray()) }

    Spacer(modifier = Modifier.height(20.dp))
    FloatingItemList {
        FloatingItemHeadingAndContent(
            heading = "Portrait",
            content = {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Image(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        bitmap = portraitBitmap,
                        contentDescription = null
                    )
                }
            }
        )
        FloatingItemHeadingAndText(
            heading = "Given name",
            text = result.givenName
        )
        FloatingItemHeadingAndText(
            heading = "Family name",
            text = result.familyName
        )
        FloatingItemHeadingAndText(
            heading = "Birth date",
            text = result.birthDate.formatLocalized(FormatStyle.LONG)
        )
    }
    Spacer(modifier = Modifier.height(20.dp))

    val composition by rememberLottieComposition(spec = LottieCompositionSpec.RawRes(R.raw.success_animation))
    val progressState = animateLottieCompositionAsState(composition = composition)

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberLottiePainter(
                composition = composition,
                progress = progressState.value,
            ),
            contentDescription = null,
            modifier = Modifier.size(50.dp)
        )

        Text(
            text = "Verified identification data",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}