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
import org.multipaz.cbor.Cdn
import org.multipaz.cbor.CdnGeneratorOptions
import org.multipaz.util.toHex
import org.multipaz.wallet.android.R
import org.multipaz.wallet.android.ui.AppBackButton
import org.multipaz.wallet.android.ui.AppMediumTopAppBar

private enum class TokenType {
    PLAIN,
    STRING,
    HEX_STRING,
    EMBEDDED,
    TAG,
    BOOL,
    NUMBER,
    PUNCTUATION,
    COMMENT
}

private data class Token(val text: String, val type: TokenType)



private fun tokenizeCborDiagnostics(diagText: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    val len = diagText.length

    while (i < len) {
        val ch = diagText[i]

        // Triple-single-quoted string: '''...''' or cert'''...'''
        if (ch == '\'' && i + 2 < len && diagText[i + 1] == '\'' && diagText[i + 2] == '\'') {
            val start = i
            i += 3
            while (i + 2 < len && !(diagText[i] == '\'' && diagText[i + 1] == '\'' && diagText[i + 2] == '\'')) {
                i++
            }
            if (i + 2 < len) i += 3 else i = len
            tokens.add(Token(diagText.substring(start, i), TokenType.STRING))
            continue
        }

        // Triple-double-quoted string: """..."""
        if (ch == '"' && i + 2 < len && diagText[i + 1] == '"' && diagText[i + 2] == '"') {
            val start = i
            i += 3
            while (i + 2 < len && !(diagText[i] == '"' && diagText[i + 1] == '"' && diagText[i + 2] == '"')) {
                i++
            }
            if (i + 2 < len) i += 3 else i = len
            tokens.add(Token(diagText.substring(start, i), TokenType.STRING))
            continue
        }

        // Single-line Comment: // or #
        if (ch == '#' || (ch == '/' && i + 1 < len && diagText[i + 1] == '/')) {
            val start = i
            while (i < len && diagText[i] != '\n') i++
            tokens.add(Token(diagText.substring(start, i), TokenType.COMMENT))
            continue
        }

        // Multi-line Comment: /* ... */
        if (ch == '/' && i + 1 < len && diagText[i + 1] == '*') {
            val start = i
            i += 2
            while (i + 1 < len && !(diagText[i] == '*' && diagText[i + 1] == '/')) i++
            if (i + 1 < len) i += 2 else i = len
            tokens.add(Token(diagText.substring(start, i), TokenType.COMMENT))
            continue
        }

        // Hex string: h'...' or b64'...'
        if ((ch == 'h' || ch == 'b') && i + 1 < len && diagText[i + 1] == '\'') {
            val start = i
            i += 2
            while (i < len && diagText[i] != '\'' && diagText[i] != '\n') i++
            if (i < len && diagText[i] == '\'') i++
            tokens.add(Token(diagText.substring(start, i), TokenType.HEX_STRING))
            continue
        }

        // Single-quoted byte string: '...'
        if (ch == '\'') {
            val start = i
            i++
            while (i < len && diagText[i] != '\'' && diagText[i] != '\n') {
                if (diagText[i] == '\\' && i + 1 < len) i += 2 else i++
            }
            if (i < len && diagText[i] == '\'') i++
            tokens.add(Token(diagText.substring(start, i), TokenType.STRING))
            continue
        }

        // Double-quoted text string: "..."
        if (ch == '"') {
            val start = i
            i++
            while (i < len && diagText[i] != '\n') {
                if (diagText[i] == '\\' && i + 1 < len) {
                    i += 2
                } else if (diagText[i] == '"') {
                    i++
                    break
                } else {
                    i++
                }
            }
            tokens.add(Token(diagText.substring(start, i), TokenType.STRING))
            continue
        }

        // Embedded CBOR: << or >>
        if (i + 1 < len && ((ch == '<' && diagText[i + 1] == '<') || (ch == '>' && diagText[i + 1] == '>'))) {
            tokens.add(Token(diagText.substring(i, i + 2), TokenType.EMBEDDED))
            i += 2
            continue
        }

        // Tag: e.g. 18( or 24( or 24_0(
        if (ch.isDigit()) {
            var j = i
            while (j < len && (diagText[j].isDigit() || (diagText[j] == '_' && j + 1 < len && diagText[j + 1].isDigit()))) j++
            if (j < len && diagText[j] == '(') {
                tokens.add(Token(diagText.substring(i, j + 1), TokenType.TAG))
                i = j + 1
                continue
            }
        }

        // Identifiers & Keywords (e.g. cert, dt, ip, true, false, null)
        if (ch.isLetter() || ch == '_' || ch == '$') {
            val start = i
            while (i < len && (diagText[i].isLetterOrDigit() || diagText[i] == '_' || diagText[i] == '-' || diagText[i] == '$')) i++
            val word = diagText.substring(start, i)
            if (word == "true" || word == "false" || word == "null" || word == "undefined") {
                tokens.add(Token(word, TokenType.BOOL))
            } else {
                tokens.add(Token(word, TokenType.PLAIN))
            }
            continue
        }

        // Numbers: -123 or 123
        if (ch.isDigit() || (ch == '-' && i + 1 < len && diagText[i + 1].isDigit())) {
            val start = i
            if (ch == '-') i++
            while (i < len && (diagText[i].isDigit() || diagText[i] == '.' || diagText[i] == 'e' || diagText[i] == 'E')) i++
            tokens.add(Token(diagText.substring(start, i), TokenType.NUMBER))
            continue
        }

        // Punctuation
        if (ch in "{}[],:()") {
            tokens.add(Token(ch.toString(), TokenType.PUNCTUATION))
            i++
            continue
        }

        // Newline
        if (ch == '\n') {
            tokens.add(Token("\n", TokenType.PLAIN))
            i++
            continue
        }

        // Whitespace and other characters
        val start = i
        while (i < len) {
            val c = diagText[i]
            if (c == '\n' || c == '#' || c == '"' || c == '\'' || c == '{' || c == '}' || c == '[' || c == ']' ||
                c == '(' || c == ')' || c == ',' || c == ':' || c.isLetterOrDigit() || c == '-' ||
                (c == 'h' && i + 1 < len && diagText[i + 1] == '\'') ||
                (i + 1 < len && ((c == '<' && diagText[i + 1] == '<') || (c == '>' && diagText[i + 1] == '>')))) {
                break
            }
            i++
        }
        if (i > start) {
            tokens.add(Token(diagText.substring(start, i), TokenType.PLAIN))
        } else {
            tokens.add(Token(diagText[i].toString(), TokenType.PLAIN))
            i++
        }
    }

    // Merge adjacent tokens of the same type
    val merged = mutableListOf<Token>()
    for (t in tokens) {
        if (merged.isNotEmpty() &&
            merged.last().type == t.type &&
            !merged.last().text.endsWith("\n") &&
            t.text != "\n" &&
            t.type != TokenType.EMBEDDED &&
            t.type != TokenType.TAG
        ) {
            merged[merged.lastIndex] = Token(merged.last().text + t.text, t.type)
        } else {
            merged.add(t)
        }
    }

    return merged
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CborViewerScreen(
    title: String,
    cborBytes: ByteArray,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val cdnText = remember(cborBytes) {
        try {
            Cdn.encode(cborBytes, CdnGeneratorOptions.Pretty)
        } catch (e: Throwable) {
            "Error decoding CBOR into CDN: ${e.message}\n\nHex:\n${cborBytes.toHex()}"
        }
    }

    val tokens = remember(cdnText) {
        tokenizeCborDiagnostics(cdnText)
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDarkTheme = surfaceColor.luminance() < 0.5f

    val annotatedString = remember(tokens, isDarkTheme) {
        buildAnnotatedString {
            for (token in tokens) {
                val color = if (isDarkTheme) {
                    when (token.type) {
                        TokenType.STRING -> Color(0xFF4ADE80)
                        TokenType.HEX_STRING -> Color(0xFFFBBF24)
                        TokenType.EMBEDDED -> Color(0xFF38BDF8)
                        TokenType.TAG -> Color(0xFFC084FC)
                        TokenType.BOOL -> Color(0xFFF43F5E)
                        TokenType.NUMBER -> Color(0xFFA855F7)
                        TokenType.PUNCTUATION -> Color(0xFF94A3B8)
                        TokenType.COMMENT -> Color(0xFF64748B)
                        TokenType.PLAIN -> Color(0xFFE2E8F0)
                    }
                } else {
                    when (token.type) {
                        TokenType.STRING -> Color(0xFF15803D)
                        TokenType.HEX_STRING -> Color(0xFFB45309)
                        TokenType.EMBEDDED -> Color(0xFF0369A1)
                        TokenType.TAG -> Color(0xFF7E22CE)
                        TokenType.BOOL -> Color(0xFFBE123C)
                        TokenType.NUMBER -> Color(0xFF6D28D9)
                        TokenType.PUNCTUATION -> Color(0xFF475569)
                        TokenType.COMMENT -> Color(0xFF64748B)
                        TokenType.PLAIN -> Color(0xFF0F172A)
                    }
                }
                val fontStyle = if (token.type == TokenType.COMMENT) FontStyle.Italic else FontStyle.Normal
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

    val copiedToastText = stringResource(R.string.cbor_viewer_copied_to_clipboard)

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
                            clipboardManager.setText(AnnotatedString(cdnText))
                            Toast.makeText(
                                context,
                                copiedToastText,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.cbor_viewer_copy)
                        )
                    }
                    IconButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, cdnText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, title)
                            context.startActivity(shareIntent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.cbor_viewer_share)
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

            val formattedSize = formatNumberWithCommas(cborBytes.size)
            val unit = if (cborBytes.size == 1) "byte" else "bytes"
            Text(
                text = stringResource(R.string.cbor_viewer_cbor_size, formattedSize, unit),
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
