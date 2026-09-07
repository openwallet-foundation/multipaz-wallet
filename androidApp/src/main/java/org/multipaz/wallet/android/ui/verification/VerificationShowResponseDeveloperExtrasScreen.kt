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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.claim.organizeByNamespace
import org.multipaz.compose.datetime.formattedDateTime
import org.multipaz.compose.decodeImage
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.SdJwtKb
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.util.Logger
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toHex
import org.multipaz.verification.Iso18013PresentmentRecord
import org.multipaz.verification.JsonVerifiedPresentation
import org.multipaz.verification.MdocVerifiedPresentation
import org.multipaz.verification.OpenID4VPPresentmentRecord
import org.multipaz.verification.PresentmentRecord
import org.multipaz.verification.QueryData
import org.multipaz.verification.SdJwtQueryData
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
import org.multipaz.revocation.RevocationChecker
import org.multipaz.revocation.RevocationCheckResult
import org.multipaz.revocation.RevocationCheckState
import kotlinx.coroutines.launch

private const val TAG = "VerificationShowResponseDeveloperExtrasScreen"

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
    revocationChecker: RevocationChecker? = null,
    onViewCbor: ((title: String, cborBytes: ByteArray) -> Unit)? = null,
    onViewJson: ((title: String, jsonString: String) -> Unit)? = null,
    onViewJwt: ((title: String, jwtString: String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var savedScrollPosition by rememberSaveable { mutableIntStateOf(0) }
    var hasRestoredScroll by remember { mutableStateOf(false) }

    val parsingResponseFailed = remember { mutableStateOf<Exception?>(null) }
    val showNotTrusted = remember { mutableStateOf(false) }
    val devModeEnabled = settingsModel.devMode.collectAsState().value

    val verificationError = remember { mutableStateOf<Throwable?>(null) }
    val verficationResult = remember { mutableStateOf<VerificationResult?>(null) }
    val revocationCheckStatuses = remember { mutableStateMapOf<Int, RevocationCheckStatus>() }

    val wrappedOnViewCbor: ((title: String, cborBytes: ByteArray) -> Unit)? = if (onViewCbor != null) {
        { title, cborBytes ->
            savedScrollPosition = scrollState.value
            onViewCbor(title, cborBytes)
        }
    } else null

    val wrappedOnViewJson: ((title: String, jsonString: String) -> Unit)? = if (onViewJson != null) {
        { title, jsonString ->
            savedScrollPosition = scrollState.value
            onViewJson(title, jsonString)
        }
    } else null

    val wrappedOnViewJwt: ((title: String, jwtString: String) -> Unit)? = if (onViewJwt != null) {
        { title, jwtString ->
            savedScrollPosition = scrollState.value
            onViewJwt(title, jwtString)
        }
    } else null

    val wrappedOnViewCertChain: ((certChain: X509CertChain) -> Unit)? = if (onViewCertChain != null) {
        { certChain ->
            savedScrollPosition = scrollState.value
            onViewCertChain(certChain)
        }
    } else null

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .collect { value ->
                if (hasRestoredScroll) {
                    savedScrollPosition = value
                }
            }
    }

    LaunchedEffect(verficationResult.value) {
        if (verficationResult.value != null && !hasRestoredScroll) {
            if (savedScrollPosition > 0) {
                withTimeoutOrNull(1000) {
                    snapshotFlow { scrollState.maxValue }
                        .filter { it > 0 }
                        .first()
                }
                scrollState.scrollTo(savedScrollPosition.coerceAtMost(scrollState.maxValue))
            }
            hasRestoredScroll = true
        }
    }

    val onTriggerRevocationCheck: (vpNum: Int, revocationStatus: RevocationStatus, certChain: X509CertChain) -> Unit = { vpNum, revStatus, certChain ->
        revocationCheckStatuses[vpNum] = RevocationCheckStatus.Checking
        coroutineScope.launch {
            val result = if (revocationChecker != null && revStatus !is RevocationStatus.Unknown) {
                val trustResult = issuerTrustManager.verify(certChain.certificates, atTime)
                val issuerCert = trustResult.trustChain?.certificates?.last()
                Logger.i(TAG, "Passing cert: ${issuerCert?.toPem()}")
                revocationChecker.check(
                    revocationStatus = revStatus,
                    issuerCert = issuerCert,
                    onlyTrusted = false,
                    atTime = atTime,
                    bypassCache = true
                )
            } else {
                RevocationCheckResult(RevocationCheckState.UNKNOWN, isTrusted = false, error = IllegalStateException("No revocation checker available"))
            }
            Logger.i(TAG, "RevocationCheckResult: $result")
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
                presentmentRecord = presentmentRecord,
                verifiedPresentations = vps,
                issuerTrustManager = issuerTrustManager,
                onViewCertChain = wrappedOnViewCertChain,
                onViewCbor = wrappedOnViewCbor,
                onViewJson = wrappedOnViewJson,
                onViewJwt = wrappedOnViewJwt,
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

    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = { Text(stringResource(R.string.verification_show_response_developer_extras_title)) },
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
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
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
                                            wrappedOnViewCertChain?.let { it(line.value.certChain) }
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


private suspend fun createPresentmentRecordSection(
    presentmentRecord: PresentmentRecord,
    onViewCbor: ((title: String, cborBytes: ByteArray) -> Unit)?,
    onViewJson: ((title: String, jsonString: String) -> Unit)?,
    onViewJwt: ((title: String, jwtString: String) -> Unit)? = null
): Section {
    val lines = mutableListOf<Line>()
    val title: String
    when (presentmentRecord) {
        is Iso18013PresentmentRecord -> {
            title = "ISO/IEC 18013-5 Presentment Record"
            val origin = presentmentRecord.origin
            if (origin != null) {
                lines.add(Line("Origin", ValueText(origin)))
            } else {
                lines.add(Line("Channel", ValueText("Proximity")))
            }

            val requestBytes = try {
                Cbor.encode(presentmentRecord.request)
            } catch (e: Throwable) {
                null
            }
            if (requestBytes != null) {
                val formatted = "%,d".format(requestBytes.size)
                lines.add(
                    Line(
                        header = "Request",
                        value = ValueText("$formatted bytes of CBOR"),
                        onClick = { onViewCbor?.invoke("Device Request", requestBytes) },
                        showChevron = true
                    )
                )
            }

            val responseBytes = try {
                Cbor.encode(presentmentRecord.response)
            } catch (e: Throwable) {
                null
            }
            if (responseBytes != null) {
                val formatted = "%,d".format(responseBytes.size)
                lines.add(
                    Line(
                        header = "Response",
                        value = ValueText("$formatted bytes of CBOR"),
                        onClick = { onViewCbor?.invoke("Device Response", responseBytes) },
                        showChevron = true
                    )
                )
            }

            val transcriptBytes = try {
                Cbor.encode(presentmentRecord.sessionTranscript)
            } catch (e: Throwable) {
                null
            }
            if (transcriptBytes != null) {
                val formatted = "%,d".format(transcriptBytes.size)
                lines.add(
                    Line(
                        header = "Session transcript",
                        value = ValueText("$formatted bytes of CBOR"),
                        onClick = { onViewCbor?.invoke("Session Transcript", transcriptBytes) },
                        showChevron = true
                    )
                )
            }

            val encInfo = presentmentRecord.encryptionInfo
            if (encInfo != null) {
                val encInfoBytes = encInfo.toByteArray()
                val formatted = "%,d".format(encInfoBytes.size)
                lines.add(
                    Line(
                        header = "DC API encryption info",
                        value = ValueText("$formatted bytes of CBOR"),
                        onClick = { onViewCbor?.invoke("DC API Encryption Info", encInfoBytes) },
                        showChevron = true
                    )
                )
            }
        }

        is OpenID4VPPresentmentRecord -> {
            title = "OpenID4VP Presentment Record"

            val requestJson = try {
                Json.parseToJsonElement(presentmentRecord.vpRequest).jsonObject
            } catch (e: Throwable) {
                null
            }

            val clientId = requestJson?.get("client_id")?.jsonPrimitive?.content
            if (clientId != null) {
                val header = if (clientId.startsWith("https://") || clientId.startsWith("http://")) {
                    "Origin"
                } else {
                    "Client ID"
                }
                lines.add(Line(header, ValueText(clientId)))
            }

            val requestBytes = presentmentRecord.vpRequest.encodeToByteArray()
            val formattedReqSize = "%,d".format(requestBytes.size)
            lines.add(
                Line(
                    header = "Request",
                    value = ValueText("$formattedReqSize bytes of JSON"),
                    onClick = { onViewJson?.invoke("OpenID4VP Authorization Request", presentmentRecord.vpRequest) },
                    showChevron = true
                )
            )

            val responseBytes = presentmentRecord.vpToken.encodeToByteArray()
            val formattedRespSize = "%,d".format(responseBytes.size)
            lines.add(
                Line(
                    header = "Response",
                    value = ValueText("$formattedRespSize bytes of JSON"),
                    onClick = { onViewJson?.invoke("OpenID4VP VP Token", presentmentRecord.vpToken) },
                    showChevron = true
                )
            )

            val vpTokenElement = try {
                Json.parseToJsonElement(presentmentRecord.vpToken)
            } catch (e: Throwable) {
                null
            }

            if (vpTokenElement != null) {
                val queryDataMap = try {
                    requestJson?.get("dcql_query")?.jsonObject?.let {
                        QueryData.fromDcql(it).associateBy { qd -> qd.id }
                    }
                } catch (e: Throwable) {
                    null
                }

                val entries: List<Pair<String, List<String>>> = when (vpTokenElement) {
                    is JsonObject -> {
                        vpTokenElement.entries.map { (key, value) ->
                            val list = when (value) {
                                is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content }
                                is JsonPrimitive -> listOf(value.content)
                                else -> listOf(value.toString())
                            }
                            Pair(key, list)
                        }
                    }
                    is JsonArray -> {
                        listOf(
                            Pair(
                                "Credential",
                                vpTokenElement.mapNotNull { (it as? JsonPrimitive)?.content }
                            )
                        )
                    }
                    is JsonPrimitive -> {
                        listOf(Pair("Credential", listOf(vpTokenElement.content)))
                    }
                }

                for ((credId, credList) in entries) {
                    credList.forEachIndexed { index, credStr ->
                        val credTitle = if (credList.size > 1) {
                            "VPToken ($credId - ${index + 1}/${credList.size})"
                        } else {
                            "VPToken ($credId)"
                        }

                        val isSdJwt = queryDataMap?.get(credId) is SdJwtQueryData
                            || credStr.contains('~')
                            || (credStr.contains('.') && !credStr.startsWith("{"))

                        val cborBytes = if (!isSdJwt) {
                            try {
                                credStr.fromBase64Url()
                            } catch (e: Throwable) {
                                null
                            }
                        } else {
                            null
                        }

                        val isValidCbor = try {
                            cborBytes != null && run { Cbor.decode(cborBytes); true }
                        } catch (e: Throwable) {
                            false
                        }

                        if (cborBytes != null && isValidCbor) {
                            val formatted = "%,d".format(cborBytes.size)
                            lines.add(
                                Line(
                                    header = credTitle,
                                    value = ValueText("$formatted bytes of CBOR"),
                                    onClick = { onViewCbor?.invoke(credTitle, cborBytes) },
                                    showChevron = true
                                )
                            )
                        } else {
                            val isSdJwt = credStr.contains("~")
                            val credBytes = credStr.encodeToByteArray()
                            val formatted = "%,d".format(credBytes.size)
                            val formatType = if (isSdJwt) "SD-JWT" else "JWT"
                            lines.add(
                                Line(
                                    header = credTitle,
                                    value = ValueText("$formatted bytes of $formatType"),
                                    onClick = { onViewJwt?.invoke(credTitle, credStr) },
                                    showChevron = true
                                )
                            )
                        }
                    }
                }
            }

            val mdocTranscript = presentmentRecord.mdocSessionTranscript
            if (mdocTranscript != null) {
                val transcriptBytes = try {
                    Cbor.encode(mdocTranscript)
                } catch (e: Throwable) {
                    null
                }
                if (transcriptBytes != null) {
                    val formatted = "%,d".format(transcriptBytes.size)
                    lines.add(
                        Line(
                            header = "Session transcript",
                            value = ValueText("$formatted bytes of CBOR"),
                            onClick = { onViewCbor?.invoke("Session Transcript", transcriptBytes) },
                            showChevron = true
                        )
                    )
                }
            }
        }
    }
    return Section(title, lines)
}

private suspend fun parseResponse(
    presentmentRecord: PresentmentRecord,
    verifiedPresentations: List<VerifiedPresentation>,
    issuerTrustManager: TrustManagerInterface,
    onViewCertChain: ((certChain: X509CertChain) -> Unit)?,
    onViewCbor: ((title: String, cborBytes: ByteArray) -> Unit)?,
    onViewJson: ((title: String, jsonString: String) -> Unit)?,
    onViewJwt: ((title: String, jwtString: String) -> Unit)? = null,
    revocationChecker: RevocationChecker?,
    revocationCheckStatuses: Map<Int, RevocationCheckStatus>,
    onTriggerRevocationCheck: (vpNum: Int, revocationStatus: RevocationStatus, certChain: X509CertChain) -> Unit,
    now: Instant = Clock.System.now(),
): VerificationResult {
    val sections = mutableListOf<Section>()
    sections.add(
        createPresentmentRecordSection(
            presentmentRecord = presentmentRecord,
            onViewCbor = onViewCbor,
            onViewJson = onViewJson,
            onViewJwt = onViewJwt
        )
    )
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
                        is RevocationCheckStatus.Completed -> {
                            val res = checkStatus.result
                            val stateStr = when (res.state) {
                                RevocationCheckState.VALID -> "Valid"
                                RevocationCheckState.INVALID -> "Invalid"
                                RevocationCheckState.SUSPENDED -> "Suspended"
                                RevocationCheckState.UNKNOWN -> "Unknown"
                            }
                            val trustStr = if (res.isTrusted) "Trusted" else "Not trusted"
                            val text = if (res.error == null) {
                                "$stateStr ($trustStr)"
                            } else {
                                "$stateStr ($trustStr) [${res.error!!::class.simpleName}]"
                            }
                            Pair(text, true)
                        }
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
                        header = "Verified Presentation ${vpNum + 1} of ${verifiedPresentations.size}",
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
                        is RevocationCheckStatus.Completed -> {
                            val res = checkStatus.result
                            val stateStr = when (res.state) {
                                RevocationCheckState.VALID -> "Valid"
                                RevocationCheckState.INVALID -> "Invalid"
                                RevocationCheckState.SUSPENDED -> "Suspended"
                                RevocationCheckState.UNKNOWN -> "Unknown"
                            }
                            val trustStr = if (res.isTrusted) "Trusted" else "Not trusted"
                            val text = if (res.error == null) {
                                "$stateStr ($trustStr)"
                            } else {
                                "$stateStr ($trustStr) [${res.error!!::class.simpleName}]"
                            }
                            Pair(text, true)
                        }
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
                        header = "Verified Presentation ${vpNum + 1} of ${verifiedPresentations.size}",
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

