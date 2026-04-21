package org.multipaz.wallet.android.ui.settings

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.context.applicationContext
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.Note
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLoggingSettingsScreen(
    settingsModel: SettingsModel,
    onActivityLoggingEnabledToggled: (newEnabledValue: Boolean) -> Unit,
    onActivityLoggingLocationEnabledToggled: (newEnabledValue: Boolean) -> Unit,
    onActivityLogView: () -> Unit,
    onBackClicked: () -> Unit,
    showToast: (message: String) -> Unit
) {
    val scrollState = rememberScrollState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.activity_logging_settings_screen_title))
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Note(
                markdownString = stringResource(R.string.activity_logging_settings_screen_activity_log_info_text)
            )
            FloatingItemList {
                FloatingItemText(
                    text = stringResource(R.string.activity_logging_settings_screen_logging_enabled_text),
                    image = {
                        Icon(Icons.Outlined.History, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsModel.eventLoggingEnabled.collectAsState().value,
                            onCheckedChange = { value -> onActivityLoggingEnabledToggled(value) }
                        )
                    }
                )
                val logLocation = settingsModel.eventLoggingLocationEnabled.collectAsState().value
                val locationPermissionType = if (logLocation) {
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED) {
                        stringResource(R.string.activity_logging_settings_screen_using_precise_location)
                    } else if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        stringResource(R.string.activity_logging_settings_screen_using_coarse_location)
                    } else {
                        val message = stringResource(R.string.activity_logging_settings_screen_permission_not_granted)
                        LaunchedEffect(Unit) {
                            showToast(message)
                            delay(2.seconds)
                            settingsModel.eventLoggingLocationEnabled.value = false
                        }
                        null
                    }
                } else {
                    null
                }
                FloatingItemText(
                    text = stringResource(R.string.activity_logging_settings_screen_logging_location_for_proximity_enabled_text),
                    secondary = locationPermissionType,
                    image = {
                        Icon(
                            imageVector = if (logLocation) {
                                Icons.Outlined.LocationOn
                            } else {
                                Icons.Outlined.LocationOff
                            },
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = logLocation,
                            onCheckedChange = { value -> onActivityLoggingLocationEnabledToggled(value) }
                        )
                    }
                )
                FloatingItemText(
                    modifier = Modifier.clickable { onActivityLogView() },
                    text = stringResource(R.string.activity_logging_settings_screen_view_and_manage_events_text),
                    image = {
                        Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = null)
                    },
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}