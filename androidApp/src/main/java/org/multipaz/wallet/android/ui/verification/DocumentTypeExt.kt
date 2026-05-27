package org.multipaz.wallet.android.ui.verification

import org.multipaz.wallet.client.verification.DocumentType

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.multipaz.wallet.android.R

@Composable
fun DocumentType.getDisplayName(): String = when(this) {
    DocumentType.MOBILE_DRIVING_LICENSE -> stringResource(R.string.document_type_mobile_driving_license)
    DocumentType.PHOTO_ID -> stringResource(R.string.document_type_photo_id)
    DocumentType.EU_PID -> stringResource(R.string.document_type_eu_pid)
    DocumentType.AADHAAR -> stringResource(R.string.document_type_aadhaar)
    DocumentType.GOOGLE_WALLET_IDPASS -> stringResource(R.string.document_type_google_wallet_idpass)
}
