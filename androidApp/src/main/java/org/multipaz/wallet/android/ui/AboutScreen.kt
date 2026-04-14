package org.multipaz.wallet.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.z4kn4fein.semver.Version
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import org.multipaz.compose.cards.InfoCard
import org.multipaz.util.Logger
import org.multipaz.util.Platform
import org.multipaz.wallet.android.R
import org.multipaz.wallet.shared.BuildConfig

private const val TAG = "AboutScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
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
                    Text(stringResource(R.string.about_screen_title))
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
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppUpdateCard()
            Text("${BuildConfig.APP_NAME} version ${BuildConfig.VERSION}")
            Text("Built using Multipaz SDK version ${Platform.version}")
        }
    }
}

@Composable
private fun AppUpdateCard() {
    // Uncomment below if working on this code from Android Studio.
    //
    //val updateUrl =  "https://apps.multipaz.org/identityreader/LATEST-VERSION.txt"
    //val updateWebsiteUrl =  "https://apps.multipaz.org/"
    //val currentVersion = "0.2.0-pre.1.574b479c"
    val updateUrl = BuildConfig.UPDATE_URL
    val updateWebsiteUrl = BuildConfig.UPDATE_WEBSITE
    val currentVersion = BuildConfig.VERSION

    if (updateUrl.isEmpty()) {
        return
    }

    val latestVersionString = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(true) {
        try {
            val httpClient = HttpClient(Android)
            val response = httpClient.get(updateUrl)
            if (response.status == HttpStatusCode.OK) {
                latestVersionString.value = response.readRawBytes().decodeToString().trim()
                Logger.i(
                    TAG, "Latest available version from $updateWebsiteUrl is ${latestVersionString.value} " +
                        "and our version is $currentVersion")
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "Error checking latest version from $updateWebsiteUrl", e)
        }
    }


    latestVersionString.value?.let {
        val currentVersion = Version.parse(
            versionString = currentVersion,
            strict = false
        )
        val availableVersion = Version.parse(
            versionString = it,
            strict = false
        )
        if (currentVersion < availableVersion) {
            InfoCard(
                modifier = Modifier.padding(8.dp)
            ) {
                val str = buildAnnotatedString {
                    append(
                        "You are running version $currentVersion and version " +
                                "${latestVersionString.value} is the latest available. " +
                                "Visit "
                    )
                    withLink(
                        LinkAnnotation.Url(
                            updateWebsiteUrl,
                            TextLinkStyles(
                                style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline),
                            )
                        )
                    ) {
                        append(updateWebsiteUrl)
                    }
                    append(" to update.")
                }
                Text(text = str)
            }
        }
    }
}