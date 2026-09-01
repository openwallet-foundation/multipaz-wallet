package org.multipaz.wallet.android.ui.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.claim.Claim
import org.multipaz.claim.JsonClaim
import org.multipaz.claim.MdocClaim
import org.multipaz.compose.decodeImage
import org.multipaz.compose.items.FloatingItemHeadingAndContent
import org.multipaz.compose.items.FloatingItemHeadingAndText
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.util.fromBase64Url

/**
 * A composable item that displays a claim within a floating item list.
 *
 * If the claim is of type [DocumentAttributeType.Picture], it decodes the image bytes and renders
 * the image. Otherwise, it renders the claim value as text.
 *
 * @param claim The [Claim] to display.
 * @param heading The heading to display above the claim value, defaulting to [Claim.displayName].
 * @param modifier The modifier to apply to the item.
 * @param timeZone The time zone to use when rendering date/time values, defaulting to current system default.
 */
@Composable
fun FloatingItemListClaim(
    claim: Claim,
    heading: String = claim.displayName,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    if (claim.attribute?.type == DocumentAttributeType.Picture) {
        val img = try {
            val bytes = when (claim) {
                is MdocClaim -> claim.value.asBstr
                is JsonClaim -> claim.value.jsonPrimitive.content.fromBase64Url()
            }
            decodeImage(bytes)
        } catch (err: Exception) {
            if (err is CancellationException) throw err
            FloatingItemHeadingAndText(
                heading = heading,
                text = "Image decoding: $err",
                modifier = modifier
            )
            null
        }
        if (img != null) {
            FloatingItemHeadingAndContent(
                heading = heading,
                modifier = modifier,
                content = {
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        alignment = Alignment.TopStart,
                        modifier = Modifier.fillMaxWidth().size(250.dp),
                    )
                }
            )
        }
    } else {
        val str = claim.render(timeZone)
        FloatingItemHeadingAndText(
            heading = heading,
            text = str,
            modifier = modifier
        )
    }
}
