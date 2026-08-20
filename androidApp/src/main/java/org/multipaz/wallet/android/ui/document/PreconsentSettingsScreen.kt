package org.multipaz.wallet.android.ui.document

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.items.FloatingItemContainer
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.documenttype.DocumentAttributeSensitivity
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.DocumentPreconsentSetting
import org.multipaz.wallet.client.preconsentSetting
import org.multipaz.wallet.client.setPreconsentSetting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreconsentSettingsScreen(
    documentId: String,
    documentModel: DocumentModel,
    onBackClicked: () -> Unit,
    onManageTrustedReaders: () -> Unit
) {
    val hazeState = remember { HazeState() }
    val coroutineScope = rememberCoroutineScope()
    val documentInfo = documentModel.documentInfos.collectAsState().value.find {
        it.document.identifier == documentId
    } ?: return
    
    val currentSetting = documentInfo.document.preconsentSetting

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = {
                    Text(stringResource(R.string.preconsent_screen_title))
                },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
            Note(stringResource(R.string.preconsent_screen_blurb))
            
            FloatingItemList {
                // 1. Always ask
                SettingRow(
                    title = stringResource(R.string.preconsent_screen_always_ask_title),
                    description = stringResource(R.string.preconsent_screen_always_ask_description),
                    selected = currentSetting is DocumentPreconsentSetting.AlwaysRequireConsent,
                    onClick = {
                        coroutineScope.launch {
                            documentInfo.document.setPreconsentSetting(DocumentPreconsentSetting.AlwaysRequireConsent)
                        }
                    }
                )

                // 2. Allow age checks
                SettingRow(
                    title = stringResource(R.string.preconsent_screen_age_checks_title),
                    description = stringResource(R.string.preconsent_screen_age_checks_description),
                    selected = currentSetting is DocumentPreconsentSetting.RequestComplexityBased,
                    onClick = {
                        coroutineScope.launch {
                            documentInfo.document.setPreconsentSetting(
                                DocumentPreconsentSetting.RequestComplexityBased(
                                    approvedSensitivity = DocumentAttributeSensitivity.PORTRAIT_IMAGE
                                )
                            )
                        }
                    }
                )

                // 3. Trusted readers
                SettingRow(
                    title = stringResource(R.string.preconsent_screen_trusted_readers_title),
                    description = stringResource(R.string.preconsent_screen_trusted_readers_description),
                    selected = currentSetting is DocumentPreconsentSetting.ReaderIdentityBased,
                    onClick = {
                        coroutineScope.launch {
                            if (currentSetting !is DocumentPreconsentSetting.ReaderIdentityBased) {
                                documentInfo.document.setPreconsentSetting(
                                    DocumentPreconsentSetting.ReaderIdentityBased(
                                        approvedReaders = emptyList()
                                    )
                                )
                            }
                        }
                    },
                    extraContent = {
                        if (currentSetting is DocumentPreconsentSetting.ReaderIdentityBased) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 8.dp)
                            ) {
                                if (currentSetting.approvedReaders.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        currentSetting.approvedReaders.forEach { reader ->
                                            AssistChip(
                                                onClick = onManageTrustedReaders,
                                                label = {
                                                    Text(reader.metadata.displayName ?: reader.certificate.subject.toString())
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.preconsent_trusted_readers_no_readers),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                FilterChip(
                                    selected = true,
                                    onClick = onManageTrustedReaders,
                                    label = { Text(stringResource(R.string.preconsent_trusted_readers_manage)) }
                                )
                            }
                        }
                    }
                )

                // 4. Share automatically
                SettingRow(
                    title = stringResource(R.string.preconsent_screen_share_automatically_title),
                    description = stringResource(R.string.preconsent_screen_share_automatically_description),
                    selected = currentSetting is DocumentPreconsentSetting.NeverRequireConsent,
                    onClick = {
                        coroutineScope.launch {
                            documentInfo.document.setPreconsentSetting(DocumentPreconsentSetting.NeverRequireConsent)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    extraContent: @Composable () -> Unit = {}
) {
    FloatingItemContainer(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = selected,
                onClick = null, // handled by container modifier
                modifier = Modifier.padding(top = 2.dp, end = 16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                extraContent()
            }
        }
    }
}
