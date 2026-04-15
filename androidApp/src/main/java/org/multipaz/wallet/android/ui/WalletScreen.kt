package org.multipaz.wallet.android.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.multipaz.compose.cards.WarningCard
import org.multipaz.compose.document.DocumentInfo
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.document.VerticalDocumentList
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.document.DocumentStore
import org.multipaz.util.Logger
import org.multipaz.wallet.android.App
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.isProximityPresentable
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.client.WalletClient
import org.multipaz.wallet.client.syncWithSharedData
import org.multipaz.wallet.shared.BuildConfig

private const val TAG = "WalletScreen"

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    walletClient: WalletClient,
    documentStore: DocumentStore,
    documentModel: DocumentModel,
    settingsModel: SettingsModel,
    focusedDocumentId: String?,
    onAvatarClicked: () -> Unit,
    onAddClicked: () -> Unit,
    onDocumentClicked: (documentId: String) -> Unit,
    onDocumentQrClicked: (documentId: String) -> Unit,
    onDocumentActivityClicked: (documentId: String) -> Unit,
    onDocumentInfoClicked: (documentId: String) -> Unit,
    onDocumentRemoveClicked: (documentId: String) -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var devModeNumTimesPressed by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val blePermissionState = rememberBluetoothPermissionState()
    val context = LocalContext.current

    val focusedDocument = documentModel.documentInfos.collectAsState().value.find { documentInfo ->
        documentInfo.document.identifier == focusedDocumentId
    }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeightDp = with(density) {
        windowInfo.containerSize.height.toDp()
    }
    val maxCardHeight = screenHeightDp / 3

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = focusedDocumentId == null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        if (BuildConfig.DEVELOPER_MODE_AVAILABLE) {
                            Text(
                                modifier = Modifier.clickable {
                                    if (settingsModel.devMode.value) {
                                        showToast(context.getString(R.string.wallet_screen_dev_mode_already_enabled))
                                    } else {
                                        if (devModeNumTimesPressed == 4) {
                                            showToast(context.getString(R.string.wallet_screen_dev_mode_enabled))
                                            settingsModel.devMode.value = true
                                        } else {
                                            val tapsRemaining = 4 - devModeNumTimesPressed
                                            if (tapsRemaining > 1) {
                                                showToast(context.getString(R.string.wallet_screen_dev_mode_taps_remaining, tapsRemaining))
                                            } else {
                                                showToast(context.getString(R.string.wallet_screen_dev_mode_taps_remaining_1))
                                            }
                                            devModeNumTimesPressed += 1
                                        }
                                    }
                                },
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    Crossfade(
                        targetState = focusedDocument != null,
                        label = "NavigationIconCrossfade"
                    ) { isFocused ->
                        if (isFocused) {
                            IconButton(onClick = {
                                onBackClicked()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null
                                )
                            }
                        } else {
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
                },
                actions = {
                    Crossfade(
                        targetState = focusedDocument != null,
                        label = "ActionIconsCrossfade"
                    ) { isFocused ->
                       if (isFocused) {
                           if (focusedDocument?.isProximityPresentable == true) {
                               IconButton(
                                   onClick = { onDocumentQrClicked(focusedDocumentId!!) }
                               ) {
                                   Icon(
                                       modifier = Modifier.size(32.dp),
                                       imageVector = Icons.Outlined.QrCode2,
                                       contentDescription = null,
                                   )
                               }
                           }
                       } else {
                           val signedIn = walletClient.signedInUser.collectAsState().value
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
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = focusedDocument == null,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = onAddClicked,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp),
                    content = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                        )
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current)
                    // Omitting the bottom padding since we want to draw under the navigation bar
                )
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!blePermissionState.isGranted) {
                WarningCard(
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable {
                            coroutineScope.launch {
                                blePermissionState.launchPermissionRequest()
                            }
                        }
                ) {
                    Text(stringResource(R.string.wallet_screen_ble_permission_warning))
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    coroutineScope.launch {
                        try {
                            walletClient.refreshPublicData()
                            walletClient.refreshSharedData()
                            walletClient.sharedData.value?.let {
                                documentStore.syncWithSharedData(
                                    sharedData = it,
                                    mpzPassIsoMdocDomain = App.DOMAIN_MDOC_SOFTWARE,
                                    mpzPassSdJwtVcDomain = App.DOMAIN_SDJWT_SOFTWARE,
                                    mpzPassKeylessSdJwtVcDomain = App.DOMAIN_SDJWT_KEYLESS
                                )
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "Error refreshing data", e)
                            showToast(e.toString())
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                val nestedScrollDispatcher = remember { NestedScrollDispatcher() }
                var estimatedScroll by remember { mutableFloatStateOf(0f) }
                val nestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (available.y > 0 && estimatedScroll <= 0f) {
                                // We are at the top and pulling down.
                                // Forward this drag to the parent PullToRefreshBox to manually trigger overscroll!
                                return nestedScrollDispatcher.dispatchPostScroll(
                                    consumed = Offset.Zero,
                                    available = available,
                                    source = source
                                )
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            estimatedScroll -= consumed.y
                            if (estimatedScroll < 0f) estimatedScroll = 0f
                            return Offset.Zero
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection, nestedScrollDispatcher)
                ) {
                    VerticalDocumentList(
                        modifier = Modifier.fillMaxSize(),
                        documentModel = documentModel,
                        focusedDocument = focusedDocument,
                        allowDocumentReordering = true,
                        showStackWhileFocused = false,
                        cardMaxHeight = maxCardHeight,
                        showDocumentInfo = { documentInfo ->
                            DocumentInfoContent(
                                documentInfo = documentInfo,
                                onDocumentActivityClicked = onDocumentActivityClicked,
                                onDocumentInfoClicked = onDocumentInfoClicked,
                                onDocumentRemoveClicked = onDocumentRemoveClicked
                            )
                        },
                        emptyDocumentContent = {
                            EmptyWalletStateContent()
                        },
                        onDocumentReordered = { documentInfo, newIndex ->
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
                        onDocumentFocused = { documentInfo ->
                            onDocumentClicked(documentInfo.document.identifier)
                        },
                        onDocumentFocusedTapped =  { documentInfo ->
                            onBackClicked()
                        },
                        onDocumentFocusedStackTapped =  { documentInfo -> }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentInfoContent(
    documentInfo: DocumentInfo,
    onDocumentActivityClicked: (documentId: String) -> Unit,
    onDocumentInfoClicked: (documentId: String) -> Unit,
    onDocumentRemoveClicked: (documentId: String) -> Unit,
) {
    val iconSize = 32.dp
    Column(
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        FloatingItemList {
            val typeDisplayName = documentInfo.document.typeDisplayName ?: stringResource(R.string.wallet_screen_document_type_name_fallback)
            FloatingItemText(
                modifier = Modifier.clickable {
                    onDocumentInfoClicked(documentInfo.document.identifier)
                },
                text = stringResource(R.string.wallet_screen_document_info, typeDisplayName),
                image = {
                    Icon(
                        modifier = Modifier.size(iconSize),
                        imageVector = Icons.Outlined.Badge,
                        contentDescription = null
                    )
                }
            )
            FloatingItemText(
                modifier = Modifier.clickable {
                    onDocumentActivityClicked(documentInfo.document.identifier)
                },
                text = stringResource(R.string.wallet_screen_activity),
                image = {
                    Icon(
                        modifier = Modifier.size(iconSize),
                        imageVector = Icons.Outlined.History,
                        contentDescription = null
                    )
                }
            )
            /* TODO
            FloatingItemText(
                text = stringResource(R.string.wallet_screen_issuer_website),
                image = {
                    Icon(
                        modifier = Modifier.size(iconSize),
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null
                    )
                }
            )
             */
            FloatingItemText(
                modifier = Modifier.clickable {
                    onDocumentRemoveClicked(documentInfo.document.identifier)
                },
                text = stringResource(R.string.wallet_screen_remove),
                image = {
                    Icon(
                        modifier = Modifier.size(iconSize),
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null
                    )
                }
            )
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
