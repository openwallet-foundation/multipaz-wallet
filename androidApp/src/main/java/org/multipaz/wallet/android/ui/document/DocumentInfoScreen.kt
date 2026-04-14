package org.multipaz.wallet.android.ui.document

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Bstr
import org.multipaz.claim.Claim
import org.multipaz.claim.JsonClaim
import org.multipaz.claim.MdocClaim
import org.multipaz.compose.claim.RenderClaimValue
import org.multipaz.compose.datetime.formattedDate
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.text.fromMarkdown
import org.multipaz.documenttype.DocumentAttribute
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.wallet.android.R
import kotlin.time.Instant

import androidx.compose.material.icons.outlined.Science
import org.multipaz.wallet.android.settings.SettingsModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentInfoScreen(
    documentId: String,
    documentModel: DocumentModel,
    documentTypeRepository: DocumentTypeRepository,
    settingsModel: SettingsModel,
    onBackClicked: () -> Unit,
    onDeveloperExtrasClicked: () -> Unit,
) {
    val devMode = settingsModel.devMode.collectAsState().value
    val documentInfo = documentModel.documentInfos.collectAsState().value.find {
        it.document.identifier == documentId
    }
    var claims by remember { mutableStateOf<List<Claim>>(emptyList()) }
    var certSignedDate by remember { mutableStateOf<Instant?>(null) }
    var certValidFrom by remember { mutableStateOf<Instant?>(null) }
    var certValidUntil by remember { mutableStateOf<Instant?>(null) }
    var certExpectedUpdate by remember { mutableStateOf<Instant?>(null) }
    var certInfoExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        val document = documentInfo?.document ?: return@LaunchedEffect
        val credentials = document.getCertifiedCredentials()
        val credential = credentials.firstOrNull() ?: return@LaunchedEffect
        claims = credential.getClaims(documentTypeRepository)
        if (credential is MdocCredential) {
            val mso = credential.mso
            certSignedDate = mso.signedAt
            certValidFrom = mso.validFrom
            certValidUntil = mso.validUntil
            certExpectedUpdate = mso.expectedUpdate
        } else if (credential is SdJwtVcCredential) {
            val sdJwt = SdJwt.fromCompactSerialization(credential.issuerProvidedData.decodeToString())
            certSignedDate = sdJwt.issuedAt
            certValidFrom = sdJwt.validFrom
            certValidUntil = sdJwt.validUntil
        }
    }

    val typeDisplayName = documentInfo?.document?.typeDisplayName.orEmpty()

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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = AnnotatedString.fromMarkdown(stringResource(R.string.doc_info_screen_usage_info)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // TODO: Need better renderers for complex claims like Driving Privileges
            //   for mDL and object claims in JSON based credentials. This should probably
            //   be done in the multipaz core library

            val jsonIgnoredClaims = setOf("iss", "vct", "iat", "nbf", "exp", "cnf", "status")
            claims.forEach { claim ->
                if (claim is JsonClaim) {
                    val name = claim.claimPath[0].jsonPrimitive.content
                    if (jsonIgnoredClaims.contains(name)) {
                        return@forEach
                    }
                }
                Text(
                    text = claim.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                RenderClaimValue(claim = claim)
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (certSignedDate != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { certInfoExpanded = !certInfoExpanded }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.doc_info_screen_cert_info_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Icon(
                                imageVector = if (certInfoExpanded)
                                    Icons.Filled.KeyboardArrowUp
                                else
                                    Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (certInfoExpanded) {
                                    stringResource(R.string.doc_info_screen_collapse)
                                } else {
                                    stringResource(R.string.doc_info_screen_expand)
                                }
                            )
                        }
                        AnimatedVisibility(visible = certInfoExpanded) {
                            Column(
                                modifier = Modifier.padding(
                                    start = 32.dp,
                                    end = 16.dp,
                                    bottom = 16.dp
                                )
                            ) {
                                CertificateInfoItem(stringResource(R.string.doc_info_screen_cert_signed_at), certSignedDate)
                                CertificateInfoItem(stringResource(R.string.doc_info_screen_cert_valid_from), certValidFrom)
                                CertificateInfoItem(stringResource(R.string.doc_info_screen_cert_valid_until), certValidUntil)
                                CertificateInfoItem(stringResource(R.string.doc_info_screen_cert_expected_update), certExpectedUpdate)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CertificateInfoItem(label: String, instant: Instant?) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        text = instant?.let { formattedDate(it) }
            ?: AnnotatedString(stringResource(R.string.doc_info_screen_cert_not_set)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
}

private fun Claim.isPictureClaim(): Boolean {
    if (attribute?.type == DocumentAttributeType.Picture) return true
    if (this is MdocClaim && value is Bstr && value.asBstr.size > 100) return true
    return false
}

private fun Claim.withPictureAttribute(): Claim {
    if (attribute?.type == DocumentAttributeType.Picture) return this
    if (this is MdocClaim) {
        return copy(
            attribute = DocumentAttribute(
                type = DocumentAttributeType.Picture,
                identifier = dataElementName,
                displayName = displayName,
                description = "",
                icon = null,
                sampleValueMdoc = null,
                sampleValueJson = null,
                parentAttribute = null,
                embeddedAttributes = emptyList()
            )
        )
    }
    return this
}