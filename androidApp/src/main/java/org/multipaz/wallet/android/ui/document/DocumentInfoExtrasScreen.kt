package org.multipaz.wallet.android.ui.document

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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.multipaz.compose.datetime.formattedDateTime
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.credential.Credential
import org.multipaz.datetime.FormatStyle
import org.multipaz.datetime.formatLocalized
import org.multipaz.wallet.android.ui.Note
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentInfoExtrasScreen(
    documentId: String,
    documentModel: DocumentModel,
    onBackClicked: () -> Unit,
    onCredentialClicked: (String) -> Unit
) {
    val documentInfo = documentModel.documentInfos.collectAsState().value.find {
        it.document.identifier == documentId
    }

    var credentialsByDomain by remember { mutableStateOf<Map<String, List<Credential>>>(emptyMap()) }

    LaunchedEffect(documentId) {
        val document = documentInfo?.document ?: return@LaunchedEffect
        val credentials = document.getCertifiedCredentials()
        credentialsByDomain = credentials.groupBy { it.domain }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text("Credentials")
                },
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
                markdownString = "This is a low-level view of the credentials backing this pass, " +
                        "organized by domain. Click on each credential for more info"
            )

            credentialsByDomain.forEach { (domain, creds) ->
                FloatingItemList(title = domain) {
                    creds.forEach { credential ->
                        FloatingItemText(
                            modifier = Modifier.clickable {
                                onCredentialClicked(credential.identifier)
                            },
                            text = credential.credentialType,
                            secondary = buildString {
                                append("Valid from ")
                                append(credential.validFrom.format())
                                append(" until ")
                                append(credential.validUntil.format())
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun Instant.format(): String {
    return toLocalDateTime(TimeZone.currentSystemDefault()).formatLocalized(
        dateStyle = FormatStyle.SHORT,
        timeStyle = FormatStyle.SHORT
    )
}