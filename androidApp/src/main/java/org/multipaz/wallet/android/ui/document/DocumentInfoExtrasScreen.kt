package org.multipaz.wallet.android.ui.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz.wallet.android.R
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.toCdn
import org.multipaz.compose.datetime.formattedDateTime
import org.multipaz.compose.decodeImage
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.credential.Credential
import org.multipaz.datetime.FormatStyle
import org.multipaz.datetime.formatLocalized
import org.multipaz.tags.Tags
import org.multipaz.util.toHex
import org.multipaz.wallet.android.ui.Note
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentInfoExtrasScreen(
    documentId: String,
    documentModel: DocumentModel,
    onBackClicked: () -> Unit,
    onRefreshCredentialsClicked: () -> Unit,
    onCredentialClicked: (String) -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val documentInfos = documentModel.documentInfos.collectAsState().value
    val documentInfo = documentInfos.find { it.document.identifier == documentId }

    val credentialsByDomain = documentInfo
        ?.credentialInfos
        ?.map { it.credential }
        ?.sortedByDescending { it.domain }
        ?.groupBy { it.domain }
        .orEmpty()

    val tags = documentInfo?.document?.tags
    val tagKeys = tags?.keys?.sorted().orEmpty()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.document_info_credentials_title))
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
                    IconButton(onClick = onRefreshCredentialsClicked) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.document_info_refresh_credentials_content_description)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Note(
                markdownString = "This screen contains low-level information about the pass, " +
                        "including its backing credentials, organized by domain."
            )

            documentInfo?.document?.let { doc ->
                FloatingItemList(title = "Metadata") {
                    FloatingItemHeadingAndText(
                        heading = "Identifier",
                        text = doc.identifier
                    )
                    FloatingItemHeadingAndText(
                        heading = "Display name",
                        text = doc.displayName ?: "N/A"
                    )
                    FloatingItemHeadingAndText(
                        heading = "Type display name",
                        text = doc.typeDisplayName ?: "N/A"
                    )
                    val createdLocal = doc.created.toLocalDateTime(TimeZone.currentSystemDefault())
                    FloatingItemHeadingAndText(
                        heading = "Created",
                        text = createdLocal.formatLocalized()
                    )
                    FloatingItemHeadingAndText(
                        heading = "Provisioned",
                        text = if (doc.provisioned) "Yes" else "No"
                    )
                    FloatingItemHeadingAndContent(
                        heading = "Card art",
                        content = {
                            val cardArt = doc.cardArt
                            if (cardArt != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = "${cardArt.size} bytes")
                                    val bitmap = remember(cardArt) { decodeImage(cardArt.toByteArray()) }
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        modifier = Modifier.height(80.dp)
                                    )
                                }
                            } else {
                                Text(text = "None")
                            }
                        }
                    )
                    FloatingItemHeadingAndContent(
                        heading = "Issuer logo",
                        content = {
                            val issuerLogo = doc.issuerLogo
                            if (issuerLogo != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = "${issuerLogo.size} bytes")
                                    val bitmap = remember(issuerLogo) { decodeImage(issuerLogo.toByteArray()) }
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        modifier = Modifier.height(80.dp)
                                    )
                                }
                            } else {
                                Text(text = "None")
                            }
                        }
                    )
                    val authDataText = formatAuthorizationData(doc.authorizationData)
                    FloatingItemHeadingAndText(
                        heading = "Authorization data",
                        text = authDataText,
                        showChevron = doc.authorizationData != null,
                        modifier = if (doc.authorizationData != null) {
                            Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(authDataText))
                            }
                        } else {
                            Modifier
                        }
                    )
                    FloatingItemHeadingAndText(
                        heading = "MpzPass ID",
                        text = doc.mpzPassId ?: "N/A"
                    )
                    FloatingItemHeadingAndText(
                        heading = "MpzPass version",
                        text = doc.mpzPassVersion?.toString() ?: "N/A"
                    )
                    FloatingItemHeadingAndText(
                        heading = "Metadata",
                        text = doc.metadata?.serialize()?.let { "${it.size} bytes" } ?: "None"
                    )
                }
            }

            FloatingItemList(title = "Document tags") {
                if (tagKeys.isEmpty()) {
                    FloatingItemCenteredText("No tags")
                } else {
                    tagKeys.forEach { key ->
                        val text = tags?.formatTagValue(key).orEmpty()
                        FloatingItemHeadingAndText(
                            heading = key,
                            text = text
                        )
                    }
                }
            }

            credentialsByDomain.forEach { (domain, creds) ->
                FloatingItemList(title = "Domain $domain") {
                    creds.forEach { credential ->

                        val (text, secondary) = if (credential.isCertified) {
                            Pair(
                                "${credential.credentialType} with use-count ${credential.usageCount}",
                                buildString {
                                    append("Valid from ")
                                    append(credential.validFrom.format())
                                    append(" until ")
                                    append(credential.validUntil.format())
                                }
                            )
                        } else {
                            Pair(
                               credential.credentialType,
                                "Pending certification"
                            )
                        }
                        FloatingItemText(
                            modifier = Modifier.clickable {
                                onCredentialClicked(credential.identifier)
                            },
                            showChevron = true,
                            text = text,
                            secondary = secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun formatAuthorizationData(authorizationData: ByteString?): String {
    if (authorizationData == null) return "None"
    return try {
        Cbor.decode(authorizationData.toByteArray()).toCdn()
    } catch (_: Exception) {
        authorizationData.toByteArray().toHex()
    }
}

// TODO: It would be nice to just have Tags.get() returning a DataItem to avoid the shenanigans below.
private fun Tags.formatTagValue(key: String): String {
    try {
        getString(key)?.let { return it }
    } catch (_: IllegalArgumentException) {}
    try {
        getBoolean(key)?.let { return it.toString() }
    } catch (_: IllegalArgumentException) {}
    try {
        getLong(key)?.let { return it.toString() }
    } catch (_: IllegalArgumentException) {}
    try {
        getInt(key)?.let { return it.toString() }
    } catch (_: IllegalArgumentException) {}
    try {
        getByteString(key)?.let { bstr ->
            return if (bstr.size > 64) {
                "${bstr.size} bytes"
            } else {
                bstr.toByteArray().toHex()
            }
        }
    } catch (_: IllegalArgumentException) {}
    try {
        getList<String>(key)?.let { return it.joinToString(", ") }
    } catch (_: IllegalArgumentException) {}
    try {
        getList<ByteString>(key)?.let { list ->
            return list.joinToString(", ") { bstr ->
                if (bstr.size > 64) "${bstr.size} bytes" else bstr.toByteArray().toHex()
            }
        }
    } catch (_: IllegalArgumentException) {}
    try {
        getList<Boolean>(key)?.let { return it.joinToString(", ") }
    } catch (_: IllegalArgumentException) {}
    try {
        getList<Long>(key)?.let { return it.joinToString(", ") }
    } catch (_: IllegalArgumentException) {}
    try {
        getList<Int>(key)?.let { return it.joinToString(", ") }
    } catch (_: IllegalArgumentException) {}
    return ""
}

private fun Instant.format(): String {
    return toLocalDateTime(TimeZone.currentSystemDefault()).formatLocalized(
        dateStyle = FormatStyle.SHORT,
        timeStyle = FormatStyle.SHORT
    )
}