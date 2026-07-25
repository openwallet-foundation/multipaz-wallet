package org.multipaz.wallet.android.ui.verification

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz.wallet.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.claim.organizeByNamespace
import org.multipaz.compose.datetime.formattedDateTime
import org.multipaz.compose.decodeImage
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.nfc.MdocHandoverType
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toHex
import org.multipaz.verification.JsonVerifiedPresentation
import org.multipaz.verification.MdocVerifiedPresentation
import org.multipaz.verification.PresentmentRecord
import org.multipaz.verification.VerificationUtil
import org.multipaz.verification.VerifiedPresentation
import org.multipaz.wallet.android.LinkVerification
import org.multipaz.wallet.android.settings.SettingsModel
import org.multipaz.wallet.android.ui.Note
import org.multipaz.wallet.client.verification.ProximityReaderModel
import org.multipaz.wallet.client.verification.ProximityReaderModelResult
import org.multipaz.wallet.client.verification.Query
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

import androidx.compose.runtime.mutableStateMapOf
import org.multipaz.revocation.RevocationStatus
import org.multipaz.wallet.client.verification.RevocationChecker
import org.multipaz.wallet.client.verification.RevocationCheckResult
import org.multipaz.wallet.client.verification.RevocationCheckState
import kotlinx.coroutines.launch

private const val TAG = "VerificationShowResponseScreen"

private sealed class RevocationCheckStatus {
    object Idle : RevocationCheckStatus()
    object Checking : RevocationCheckStatus()
    data class Completed(val result: RevocationCheckResult) : RevocationCheckStatus()
}

private sealed class Value

private data class ValueText(
    val text: String
): Value()

private data class ValueSize(
    val size: Long
): Value()

private data class ValueImage(
    val text: String?,
    val image: ImageBitmap
): Value()

private data class ValueDateTime(
    val dateTime: Instant?
): Value()

private data class ValueDuration(
    val duration: Duration?
): Value()

private data class ValueCertChain(
    val certChain: X509CertChain
): Value()

private data class Line(
    val header: String,
    val value: Value,
    val onClick: (() -> Unit)? = null,
    val showChevron: Boolean = true
)

private data class Section(
    val header: String,
    val lines: List<Line>
)

private data class VerificationResult(
    val sections: List<Section>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationShowResponseDeveloperExtrasScreen(
    query: Query,
    presentmentRecord: PresentmentRecord,
    atTime: Instant,
    issuerTrustManager: TrustManagerInterface,
    settingsModel: SettingsModel,
    documentTypeRepository: DocumentTypeRepository,
    zkSystemRepository: ZkSystemRepository,
    onBackClicked: () -> Unit,
    onViewCertChain: ((certChain: X509CertChain) -> Unit)?,
    revocationChecker: RevocationChecker? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val parsingResponseFailed = remember { mutableStateOf<Exception?>(null) }
    val showNotTrusted = remember { mutableStateOf(false) }
    val devModeEnabled = settingsModel.devMode.collectAsState().value

    val verificationError = remember { mutableStateOf<Throwable?>(null) }
    val verficationResult = remember { mutableStateOf<VerificationResult?>(null) }
    val revocationCheckStatuses = remember { mutableStateMapOf<Int, RevocationCheckStatus>() }

    val onTriggerRevocationCheck: (vpNum: Int, revocationStatus: RevocationStatus, certChain: X509CertChain) -> Unit = { vpNum, revStatus, certChain ->
        revocationCheckStatuses[vpNum] = RevocationCheckStatus.Checking
        coroutineScope.launch {
            val result = if (revocationChecker != null && revStatus !is RevocationStatus.Unknown) {
                revocationChecker.check(
                    revocationStatus = revStatus,
                    issuerCertChain = certChain,
                    atTime = atTime,
                    bypassCache = true
                )
            } else {
                RevocationCheckResult(RevocationCheckState.UNKNOWN, IllegalStateException("No revocation checker available"))
            }
            revocationCheckStatuses[vpNum] = RevocationCheckStatus.Completed(result)
        }
    }

    val verifiedPresentationsState = remember { mutableStateOf<List<VerifiedPresentation>?>(null) }

    LaunchedEffect(Unit) {
        try {
            verifiedPresentationsState.value = presentmentRecord.verify(
                atTime = atTime,
                documentTypeRepository = documentTypeRepository,
                zkSystemRepository = zkSystemRepository
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "Error verifying presentment record", e)
            verificationError.value = e
        }
    }

    LaunchedEffect(verifiedPresentationsState.value, revocationCheckStatuses.toMap()) {
        val vps = verifiedPresentationsState.value ?: return@LaunchedEffect
        try {
            verficationResult.value = parseResponse(
                verifiedPresentations = vps,
                issuerTrustManager = issuerTrustManager,
                onViewCertChain = onViewCertChain,
                revocationChecker = revocationChecker,
                revocationCheckStatuses = revocationCheckStatuses,
                onTriggerRevocationCheck = onTriggerRevocationCheck
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e(TAG, "Error parsing response", e)
            verificationError.value = e
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.verification_show_response_developer_extras_title)) },
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
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Note(
                markdownString = stringResource(R.string.verification_show_response_developer_extras_note)
            )
            if (verificationError.value != null) {
                Text(text = verificationError.value!!.message ?: stringResource(R.string.verification_show_response_developer_extras_failed))
            } else if (verficationResult.value != null) {
                for (section in verficationResult.value!!.sections) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FloatingItemList(title = section.header) {
                        for (line in section.lines) {
                            when (line.value) {
                                is ValueText -> {
                                    FloatingItemHeadingAndText(
                                        modifier = if (line.onClick != null) Modifier.clickable { line.onClick.invoke() } else Modifier,
                                        showChevron = line.onClick != null && line.showChevron,
                                        heading = line.header,
                                        text = line.value.text
                                    )
                                }

                                is ValueSize -> {
                                    FloatingItemHeadingAndText(
                                        heading = line.header,
                                        text = stringResource(R.string.verification_show_response_developer_extras_bytes, line.value.size)
                                    )
                                }

                                is ValueImage -> {
                                    FloatingItemHeadingAndContent(
                                        heading = line.header,
                                        content = {
                                            line.value.text?.let {
                                                Text(text = line.value.text)
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                            Image(
                                                bitmap = line.value.image,
                                                modifier = Modifier.size(200.dp),
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }

                                is ValueCertChain -> {
                                    FloatingItemHeadingAndText(
                                        modifier = Modifier.clickable {
                                            onViewCertChain?.let { it(line.value.certChain) }
                                        },
                                        showChevron = true,
                                        heading = line.header,
                                        text = stringResource(R.string.verification_show_response_developer_extras_click_to_view_chain)
                                    )
                                }

                                is ValueDuration -> {
                                    FloatingItemHeadingAndText(
                                        heading = line.header,
                                        text = line.value.duration?.let { stringResource(R.string.verification_show_response_developer_extras_msec, it.inWholeMilliseconds) } ?: "-"
                                    )
                                }

                                is ValueDateTime -> {
                                    FloatingItemHeadingAndText(
                                        heading = line.header,
                                        text = line.value.dateTime?.let {
                                            formattedDateTime(instant = it)
                                        } ?: AnnotatedString("-")
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}


private suspend fun parseResponse(
    verifiedPresentations: List<VerifiedPresentation>,
    issuerTrustManager: TrustManagerInterface,
    onViewCertChain: ((certChain: X509CertChain) -> Unit)?,
    revocationChecker: RevocationChecker?,
    revocationCheckStatuses: Map<Int, RevocationCheckStatus>,
    onTriggerRevocationCheck: (vpNum: Int, revocationStatus: RevocationStatus, certChain: X509CertChain) -> Unit,
    now: Instant = Clock.System.now(),
): VerificationResult {
    val sections = mutableListOf<Section>()
    verifiedPresentations.forEachIndexed { vpNum, vp ->
        when (vp) {
            is MdocVerifiedPresentation -> {
                val lines = mutableListOf<Line>()
                lines.add(Line("Credential format", ValueText("ISO mdoc")))
                lines.add(Line("DocType", ValueText(vp.docType)))
                lines.add(Line("Issuer DS curve", ValueText(vp.documentSignerCertChain.certificates.first().ecPublicKey.curve.name)))
                val trustResult =
                    issuerTrustManager.verify(vp.documentSignerCertChain.certificates, now)
                if (trustResult.isTrusted) {
                    val tpName =
                        trustResult.trustPoints.first().metadata?.displayName?.let { " ($it)" } ?: ""
                    lines.add(Line("Issuer Trusted", ValueText("Yes$tpName")))
                } else {
                    lines.add(Line("Issuer Trusted", ValueText("No")))
                }
                lines.add(
                    Line(
                        "Issuer certificate chain",
                        ValueCertChain(vp.documentSignerCertChain),
                        { onViewCertChain?.let { it(vp.documentSignerCertChain) } }
                    )
                )
                if (vp.zkpUsed) {
                    lines.add(Line("ZK proof", ValueText("Successfully verified \uD83E\uDE84")))
                }
                lines.add(Line("Valid from", ValueDateTime(vp.validFrom)))
                lines.add(Line("Valid until", ValueDateTime(vp.validUntil)))
                lines.add(Line("Signed at", ValueDateTime(vp.signedAt)))
                lines.add(Line("Expected update", ValueDateTime(vp.expectedUpdate)))

                val revStatus = vp.revocationStatus
                if (revStatus != null && revStatus !is RevocationStatus.Unknown) {
                    val revInfoText = buildString {
                        when (revStatus) {
                            is RevocationStatus.StatusList -> {
                                append("Format: StatusList\n")
                                append("URI: ${revStatus.uri}\n")
                                append("Index: ${revStatus.idx}\n")
                                append("Cert in payload: ${if (revStatus.certificate != null) "Yes" else "No (using issuer cert)"}")
                            }
                            is RevocationStatus.IdentifierList -> {
                                append("Format: IdentifierList\n")
                                append("URI: ${revStatus.uri}\n")
                                append("Identifier: ${revStatus.id.toByteArray().toHex()}\n")
                                append("Cert in payload: ${if (revStatus.certificate != null) "Yes" else "No (using issuer cert)"}")
                            }
                        }
                    }
                    lines.add(Line("Revocation info", ValueText(revInfoText)))

                    val checkStatus = revocationCheckStatuses[vpNum] ?: RevocationCheckStatus.Idle
                    val (statusText, isClickable) = when (checkStatus) {
                        is RevocationCheckStatus.Idle -> Pair("Click to check status", true)
                        is RevocationCheckStatus.Checking -> Pair("Checking status...", false)
                        is RevocationCheckStatus.Completed -> Pair("${checkStatus.result.state}${checkStatus.result.error?.let { ": ${it.message}" } ?: ""}", true)
                    }

                    val onClickAction: (() -> Unit)? = if (revocationChecker != null && isClickable) {
                        {
                            onTriggerRevocationCheck(vpNum, revStatus, vp.documentSignerCertChain)
                        }
                    } else null

                    lines.add(
                        Line(
                            header = "Revocation status check",
                            value = ValueText(statusText),
                            onClick = onClickAction,
                            showChevron = false
                        )
                    )
                } else {
                    lines.add(Line("Revocation status", ValueText("Not present")))
                }

                sections.add(
                    Section(
                        header = "Document ${vpNum + 1} of ${verifiedPresentations.size}",
                        lines = lines
                    )
                )

                for (n in listOf(0, 1)) {
                    val claims = if (n == 0) { vp.issuerSignedClaims } else { vp.deviceSignedClaims }
                    for ((namespace, claims) in claims.organizeByNamespace()) {
                        val claimLines = mutableListOf<Line>()
                        for (claim in claims) {
                            val line = if (claim.attribute != null && claim.attribute!!.type == DocumentAttributeType.Picture) {
                                val image = decodeImage(claim.value.asBstr)
                                Line(claim.dataElementName, ValueImage(claim.render(), image))
                            } else {
                                Line(claim.dataElementName, ValueText(claim.render()))
                            }
                            claimLines.add(line)
                        }
                        if (claimLines.isNotEmpty()) {
                            sections.add(
                                Section(
                                    header = if (n == 0) {
                                        "Namespace $namespace"
                                    } else {
                                        "Namespace $namespace (Device-Signed)"
                                    },
                                    lines = claimLines
                                )
                            )
                        }
                    }
                }
            }

            is JsonVerifiedPresentation -> {
                val lines = mutableListOf<Line>()
                lines.add(Line("Credential format", ValueText("IETF SD-JWT VC")))
                lines.add(Line("Verifiable Credential Type", ValueText(vp.vct)))
                lines.add(Line("Issuer DS curve", ValueText(vp.documentSignerCertChain.certificates.first().ecPublicKey.curve.name)))
                val trustResult =
                    issuerTrustManager.verify(vp.documentSignerCertChain.certificates, now)
                if (trustResult.isTrusted) {
                    val tpName =
                        trustResult.trustPoints.first().metadata?.displayName?.let { " ($it)" } ?: ""
                    lines.add(Line("Issuer Trusted", ValueText("Yes$tpName")))
                } else {
                    lines.add(Line("Issuer Trusted", ValueText("No")))
                }
                lines.add(
                    Line(
                        "Issuer certificate chain",
                        ValueCertChain(vp.documentSignerCertChain),
                        { onViewCertChain?.let { it(vp.documentSignerCertChain) } }
                    )
                )
                if (vp.zkpUsed) {
                    lines.add(Line("ZK proof", ValueText("Successfully verified \uD83E\uDE84")))
                }
                lines.add(Line("Valid from", ValueDateTime(vp.validFrom)))
                lines.add(Line("Valid until", ValueDateTime(vp.validUntil)))
                lines.add(Line("Signed at", ValueDateTime(vp.signedAt)))
                lines.add(Line("Expected update", ValueDateTime(vp.expectedUpdate)))

                val revStatus = vp.revocationStatus
                if (revStatus != null && revStatus !is RevocationStatus.Unknown) {
                    val revInfoText = buildString {
                        when (revStatus) {
                            is RevocationStatus.StatusList -> {
                                append("Format: StatusList\n")
                                append("URI: ${revStatus.uri}\n")
                                append("Index: ${revStatus.idx}\n")
                                append("Cert in payload: ${if (revStatus.certificate != null) "Yes" else "No (using issuer cert)"}")
                            }
                            is RevocationStatus.IdentifierList -> {
                                append("Format: IdentifierList\n")
                                append("URI: ${revStatus.uri}\n")
                                append("Identifier: ${revStatus.id.toByteArray().toHex()}\n")
                                append("Cert in payload: ${if (revStatus.certificate != null) "Yes" else "No (using issuer cert)"}")
                            }
                        }
                    }
                    lines.add(Line("Revocation info", ValueText(revInfoText)))

                    val checkStatus = revocationCheckStatuses[vpNum] ?: RevocationCheckStatus.Idle
                    val (statusText, isClickable) = when (checkStatus) {
                        is RevocationCheckStatus.Idle -> Pair("Click to check status", true)
                        is RevocationCheckStatus.Checking -> Pair("Checking status...", false)
                        is RevocationCheckStatus.Completed -> Pair("${checkStatus.result.state}${checkStatus.result.error?.let { ": ${it.message}" } ?: ""}", true)
                    }

                    val onClickAction: (() -> Unit)? = if (revocationChecker != null && isClickable) {
                        {
                            onTriggerRevocationCheck(vpNum, revStatus, vp.documentSignerCertChain)
                        }
                    } else null

                    lines.add(
                        Line(
                            header = "Revocation status check",
                            value = ValueText(statusText),
                            onClick = onClickAction,
                            showChevron = false
                        )
                    )
                } else {
                    lines.add(Line("Revocation status", ValueText("Not present")))
                }

                sections.add(
                    Section(
                        header = "Document ${vpNum + 1} of ${verifiedPresentations.size}",
                        lines = lines
                    )
                )

                for (n in listOf(0, 1)) {
                    val claimLines = mutableListOf<Line>()
                    val (claims, claimsHeader) = if (n == 0) {
                        Pair(vp.issuerSignedClaims, "Claims")
                    } else {
                        Pair(vp.deviceSignedClaims, "Claims (Device Signed)")
                    }
                    for (claim in claims) {
                        val path = claim.claimPath.map { it.jsonPrimitive.content }.joinToString(".")
                        val line = if (claim.attribute != null && claim.attribute!!.type == DocumentAttributeType.Picture) {
                            val image = decodeImage(claim.value.jsonPrimitive.content.fromBase64Url())
                            Line(path, ValueImage(claim.render(), image))
                        } else {
                            Line(path, ValueText(claim.render()))
                        }
                        claimLines.add(line)
                    }
                    if (claimLines.isNotEmpty()) {
                        sections.add(
                            Section(
                                header = claimsHeader,
                                lines = claimLines
                            )
                        )
                    }
                }
            }
        }
    }
    return VerificationResult(sections)
}

