package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz.compose.items.FloatingItemContainer
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.getDescription
import org.multipaz.wallet.android.getDisplayName
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.verification.AgeOverQuery
import org.multipaz.wallet.client.verification.DrivingPrivilegesQuery
import org.multipaz.wallet.client.verification.IdentificationQuery


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectVerificationTypeScreen(
    settingsModel: SettingsModel,
    onCustomAgeClicked: () -> Unit,
    onBackClicked: () -> Unit,
) {
    val scrollState = rememberScrollState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.select_verification_type_screen_title)) },
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
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Note(stringResource(R.string.select_verification_type_note))
            Spacer(modifier = Modifier.height(10.dp))
            val selectedQuery = settingsModel.readerQuery.collectAsState().value
            FloatingItemList {
                val builtInAges = listOf(18, 21, 65).map { AgeOverQuery(it) }
                RequestOption(
                    title = stringResource(R.string.select_verification_type_age_over_title),
                    description = stringResource(R.string.select_verification_type_age_over_description),
                    selected = selectedQuery is AgeOverQuery,
                    onClick = {
                        if (selectedQuery !is AgeOverQuery) {
                            settingsModel.readerQuery.value = builtInAges.first()
                        }
                    },
                    extraContent = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(
                                16.dp,
                                alignment = Alignment.CenterHorizontally
                            ),
                        ) {
                            for (query in builtInAges) {
                                FilterChip(
                                    selected = query == selectedQuery,
                                    onClick = { settingsModel.readerQuery.value = query },
                                    label = { Text(text = query.getDisplayName()) },
                                )
                            }
                            val customSelected = selectedQuery is AgeOverQuery && !builtInAges.contains(selectedQuery)
                            FilterChip(
                                label = {
                                    Text(
                                        text = if (customSelected) {
                                            stringResource(R.string.select_verification_type_custom_age_selected, selectedQuery.getDisplayName())
                                        } else {
                                            stringResource(R.string.select_verification_type_custom_age)
                                        }
                                    )
                                },
                                selected = customSelected,
                                onClick = { onCustomAgeClicked() },
                            )
                        }

                    }
                )
                RequestOption(
                    title = IdentificationQuery(false).getDisplayName(),
                    description = IdentificationQuery(false).getDescription(),
                    selected = selectedQuery is IdentificationQuery && !selectedQuery.requestStreetAddress,
                    onClick = {
                        settingsModel.readerQuery.value = IdentificationQuery(false)
                    },
                )
                RequestOption(
                    title = IdentificationQuery(true).getDisplayName(),
                    description = IdentificationQuery(true).getDescription(),
                    selected = selectedQuery is IdentificationQuery && selectedQuery.requestStreetAddress,
                    onClick = {
                        settingsModel.readerQuery.value = IdentificationQuery(true)
                    },
                )
                RequestOption(
                    title = DrivingPrivilegesQuery().getDisplayName(),
                    description = DrivingPrivilegesQuery().getDescription(),
                    selected = selectedQuery is DrivingPrivilegesQuery,
                    onClick = {
                        settingsModel.readerQuery.value = DrivingPrivilegesQuery()
                    },
                )

            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RequestOption(
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
