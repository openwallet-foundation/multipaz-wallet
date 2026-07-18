package org.multipaz.wallet.android.ui.trustmanagement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import org.multipaz.asn1.OID
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.compose.branding.Branding
import org.multipaz.compose.cards.InfoCard
import org.multipaz.compose.certificateviewer.X509CertViewer
import org.multipaz.compose.datetime.formattedDateTime
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.compose.items.FloatingItemList
import org.multipaz.compose.items.FloatingItemText
import org.multipaz.compose.trustmanagement.TrustEntryInfo
import org.multipaz.compose.trustmanagement.TrustManagerModel
import org.multipaz.crypto.X509CertChain
import org.multipaz.mdoc.rical.RicalCertificateInfo
import org.multipaz.mdoc.rical.SignedRical
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.mdoc.vical.VicalCertificateInfo
import org.multipaz.trustmanagement.TrustEntryBasedTrustManager
import org.multipaz.trustmanagement.TrustEntryRical
import org.multipaz.trustmanagement.TrustEntryVical
import org.multipaz.trustmanagement.TrustEntryX509Cert
import org.multipaz.wallet.android.R

/**
 * A Composable that displays the full details of a specific trust entry.
 *
 * It conditionally renders detailed UI components based on the type of the trust entry
 * (Single X.509 Certificate, VICAL list, or RICAL list). It also provides informational
 * banners for newly imported entries reminding the user to verify the provider.
 *
 * @param trustManagerModel A [TrustManagerModel].
 * @param trustEntryId The unique identifier of the trust entry to display.
 * @param justImported True if the entry was recently added, triggering an informational banner.
 * @param imageLoader a [ImageLoader].
 * @param onViewSignerCertificateChain Callback invoked to view the signer's certificate chain for a VICAL or RICAL.
 * @param onViewVicalEntry Callback invoked to navigate to a specific certificate within a VICAL.
 * @param onViewRicalEntry Callback invoked to navigate to a specific certificate within a RICAL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustEntryViewer(
    trustManagerModel: TrustManagerModel,
    trustEntryId: String,
    justImported: Boolean,
    imageLoader: ImageLoader,
    onViewSignerCertificateChain: (certificateChain: X509CertChain) -> Unit,
    onViewVicalEntry: (vicalCertNum: Int) -> Unit,
    onViewRicalEntry: (ricalCertNum: Int) -> Unit,
) {
    val entryInfo = trustManagerModel.trustManagerInfos.collectAsState().value?.find {
        it.entry.identifier == trustEntryId
    } ?: return
    val entry = entryInfo.entry

    Column() {
        if (justImported) {
            InfoCard(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = when (entry) {
                        is TrustEntryX509Cert -> stringResource(R.string.trust_entry_viewer_just_imported_cert)
                        is TrustEntryVical -> stringResource(R.string.trust_entry_viewer_just_imported_vical)
                        is TrustEntryRical -> stringResource(R.string.trust_entry_viewer_just_imported_rical)
                    }
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            entryInfo.RenderImage(
                size = 160.dp,
                imageLoader = imageLoader
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = entryInfo.getDisplayName(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            FloatingItemList(
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
                title = null
            ) {
                FloatingItemHeadingAndText(stringResource(R.string.trust_entry_viewer_test_only_heading),
                    if (entry.metadata.testOnly) {
                        stringResource(R.string.trust_entry_viewer_yes)
                    } else {
                        stringResource(R.string.trust_entry_viewer_no)
                    }
                )
            }

            when (entry) {
                is TrustEntryX509Cert -> {
                    X509CertViewer(certificate = entry.certificate)
                }
                is TrustEntryVical -> {
                    VicalDetails(
                        trustEntry = entry,
                        signedVical = entryInfo.signedVical!!,
                        onViewVicalEntry = onViewVicalEntry,
                        onViewCertificateChain = onViewSignerCertificateChain
                    )
                }
                is TrustEntryRical -> {
                    RicalDetails(
                        trustEntry = entry,
                        signedRical = entryInfo.signedRical!!,
                        onViewRicalEntry = onViewRicalEntry,
                        onViewCertificateChain = onViewSignerCertificateChain
                    )
                }
            }
        }
    }
}

@Composable
private fun VicalDetails(
    trustEntry: TrustEntryVical,
    signedVical: SignedVical,
    onViewVicalEntry: (vicalCertNum: Int) -> Unit,
    onViewCertificateChain: (certificateChain: X509CertChain) -> Unit,
) {
    FloatingItemList(
        modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
        title = stringResource(R.string.trust_entry_viewer_vical_data_heading)
    ) {
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_version),
            text = signedVical.vical.version
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_provider),
            text = signedVical.vical.vicalProvider
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_issue),
            text = signedVical.vical.vicalIssueID.toString()
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_issued_at),
            text = formattedDateTime(signedVical.vical.date)
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_next_update),
            text = signedVical.vical.nextUpdate?.let {
                formattedDateTime(it)
            } ?: AnnotatedString("-")
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_valid_until),
            text = signedVical.vical.notAfter?.let {
                formattedDateTime(it)
            } ?: AnnotatedString("-")
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_update_url),
            text = signedVical.vical.vicalUrl ?: "-"
        )
        FloatingItemHeadingAndText(
            stringResource(R.string.trust_entry_viewer_signer),
            stringResource(R.string.trust_entry_viewer_click_to_view_chain),
            modifier = Modifier.clickable {
                onViewCertificateChain(signedVical.vicalProviderCertificateChain)
            }
        )
        if (signedVical.vical.extensions.isNotEmpty()) {
            ItemWithExtensions(
                heading = stringResource(R.string.trust_entry_viewer_extensions),
                extensions = signedVical.vical.extensions
            )
        }
    }

    FloatingItemList(
        modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
        title = stringResource(R.string.trust_entry_viewer_certificates)
    ) {
        signedVical.vical.certificateInfos.forEachIndexed { n, certificateInfo ->
            FloatingItemText(
                modifier = Modifier.clickable { onViewVicalEntry(n) },
                image = { certificateInfo.RenderIconWithFallback() },
                text = certificateInfo.displayNameWithFallback,
                secondary = stringResource(R.string.trust_entry_viewer_certificate_label),
            )
        }
    }
}

@Composable
private fun RicalDetails(
    trustEntry: TrustEntryRical,
    signedRical: SignedRical,
    onViewRicalEntry: (ricalCertNum: Int) -> Unit,
    onViewCertificateChain: (certificateChain: X509CertChain) -> Unit,
) {
    FloatingItemList(
        modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
        title = stringResource(R.string.trust_entry_viewer_rical_data_heading)
    ) {
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_type),
            text = signedRical.rical.type
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_version),
            text = signedRical.rical.version
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_provider),
            text = signedRical.rical.provider
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_id),
            text = signedRical.rical.id?.toString() ?: "-"
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_issued_at),
            text = formattedDateTime(signedRical.rical.date)
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_next_update),
            text = signedRical.rical.nextUpdate?.let {
                formattedDateTime(it)
            } ?: AnnotatedString("-")
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_valid_until),
            text = signedRical.rical.notAfter?.let {
                formattedDateTime(it)
            } ?: AnnotatedString("-")
        )
        FloatingItemHeadingAndText(
            heading = stringResource(R.string.trust_entry_viewer_url),
            text = signedRical.rical.latestRicalUrl ?: "-"
        )
        FloatingItemHeadingAndText(
            stringResource(R.string.trust_entry_viewer_signer),
            stringResource(R.string.trust_entry_viewer_click_to_view_chain),
            modifier = Modifier.clickable {
                onViewCertificateChain(signedRical.ricalProviderCertificateChain)
            }
        )
        if (signedRical.rical.extensions.isNotEmpty()) {
            ItemWithExtensions(
                heading = stringResource(R.string.trust_entry_viewer_extensions),
                extensions = signedRical.rical.extensions
            )
        }
    }

    FloatingItemList(
        modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
        title = stringResource(R.string.trust_entry_viewer_certificates)
    ) {
        signedRical.rical.certificateInfos.forEachIndexed { n, certificateInfo ->
            FloatingItemText(
                modifier = Modifier.clickable { onViewRicalEntry(n) },
                image = { certificateInfo.RenderIconWithFallback() },
                text = certificateInfo.displayNameWithFallback,
                secondary = stringResource(R.string.trust_entry_viewer_certificate_label),
            )
        }
    }
}

/**
 * Shows a list of VICAL/RICAL extensions.
 *
 * @param heading will be shown in bold at the top.
 * @param extensions the extensions to show.
 * @param modifier a [Modifier].
 */
@Composable
internal fun ItemWithExtensions(
    heading: String,
    extensions: Map<String, DataItem>,
    modifier: Modifier = Modifier
) {
    val sb = StringBuilder()
    extensions.forEach { (key, value) ->
        val valueStr = Cbor.toDiagnostics(value, setOf(
            DiagnosticOption.PRETTY_PRINT
        ))
        sb.append("$key: $valueStr\n")
    }
    return FloatingItemHeadingAndText(
        heading = heading,
        text = sb.toString(),
        modifier = modifier
    )
}

/**
 * Generates an avatar icon for a [VicalCertificateInfo] based on its display name.
 * Uses a deterministic background color and the name's initials.
 */
@Composable
internal fun VicalCertificateInfo.RenderIconWithFallback(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Branding.Current.collectAsState().value.AvatarIcon(
        size = size,
        name = displayNameWithFallback,
        additionalData = certificate.encoded.toByteArray(),
        modifier = modifier
    )
}

/**
 * Resolves the common name of the certificate subject as a display string,
 * falling back to the full subject name if a common name is not present.
 */
internal val VicalCertificateInfo.displayNameWithFallback: String
    get() {
        val subject = certificate.subject
        val commonName = subject.components[OID.COMMON_NAME.oid]
        if (commonName != null) {
            return commonName.value
        }
        return subject.name
    }

/**
 * Generates an avatar icon for a [RicalCertificateInfo] based on its display name.
 * Uses a deterministic background color and the name's initials.
 */
@Composable
internal fun RicalCertificateInfo.RenderIconWithFallback(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Branding.Current.collectAsState().value.AvatarIcon(
        size = size,
        name = displayNameWithFallback,
        additionalData = certificate.encoded.toByteArray(),
        modifier = modifier
    )
}

/**
 * Resolves the common name of the certificate subject as a display string,
 * falling back to the full subject name if a common name is not present.
 */
internal val RicalCertificateInfo.displayNameWithFallback: String
    get() {
        val subject = certificate.subject
        val commonName = subject.components[OID.COMMON_NAME.oid]
        if (commonName != null) {
            return commonName.value
        }
        return subject.name
    }
