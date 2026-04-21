package org.multipaz.wallet.android.ui.document

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Science
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.multipaz.claim.JsonClaim
import org.multipaz.compose.claim.RenderClaimValue
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemHeadingAndDate
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.isProximityPresentable
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.Note
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentInfoScreen(
    documentId: String,
    documentModel: DocumentModel,
    settingsModel: SettingsModel,
    onBackClicked: () -> Unit,
    onDeveloperExtrasClicked: () -> Unit,
) {
    val devMode = settingsModel.devMode.collectAsState().value
    val documentInfo = documentModel.documentInfos.collectAsState().value.find {
        it.document.identifier == documentId
    }
    val credentialInfo = documentInfo?.credentialInfos?.first() ?: return

    val typeDisplayName = documentInfo.document.typeDisplayName.orEmpty()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.doc_info_screen_title, typeDisplayName))
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
                    if (devMode) {
                        IconButton(onClick = onDeveloperExtrasClicked) {
                            Icon(
                                imageVector = Icons.Outlined.Science,
                                contentDescription = "Developer Extras"
                            )
                        }
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
                .verticalScroll(rememberScrollState())
        ) {
            val hint = if (documentInfo.isProximityPresentable) {
                stringResource(R.string.doc_info_screen_usage_info)
            } else {
                stringResource(R.string.doc_info_screen_usage_info_not_proximity_presentable)
            }
            Note(markdownString = hint)

            Spacer(modifier = Modifier.height(16.dp))

            // TODO: Need better renderers for complex claims like Driving Privileges
            //   for mDL and object claims in JSON based credentials. This should probably
            //   be done in the multipaz core library

            FloatingItemList {
                val jsonIgnoredClaims = setOf("iss", "vct", "iat", "nbf", "exp", "cnf", "status")
                credentialInfo.claims.forEach { claim ->
                    if (claim is JsonClaim) {
                        val name = claim.claimPath[0].jsonPrimitive.content
                        if (jsonIgnoredClaims.contains(name)) {
                            return@forEach
                        }
                    }
                    FloatingItemHeadingAndContent(
                        heading = claim.displayName,
                        content = {
                            RenderClaimValue(claim = claim)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            FloatingItemList(title = stringResource(R.string.doc_info_screen_cert_info_title)) {
                var certSignedDate: Instant? = null
                var certValidFrom: Instant? = null
                var certValidUntil: Instant? = null
                var certExpectedUpdate: Instant? = null
                if (credentialInfo.credential is MdocCredential) {
                    val mso = (credentialInfo.credential as MdocCredential).mso
                    certSignedDate = mso.signedAt
                    certValidFrom = mso.validFrom
                    certValidUntil = mso.validUntil
                    certExpectedUpdate = mso.expectedUpdate
                } else {
                    val claims = credentialInfo.claims as List<JsonClaim>
                    certSignedDate = claims.getInstant("iat")
                    certValidFrom = claims.getInstant("nbf")
                    certValidUntil = claims.getInstant("exp")
                }
                FloatingItemHeadingAndDate(
                    heading = stringResource(R.string.doc_info_screen_cert_signed_at),
                    date = certSignedDate
                )
                FloatingItemHeadingAndDate(
                    heading = stringResource(R.string.doc_info_screen_cert_valid_from),
                    date = certValidFrom
                )
                FloatingItemHeadingAndDate(
                    heading = stringResource(R.string.doc_info_screen_cert_valid_until),
                    date = certValidUntil
                )
                FloatingItemHeadingAndDate(
                    heading = stringResource(R.string.doc_info_screen_cert_expected_update),
                    date = certExpectedUpdate
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun List<JsonClaim>.getInstant(claimName: String): Instant? {
    val claim = find { it.claimPath.size == 1 && it.claimPath[0].jsonPrimitive.content == claimName }
    claim?.value?.jsonPrimitive?.longOrNull?.let {
        return Instant.fromEpochSeconds(it)
    }
    return null
}
