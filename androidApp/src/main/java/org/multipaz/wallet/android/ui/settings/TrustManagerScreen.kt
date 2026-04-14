package org.multipaz.wallet.android.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.multipaz.wallet.android.R
import coil3.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.pickers.FilePicker
import org.multipaz.compose.pickers.rememberFilePicker
import org.multipaz.compose.trustmanagement.TrustEntryList
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.crypto.X509Cert
import org.multipaz.mdoc.rical.SignedRical
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.trustmanagement.TrustEntryAlreadyExistsException
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.wallet.android.ui.InfoNote

@Composable
private fun FloatingActionButtonMenu(
    importCertificateFilePicker: FilePicker,
    importVicalFilePicker: FilePicker?,
    importRicalFilePicker: FilePicker?,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        // Transparent overlay to catch outside clicks, to close the menu
        if (isMenuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // Prevents a visual ripple effect on the whole screen
                        onClick = { isMenuExpanded = false }
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = isMenuExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(R.string.trust_manager_import_cert)) },
                            onClick = {
                                isMenuExpanded = false
                                importCertificateFilePicker.launch()
                            },
                            icon = { Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null
                            ) },
                            elevation = FloatingActionButtonDefaults.elevation(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (importVicalFilePicker != null) {
                            ExtendedFloatingActionButton(
                                text = { Text(stringResource(R.string.trust_manager_import_vical)) },
                                onClick = {
                                    isMenuExpanded = false
                                    importVicalFilePicker.launch()
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null
                                    )
                                },
                                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        if (importRicalFilePicker != null) {
                            ExtendedFloatingActionButton(
                                text = { Text(stringResource(R.string.trust_manager_import_rical)) },
                                onClick = {
                                    isMenuExpanded = false
                                    importRicalFilePicker.launch()
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null
                                    )
                                },
                                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { isMenuExpanded = !isMenuExpanded },
                    elevation = FloatingActionButtonDefaults.elevation(8.dp),
                    content = {
                        Icon(
                            imageVector = if (isMenuExpanded) Icons.Filled.Menu else Icons.Filled.Add,
                            contentDescription = null,
                        )
                    }
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustManagerScreen(
    builtIn: TrustManagerModel,
    user: TrustManagerModel,
    isVical: Boolean,
    imageLoader: ImageLoader,
    onTrustEntryClicked: (trustManagerId: String, trustEntryId: String) -> Unit,
    onTrustEntryAdded: (trustManagerId: String, trustEntryId: String) -> Unit,
    onTrustEntryImportError: (errorTitle: String, errorMessage: String) -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val importCertificateFilePicker = rememberFilePicker(
        types = listOf(
            "application/x-pem-file",
            "application/x-x509-key; format=pem",
            "application/x-x509-cert; format=pem",
            "application/x-x509-ca-cert",
            "application/x-x509-ca-cert; format=der",
            "application/pkix-cert",
            "application/pkix-crl",
        ),
        allowMultiple = false,
        onResult = { files ->
            if (files.isNotEmpty()) {
                coroutineScope.launch {
                    try {
                        val cert = X509Cert.fromPem(pemEncoding = files[0].toByteArray().decodeToString())
                        val entry = (user.trustManager as TrustManager).addX509Cert(
                            certificate = cert,
                            metadata = TrustMetadata()
                        )
                        onTrustEntryAdded(user.trustManager.identifier, entry.identifier)
                    } catch (_: TrustEntryAlreadyExistsException) {
                        onTrustEntryImportError(
                            context.getString(R.string.trust_manager_error_importing_cert),
                            context.getString(R.string.trust_manager_error_cert_exists)
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        e.printStackTrace()
                        onTrustEntryImportError(
                            context.getString(R.string.trust_manager_error_importing_cert),
                            context.getString(R.string.trust_manager_error_cert_failed, e.toString())
                        )
                    }
                }
            }
        }
    )

    val importVicalFilePicker = rememberFilePicker(
        // Unfortunately there's no well-defined MIME type for a VICAL.
        types = listOf(
            "*/*",
        ),
        allowMultiple = false,
        onResult = { files ->
            if (files.isNotEmpty()) {
                coroutineScope.launch {
                    try {
                        val encodedSignedVical = files[0]
                        // Parse it once, to check the signature is good
                        val signedVical = SignedVical.parse(
                            encodedSignedVical = encodedSignedVical.toByteArray(),
                            disableSignatureVerification = false
                        )
                        val entry = (user.trustManager as TrustManager).addVical(
                            encodedSignedVical = encodedSignedVical,
                            metadata = TrustMetadata()
                        )
                        onTrustEntryAdded(user.trustManager.identifier, entry.identifier)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        e.printStackTrace()
                        onTrustEntryImportError(
                            context.getString(R.string.trust_manager_error_importing_vical),
                            context.getString(R.string.trust_manager_error_vical_failed, e.toString())
                        )
                    }
                }
            }
        }
    )

    val importRicalFilePicker = rememberFilePicker(
        // Unfortunately there's no well-defined MIME type for a VICAL.
        types = listOf(
            "*/*",
        ),
        allowMultiple = false,
        onResult = { files ->
            if (files.isNotEmpty()) {
                coroutineScope.launch {
                    try {
                        val encodedSignedRical = files[0]
                        // Parse it once, to check the signature is good
                        val signedRical = SignedRical.parse(
                            encodedSignedRical = encodedSignedRical.toByteArray(),
                            disableSignatureVerification = false
                        )
                        val entry = (user.trustManager as TrustManager).addRical(
                            encodedSignedRical = encodedSignedRical,
                            metadata = TrustMetadata()
                        )
                        onTrustEntryAdded(user.trustManager.identifier, entry.identifier)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        e.printStackTrace()
                        onTrustEntryImportError(
                            context.getString(R.string.trust_manager_error_importing_rical),
                            context.getString(R.string.trust_manager_error_rical_failed, e.toString())
                        )
                    }
                }
            }
        }
    )

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(if (isVical) stringResource(R.string.trust_manager_trusted_issuers) else stringResource(R.string.trust_manager_trusted_verifiers))
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                importCertificateFilePicker = importCertificateFilePicker,
                importVicalFilePicker = if (isVical) importVicalFilePicker else null,
                importRicalFilePicker = if (!isVical) importRicalFilePicker else null
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isVical) {
                InfoNote(
                    markdownString = stringResource(R.string.trust_manager_info_issuers)
                )
            } else {
                InfoNote(
                    markdownString = stringResource(R.string.trust_manager_info_verifiers)
                )
            }
            TrustEntryList(
                trustManagerModel = builtIn,
                title = stringResource(R.string.trust_manager_built_in),
                imageLoader = imageLoader,
                loading = { FloatingItemCenteredText(text = stringResource(R.string.trust_manager_loading)) },
                noItems = { FloatingItemCenteredText(text = stringResource(R.string.trust_manager_no_built_in)) },
                onTrustEntryClicked = { trustEntry ->
                    onTrustEntryClicked(builtIn.trustManager.identifier, trustEntry.identifier)
                }
            )
            TrustEntryList(
                trustManagerModel = user,
                title = stringResource(R.string.trust_manager_manually_imported),
                imageLoader = imageLoader,
                loading = { FloatingItemCenteredText(text = stringResource(R.string.trust_manager_loading)) },
                noItems = { FloatingItemCenteredText(text = stringResource(R.string.trust_manager_no_manually_imported)) },
                onTrustEntryClicked = { trustEntry ->
                    onTrustEntryClicked(user.trustManager.identifier, trustEntry.identifier)
                }
            )
        }
    }
}
