package org.multipaz.wallet.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import org.multipaz.compose.branding.Branding
import org.multipaz.compose.qrcode.generateQrCode
import org.multipaz.context.initializeApplication

class VerificationLinkQrCodeDisplayActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()

        val layoutParams = window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = layoutParams

        val url = intent.getStringExtra("URL")
        if (url == null) {
            finish()
            return
        }

        setContent {
            val currentBranding = Branding.Current.collectAsState().value
            currentBranding.theme {
                QrCodeDisplayDialog(
                    url = url,
                    onDismissed = { finish() }
                )
            }
        }
    }
}

@Composable
private fun QrCodeDisplayDialog(
    url: String,
    onDismissed: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissed,
        confirmButton = {
            TextButton(onClick = onDismissed) {
                Text(text = stringResource(R.string.request_verification_share_close))
            }
        },
        title = {
            Text(text = stringResource(R.string.request_verification_share_qr_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.request_verification_share_qr_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .dropShadow(
                            shape = RoundedCornerShape(16.dp),
                            shadow = Shadow(
                                radius = 10.dp,
                                spread = 7.5.dp,
                                color = Color.Black.copy(alpha = 0.05f),
                                offset = DpOffset(x = 0.dp, 2.dp)
                            )
                        )
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    val qrCodeBitmap = remember(url) { generateQrCode(url) }
                    Image(
                        modifier = Modifier.fillMaxWidth(),
                        bitmap = qrCodeBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    )
}
