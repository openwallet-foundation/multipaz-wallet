package org.multipaz.wallet.android.ui.viewers

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar

private enum class JsonTokenType {
    PLAIN,
    KEY,
    STRING,
    NUMBER,
    BOOL,
    PUNCTUATION,
    COMMENT
}

private data class JsonToken(val text: String, val type: JsonTokenType)

internal fun formatNumberWithCommas(number: Int): String {
    val str = number.toString()
    val regex = Regex("(\\d)(?=(\\d{3})+$)")
    return str.replace(regex, "$1,")
}


private fun tokenizeJson(jsonText: String): List<JsonToken> {
    val tokens = mutableListOf<JsonToken>()
    var i = 0
    val len = jsonText.length

    while (i < len) {
        val ch = jsonText[i]

        // Single-line comment: //
        if (ch == '/' && i + 1 < len && jsonText[i + 1] == '/') {
            val start = i
            while (i < len && jsonText[i] != '\n') i++
            tokens.add(JsonToken(jsonText.substring(start, i), JsonTokenType.COMMENT))
            continue
        }

        // Multi-line comment: /* ... */
        if (ch == '/' && i + 1 < len && jsonText[i + 1] == '*') {
            val start = i
            i += 2
            while (i + 1 < len && !(jsonText[i] == '*' && jsonText[i + 1] == '/')) i++
            if (i + 1 < len) i += 2 else i = len
            tokens.add(JsonToken(jsonText.substring(start, i), JsonTokenType.COMMENT))
            continue
        }

        // String or Key
        if (ch == '"') {
            val start = i
            i++
            while (i < len) {
                if (jsonText[i] == '\\' && i + 1 < len) {
                    i += 2
                } else if (jsonText[i] == '"') {
                    i++
                    break
                } else {
                    i++
                }
            }
            val str = jsonText.substring(start, i)
            var peek = i
            while (peek < len && jsonText[peek].isWhitespace()) {
                peek++
            }
            val isKey = peek < len && jsonText[peek] == ':'
            tokens.add(JsonToken(str, if (isKey) JsonTokenType.KEY else JsonTokenType.STRING))
            continue
        }

        // Number: -?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?
        if (ch.isDigit() || (ch == '-' && i + 1 < len && jsonText[i + 1].isDigit())) {
            val start = i
            if (ch == '-') i++
            while (i < len && (jsonText[i].isDigit() || jsonText[i] == '.' || jsonText[i] == 'e' || jsonText[i] == 'E' || jsonText[i] == '+' || jsonText[i] == '-')) {
                i++
            }
            tokens.add(JsonToken(jsonText.substring(start, i), JsonTokenType.NUMBER))
            continue
        }

        // Keyword literals: true, false, null
        if (ch.isLetter()) {
            val start = i
            while (i < len && jsonText[i].isLetter()) i++
            val word = jsonText.substring(start, i)
            if (word == "true" || word == "false" || word == "null") {
                tokens.add(JsonToken(word, JsonTokenType.BOOL))
            } else {
                tokens.add(JsonToken(word, JsonTokenType.PLAIN))
            }
            continue
        }

        // Punctuation
        if (ch in "{}[],:") {
            tokens.add(JsonToken(ch.toString(), JsonTokenType.PUNCTUATION))
            i++
            continue
        }

        // Whitespace and newline
        val start = i
        while (i < len) {
            val c = jsonText[i]
            if (c == '"' || c == '/' || c.isDigit() ||
                (c == '-' && i + 1 < len && jsonText[i + 1].isDigit()) ||
                c.isLetter() || c in "{}[],:") {
                break
            }
            i++
        }
        if (i > start) {
            tokens.add(JsonToken(jsonText.substring(start, i), JsonTokenType.PLAIN))
        } else {
            tokens.add(JsonToken(jsonText[i].toString(), JsonTokenType.PLAIN))
            i++
        }
    }

    // Merge adjacent tokens of the same type (like PLAIN)
    val merged = mutableListOf<JsonToken>()
    for (t in tokens) {
        if (merged.isNotEmpty() &&
            merged.last().type == t.type &&
            !merged.last().text.endsWith("\n") &&
            t.text != "\n"
        ) {
            merged[merged.lastIndex] = JsonToken(merged.last().text + t.text, t.type)
        } else {
            merged.add(t)
        }
    }

    return merged
}

@OptIn(ExperimentalSerializationApi::class)
internal fun prettyPrintJson(jsonString: String): String {
    return try {
        val element = Json.parseToJsonElement(jsonString)
        val jsonFormatter = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        jsonFormatter.encodeToString(JsonElement.serializer(), element)
    } catch (e: Throwable) {
        jsonString
    }
}

internal fun formatJsonAnnotatedString(jsonText: String, isDarkTheme: Boolean): AnnotatedString {
    val tokens = tokenizeJson(jsonText)
    return buildAnnotatedString {
        for (token in tokens) {
            val color = if (isDarkTheme) {
                when (token.type) {
                    JsonTokenType.KEY -> Color(0xFF38BDF8)
                    JsonTokenType.STRING -> Color(0xFF4ADE80)
                    JsonTokenType.NUMBER -> Color(0xFFA855F7)
                    JsonTokenType.BOOL -> Color(0xFFF43F5E)
                    JsonTokenType.PUNCTUATION -> Color(0xFF94A3B8)
                    JsonTokenType.COMMENT -> Color(0xFF64748B)
                    JsonTokenType.PLAIN -> Color(0xFFE2E8F0)
                }
            } else {
                when (token.type) {
                    JsonTokenType.KEY -> Color(0xFF0369A1)
                    JsonTokenType.STRING -> Color(0xFF15803D)
                    JsonTokenType.NUMBER -> Color(0xFF6D28D9)
                    JsonTokenType.BOOL -> Color(0xFFBE123C)
                    JsonTokenType.PUNCTUATION -> Color(0xFF475569)
                    JsonTokenType.COMMENT -> Color(0xFF64748B)
                    JsonTokenType.PLAIN -> Color(0xFF0F172A)
                }
            }
            val fontStyle = if (token.type == JsonTokenType.COMMENT) FontStyle.Italic else FontStyle.Normal
            withStyle(
                SpanStyle(
                    color = color,
                    fontStyle = fontStyle,
                    fontFamily = FontFamily.Monospace,
                )
            ) {
                append(token.text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonViewerScreen(
    title: String,
    jsonString: String,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val prettyJsonText = remember(jsonString) {
        prettyPrintJson(jsonString)
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDarkTheme = surfaceColor.luminance() < 0.5f

    val annotatedString = remember(prettyJsonText, isDarkTheme) {
        formatJsonAnnotatedString(prettyJsonText, isDarkTheme)
    }

    val copiedToastText = stringResource(R.string.json_viewer_copied_to_clipboard)

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
                            clipboardManager.setText(AnnotatedString(prettyJsonText))
                            Toast.makeText(
                                context,
                                copiedToastText,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.json_viewer_copy)
                        )
                    }
                    IconButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, prettyJsonText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, title)
                            context.startActivity(shareIntent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.json_viewer_share)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState
            )
        }
    ) { innerPadding ->
        val verticalScrollState = rememberScrollState()
        val horizontalScrollState = rememberScrollState()

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

            val byteCount = jsonString.encodeToByteArray().size
            val formattedSize = formatNumberWithCommas(byteCount)
            val unit = if (byteCount == 1) "byte" else "bytes"
            Text(
                text = stringResource(R.string.json_viewer_json_size, formattedSize, unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
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
    }
}
