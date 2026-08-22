package org.multipaz.wallet.android.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarColors
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottiePainter
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.multipaz.compose.cards.VerticalCardList
import org.multipaz.compose.prompt.PresentmentActivity
import org.multipaz.wallet.android.App
import org.multipaz.wallet.android.MainActivity
import org.multipaz.wallet.android.WalletCombinedNfcService
import org.multipaz.compose.cards.VerticalCardListState
import org.multipaz.compose.cards.WarningCard
import org.multipaz.compose.document.DocumentInfo
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.compose.text.fromMarkdown
import android.Manifest
import android.os.Build
import androidx.compose.material3.DividerDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import org.multipaz.compose.cards.InfoCard
import org.multipaz.document.DocumentStore
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.getPendingIntentForLaunchingQuickAccessWallet
import org.multipaz.wallet.android.hasPortrait
import org.multipaz.wallet.android.isProximityPresentable
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.DocumentPreconsentSetting
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.isSyncing
import org.multipaz.wallet.client.preconsentSetting
import org.multipaz.wallet.client.provisionedDocumentSetupNeeded
import org.multipaz.wallet.shared.BuildConfig
import org.multipaz.wallet.shared.Domains
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val TAG = "WalletScreen"

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalPermissionsApi::class
)
@Composable
fun WalletScreen(
    verticalCardListState: VerticalCardListState,
    walletClient: WalletClient,
    documentStore: DocumentStore,
    documentModel: DocumentModel,
    settingsModel: SettingsModel,
    focusedDocumentId: String?,
    justAdded: Boolean,
    onAvatarClicked: () -> Unit,
    onAddClicked: () -> Unit,
    onVerifyClicked: () -> Unit,
    onDocumentClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentQrClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentShareClicked: (documentInfo: DocumentInfo) -> Unit = {},
    onDocumentActivityClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentInfoClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentInfoExtrasClicked: (documentInfo: DocumentInfo) -> Unit = {},
    onDocumentRemoveClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentSetupClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentSyncClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentPreconsentSettingsClicked: (documentInfo: DocumentInfo) -> Unit,
    onBackClicked: () -> Unit,
    onRefresh: suspend () -> Unit = {},
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    var devModeNumTimesPressed by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val blePermissionState = rememberBluetoothPermissionState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val signedIn = walletClient.signedInUser.collectAsState().value

    val focusedDocument = documentModel.documentInfos.collectAsState().value.find { documentInfo ->
        documentInfo.document.identifier == focusedDocumentId
    }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeightDp = with(density) {
        windowInfo.containerSize.height.toDp()
    }
    val maxCardHeight = screenHeightDp / 3

    // Local state to drive animations since navigation swap is instant
    var titleAndFabVisible by remember { mutableStateOf(focusedDocumentId == null) }
    LaunchedEffect(focusedDocumentId) {
        titleAndFabVisible = focusedDocumentId == null
    }

    Scaffold(
        topBar = {
            AppCenterAlignedTopAppBar(
                hazeState = hazeState,
                title = {
                    AnimatedVisibility(
                        visible = titleAndFabVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        if (BuildConfig.DEVELOPER_MODE_AVAILABLE) {
                            Column(
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (settingsModel.devMode.value) {
                                            showToast(context.getString(R.string.wallet_screen_dev_mode_already_enabled))
                                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                        } else {
                                            if (devModeNumTimesPressed == 4) {
                                                showToast(context.getString(R.string.wallet_screen_dev_mode_enabled))
                                                settingsModel.devMode.value = true
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            } else {
                                                val tapsRemaining = 4 - devModeNumTimesPressed
                                                if (tapsRemaining > 1) {
                                                    showToast(context.getString(R.string.wallet_screen_dev_mode_taps_remaining, tapsRemaining))
                                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                                } else {
                                                    showToast(context.getString(R.string.wallet_screen_dev_mode_taps_remaining_1))
                                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                                }
                                                devModeNumTimesPressed += 1
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (settingsModel.devMode.value) {
                                            coroutineScope.launch {
                                                try {
                                                    val app = App.getInstance()
                                                    val source = App.getPresentmentSource()
                                                    val pendingIntent = getPendingIntentForLaunchingQuickAccessWallet(
                                                        source = source,
                                                        initiallySelectedDocumentId = null,
                                                        app = app
                                                    )
                                                    pendingIntent.send()
                                                } catch (e: Exception) {
                                                    if (e is CancellationException) throw e
                                                    Logger.e(TAG, "Failed to launch quick access wallet", e)
                                                }
                                            }
                                        }
                                    }
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = BuildConfig.APP_NAME,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                val walletBackendUrl = settingsModel.walletBackendUrl.collectAsState().value
                                if (walletBackendUrl != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.wallet_screen_backend_url_format, walletBackendUrl),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = BuildConfig.APP_NAME,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    Row {
                        Box {
                            this@Row.AnimatedVisibility(
                                visible = !titleAndFabVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                IconButton(onClick = {
                                    onBackClicked()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                            }
                            this@Row.AnimatedVisibility(
                                visible = titleAndFabVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Icon(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(32.dp),
                                    painter = painterResource(R.drawable.app_icon),
                                    contentDescription = null,
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                },
                actions = {
                    Row {
                        Box {
                            this@Row.AnimatedVisibility(
                                visible = !titleAndFabVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Row {
                                    if (focusedDocument?.document?.mpzPassId != null) {
                                        IconButton(
                                            onClick = { onDocumentShareClicked(focusedDocument) }
                                        ) {
                                            Icon(
                                                modifier = Modifier.size(32.dp),
                                                imageVector = Icons.Outlined.Share,
                                                contentDescription = stringResource(R.string.wallet_screen_share_pass_content_description),
                                            )
                                        }
                                    }
                                    if (focusedDocument?.isProximityPresentable == true) {
                                        IconButton(
                                            onClick = { onDocumentQrClicked(focusedDocument) }
                                        ) {
                                            Icon(
                                                modifier = Modifier.size(32.dp),
                                                imageVector = Icons.Outlined.QrCode2,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            }
                            this@Row.AnimatedVisibility(
                                visible = titleAndFabVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                IconButton(
                                    onClick = { onAvatarClicked() }
                                ) {
                                    if (signedIn != null) {
                                        signedIn.ProfilePicture(size = 32.dp)
                                    } else {
                                        Icon(
                                            modifier = Modifier.size(32.dp),
                                            imageVector = Icons.Outlined.AccountCircle,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = titleAndFabVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                // Add padding to provide space for the shadow to draw during animation
                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    HorizontalFloatingToolbar(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        expanded = true,
                        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                        expandedShadowElevation = 6.dp
                    ) {
                        TextButton(
                            onClick = onVerifyClicked,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.wallet_screen_verify))
                        }
                        VerticalDivider(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        TextButton(
                            onClick = onAddClicked,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current)
                    // Omitting top padding so the card list extends up under the CenterAlignedTopAppBar
                    // Omitting the bottom padding since we want to draw under the navigation bar
                )
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!blePermissionState.isGranted) {
                WarningCard(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .padding(16.dp)
                        .clickable {
                            coroutineScope.launch {
                                blePermissionState.launchPermissionRequest()
                            }
                        }
                ) {
                    Text(stringResource(R.string.wallet_screen_ble_permission_warning))
                }
            }

            AppUpdateCard()

            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    coroutineScope.launch {
                        try {
                            onRefresh()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Logger.e(TAG, "Error refreshing data", e)
                            showToast(e.toString())
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                translationY = with(density) { innerPadding.calculateTopPadding().toPx() }
                            }
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .hazeSource(hazeState)
            ) {
                val cardInfos by documentModel.documentInfos.collectAsState()
                VerticalCardList(
                    modifier = Modifier.fillMaxSize(),
                    cardInfos = cardInfos,
                    focusedCard = focusedDocument,
                    allowCardReordering = true,
                    showStackWhileFocused = false,
                    cardMaxHeight = maxCardHeight,
                    paddingTop = innerPadding.calculateTopPadding(),
                    state = verticalCardListState,
                    showCardInfo = { cardInfo ->
                        val documentInfo = cardInfo as DocumentInfo
                        DocumentInfoContent(
                            documentInfo = documentInfo,
                            settingsModel = settingsModel,
                            isSyncing = signedIn != null && documentInfo.document.isSyncing,
                            justAdded = justAdded,
                            onDocumentActivityClicked = onDocumentActivityClicked,
                            onDocumentInfoClicked = onDocumentInfoClicked,
                            onDocumentInfoExtrasClicked = onDocumentInfoExtrasClicked,
                            onDocumentRemoveClicked = onDocumentRemoveClicked,
                            onDocumentSetupClicked = onDocumentSetupClicked,
                            onDocumentSyncClicked = onDocumentSyncClicked,
                            onDocumentPreconsentSettingsClicked = onDocumentPreconsentSettingsClicked
                        )
                    },
                    emptyContent = {
                        EmptyWalletStateContent()
                    },
                    onCardReordered = { cardInfo, newIndex ->
                        val documentInfo = cardInfo as DocumentInfo
                        coroutineScope.launch {
                            try {
                                documentModel.setDocumentPosition(
                                    documentInfo = documentInfo,
                                    position = newIndex
                                )
                            } catch (e: IllegalArgumentException) {
                                Logger.e(TAG, "Error setting document position", e)
                            }
                        }
                    },
                    onCardFocused = { cardInfo ->
                        val documentInfo = cardInfo as DocumentInfo
                        onDocumentClicked(documentInfo)
                    },
                    onCardFocusedTapped =  { cardInfo ->
                        onBackClicked()
                    },
                    onCardFocusedStackTapped =  { cardInfo -> }
                )
            }
        }
    }
}

@Composable
private fun JustAdded() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
    ) {
        val composition by rememberLottieComposition(
            spec = LottieCompositionSpec.RawRes(R.raw.success_animation)
        )
        val progressState = animateLottieCompositionAsState(
            composition = composition
        )
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Image(
                painter = rememberLottiePainter(
                    composition = composition,
                    progress = progressState.value,
                ),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.wallet_screen_pass_was_added),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DocumentInfoContent(
    documentInfo: DocumentInfo,
    settingsModel: SettingsModel,
    isSyncing: Boolean,
    justAdded: Boolean,
    onDocumentActivityClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentInfoClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentInfoExtrasClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentRemoveClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentSetupClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentSyncClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentPreconsentSettingsClicked: (documentInfo: DocumentInfo) -> Unit
) {
    var showJustAdded by remember { mutableStateOf(justAdded) }

    Crossfade(
        targetState = showJustAdded,
        label = "JustAddedCrossfade",
        animationSpec = tween(durationMillis = 500)
    ) { isShowingJustAdded ->
        if (isShowingJustAdded) {
            LaunchedEffect(Unit) {
                delay(3.seconds)
                showJustAdded = false
            }
            JustAdded()
        } else {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DocumentInfoContentReal(
                    documentInfo = documentInfo,
                    settingsModel = settingsModel,
                    isSyncing = isSyncing,
                    onDocumentActivityClicked = onDocumentActivityClicked,
                    onDocumentInfoClicked = onDocumentInfoClicked,
                    onDocumentInfoExtrasClicked = onDocumentInfoExtrasClicked,
                    onDocumentRemoveClicked = onDocumentRemoveClicked,
                    onDocumentSetupClicked = onDocumentSetupClicked,
                    onDocumentSyncClicked = onDocumentSyncClicked,
                    onDocumentPreconsentSettingsClicked = onDocumentPreconsentSettingsClicked
                )
            }
        }
    }
}

@Composable
private fun DocumentInfoContentReal(
    documentInfo: DocumentInfo,
    settingsModel: SettingsModel,
    isSyncing: Boolean,
    onDocumentActivityClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentInfoClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentInfoExtrasClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentRemoveClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentSetupClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentSyncClicked: (documentInfo: DocumentInfo) -> Unit,
    onDocumentPreconsentSettingsClicked: (documentInfo: DocumentInfo) -> Unit
) {
    val iconSize = 24.dp
    if (documentInfo.isProximityPresentable) {
        var showHoldToReader by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(0.5.seconds)
            showHoldToReader = true
        }
        Box(
            modifier = Modifier.height(22.dp)
        ) {
            val alpha by animateFloatAsState(
                targetValue = if (showHoldToReader) 1f else 0f,
                animationSpec = tween(1000),
                label = "HoldToReaderFade"
            )
            Row(
                modifier = Modifier
                    .offset(y = (-20).dp)
                    .alpha(alpha),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Contactless,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.wallet_screen_hold_to_reader),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        FloatingItemList {
            val typeDisplayName = documentInfo.document.typeDisplayName
                ?: stringResource(R.string.wallet_screen_document_type_name_fallback)
            if (isSyncing) {
                val devMode = settingsModel.devMode.collectAsState().value
                val setupNeeded = documentInfo.document.provisionedDocumentSetupNeeded
                val syncedSecondaryText = if (setupNeeded) {
                    stringResource(R.string.wallet_screen_setup)
                } else {
                    // TODO: Update to "Available across N devices" when we keep better track of
                    //   devices currently signed in
                    stringResource(R.string.wallet_screen_ready_to_use_on_this_device)
                }
                FloatingItemText(
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (setupNeeded) {
                                onDocumentSetupClicked(documentInfo)
                            } else {
                                onDocumentSyncClicked(documentInfo)
                            }
                        },
                        onLongClick = if (devMode && setupNeeded) {
                            { onDocumentInfoExtrasClicked(documentInfo) }
                        } else null
                    ),
                    showChevron = true,
                    text = stringResource(R.string.wallet_screen_syncs_to_account),
                    secondary = syncedSecondaryText,
                    image = {
                        Icon(
                            modifier = Modifier.size(iconSize),
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null
                        )
                    }
                )
            }
            if (!documentInfo.document.provisionedDocumentSetupNeeded) {
                FloatingItemText(
                    modifier = Modifier.clickable {
                        onDocumentInfoClicked(documentInfo)
                    },
                    showChevron = true,
                    text = stringResource(R.string.wallet_screen_document_info, typeDisplayName),
                    // TODO: Update to "Last update X" where X = today, yesterday, last week, etc
                    //  when we have a way to know when the PII was actually updated
                    secondary = stringResource(R.string.wallet_screen_document_info_secondary),
                    image = {
                        Icon(
                            modifier = Modifier.size(iconSize),
                            imageVector = Icons.Outlined.Badge,
                            contentDescription = null
                        )
                    }
                )
                val activityLoggingEnabledText =
                    if (settingsModel.eventLoggingEnabled.collectAsState().value) {
                        stringResource(R.string.wallet_screen_logging_enabled)
                    } else {
                        stringResource(R.string.wallet_screen_logging_not_enabled)
                    }
                FloatingItemText(
                    modifier = Modifier.clickable {
                        onDocumentActivityClicked(documentInfo)
                    },
                    showChevron = true,
                    text = stringResource(R.string.wallet_screen_activity),
                    secondary = activityLoggingEnabledText,
                    image = {
                        Icon(
                            modifier = Modifier.size(iconSize),
                            imageVector = Icons.Outlined.History,
                            contentDescription = null
                        )
                    }
                )
                if (documentInfo.isProximityPresentable) {
                    val preconsentSetting = documentInfo.document.preconsentSetting
                    FloatingItemText(
                        modifier = Modifier.clickable {
                            onDocumentPreconsentSettingsClicked(documentInfo)
                        },
                        showChevron = true,
                        text = stringResource(R.string.preconsent_screen_title),
                        secondary = preconsentSetting.toHumanReadable(documentInfo),
                        secondaryColor = MaterialTheme.colorScheme.secondary,
                        image = {
                            Icon(
                                modifier = Modifier.size(iconSize),
                                imageVector = Icons.Outlined.Contactless,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Make Remove stand out by having it in its own list
        FloatingItemList {
            FloatingItemText(
                modifier = Modifier.clickable {
                    onDocumentRemoveClicked(documentInfo)
                },
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                        append(stringResource(R.string.wallet_screen_remove))
                    }
                },
                image = {
                    Icon(
                        modifier = Modifier.size(iconSize),
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun DocumentPreconsentSetting.toHumanReadable(documentInfo: DocumentInfo): String? {
    return when (this) {
        DocumentPreconsentSetting.AlwaysRequireConsent -> {
            stringResource(R.string.wallet_screen_preconsent_always_ask)
        }
        DocumentPreconsentSetting.NeverRequireConsent -> {
            stringResource(R.string.wallet_screen_preconsent_never_require)
        }
        is DocumentPreconsentSetting.RequestComplexityBased -> {
            // We currently only support approvedSensitivity == PORTRAIT_IMAGE which include AGE_INFORMATION
            //
            // However not all documents have a portrait (for example EU Age Verification Credential lacks one)
            // so use the right string here
            //
            if (documentInfo.hasPortrait) {
                stringResource(R.string.wallet_screen_preconsent_request_complexity)
            } else {
                stringResource(R.string.wallet_screen_preconsent_request_complexity_no_portrait)
            }
        }
        is DocumentPreconsentSetting.ReaderIdentityBased -> {
            stringResource(R.string.wallet_screen_preconsent_reader_identity)
        }
    }
}

@Composable
private fun EmptyWalletStateContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = AnnotatedString.fromMarkdown(stringResource(R.string.wallet_screen_no_documents)),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
            fontStyle = FontStyle.Italic
        )
    }
}

private val cachedLatestVersion = mutableStateOf<String?>(null)
private var lastCheckedInstant: Instant? = null

@Composable
private fun AppUpdateCard() {
    // Uncomment below if working on this code from Android Studio.
    //
    //val updateUrl =  "https://apps.multipaz.org/multipaz-wallet/LATEST-VERSION.txt"
    //val updateWebsiteUrl =  "https://apps.multipaz.org/"
    //val currentVersion = "2026.W22.1-15-git-4808d2a"
    val updateUrl = BuildConfig.UPDATE_URL
    val updateWebsiteUrl = BuildConfig.UPDATE_WEBSITE
    val currentVersion = BuildConfig.VERSION

    if (updateUrl.isEmpty()) {
        return
    }

    LaunchedEffect(Unit) {
        val now = Clock.System.now()
        val lastChecked = lastCheckedInstant
        if (lastChecked == null || now - lastChecked >= 30.minutes) {
            lastCheckedInstant = now
            try {
                val httpClient = HttpClient(Android)
                val response = httpClient.get(updateUrl)
                if (response.status == HttpStatusCode.OK) {
                    val version = response.readRawBytes().decodeToString().trim()
                    cachedLatestVersion.value = version
                    Logger.i(
                        TAG, "Latest available version from $updateWebsiteUrl is $version " +
                                "and our version is $currentVersion"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e(TAG, "Error checking latest version from $updateWebsiteUrl", e)
            }
        }
    }

    cachedLatestVersion.value?.let {
        // Our version numbers are so arranged that we can just compare strings.
        if (currentVersion < it) {
            InfoCard(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                val str = buildAnnotatedString {
                    append(
                        "Version $it is available for download. Visit "
                    )
                    withLink(
                        LinkAnnotation.Url(
                            updateWebsiteUrl,
                            TextLinkStyles(
                                style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline),
                            )
                        )
                    ) {
                        append(updateWebsiteUrl)
                    }
                    append(" to update.")
                }
                Text(text = str)
            }
        }
    }
}