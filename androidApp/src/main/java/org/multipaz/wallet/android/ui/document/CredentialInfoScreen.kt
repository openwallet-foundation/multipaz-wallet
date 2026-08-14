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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz.wallet.android.R
import org.multipaz.revocation.IdentifierList
import org.multipaz.revocation.RevocationCheckState
import org.multipaz.revocation.RevocationChecker
import org.multipaz.revocation.RevocationInfo
import org.multipaz.revocation.RevocationStatus
import org.multipaz.revocation.StatusList
import org.multipaz.revocation.getRevocationInfo
import org.multipaz.trustmanagement.TrustManagerInterface
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
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.util.Logger
import org.multipaz.util.toHex
import kotlin.time.Duration
import org.multipaz.securearea.AndroidKeystoreKeyInfo
import org.multipaz.securearea.software.SoftwareKeyInfo
import org.multipaz.securearea.cloud.CloudKeyInfo
import org.multipaz.wallet.android.ui.Note

private const val TAG = "CredentialInfoScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialInfoScreen(
    documentModel: DocumentModel,
    documentId: String,
    credentialId: String,
    revocationChecker: RevocationChecker,
    issuerTrustManager: TrustManagerInterface,
    onBackClicked: () -> Unit,
    onViewCertificateChain: (certChain: X509CertChain) -> Unit,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    val documentInfos = documentModel.documentInfos.collectAsState().value
    val documentInfo = documentInfos.find { it.document.identifier == documentId }
    val credentialInfo = documentInfo?.credentialInfos?.find { it.credential.identifier == credentialId  }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = {
                Text(text = stringResource(R.string.credential_info_delete_pending_title))
            },
            text = {
                Text(text = stringResource(R.string.credential_info_delete_pending_text))
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmationDialog = false }
                ) {
                    Text(text = stringResource(R.string.credential_info_delete_pending_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        coroutineScope.launch {
                            try {
                                documentInfo?.document?.deleteCredential(credentialId)
                                onBackClicked()
                            } catch (e: Throwable) {
                                if (e is CancellationException) throw e
                                Logger.e(TAG, "Failed to delete credential $credentialId", e)
                                showToast("Failed to delete credential: ${e.message}")
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.credential_info_delete_pending_confirm))
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(stringResource(R.string.credential_info_title))
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
                    if (credentialInfo != null && !credentialInfo.credential.isCertified) {
                        IconButton(onClick = { showDeleteConfirmationDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.credential_info_delete_pending_content_description)
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Note(
                markdownString = stringResource(R.string.credential_info_note)
            )
            if (credentialInfo != null) {
                FloatingItemList() {
                    CredentialInfoSection(
                        credentialInfo = credentialInfo,
                        revocationChecker = revocationChecker,
                        issuerTrustManager = issuerTrustManager,
                        onViewCertificateChain = onViewCertificateChain,
                        showToast = showToast
                    )
                }
                if (credentialInfo.credential.isCertified) {
                    CredentialClaimsSection(credentialInfo)
                }
            } else {
                FloatingItemList {
                    FloatingItemCenteredText(stringResource(R.string.credential_info_empty))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CredentialInfoSection(
    credentialInfo: CredentialInfo,
    revocationChecker: RevocationChecker,
    issuerTrustManager: TrustManagerInterface,
    onViewCertificateChain: (certChain: X509CertChain) -> Unit,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    FloatingItemHeadingAndText(stringResource(R.string.credential_info_type), credentialInfo.credential.credentialType)
    FloatingItemHeadingAndText(stringResource(R.string.credential_info_identifier), credentialInfo.credential.identifier)
    FloatingItemHeadingAndText(stringResource(R.string.credential_info_domain), credentialInfo.credential.domain)
    FloatingItemHeadingAndText(stringResource(R.string.credential_info_certified), if (credentialInfo.credential.isCertified) stringResource(R.string.credential_info_yes) else stringResource(R.string.credential_info_no))
    if (credentialInfo.credential.isCertified) {
        FloatingItemHeadingAndText(
            stringResource(R.string.credential_info_valid_from),
            formattedDateTime(credentialInfo.credential.validFrom)
        )
        FloatingItemHeadingAndText(
            stringResource(R.string.credential_info_valid_until),
            formattedDateTime(credentialInfo.credential.validUntil)
        )
        FloatingItemHeadingAndText(
            "Issuer provided data",
            "${credentialInfo.credential.issuerProvidedData.size} bytes"
        )
        FloatingItemHeadingAndText("Usage Count", credentialInfo.credential.usageCount.toString())
        RevocationStatusSection(
            revocationChecker = revocationChecker,
            issuerTrustManager = issuerTrustManager,
            credential = credentialInfo.credential
        )
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
                    showChevron = true,
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
        val keyInfo = credentialInfo.keyInfo
        FloatingItemHeadingAndText(
            "Secure Area",
            (credentialInfo.credential as SecureAreaBoundCredential).secureArea.displayName
        )
        FloatingItemHeadingAndText(
            "Secure Area Identifier",
            (credentialInfo.credential as SecureAreaBoundCredential).secureArea.identifier
        )
        if (keyInfo != null) {
            FloatingItemHeadingAndText(
                "Device Key Algorithm",
                keyInfo.algorithm.description
            )
            when (keyInfo) {
                is AndroidKeystoreKeyInfo -> {
                    FloatingItemHeadingAndText(
                        "Device Key in StrongBox",
                        if (keyInfo.isStrongBoxBacked) "Yes" else "No"
                    )
                    FloatingItemHeadingAndText(
                        "Device Key User Auth Required",
                        if (keyInfo.isUserAuthenticationRequired) "Yes" else "No"
                    )
                    if (keyInfo.isUserAuthenticationRequired) {
                        val timeoutText = if (keyInfo.userAuthenticationTimeout == Duration.ZERO) {
                            "0 (Authenticate for every use)"
                        } else {
                            "${keyInfo.userAuthenticationTimeout.inWholeSeconds} s"
                        }
                        FloatingItemHeadingAndText("Device Key User Auth Timeout", timeoutText)
                        val typesText = if (keyInfo.userAuthenticationTypes.isEmpty()) {
                            "None"
                        } else {
                            keyInfo.userAuthenticationTypes.joinToString(", ") { it.name }
                        }
                        FloatingItemHeadingAndText("Device Key User Auth Types", typesText)
                    }
                    if (keyInfo.attestKeyAlias != null) {
                        FloatingItemHeadingAndText("Device Key Attest Key Alias", keyInfo.attestKeyAlias!!)
                    }
                    if (keyInfo.validFrom != null) {
                        FloatingItemHeadingAndText("Device Key Valid From", formattedDateTime(keyInfo.validFrom!!))
                    }
                    if (keyInfo.validUntil != null) {
                        FloatingItemHeadingAndText("Device Key Valid Until", formattedDateTime(keyInfo.validUntil!!))
                    }
                }
                is SoftwareKeyInfo -> {
                    FloatingItemHeadingAndText(
                        "Device Key Passphrase Protected",
                        if (keyInfo.isPassphraseProtected) "Yes" else "No"
                    )
                    FloatingItemHeadingAndText(
                        "Device Key User Auth Required",
                        if (keyInfo.isUserAuthenticationRequired) "Yes" else "No"
                    )
                    if (keyInfo.isUserAuthenticationRequired) {
                        val typesText = if (keyInfo.userAuthenticationTypes.isEmpty()) {
                            "None"
                        } else {
                            keyInfo.userAuthenticationTypes.joinToString(", ") { it.name }
                        }
                        FloatingItemHeadingAndText("Device Key User Auth Types", typesText)
                    }
                }
                is CloudKeyInfo -> {
                    FloatingItemHeadingAndText(
                        "Device Key Passphrase Required",
                        if (keyInfo.isPassphraseRequired) "Yes" else "No"
                    )
                    FloatingItemHeadingAndText(
                        "Device Key User Auth Required",
                        if (keyInfo.isUserAuthenticationRequired) "Yes" else "No"
                    )
                    if (keyInfo.isUserAuthenticationRequired) {
                        val typesText = if (keyInfo.userAuthenticationTypes.isEmpty()) {
                            "None"
                        } else {
                            keyInfo.userAuthenticationTypes.joinToString(", ") { it.name }
                        }
                        FloatingItemHeadingAndText("Device Key User Auth Types", typesText)
                    }
                    if (keyInfo.validFrom != null) {
                        FloatingItemHeadingAndText("Device Key Valid From", formattedDateTime(keyInfo.validFrom!!))
                    }
                    if (keyInfo.validUntil != null) {
                        FloatingItemHeadingAndText("Device Key Valid Until", formattedDateTime(keyInfo.validUntil!!))
                    }
                }
            }
        }
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
            showChevron = true,
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
private fun RevocationStatusSection(
    revocationChecker: RevocationChecker,
    issuerTrustManager: TrustManagerInterface,
    credential: Credential
) {
    val coroutineScope = rememberCoroutineScope()
    val revocationData = remember { mutableStateOf<RevocationInfo?>(null) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            revocationData.value = credential.getRevocationInfo(issuerTrustManager)
        }
    }
    val value = revocationData.value
    if (value != null) {
        RevocationCheckSection(revocationChecker, value)
    } else {
        FloatingItemHeadingAndText(
            heading = "Revocation info",
            text = "Not found"
        )
    }
}

@Composable
private fun RevocationCheckSection(
    revocationChecker: RevocationChecker,
    revocationData: RevocationInfo
) {
    val coroutineScope = rememberCoroutineScope()
    val statusText = remember { mutableStateOf("Click to check status") }
    val heading = when (revocationData.revocationStatus) {
        is RevocationStatus.StatusList -> "Status List Revocation"
        is RevocationStatus.IdentifierList -> "Identifier List Revocation"
        else -> "Revocation info"
    }
    val detailsText = when (val status = revocationData.revocationStatus) {
        is RevocationStatus.StatusList -> {
            "Index: ${status.idx}\nUrl: ${status.uri}"
        }
        is RevocationStatus.IdentifierList -> {
            "Identifier: ${status.id.toByteArray().toHex()}\nUrl: ${status.uri}"
        }
        else -> "Unknown revocation data format"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                coroutineScope.launch {
                    val result = revocationChecker.check(
                        revocationStatus = revocationData.revocationStatus,
                        issuerCert = revocationData.certificate,
                        onlyTrusted = false
                    )
                    Logger.i(TAG, "RevocationCheckResult: $result")
                    val state = when (result.state) {
                        RevocationCheckState.VALID -> "Valid"
                        RevocationCheckState.INVALID -> "Invalid"
                        RevocationCheckState.SUSPENDED -> "Suspended"
                        RevocationCheckState.UNKNOWN -> "Unknown"
                    }
                    val trust = if (result.isTrusted) "Trusted" else "Not trusted"
                    statusText.value = if (result.error == null) {
                        "$state ($trust)"
                    } else {
                        "$state ($trust) [${result.error!!::class.simpleName}]"
                    }
                }
            }
    ) {
        FloatingItemHeadingAndText(
            heading = heading,
            text = "$detailsText\n${statusText.value}"
        )
    }
}


