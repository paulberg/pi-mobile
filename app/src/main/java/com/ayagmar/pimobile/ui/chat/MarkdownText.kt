package com.ayagmar.pimobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.ayagmar.pimobile.ui.theme.PiCodeFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight markdown renderer for assistant message paragraphs.
 *
 * Handles block-level elements (headers, lists, blockquotes, horizontal rules)
 * and inline formatting (bold, italic, inline code, links, strikethrough).
 *
 * This is intentionally NOT a full CommonMark parser — it covers the subset
 * that LLMs routinely produce in conversational responses.
 */

// ── Inline markdown regex patterns ──────────────────────────────────────────

/** Bold-italic: `***text***` or `___text___` */
private val BOLD_ITALIC_REGEX = Regex("\\*{3}(.+?)\\*{3}|_{3}(.+?)_{3}")

/** Bold: `**text**` or `__text__` */
private val BOLD_REGEX = Regex("\\*{2}(.+?)\\*{2}|_{2}(.+?)_{2}")

/** Italic: `*text*` or `_text_` (single star not adjacent to another star; word-boundary aware for underscore) */
private val ITALIC_REGEX = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)|(?<=\\s|^)_(.+?)_(?=\\s|$|[.,;:!?)])")

/** Strikethrough: `~~text~~` */
private val STRIKETHROUGH_REGEX = Regex("~~(.+?)~~")

/** Inline code: `` `code` `` */
private val INLINE_CODE_REGEX = Regex("`([^`]+)`")

/** Links: `[text](url)` */
private val LINK_REGEX = Regex("\\[([^]]+)]\\(([^)]+)\\)")

// ── Block-level patterns ────────────────────────────────────────────────────

/** Header: `# text` through `###### text` */
private val HEADER_REGEX = Regex("^(#{1,6})\\s+(.+)$")

/** Unordered list item: `- text` or `* text` or `+ text` (with optional leading spaces) */
private val UNORDERED_LIST_REGEX = Regex("^(\\s*)[-*+]\\s+(.+)$")

/** Ordered list item: `1. text` (with optional leading spaces) */
private val ORDERED_LIST_REGEX = Regex("^(\\s*)(\\d+)\\.\\s+(.+)$")

/** Blockquote: `> text` */
private val BLOCKQUOTE_REGEX = Regex("^>\\s?(.*)")

/** Horizontal rule: `---`, `***`, or `___` (with optional spaces) */
private val HORIZONTAL_RULE_REGEX = Regex("^\\s*([-*_])\\s*\\1\\s*\\1+\\s*$")

// ── Sealed interface for parsed markdown blocks ─────────────────────────────

internal sealed interface MarkdownBlock {
    data class HeaderBlock(val level: Int, val content: String) : MarkdownBlock
    data class ParagraphBlock(val content: String) : MarkdownBlock
    data class UnorderedListItemBlock(val indent: Int, val content: String) : MarkdownBlock
    data class OrderedListItemBlock(val indent: Int, val number: String, val content: String) : MarkdownBlock
    data class BlockquoteBlock(val content: String) : MarkdownBlock
    data object HorizontalRuleBlock : MarkdownBlock
}

// ── Public composables ──────────────────────────────────────────────────────

/**
 * Renders markdown-formatted text with block and inline formatting.
 *
 * For streaming performance, this composable uses [remember] keyed on [text]
 * so parsing only runs when the text actually changes.
 */
@Composable
internal fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val colors = MarkdownColors(
        text = baseColor,
        code = MaterialTheme.colorScheme.primary,
        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        link = MaterialTheme.colorScheme.primary,
        blockquoteBorder = MaterialTheme.colorScheme.outlineVariant,
        blockquoteText = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.HeaderBlock -> {
                    MarkdownHeader(
                        level = block.level,
                        content = block.content,
                        colors = colors,
                    )
                }

                is MarkdownBlock.ParagraphBlock -> {
                    Text(
                        text = parseInlineMarkdown(block.content, colors),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is MarkdownBlock.UnorderedListItemBlock -> {
                    MarkdownUnorderedListItem(
                        indent = block.indent,
                        content = block.content,
                        colors = colors,
                    )
                }

                is MarkdownBlock.OrderedListItemBlock -> {
                    MarkdownOrderedListItem(
                        indent = block.indent,
                        number = block.number,
                        content = block.content,
                        colors = colors,
                    )
                }

                is MarkdownBlock.BlockquoteBlock -> {
                    MarkdownBlockquote(
                        content = block.content,
                        colors = colors,
                    )
                }

                is MarkdownBlock.HorizontalRuleBlock -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = colors.blockquoteBorder,
                    )
                }
            }
        }
    }
}

/**
 * Lightweight inline-only markdown renderer. Returns an [AnnotatedString] with
 * bold, italic, inline code, links, and strikethrough formatting applied.
 *
 * Use this for places where only inline formatting is needed (e.g. streaming
 * fast-path where we don't want block-level overhead).
 */
internal fun parseInlineMarkdown(
    text: String,
    colors: MarkdownColors,
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val matches = collectInlineMatches(text)

        matches.forEach { match ->
            // Append plain text before this match
            if (match.range.first > cursor) {
                withStyle(SpanStyle(color = colors.text)) {
                    append(text.substring(cursor, match.range.first))
                }
            }

            when (match.type) {
                InlineMatchType.BOLD_ITALIC -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = colors.text)) {
                        append(match.content)
                    }
                }

                InlineMatchType.BOLD -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.text)) {
                        append(match.content)
                    }
                }

                InlineMatchType.ITALIC -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.text)) {
                        append(match.content)
                    }
                }

                InlineMatchType.STRIKETHROUGH -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.text)) {
                        append(match.content)
                    }
                }

                InlineMatchType.INLINE_CODE -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = PiCodeFontFamily,
                            color = colors.code,
                            background = colors.codeBackground,
                        ),
                    ) {
                        append(match.content)
                    }
                }

                InlineMatchType.LINK -> {
                    withStyle(
                        SpanStyle(
                            color = colors.link,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append(match.content)
                    }
                }
            }

            cursor = match.range.last + 1
        }

        // Append remaining text
        if (cursor < text.length) {
            withStyle(SpanStyle(color = colors.text)) {
                append(text.substring(cursor))
            }
        }
    }
}

// ── Block-level composables ─────────────────────────────────────────────────

@Composable
private fun MarkdownHeader(
    level: Int,
    content: String,
    colors: MarkdownColors,
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        4 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.labelLarge
    }

    Text(
        text = parseInlineMarkdown(content, colors),
        style = style,
        modifier = Modifier.padding(top = if (level <= 2) 8.dp else 4.dp),
    )
}

@Composable
private fun MarkdownUnorderedListItem(
    indent: Int,
    content: String,
    colors: MarkdownColors,
) {
    val bullet = when (indent) {
        0 -> "•"
        1 -> "◦"
        else -> "▪"
    }
    Row(
        modifier = Modifier.padding(start = (indent * 16).dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = bullet,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.width(12.dp),
        )
        Text(
            text = parseInlineMarkdown(content, colors),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MarkdownOrderedListItem(
    indent: Int,
    number: String,
    content: String,
    colors: MarkdownColors,
) {
    Row(
        modifier = Modifier.padding(start = (indent * 16).dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = parseInlineMarkdown(content, colors),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MarkdownBlockquote(
    content: String,
    colors: MarkdownColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.blockquoteBorder.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(start = 2.dp),
    ) {
        // Vertical accent bar
        @Suppress("MagicNumber")
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(3.dp)
                .padding(vertical = 4.dp)
                .background(
                    color = colors.blockquoteBorder,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
        Text(
            text = parseInlineMarkdown(content, colors),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
            ),
            color = colors.blockquoteText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── Data types ──────────────────────────────────────────────────────────────

internal data class MarkdownColors(
    val text: Color,
    val code: Color,
    val codeBackground: Color,
    val link: Color,
    val blockquoteBorder: Color,
    val blockquoteText: Color,
)

private enum class InlineMatchType {
    BOLD_ITALIC,
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    INLINE_CODE,
    LINK,
}

private data class InlineMatch(
    val range: IntRange,
    val content: String,
    val type: InlineMatchType,
)

// ── Parsing functions ───────────────────────────────────────────────────────

/**
 * Parse text into markdown blocks (headers, list items, blockquotes, paragraphs, rules).
 * Adjacent non-block lines are merged into a single paragraph.
 */
internal fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        val content = paragraphBuffer.toString().trim()
        if (content.isNotEmpty()) {
            blocks += MarkdownBlock.ParagraphBlock(content)
        }
        paragraphBuffer.clear()
    }

    for (line in lines) {
        // Horizontal rule (must check before unordered list since `---` could match)
        if (HORIZONTAL_RULE_REGEX.matches(line)) {
            flushParagraph()
            blocks += MarkdownBlock.HorizontalRuleBlock
            continue
        }

        // Header
        val headerMatch = HEADER_REGEX.matchEntire(line)
        if (headerMatch != null) {
            flushParagraph()
            val level = headerMatch.groupValues[1].length
            val content = headerMatch.groupValues[2].trim()
            blocks += MarkdownBlock.HeaderBlock(level = level, content = content)
            continue
        }

        // Blockquote
        val blockquoteMatch = BLOCKQUOTE_REGEX.matchEntire(line)
        if (blockquoteMatch != null) {
            flushParagraph()
            blocks += MarkdownBlock.BlockquoteBlock(content = blockquoteMatch.groupValues[1])
            continue
        }

        // Unordered list
        val ulMatch = UNORDERED_LIST_REGEX.matchEntire(line)
        if (ulMatch != null) {
            flushParagraph()
            val indent = ulMatch.groupValues[1].length / 2
            blocks += MarkdownBlock.UnorderedListItemBlock(indent = indent, content = ulMatch.groupValues[2])
            continue
        }

        // Ordered list
        val olMatch = ORDERED_LIST_REGEX.matchEntire(line)
        if (olMatch != null) {
            flushParagraph()
            val indent = olMatch.groupValues[1].length / 2
            blocks += MarkdownBlock.OrderedListItemBlock(
                indent = indent,
                number = olMatch.groupValues[2],
                content = olMatch.groupValues[3],
            )
            continue
        }

        // Empty line — flush paragraph
        if (line.isBlank()) {
            flushParagraph()
            continue
        }

        // Regular text — accumulate into paragraph
        if (paragraphBuffer.isNotEmpty()) {
            paragraphBuffer.append(' ')
        }
        paragraphBuffer.append(line)
    }

    flushParagraph()
    return blocks
}

/**
 * Collect all inline markdown matches in priority order, discarding any
 * that overlap with earlier (higher-priority) matches.
 *
 * Priority: inline code > bold-italic > bold > italic > strikethrough > link
 * (Inline code is highest so that markdown inside backticks is not parsed.)
 */
private fun collectInlineMatches(text: String): List<InlineMatch> {
    val candidates = mutableListOf<InlineMatch>()

    // Inline code (highest priority — contents are literal)
    INLINE_CODE_REGEX.findAll(text).forEach { match ->
        candidates += InlineMatch(
            range = match.range,
            content = match.groupValues[1],
            type = InlineMatchType.INLINE_CODE,
        )
    }

    // Bold-italic
    BOLD_ITALIC_REGEX.findAll(text).forEach { match ->
        val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
        candidates += InlineMatch(
            range = match.range,
            content = content,
            type = InlineMatchType.BOLD_ITALIC,
        )
    }

    // Bold
    BOLD_REGEX.findAll(text).forEach { match ->
        val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
        candidates += InlineMatch(
            range = match.range,
            content = content,
            type = InlineMatchType.BOLD,
        )
    }

    // Italic
    ITALIC_REGEX.findAll(text).forEach { match ->
        val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
        candidates += InlineMatch(
            range = match.range,
            content = content,
            type = InlineMatchType.ITALIC,
        )
    }

    // Strikethrough
    STRIKETHROUGH_REGEX.findAll(text).forEach { match ->
        candidates += InlineMatch(
            range = match.range,
            content = match.groupValues[1],
            type = InlineMatchType.STRIKETHROUGH,
        )
    }

    // Links
    LINK_REGEX.findAll(text).forEach { match ->
        candidates += InlineMatch(
            range = match.range,
            content = match.groupValues[1], // display text only
            type = InlineMatchType.LINK,
        )
    }

    // Sort by start position, then resolve overlaps (first match wins)
    candidates.sortBy { it.range.first }

    val resolved = mutableListOf<InlineMatch>()
    var lastEnd = -1
    for (candidate in candidates) {
        if (candidate.range.first > lastEnd) {
            resolved += candidate
            lastEnd = candidate.range.last
        }
    }

    return resolved
}
