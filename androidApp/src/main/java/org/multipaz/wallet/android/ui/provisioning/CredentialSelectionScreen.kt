package org.multipaz.wallet.android.ui.provisioning

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.multipaz.compose.decodeImage
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.ProvisioningMetadata
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialSelectionScreen(
    metadata: ProvisioningMetadata,
    onCloseClicked: () -> Unit,
    onCredentialSelected: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.provisioning_selection_title)) },
                navigationIcon = {
                    IconButton(onClick = onCloseClicked) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.provisioning_selection_cancel_description)
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
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top)
        ) {
            Note(
                stringResource(
                    R.string.provisioning_selection_explainer,
                    metadata.display.text
                )
            )

            FloatingItemList {
                metadata.credentials.forEach { (id, credential) ->
                    FloatingItemText(
                        modifier = Modifier.clickable {
                            onCredentialSelected(id)
                        },
                        text = credential.display.text,
                        secondary = credential.format.toHumanReadable(),
                        image = {
                            credential.display.logo?.let {
                                val bitmap = remember { decodeImage(it.toByteArray()) }
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CredentialFormat.toHumanReadable(): String {
    return when (this) {
        is CredentialFormat.Mdoc -> stringResource(R.string.provisioning_selection_format_mdoc, docType)
        is CredentialFormat.SdJwt -> stringResource(R.string.provisioning_selection_format_sd_jwt, vct)
    }
}
