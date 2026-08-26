package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Numbers
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.io.bytestring.ByteString
import org.multipaz.compose.branding.Branding
import org.multipaz.compose.decodeImage
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.compose.trustmanagement.TrustEntryInfo
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.crypto.X509Cert
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.trustmanagement.TrustEntryRical
import org.multipaz.trustmanagement.TrustEntryVical
import org.multipaz.trustmanagement.TrustEntryX509Cert
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.toHex
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestVerificationIssuerIdentifiersScreen(
    settingsModel: SettingsModel,
    backendIssuerTrustManagerModel: TrustManagerModel,
    userIssuerTrustManagerModel: TrustManagerModel,
    issuerTrustManager: CompositeTrustManager,
    imageLoader: ImageLoader,
    onAddIssuerIdentifierClicked: () -> Unit,
    onBackClicked: () -> Unit
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val issuerIdentifiers by settingsModel.verificationIssuerIdentifiers.collectAsState()

    val backendInfos = backendIssuerTrustManagerModel.trustManagerInfos.collectAsState().value ?: emptyList()
    val userInfos = userIssuerTrustManagerModel.trustManagerInfos.collectAsState().value ?: emptyList()
    val allInfos = backendInfos + userInfos

    val defaultSecondaryText = stringResource(R.string.trust_entry_viewer_certificate_label)

    fun findInfoForCertificate(certificate: X509Cert): TrustEntryInfo? {
        for (info in allInfos) {
            when (val entry = info.entry) {
                is TrustEntryX509Cert -> {
                    if (entry.certificate == certificate) {
                        return info
                    }
                }
                is TrustEntryVical -> {
                    if (info.signedVical?.vical?.certificateInfos?.any { it.certificate == certificate } == true) {
                        return info
                    }
                }
                is TrustEntryRical -> {
                    if (info.signedRical?.rical?.certificateInfos?.any { it.certificate == certificate } == true) {
                        return info
                    }
                }
            }
        }
        return null
    }

    @Composable
    fun getSecondaryText(info: TrustEntryInfo?): String {
        return when (info?.entry) {
            is TrustEntryX509Cert -> defaultSecondaryText
            is TrustEntryVical, is TrustEntryRical -> info.getDisplayName()
            else -> defaultSecondaryText
        }
    }

    @Composable
    fun RenderItemImage(
        trustPoint: TrustPoint?,
        name: String,
        info: TrustEntryInfo?,
    ) {
        if (trustPoint == null) {
            Icon(
                modifier = Modifier.size(48.dp),
                imageVector = Icons.Outlined.Numbers,
                contentDescription = null
            )
            return
        }

        if (trustPoint.metadata.displayIcon != null) {
            val bitmap = remember(trustPoint.metadata.displayIcon) {
                decodeImage(trustPoint.metadata.displayIcon!!.toByteArray())
            }
            Image(
                modifier = Modifier.size(48.dp),
                bitmap = bitmap,
                contentDescription = null
            )
            return
        }

        if (!trustPoint.metadata.displayIconUrl.isNullOrEmpty()) {
            AsyncImage(
                modifier = Modifier.size(48.dp),
                model = trustPoint.metadata.displayIconUrl,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                contentDescription = null
            )
            return
        }

        val entry = info?.entry
        val additionalData = when (entry) {
            is TrustEntryX509Cert -> entry.certificate.subjectKeyIdentifier
            null -> trustPoint.certificate.subjectKeyIdentifier
            else -> entry.identifier.encodeToByteArray()
        }

        Branding.Current.collectAsState().value.AvatarIcon(
            size = 48.dp,
            name = name,
            additionalData = additionalData
        )
    }

    val allTrustPoints by produceState<List<TrustPoint>>(initialValue = emptyList()) {
        value = issuerTrustManager.getTrustPoints()
    }
    val availablePoints = allTrustPoints.filter { point ->
        val ski = point.certificate.subjectKeyIdentifier?.let { ByteString(it) }
        ski != null && issuerIdentifiers.none { it == ski }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = { Text(stringResource(R.string.request_verification_issuer_identifiers_screen_title)) },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    IconButton(onClick = onAddIssuerIdentifierClicked) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.request_verification_issuer_identifiers_add_manual)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        }
    ) { innerPadding ->
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
            Note(stringResource(R.string.request_verification_issuer_identifiers_note))

            FloatingItemList(title = stringResource(R.string.request_verification_issuer_identifiers_screen_title)) {
                if (issuerIdentifiers.isEmpty()) {
                    FloatingItemCenteredText(text = stringResource(R.string.request_verification_advanced_no_issuer_identifiers))
                } else {
                    issuerIdentifiers.forEach { identifier ->
                        val trustPoint = allTrustPoints.find { point ->
                            point.certificate.subjectKeyIdentifier?.let { ByteString(it) } == identifier
                        }
                        val info = trustPoint?.let { findInfoForCertificate(it.certificate) }
                        val name = trustPoint?.metadata?.displayName
                            ?: trustPoint?.certificate?.subject?.toString()
                            ?: identifier.toByteArray().toHex()
                        val secondaryText = if (trustPoint == null) {
                            stringResource(R.string.request_verification_issuer_identifiers_no_trusted_issuer)
                        } else {
                            getSecondaryText(info)
                        }
                        FloatingItemText(
                            image = {
                                RenderItemImage(
                                    trustPoint = trustPoint,
                                    name = name,
                                    info = info
                                )
                            },
                            text = name,
                            secondary = secondaryText,
                            trailingContent = {
                                IconButton(onClick = {
                                    settingsModel.verificationIssuerIdentifiers.value =
                                        issuerIdentifiers.filter { it != identifier }
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.request_verification_issuer_identifiers_remove)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            FloatingItemList(title = stringResource(R.string.request_verification_available_trusted_issuers)) {
                if (availablePoints.isEmpty()) {
                    FloatingItemCenteredText(text = stringResource(R.string.request_verification_no_available_trusted_issuers))
                } else {
                    availablePoints.forEach { point ->
                        val info = findInfoForCertificate(point.certificate)
                        val name = point.metadata.displayName ?: point.certificate.subject.toString()
                        val secondaryText = getSecondaryText(info)
                        val ski = point.certificate.subjectKeyIdentifier?.let { ByteString(it) }
                        FloatingItemText(
                            modifier = Modifier.clickable {
                                if (ski != null) {
                                    settingsModel.verificationIssuerIdentifiers.value =
                                        issuerIdentifiers + ski
                                }
                            },
                            image = {
                                RenderItemImage(
                                    trustPoint = point,
                                    name = name,
                                    info = info
                                )
                            },
                            text = name,
                            secondary = secondaryText,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.request_verification_issuer_identifiers_add)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
