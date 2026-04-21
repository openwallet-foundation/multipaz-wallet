package org.multipaz.wallet.android.ui.setup

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.multipaz.compose.webview.MarkdownText
import org.multipaz.wallet.android.R
import kotlin.math.min

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupEulaScreen(
    loadEula: suspend (locale: String) -> String,
    acceptText: String,
    declineText: String,
    onAcceptClicked: () -> Unit,
    onDeclineClicked: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var eulaText by remember { mutableStateOf<String?>(null) }
    var eulaError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    
    val locale = Locale.current.toLanguageTag()
    LaunchedEffect(retryTrigger) {
        try {
            eulaError = null
            eulaText = loadEula(locale)
        } catch (e: Exception) {
            eulaError = context.getString(R.string.setup_eula_screen_error_message, e.message)
        }
    }

    val isScrolledToBottom by remember {
        derivedStateOf {
            scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue - 50 // allowing a bit of leeway
        }
    }
    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(0.5f).heightIn(min = 64.dp),
                    onClick = onDeclineClicked
                ) {
                    Text(
                        text = declineText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    modifier = Modifier.weight(0.5f).heightIn(min = 64.dp),
                    enabled = eulaText != null,
                    onClick = {
                        if (!isScrolledToBottom) {
                            coroutineScope.launch {
                                val step = (scrollState.viewportSize * 0.9).toInt()
                                scrollState.animateScrollTo(
                                    min(
                                        scrollState.value + step,
                                        scrollState.maxValue
                                    )
                                )
                            }
                        } else {
                            onAcceptClicked()
                        }
                    }
                ) {
                    if (!isScrolledToBottom) {
                        Text(
                            text = stringResource(R.string.setup_eula_screen_more_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = acceptText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (eulaText == null && eulaError == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (eulaError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            textAlign = TextAlign.Center,
                            text = eulaError!!
                        )
                        Button(onClick = { retryTrigger++ }) {
                            Text(stringResource(R.string.setup_eula_screen_retry_button))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.verticalScroll(scrollState)
                ) {
                    MarkdownText(
                        modifier = Modifier.padding(vertical = 16.dp),
                        content = eulaText!!
                    )
                }
            }
        }
    }
}
