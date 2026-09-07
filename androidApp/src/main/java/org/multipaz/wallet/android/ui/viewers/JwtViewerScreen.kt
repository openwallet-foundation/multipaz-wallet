package org.multipaz.wallet.android.ui.viewers

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.multipaz.util.fromBase64Url
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar

internal data class JwtParts(
    val headerPrettyJson: String,
    val payloadPrettyJson: String,
    val signature: String?
)

internal data class DisclosurePart(
    val index: Int,
    val claimName: String?,
    val prettyJson: String,
    val rawBase64Url: String
)

internal sealed interface ParsedToken {
    data class StandardJwt(
        val jwt: JwtParts,
        val rawToken: String
    ) : ParsedToken

    data class SdJwtToken(
        val issuerJwt: JwtParts,
        val disclosures: List<DisclosurePart>,
        val keyBindingJwt: JwtParts?,
        val rawToken: String
    ) : ParsedToken

    data class ParseError(
        val message: String,
        val rawToken: String
    ) : ParsedToken
}

internal fun parseJwtParts(jwtString: String): JwtParts {
    val parts = jwtString.split(".")
    require(parts.size in 2..3) { "JWT must have 2 or 3 dot-separated parts (found ${parts.size})" }
    val headerJson = parts[0].fromBase64Url().decodeToString()
    val payloadJson = parts[1].fromBase64Url().decodeToString()
    val signature = parts.getOrNull(2)?.ifEmpty { null }
    return JwtParts(
        headerPrettyJson = prettyPrintJson(headerJson),
        payloadPrettyJson = prettyPrintJson(payloadJson),
        signature = signature
    )
}

internal fun parseToken(rawTokenString: String): ParsedToken {
    val trimmed = rawTokenString.trim()
    return try {
        if (!trimmed.contains("~")) {
            val jwtParts = parseJwtParts(trimmed)
            ParsedToken.StandardJwt(jwt = jwtParts, rawToken = trimmed)
        } else {
            val tildeParts = trimmed.split("~")
            val issuerJwtStr = tildeParts[0]
            val issuerJwtParts = parseJwtParts(issuerJwtStr)

            val endsWithTilde = trimmed.endsWith("~")
            val disclosureStrings = if (endsWithTilde) {
                tildeParts.drop(1).dropLast(1).filter { it.isNotEmpty() }
            } else {
                tildeParts.subList(1, tildeParts.lastIndex).filter { it.isNotEmpty() }
            }

            val disclosures = disclosureStrings.mapIndexed { index, discB64 ->
                val decodedText = try {
                    discB64.fromBase64Url().decodeToString()
                } catch (_: Throwable) {
                    discB64
                }
                val prettyJson = prettyPrintJson(decodedText)
                var claimName: String? = null
                try {
                    val element = Json.parseToJsonElement(decodedText)
                    if (element is JsonArray && element.size >= 3) {
                        val secondElem = element[1]
                        if (secondElem is JsonPrimitive && secondElem.isString) {
                            claimName = secondElem.content
                        }
                    }
                } catch (_: Throwable) {
                }
                DisclosurePart(
                    index = index,
                    claimName = claimName,
                    prettyJson = prettyJson,
                    rawBase64Url = discB64
                )
            }

            val kbJwtParts = if (!endsWithTilde && tildeParts.last().isNotEmpty()) {
                parseJwtParts(tildeParts.last())
            } else {
                null
            }

            ParsedToken.SdJwtToken(
                issuerJwt = issuerJwtParts,
                disclosures = disclosures,
                keyBindingJwt = kbJwtParts,
                rawToken = trimmed
            )
        }
    } catch (e: Throwable) {
        ParsedToken.ParseError(
            message = e.message ?: "Failed to parse token",
            rawToken = trimmed
        )
    }
}

@Composable
private fun JsonCodeBox(
    jsonText: String,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDarkTheme = surfaceColor.luminance() < 0.5f
    val annotatedString = remember(jsonText, isDarkTheme) {
        formatJsonAnnotatedString(jsonText, isDarkTheme)
    }
    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .horizontalScroll(horizontalScrollState)
            .padding(16.dp)
    ) {
        SelectionContainer {
            Text(
                text = annotatedString,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun PlainCodeBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .horizontalScroll(horizontalScrollState)
            .padding(16.dp)
    ) {
        SelectionContainer {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SubSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    textToCopy: String? = null,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val copiedToastText = stringResource(R.string.jwt_viewer_copied_to_clipboard)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (textToCopy != null) {
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(textToCopy))
                    Toast.makeText(
                        context,
                        copiedToastText,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.jwt_viewer_copy),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwtViewerScreen(
    title: String,
    jwtString: String,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val parsedToken = remember(jwtString) {
        parseToken(jwtString)
    }

    val copiedToastText = stringResource(R.string.jwt_viewer_copied_to_clipboard)

    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            AppMediumTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    AppBackButton(onClick = onBackClicked)
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(jwtString))
                            Toast.makeText(
                                context,
                                copiedToastText,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.jwt_viewer_copy)
                        )
                    }
                    IconButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, jwtString)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, title)
                            context.startActivity(shareIntent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.jwt_viewer_share)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        }
    ) { innerPadding ->
        val verticalScrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(verticalScrollState)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                )
        ) {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))

            val byteCount = jwtString.encodeToByteArray().size
            val formattedSize = formatNumberWithCommas(byteCount)
            val unit = if (byteCount == 1) "byte" else "bytes"

            val sizeText = when (parsedToken) {
                is ParsedToken.SdJwtToken -> {
                    if (parsedToken.keyBindingJwt != null) {
                        stringResource(R.string.jwt_viewer_sd_jwt_kb_size, formattedSize, unit)
                    } else {
                        stringResource(R.string.jwt_viewer_sd_jwt_size, formattedSize, unit)
                    }
                }
                else -> {
                    stringResource(R.string.jwt_viewer_jwt_size, formattedSize, unit)
                }
            }

            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when (parsedToken) {
                is ParsedToken.StandardJwt -> {
                    SubSectionHeading(
                        title = stringResource(R.string.jwt_viewer_header),
                        textToCopy = parsedToken.jwt.headerPrettyJson
                    )
                    JsonCodeBox(parsedToken.jwt.headerPrettyJson)
                    Spacer(modifier = Modifier.height(12.dp))

                    SubSectionHeading(
                        title = stringResource(R.string.jwt_viewer_payload),
                        textToCopy = parsedToken.jwt.payloadPrettyJson
                    )
                    JsonCodeBox(parsedToken.jwt.payloadPrettyJson)
                    Spacer(modifier = Modifier.height(12.dp))

                    SubSectionHeading(
                        title = stringResource(R.string.jwt_viewer_signature),
                        textToCopy = parsedToken.jwt.signature
                    )
                    PlainCodeBox(
                        parsedToken.jwt.signature ?: stringResource(R.string.jwt_viewer_unsigned)
                    )
                }

                is ParsedToken.SdJwtToken -> {
                    SectionHeading(stringResource(R.string.jwt_viewer_issuer_signed_jwt))
                    Spacer(modifier = Modifier.height(4.dp))

                    SubSectionHeading(
                        title = stringResource(R.string.jwt_viewer_header),
                        textToCopy = parsedToken.issuerJwt.headerPrettyJson
                    )
                    JsonCodeBox(parsedToken.issuerJwt.headerPrettyJson)
                    Spacer(modifier = Modifier.height(12.dp))

                    SubSectionHeading(
                        title = stringResource(R.string.jwt_viewer_payload),
                        textToCopy = parsedToken.issuerJwt.payloadPrettyJson
                    )
                    JsonCodeBox(parsedToken.issuerJwt.payloadPrettyJson)
                    Spacer(modifier = Modifier.height(12.dp))

                    SubSectionHeading(
                        title = stringResource(R.string.jwt_viewer_signature),
                        textToCopy = parsedToken.issuerJwt.signature
                    )
                    PlainCodeBox(
                        parsedToken.issuerJwt.signature ?: stringResource(R.string.jwt_viewer_unsigned)
                    )

                    if (parsedToken.disclosures.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeading(
                            "${stringResource(R.string.jwt_viewer_disclosures)} (${parsedToken.disclosures.size})"
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        for (disclosure in parsedToken.disclosures) {
                            val disclosureTitle = if (disclosure.claimName != null) {
                                stringResource(
                                    R.string.jwt_viewer_disclosure_item_with_name,
                                    disclosure.index + 1,
                                    disclosure.claimName
                                )
                            } else {
                                stringResource(
                                    R.string.jwt_viewer_disclosure_item,
                                    disclosure.index + 1
                                )
                            }
                            SubSectionHeading(
                                title = disclosureTitle,
                                textToCopy = disclosure.prettyJson
                            )
                            JsonCodeBox(disclosure.prettyJson)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    if (parsedToken.keyBindingJwt != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeading(stringResource(R.string.jwt_viewer_key_binding_jwt))
                        Spacer(modifier = Modifier.height(4.dp))

                        SubSectionHeading(
                            title = stringResource(R.string.jwt_viewer_header),
                            textToCopy = parsedToken.keyBindingJwt.headerPrettyJson
                        )
                        JsonCodeBox(parsedToken.keyBindingJwt.headerPrettyJson)
                        Spacer(modifier = Modifier.height(12.dp))

                        SubSectionHeading(
                            title = stringResource(R.string.jwt_viewer_payload),
                            textToCopy = parsedToken.keyBindingJwt.payloadPrettyJson
                        )
                        JsonCodeBox(parsedToken.keyBindingJwt.payloadPrettyJson)
                        Spacer(modifier = Modifier.height(12.dp))

                        SubSectionHeading(
                            title = stringResource(R.string.jwt_viewer_signature),
                            textToCopy = parsedToken.keyBindingJwt.signature
                        )
                        PlainCodeBox(
                            parsedToken.keyBindingJwt.signature
                                ?: stringResource(R.string.jwt_viewer_unsigned)
                        )
                    }
                }

                is ParsedToken.ParseError -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.jwt_viewer_parse_error)}: ${parsedToken.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    SubSectionHeading(
                        title = stringResource(R.string.jwt_viewer_raw_token),
                        textToCopy = parsedToken.rawToken
                    )
                    PlainCodeBox(parsedToken.rawToken)
                }
            }

            if (parsedToken !is ParsedToken.ParseError) {
                Spacer(modifier = Modifier.height(16.dp))
                var showRawToken by rememberSaveable { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showRawToken = !showRawToken }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.jwt_viewer_raw_token),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showRawToken) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(jwtString))
                            Toast.makeText(
                                context,
                                copiedToastText,
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.jwt_viewer_copy),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (showRawToken) {
                    PlainCodeBox(jwtString)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
