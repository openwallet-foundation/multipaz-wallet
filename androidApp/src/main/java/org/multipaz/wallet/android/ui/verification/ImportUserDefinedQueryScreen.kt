package org.multipaz.wallet.android.ui.verification

import android.os.LocaleList
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import org.multipaz.compose.decodeImage
import org.multipaz.compose.items.SegmentedItemContainer
import org.multipaz.compose.items.SegmentedItemList
import org.multipaz.compose.items.SegmentedItemText
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.CredentialMetadata
import org.multipaz.provisioning.ProvisioningMetadata
import org.multipaz.util.Logger
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.verification.UserDefinedQuery

private const val TAG = "ImportUserDefinedQueryScreen"

/**
 * Screen for entering the URL of the issuer whose metadata a user defined query is imported from.
 *
 * @param initialIssuerUrl the URL to start with, normally the one last used.
 * @param defaultIssuerUrl the URL offered as the default.
 * @param onConnect called with the URL entered.
 * @param onBackClicked called when the user leaves.
 */
@Composable
fun ImportUserDefinedQueryUrlScreen(
    initialIssuerUrl: String,
    defaultIssuerUrl: String,
    onConnect: (issuerUrl: String) -> Unit,
    onBackClicked: () -> Unit
) {
    var issuerUrl by remember { mutableStateOf(initialIssuerUrl) }
    ImportScaffold(
        title = stringResource(R.string.import_user_defined_query_url_title),
        onBackClicked = onBackClicked
    ) {
        Note(stringResource(R.string.import_user_defined_query_url_text))
        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = issuerUrl,
            onValueChange = { issuerUrl = it },
            singleLine = true,
            label = {
                Text(stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_url_label))
            }
        )
        val resetToDefaultText = stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_reset)
        Text(
            text = AnnotatedString.fromMarkdown(
                markdownString = "[$resetToDefaultText](reset://)",
                linkInteractionListener = { issuerUrl = defaultIssuerUrl }
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = issuerUrl.isNotBlank(),
            onClick = { onConnect(issuerUrl.trim()) }
        ) {
            Text(
                modifier = Modifier.padding(vertical = 8.dp),
                text = stringResource(R.string.provisioning_add_to_wallet_screen_issuer_server_dialog_connect),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Screen listing the credential configurations of an issuer, of which ISO mdoc ones can be picked.
 *
 * @param issuerUrl the URL of the issuer.
 * @param getIssuerMetadata gets the metadata of the issuer at the given URL.
 * @param onCredentialSelected called with the identifier of the credential configuration picked.
 * @param onBackClicked called when the user leaves.
 */
@Composable
fun ImportUserDefinedQueryCredentialScreen(
    issuerUrl: String,
    getIssuerMetadata: suspend (issuerUrl: String) -> ProvisioningMetadata,
    onCredentialSelected: (credentialId: String) -> Unit,
    onBackClicked: () -> Unit
) {
    ImportScaffold(
        title = stringResource(R.string.import_user_defined_query_credential_title),
        onBackClicked = onBackClicked
    ) {
        WithIssuerMetadata(issuerUrl, getIssuerMetadata) { metadata ->
            Note(stringResource(R.string.import_user_defined_query_credential_text, metadata.display.text))
            SegmentedItemList {
                metadata.credentials.forEach { (id, credential) ->
                    CredentialItem(credential = credential, onClick = { onCredentialSelected(id) })
                }
            }
        }
    }
}

/**
 * Screen for picking the data elements of an ISO mdoc credential configuration, all of which are selected at first.
 *
 * @param issuerUrl the URL of the issuer.
 * @param credentialId the identifier of the credential configuration.
 * @param getIssuerMetadata gets the metadata of the issuer at the given URL.
 * @param onImported called with the query made of the selection, named after the credential.
 * @param onBackClicked called when the user leaves.
 */
@Composable
fun ImportUserDefinedQueryDataElementsScreen(
    issuerUrl: String,
    credentialId: String,
    getIssuerMetadata: suspend (issuerUrl: String) -> ProvisioningMetadata,
    onImported: (query: UserDefinedQuery) -> Unit,
    onBackClicked: () -> Unit
) {
    ImportScaffold(
        title = stringResource(R.string.import_user_defined_query_data_elements_title),
        onBackClicked = onBackClicked
    ) {
        WithIssuerMetadata(issuerUrl, getIssuerMetadata) { metadata ->
            val credential = metadata.credentials[credentialId]
            val format = credential?.format
            if (format is CredentialFormat.Mdoc) {
                DataElementSelection(credential = credential, docType = format.docType, onImported = onImported)
            } else {
                Text(
                    text = stringResource(R.string.import_user_defined_query_credential_missing, credentialId),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private sealed class MetadataState {
    data object Loading : MetadataState()
    data class Loaded(val metadata: ProvisioningMetadata) : MetadataState()
    data class Failed(val message: String?) : MetadataState()
}

@Composable
private fun ColumnScope.WithIssuerMetadata(
    issuerUrl: String,
    getIssuerMetadata: suspend (issuerUrl: String) -> ProvisioningMetadata,
    content: @Composable ColumnScope.(metadata: ProvisioningMetadata) -> Unit
) {
    var state by remember(issuerUrl) { mutableStateOf<MetadataState>(MetadataState.Loading) }
    LaunchedEffect(issuerUrl) {
        state = try {
            MetadataState.Loaded(getIssuerMetadata(issuerUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Error getting issuer metadata from $issuerUrl", e)
            MetadataState.Failed(e.message)
        }
    }
    when (val current = state) {
        MetadataState.Loading -> {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            Text(
                text = stringResource(R.string.import_user_defined_query_loading, issuerUrl),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        is MetadataState.Failed -> {
            Text(
                text = stringResource(R.string.import_user_defined_query_failed, issuerUrl),
                style = MaterialTheme.typography.bodyLarge
            )
            current.message?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is MetadataState.Loaded -> content(current.metadata)
    }
}

@Composable
private fun CredentialItem(
    credential: CredentialMetadata,
    onClick: () -> Unit
) {
    val image: @Composable () -> Unit = {
        credential.display.logo?.let {
            val bitmap = remember { decodeImage(it.toByteArray()) }
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Fit)
            }
        }
    }
    when (val format = credential.format) {
        is CredentialFormat.Mdoc -> SegmentedItemText(
            modifier = Modifier.clickable(onClick = onClick),
            showChevron = true,
            text = credential.display.text,
            secondary = stringResource(R.string.provisioning_selection_format_mdoc, format.docType),
            image = image
        )
        is CredentialFormat.SdJwt -> SegmentedItemText(
            text = credential.display.text,
            secondary = stringResource(R.string.import_user_defined_query_not_mdoc, format.vct),
            image = image
        )
    }
}

@Composable
private fun DataElementSelection(
    credential: CredentialMetadata,
    docType: String,
    onImported: (query: UserDefinedQuery) -> Unit
) {
    val namespaces = remember(credential) {
        importableNamespaces(
            descriptions = credential.openID4VCIClaimDescriptions ?: emptyList(),
            locales = LocaleList.getDefault().toLanguageTags().split(",")
        )
    }
    // Keyed by namespace and data element. Every data element is selected at first.
    val selected = remember(credential) {
        mutableStateMapOf<Pair<String, String>, Boolean>().apply {
            namespaces.forEach { ns -> ns.dataElements.forEach { put(ns.namespace to it.dataElement, true) } }
        }
    }

    Note(
        if (namespaces.isEmpty()) {
            stringResource(R.string.import_user_defined_query_no_data_elements, credential.display.text)
        } else {
            stringResource(R.string.import_user_defined_query_data_elements_text, credential.display.text)
        }
    )
    namespaces.forEach { ns ->
        val keys = ns.dataElements.map { ns.namespace to it.dataElement }
        val allSelected = keys.all { selected[it] == true }
        SegmentedItemList(title = ns.namespace) {
            SegmentedItemContainer(
                modifier = Modifier.clickable { keys.forEach { selected[it] = !allSelected } }
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    text = stringResource(
                        if (allSelected) {
                            R.string.import_user_defined_query_deselect_all
                        } else {
                            R.string.import_user_defined_query_select_all
                        }
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            ns.dataElements.forEach { element ->
                val key = ns.namespace to element.dataElement
                SegmentedItemText(
                    modifier = Modifier.clickable { selected[key] = selected[key] != true },
                    text = element.displayName ?: element.dataElement,
                    secondary = if (element.displayName != null) element.dataElement else null,
                    trailingContent = {
                        Checkbox(
                            checked = selected[key] == true,
                            onCheckedChange = { selected[key] = it }
                        )
                    }
                )
            }
        }
    }
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val selection = namespaces.mapNotNull { ns ->
                val elements = ns.dataElements
                    .map { it.dataElement }
                    .filter { selected[ns.namespace to it] == true }
                if (elements.isEmpty()) null else ns.namespace to elements
            }.toMap()
            onImported(
                UserDefinedQuery(
                    docType = docType,
                    namespaces = selection,
                    name = credential.display.text
                )
            )
        }
    ) {
        Text(stringResource(R.string.import_user_defined_query_use))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportScaffold(
    title: String,
    onBackClicked: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = { Text(title) },
                navigationIcon = { AppBackButton(onClick = onBackClicked) },
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
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            content()
        }
    }
}
