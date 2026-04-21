package org.multipaz.wallet.android.ui.document

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Tagged
import org.multipaz.claim.JsonClaim
import org.multipaz.claim.MdocClaim
import org.multipaz.claim.organizeByNamespace
import org.multipaz.compose.datetime.formattedDateTime
import org.multipaz.compose.document.CredentialInfo
import org.multipaz.compose.document.DocumentModel
import org.multipaz.compose.items.FloatingItemCenteredText
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.credential.Credential
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.crypto.X509CertChain
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.mdoc.mso.MobileSecurityObject
import org.multipaz.revocation.IdentifierList
import org.multipaz.revocation.RevocationStatus
import org.multipaz.revocation.StatusList
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.util.Logger
import org.multipaz.util.toHex
import org.multipaz.wallet.android.ui.Note

private const val TAG = "CredentialInfoScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialInfoScreen(
    documentModel: DocumentModel,
    documentId: String,
    credentialId: String,
    onBackClicked: () -> Unit,
    onViewCertificateChain: (certChain: X509CertChain) -> Unit,
    showToast: (message: String) -> Unit
) {
    val documentInfos = documentModel.documentInfos.collectAsState().value
    val documentInfo = documentInfos.find { it.document.identifier == documentId }
    val credentialInfo = documentInfo?.credentialInfos?.find { it.credential.identifier == credentialId  }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text("Credential info")
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Note(
                markdownString = "This screen contains low-level technical details about a specific credential"
            )
            if (credentialInfo != null) {
                FloatingItemList() {
                    CredentialInfoSection(
                        credentialInfo = credentialInfo,
                        onViewCertificateChain = onViewCertificateChain,
                        showToast = showToast
                    )
                }
                if (credentialInfo.credential.isCertified) {
                    CredentialClaimsSection(credentialInfo)
                }
            } else {
                FloatingItemList {
                    FloatingItemCenteredText("No info for credential")
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CredentialInfoSection(
    credentialInfo: CredentialInfo,
    onViewCertificateChain: (certChain: X509CertChain) -> Unit,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    FloatingItemHeadingAndText("Type", credentialInfo.credential.credentialType)
    FloatingItemHeadingAndText("Identifier", credentialInfo.credential.identifier)
    FloatingItemHeadingAndText("Domain", credentialInfo.credential.domain)
    FloatingItemHeadingAndText("Certified", if (credentialInfo.credential.isCertified) "Yes" else "No")
    if (credentialInfo.credential.isCertified) {
        FloatingItemHeadingAndText(
            "Valid From",
            formattedDateTime(credentialInfo.credential.validFrom)
        )
        FloatingItemHeadingAndText(
            "Valid Until",
            formattedDateTime(credentialInfo.credential.validUntil)
        )
        FloatingItemHeadingAndText(
            "Issuer provided data",
            "${credentialInfo.credential.issuerProvidedData.size} bytes"
        )
        FloatingItemHeadingAndText("Usage Count", credentialInfo.credential.usageCount.toString())
        RevocationStatusSection(credentialInfo.credential)
        when (credentialInfo.credential) {
            is MdocCredential -> {
                val issuerSigned = Cbor.decode(credentialInfo.credential.issuerProvidedData.toByteArray())
                val issuerAuth = issuerSigned["issuerAuth"].asCoseSign1
                val msoBytes = issuerAuth.payload!!
                FloatingItemHeadingAndText("MSO size", "${msoBytes.size} bytes")
                FloatingItemHeadingAndText(
                    "ISO mdoc DocType",
                    (credentialInfo.credential as MdocCredential).docType
                )
                FloatingItemHeadingAndText(
                    heading = "ISO mdoc DS Key Certificate",
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.secondary)) {
                            append("Click for details")
                        }
                    },
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            val certChain =
                                issuerAuth.unprotectedHeaders[
                                    CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN)
                                ]!!.asX509CertChain
                            onViewCertificateChain(certChain)
                        }
                    }
                )
            }

            is SdJwtVcCredential -> {
                FloatingItemHeadingAndText(
                    "Verifiable Credential Type",
                    (credentialInfo.credential as SdJwtVcCredential).vct
                )
                // TODO: Show cert chain for key used to sign issuer-signed data. Involves
                //  getting this over the network as specified in section 5 "JWT VC Issuer Metadata"
                //  of https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/ ... how annoying
            }
        }
    }

    if (credentialInfo.credential is SecureAreaBoundCredential) {
        FloatingItemHeadingAndText(
            "Secure Area",
            (credentialInfo.credential as SecureAreaBoundCredential).secureArea.displayName
        )
        FloatingItemHeadingAndText(
            "Secure Area Identifier",
            (credentialInfo.credential as SecureAreaBoundCredential).secureArea.identifier
        )
        FloatingItemHeadingAndText(
            "Device Key Algorithm",
            credentialInfo.keyInfo!!.algorithm.description
        )
        FloatingItemHeadingAndText("Device Key Invalidated",
            buildAnnotatedString {
                if (credentialInfo.keyInvalidated) {
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )) {
                        append("YES")
                    }
                } else {
                    append("No")
                }
            })
        FloatingItemHeadingAndText(
            heading = "Device Key Attestation",
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.secondary)) {
                    append("Click for details")
                }
            },
            modifier = Modifier.clickable {
                coroutineScope.launch {
                    val attestation = (credentialInfo.credential as SecureAreaBoundCredential).getAttestation()
                    if (attestation.certChain != null) {
                        onViewCertificateChain(attestation.certChain!!)
                    } else {
                        showToast("No attestation for Device Key")
                    }
                }
            }
        )
    } else {
        FloatingItemHeadingAndText("Secure Area", "N/A")
    }
}

@Composable
private fun CredentialClaimsSection(credentialInfo: CredentialInfo) {
    when (credentialInfo.credential) {
        is MdocCredential -> {
            val mdocClaimsByNamespace = (credentialInfo.claims as List<MdocClaim>).organizeByNamespace()
            for ((namespace, claims) in mdocClaimsByNamespace) {
                FloatingItemList(title = "Namespace $namespace") {
                    claims.forEach { claim ->
                        FloatingItemHeadingAndText(
                            heading = claim.dataElementName,
                            text = claim.render()
                        )
                    }
                }
            }
        }
        else -> {
            FloatingItemList(title = "Claims") {
                credentialInfo.claims.forEach { claim ->
                    val claimName = if (claim is JsonClaim) {
                        claim.claimPath.map { it.jsonPrimitive.content }.joinToString(".")
                    } else {
                        claim.displayName
                    }
                    FloatingItemHeadingAndText(
                        heading = claimName,
                        text = claim.render()
                    )
                }
            }
        }
    }
}

@Composable
private fun RevocationStatusSection(credential: Credential) {
    val coroutineScope = rememberCoroutineScope()
    val revocationData = remember { mutableStateOf<RevocationData?>(null) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            revocationData.value = extractRevocationData(credential)
        }
    }

    when (val status = revocationData.value?.revocationStatus) {
        is RevocationStatus.Unknown -> {
            FloatingItemHeadingAndText(
                heading = "Revocation info",
                text = "Not parsed"
            )
        }
        is RevocationStatus.StatusList -> StatusListCheckSection(status, revocationData.value!!.certChain)
        is RevocationStatus.IdentifierList -> IdentifierListCheckSection(status, revocationData.value!!.certChain)
        else -> {
            FloatingItemHeadingAndText(
                heading = "Revocation info",
                text = "Not found"
            )
        }
    }
}

private suspend fun extractRevocationData(credential: Credential): RevocationData? {
    return when (credential) {
        is MdocCredential -> {
            val issuerSigned = Cbor.decode(credential.issuerProvidedData.toByteArray())
            val issuerAuth = issuerSigned["issuerAuth"].asCoseSign1
            val certChain = issuerAuth.unprotectedHeaders[
                CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN)
            ]!!.asX509CertChain
            val tagged = Cbor.decode(issuerAuth.payload!!)
            val mso = MobileSecurityObject.fromDataItem(Cbor.decode((tagged as Tagged).taggedItem.asBstr))
            mso.revocationStatus?.let { RevocationData(it, certChain) }
        }
        is SdJwtVcCredential -> {
            val sdjwt = SdJwt.fromCompactSerialization(credential.issuerProvidedData.decodeToString())
            val certChain = sdjwt.x5c ?: return null
            sdjwt.revocationStatus?.let { RevocationData(it, certChain) }
        }
        else -> null
    }
}

private class RevocationData(
    val revocationStatus: RevocationStatus,
    val certChain: X509CertChain
)

private val STATUSLIST_JWT = ContentType("application", "statuslist+jwt")
private val STATUSLIST_CWT = ContentType("application", "statuslist+cwt")

@Composable
private fun StatusListCheckSection(
    status: RevocationStatus.StatusList,
    certChain: X509CertChain
) {
    val coroutineScope = rememberCoroutineScope()
    val statusText = remember { mutableStateOf("Click to check status") }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable {
                coroutineScope.launch {
                    val client = HttpClient(Android)
                    val response = client.get(status.uri) {
                        // CWT is more compact, so prefer that
                        headers.append(
                            name = HttpHeaders.Accept,
                            value = "$STATUSLIST_CWT, $STATUSLIST_JWT;q=0.9"
                        )
                    }
                    if (response.status != HttpStatusCode.OK) {
                        statusText.value = "HTTP Status: ${response.status}"
                    } else {
                        try {
                            val cert = status.certificate ?: certChain.certificates.first()
                            val statusList = when (val type = response.contentType()) {
                                STATUSLIST_JWT -> StatusList.fromJwt(
                                    jwt = response.readRawBytes().decodeToString(),
                                    publicKey = cert.ecPublicKey
                                )
                                STATUSLIST_CWT -> StatusList.fromCwt(
                                    cwt = response.readRawBytes(),
                                    publicKey = cert.ecPublicKey
                                )
                                else -> throw IllegalStateException("Unknown type: $type")
                            }

                            statusText.value = when (val code = statusList[status.idx]) {
                                0 -> "Valid"
                                1 -> "Invalid"
                                2 -> "Suspended"
                                else -> "Unexpected status $code"
                            }
                        } catch (err: Exception) {
                            if (err is CancellationException) throw err
                            Logger.e(TAG, "Failed to parse status list", err)
                            statusText.value = "Failed to parse status list"
                        }
                    }
                }
            }
    ) {
        FloatingItemHeadingAndText(
            heading = "Status List Revocation",
            text = "Index: ${status.idx}\n" +
                    "Url: ${status.uri}\n" +
                    statusText.value
        )
    }
}

@Composable
private fun IdentifierListCheckSection(
    status: RevocationStatus.IdentifierList,
    certChain: X509CertChain
) {
    val coroutineScope = rememberCoroutineScope()
    val statusText = remember { mutableStateOf("Click to check status") }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable {
                coroutineScope.launch {
                    val client = HttpClient(Android)
                    val response = client.get(status.uri)
                    if (response.status != HttpStatusCode.OK) {
                        statusText.value = "HTTP Status: ${response.status}"
                    } else {
                        val cert = status.certificate ?: certChain.certificates.first()
                        try {
                            val identifierList = IdentifierList.fromCwt(
                                cwt = response.readRawBytes(),
                                publicKey = cert.ecPublicKey
                            )
                            statusText.value = if (identifierList.contains(status.id)) {
                                "Invalid"
                            } else {
                                "Valid"
                            }
                        } catch (err: Exception) {
                            if (err is CancellationException) throw err
                            Logger.e(TAG, "Failed to parse identifier list", err)
                            statusText.value = "Failed to parse identifier list"
                        }
                    }
                }
            }
    ) {
        FloatingItemHeadingAndText(
            heading = "Identifier List Revocation",
            text = "Identifier: ${status.id.toByteArray().toHex()}\n" +
                    "Url: ${status.uri}\n" +
                    statusText.value
        )
    }
}


