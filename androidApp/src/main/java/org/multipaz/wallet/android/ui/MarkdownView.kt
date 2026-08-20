package org.multipaz.wallet.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.multipaz.compose.text.fromMarkdown

/**
 * Represents parsed top-level structural Markdown blocks.
 */
internal sealed interface MarkdownBlock {
    /** Heading level from 1 to 6 with unformatted text. */
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    /** Regular paragraph text with inline Markdown formatting. */
    data class Paragraph(val text: String) : MarkdownBlock

    /** Unordered bullet list item. */
    data class BulletItem(val text: String) : MarkdownBlock

    /** Ordered numbered list item with its numeric label prefix. */
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock

    /** Markdown table containing column header labels and rows of cell text. */
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock

    /** Multi-line fenced code block with optional syntax language. */
    data class CodeBlock(val code: String, val language: String? = null) : MarkdownBlock

    /** Horizontal rule separator (`---`, `***`, or `___`). */
    data object HorizontalRule : MarkdownBlock
}

/**
 * Renders Markdown-formatted text natively using Jetpack Compose components.
 *
 * Unlike WebViews, this composable parses Markdown into structural blocks and renders them
 * directly onto the Compose canvas using Material 3 design tokens, typography, and theme
 * colors. This eliminates WebView startup latency, IPC bridge overhead, and graphical
 * compositing incompatibilities with Compose hardware drawing layers (such as Haze blur).
 *
 * Supported block-level elements:
 * - **Headings**: `# H1`, `## H2`, `### H3`, etc.
 * - **Paragraphs**: Standard text blocks with inline styling.
 * - **Bullet Lists**: Lines prefixed with `- `, `* `, or `+ `.
 * - **Numbered Lists**: Lines prefixed with `1. `, `2. `, etc.
 * - **Tables**: GFM pipe tables with headers and row dividers.
 * - **Fenced Code Blocks**: Text enclosed within ```` ``` ```` code fences.
 * - **Horizontal Rules**: `---`, `***`, or `___` dividers.
 *
 * Supported inline Markdown formatting (via [fromMarkdown]):
 * - **Links**: `[text](url)` (clickable and automatically routed to the platform browser)
 * - **Bold**: `**text**` or `__text__`
 * - **Italics**: `*text*` or `_text_`
 * - **Inline Code**: `` `text` ``
 * - **Strikethrough**: `~~text~~`
 *
 * @param markdown The raw Markdown string to parse and display.
 * @param modifier The [Modifier] to be applied to the outer layout container.
 */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = AnnotatedString.fromMarkdown(block.text),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = AnnotatedString.fromMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AnnotatedString.fromMarkdown(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "${block.number}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = AnnotatedString.fromMarkdown(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                is MarkdownBlock.Table -> {
                    MarkdownTable(block)
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for ((index, header) in table.headers.withIndex()) {
                    val weight = getColumnWeight(table.headers, index)
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(weight)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Rows
            for ((rowIndex, row) in table.rows.withIndex()) {
                if (rowIndex > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for ((colIndex, cell) in row.withIndex()) {
                        val weight = getColumnWeight(table.headers, colIndex)
                        Text(
                            text = AnnotatedString.fromMarkdown(cell),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(weight)
                        )
                    }
                }
            }
        }
    }
}

private fun getColumnWeight(headers: List<String>, index: Int): Float {
    if (headers.size == 3 && headers[0].equals("Commit", ignoreCase = true)) {
        return when (index) {
            0 -> 0.28f
            1 -> 0.28f
            else -> 0.44f
        }
    }
    return 1f / headers.size.coerceAtLeast(1).toFloat()
}

private fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Code block
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim().ifEmpty { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language))
            continue
        }

        // Horizontal rule
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.HorizontalRule)
            i++
            continue
        }

        // Heading
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                val text = trimmed.substring(level + 1).trim()
                blocks.add(MarkdownBlock.Heading(level, text))
                i++
                continue
            }
        }

        // Table
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                tableLines.add(lines[i].trim())
                i++
            }
            if (tableLines.size >= 2) {
                val headers = parseTableRow(tableLines[0])
                val rows = tableLines.drop(2).map { parseTableRow(it) }
                blocks.add(MarkdownBlock.Table(headers, rows))
            } else if (tableLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(tableLines[0]))
            }
            continue
        }

        // Bullet list
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            val text = trimmed.substring(2).trim()
            blocks.add(MarkdownBlock.BulletItem(text))
            i++
            continue
        }

        // Numbered list
        val numberedMatch = """^(\d+)\.\s+(.*)$""".toRegex().matchEntire(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1]
            val text = numberedMatch.groupValues[2]
            blocks.add(MarkdownBlock.NumberedItem(num, text))
            i++
            continue
        }

        // Paragraph
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val pTrimmed = lines[i].trim()
            if (pTrimmed.isEmpty() ||
                pTrimmed.startsWith("#") ||
                pTrimmed.startsWith("```") ||
                (pTrimmed.startsWith("|") && pTrimmed.endsWith("|")) ||
                pTrimmed.startsWith("- ") ||
                pTrimmed.startsWith("* ") ||
                pTrimmed.startsWith("+ ") ||
                """^(\d+)\.\s+""".toRegex().containsMatchIn(pTrimmed) ||
                pTrimmed == "---"
            ) {
                break
            }
            paragraphLines.add(pTrimmed)
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString(" ")))
        }
    }

    return blocks
}

private fun parseTableRow(row: String): List<String> {
    val trimmed = row.removePrefix("|").removeSuffix("|")
    return trimmed.split("|").map { it.trim() }
}
