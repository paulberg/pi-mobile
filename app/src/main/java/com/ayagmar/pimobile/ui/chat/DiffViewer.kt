@file:Suppress("MagicNumber")

package com.ayagmar.pimobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayagmar.pimobile.R
import com.ayagmar.pimobile.chat.EditDiffInfo
import com.github.difflib.DiffUtils
import com.github.difflib.patch.AbstractDelta
import com.github.difflib.patch.DeltaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_COLLAPSED_DIFF_LINES = 120
private const val DEFAULT_CONTEXT_LINES = 3

@Immutable
data class DiffViewerStyle(
    val collapsedDiffLines: Int = DEFAULT_COLLAPSED_DIFF_LINES,
    val contextLines: Int = DEFAULT_CONTEXT_LINES,
    val gutterWidth: Dp = 44.dp,
    val lineRowHorizontalPadding: Dp = 4.dp,
    val lineRowVerticalPadding: Dp = 2.dp,
    val contentHorizontalPadding: Dp = 8.dp,
    val headerHorizontalPadding: Dp = 12.dp,
    val headerVerticalPadding: Dp = 8.dp,
    val skippedLineHorizontalPadding: Dp = 8.dp,
    val skippedLineVerticalPadding: Dp = 4.dp,
)

@Immutable
data class DiffViewerColors(
    val addedBackground: Color,
    val removedBackground: Color,
    val addedText: Color,
    val removedText: Color,
    val gutterText: Color,
    val commentText: Color,
    val stringText: Color,
    val numberText: Color,
    val keywordText: Color,
)

@Composable
private fun rememberDiffViewerColors(): DiffViewerColors {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        DiffViewerColors(
            addedBackground = colors.primaryContainer.copy(alpha = 0.32f),
            removedBackground = colors.errorContainer.copy(alpha = 0.32f),
            addedText = colors.primary,
            removedText = colors.error,
            gutterText = colors.onSurfaceVariant,
            commentText = colors.onSurfaceVariant,
            stringText = colors.tertiary,
            numberText = colors.secondary,
            keywordText = colors.primary,
        )
    }
}

private data class DiffPresentationLine(
    val line: DiffLine,
    val highlightSpans: List<HighlightSpan>,
)

private data class DiffComputationState(
    val lines: List<DiffPresentationLine>,
    val isLoading: Boolean,
)

@Composable
fun DiffViewer(
    diffInfo: EditDiffInfo,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    style: DiffViewerStyle = DiffViewerStyle(),
) {
    val clipboardManager = LocalClipboardManager.current
    val syntaxLanguage = remember(diffInfo.path) { detectSyntaxLanguageFromPath(diffInfo.path) }
    val diffColors = rememberDiffViewerColors()
    val computationState by
        produceState(
            initialValue = DiffComputationState(lines = emptyList(), isLoading = true),
            diffInfo,
            style.contextLines,
            syntaxLanguage,
        ) {
            value = value.copy(isLoading = true)
            val computedLines =
                withContext(Dispatchers.Default) {
                    computeDiffPresentationLines(
                        diffInfo = diffInfo,
                        contextLines = style.contextLines,
                        syntaxLanguage = syntaxLanguage,
                    )
                }
            value = DiffComputationState(lines = computedLines, isLoading = false)
        }

    val displayLines =
        if (isCollapsed && computationState.lines.size > style.collapsedDiffLines) {
            computationState.lines.take(style.collapsedDiffLines)
        } else {
            computationState.lines
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DiffHeader(
                path = diffInfo.path,
                onCopyPath = { clipboardManager.setText(AnnotatedString(diffInfo.path)) },
                style = style,
            )

            DiffLinesList(
                lines = displayLines,
                style = style,
                colors = diffColors,
            )

            if (computationState.isLoading) {
                DiffLoadingRow(style = style)
            }

            DiffCollapseToggle(
                totalLines = computationState.lines.size,
                isCollapsed = isCollapsed,
                style = style,
                onToggleCollapse = onToggleCollapse,
            )
        }
    }
}

@Composable
private fun DiffLinesList(
    lines: List<DiffPresentationLine>,
    style: DiffViewerStyle,
    colors: DiffViewerColors,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = style.contentHorizontalPadding),
    ) {
        items(lines) { presentationLine ->
            DiffLineItem(
                presentationLine = presentationLine,
                style = style,
                colors = colors,
            )
        }
    }
}

@Composable
private fun DiffLoadingRow(style: DiffViewerStyle) {
    Text(
        text = stringResource(id = R.string.diff_viewer_loading),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = style.skippedLineHorizontalPadding,
                    vertical = style.skippedLineVerticalPadding,
                ),
    )
}

@Composable
private fun DiffCollapseToggle(
    totalLines: Int,
    isCollapsed: Boolean,
    style: DiffViewerStyle,
    onToggleCollapse: () -> Unit,
) {
    if (totalLines <= style.collapsedDiffLines) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(
            onClick = onToggleCollapse,
        ) {
            val remainingLines = totalLines - style.collapsedDiffLines
            val buttonText =
                if (isCollapsed) {
                    pluralStringResource(
                        id = R.plurals.diff_viewer_expand_more_lines,
                        count = remainingLines,
                        remainingLines,
                    )
                } else {
                    stringResource(id = R.string.diff_viewer_collapse)
                }
            Text(buttonText)
        }
    }
}

@Composable
private fun DiffHeader(
    path: String,
    onCopyPath: () -> Unit,
    style: DiffViewerStyle,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(
                    horizontal = style.headerHorizontalPadding,
                    vertical = style.headerVerticalPadding,
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = path,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCopyPath) {
            Text(stringResource(id = R.string.diff_viewer_copy))
        }
    }
}

@Composable
private fun DiffLineItem(
    presentationLine: DiffPresentationLine,
    style: DiffViewerStyle,
    colors: DiffViewerColors,
) {
    val line = presentationLine.line

    if (line.type == DiffLineType.SKIPPED) {
        SkippedDiffLine(line = line, style = style)
        return
    }

    val backgroundColor =
        when (line.type) {
            DiffLineType.ADDED -> colors.addedBackground
            DiffLineType.REMOVED -> colors.removedBackground
            DiffLineType.CONTEXT,
            DiffLineType.SKIPPED,
            -> Color.Transparent
        }

    val contentColor =
        when (line.type) {
            DiffLineType.ADDED -> colors.addedText
            DiffLineType.REMOVED -> colors.removedText
            DiffLineType.CONTEXT,
            DiffLineType.SKIPPED,
            -> MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(
                    horizontal = style.lineRowHorizontalPadding,
                    vertical = style.lineRowVerticalPadding,
                ),
        verticalAlignment = Alignment.Top,
    ) {
        LineNumberCell(number = line.oldLineNumber, style = style, colors = colors)
        LineNumberCell(number = line.newLineNumber, style = style, colors = colors)

        SelectionContainer {
            Text(
                text =
                    buildHighlightedDiffLine(
                        line = line,
                        baseContentColor = contentColor,
                        colors = colors,
                        highlightSpans = presentationLine.highlightSpans,
                    ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SkippedDiffLine(
    line: DiffLine,
    style: DiffViewerStyle,
) {
    val hiddenLines = line.hiddenUnchangedCount ?: 0
    val skippedLabel =
        pluralStringResource(
            id = R.plurals.diff_viewer_hidden_unchanged_lines,
            count = hiddenLines,
            hiddenLines,
        )
    Text(
        text = skippedLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = style.skippedLineHorizontalPadding,
                    vertical = style.skippedLineVerticalPadding,
                ),
    )
}

@Composable
private fun LineNumberCell(
    number: Int?,
    style: DiffViewerStyle,
    colors: DiffViewerColors,
) {
    Text(
        text = number?.toString().orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = colors.gutterText,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.End,
        modifier = Modifier.width(style.gutterWidth).padding(end = 6.dp),
    )
}

private fun buildHighlightedDiffLine(
    line: DiffLine,
    baseContentColor: Color,
    colors: DiffViewerColors,
    highlightSpans: List<HighlightSpan>,
): AnnotatedString {
    val prefix =
        when (line.type) {
            DiffLineType.ADDED -> "+"
            DiffLineType.REMOVED -> "-"
            DiffLineType.CONTEXT -> " "
            DiffLineType.SKIPPED -> " "
        }

    val content = line.content
    val baseStyle = SpanStyle(color = baseContentColor, fontFamily = FontFamily.Monospace)
    val syntaxColors = SyntaxHighlightColors(
        comment = colors.commentText,
        string = colors.stringText,
        number = colors.numberText,
        keyword = colors.keywordText,
    )

    return buildAnnotatedString {
        append(prefix)
        append(" ")
        append(content)
        addStyle(baseStyle, start = 0, end = length)

        val offset = 2
        highlightSpans.forEach { span ->
            addStyle(
                style = highlightKindStyle(span.kind, syntaxColors),
                start = span.start + offset,
                end = span.end + offset,
            )
        }
    }
}

private fun computeDiffPresentationLines(
    diffInfo: EditDiffInfo,
    contextLines: Int,
    syntaxLanguage: SyntaxLanguage,
): List<DiffPresentationLine> {
    val diffLines = computeDiffLines(diffInfo = diffInfo, contextLines = contextLines)
    return diffLines.map { line ->
        val spans =
            if (line.type == DiffLineType.SKIPPED || line.content.isEmpty()) {
                emptyList()
            } else {
                computeHighlightSpans(content = line.content, language = syntaxLanguage)
            }
        DiffPresentationLine(
            line = line,
            highlightSpans = spans,
        )
    }
}

private fun computeHighlightSpans(
    content: String,
    language: SyntaxLanguage,
): List<HighlightSpan> {
    return PrismHighlighter.highlight(
        content = content,
        language = language,
    )
}

internal fun detectHighlightKindsForTest(
    content: String,
    path: String,
): Set<String> {
    val language = detectSyntaxLanguageFromPath(path)
    return computeHighlightSpans(content = content, language = language)
        .map { span -> span.kind.name }
        .toSet()
}

/**
 * Represents a single line in a diff.
 */
data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null,
    val hiddenUnchangedCount: Int? = null,
)

enum class DiffLineType {
    ADDED,
    REMOVED,
    CONTEXT,
    SKIPPED,
}

internal fun computeDiffLines(
    diffInfo: EditDiffInfo,
    contextLines: Int = DEFAULT_CONTEXT_LINES,
): List<DiffLine> {
    val oldLines = splitLines(diffInfo.oldString)
    val newLines = splitLines(diffInfo.newString)
    val completeDiff = buildCompleteDiff(oldLines = oldLines, newLines = newLines)
    return collapseToContextHunks(completeDiff, contextLines = contextLines)
}

private fun splitLines(text: String): List<String> {
    val normalizedText = normalizeLineEndings(text)
    return if (normalizedText.isEmpty()) {
        emptyList()
    } else {
        normalizedText.split('\n', ignoreCase = false, limit = Int.MAX_VALUE)
    }
}

private fun normalizeLineEndings(text: String): String {
    return text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
}

private data class DiffCursor(
    var oldIndex: Int = 0,
    var newIndex: Int = 0,
)

private fun buildCompleteDiff(
    oldLines: List<String>,
    newLines: List<String>,
): List<DiffLine> {
    val deltas = sortedDeltas(oldLines, newLines)
    val diffLines = mutableListOf<DiffLine>()
    val cursor = DiffCursor()

    deltas.forEach { delta ->
        appendContextUntil(
            lines = diffLines,
            oldLines = oldLines,
            targetOldIndex = delta.source.position,
            cursor = cursor,
        )
        appendDeltaLines(lines = diffLines, delta = delta, cursor = cursor)
    }

    appendRemainingLines(lines = diffLines, oldLines = oldLines, newLines = newLines, cursor = cursor)

    return diffLines
}

private fun sortedDeltas(
    oldLines: List<String>,
    newLines: List<String>,
): List<AbstractDelta<String>> {
    return DiffUtils
        .diff(oldLines, newLines)
        .deltas
        .sortedWith(compareBy<AbstractDelta<String>> { it.source.position }.thenBy { it.target.position })
}

private fun appendContextUntil(
    lines: MutableList<DiffLine>,
    oldLines: List<String>,
    targetOldIndex: Int,
    cursor: DiffCursor,
) {
    while (cursor.oldIndex < targetOldIndex) {
        lines +=
            DiffLine(
                type = DiffLineType.CONTEXT,
                content = oldLines[cursor.oldIndex],
                oldLineNumber = cursor.oldIndex + 1,
                newLineNumber = cursor.newIndex + 1,
            )
        cursor.oldIndex += 1
        cursor.newIndex += 1
    }
}

private fun appendDeltaLines(
    lines: MutableList<DiffLine>,
    delta: AbstractDelta<String>,
    cursor: DiffCursor,
) {
    when (delta.type) {
        DeltaType.INSERT -> appendAddedLines(lines, delta.target.lines, cursor)
        DeltaType.DELETE -> appendRemovedLines(lines, delta.source.lines, cursor)
        DeltaType.CHANGE -> {
            appendRemovedLines(lines, delta.source.lines, cursor)
            appendAddedLines(lines, delta.target.lines, cursor)
        }
        DeltaType.EQUAL,
        null,
        -> Unit
    }
}

private fun appendRemovedLines(
    lines: MutableList<DiffLine>,
    sourceLines: List<String>,
    cursor: DiffCursor,
) {
    sourceLines.forEach { content ->
        lines +=
            DiffLine(
                type = DiffLineType.REMOVED,
                content = content,
                oldLineNumber = cursor.oldIndex + 1,
            )
        cursor.oldIndex += 1
    }
}

private fun appendAddedLines(
    lines: MutableList<DiffLine>,
    targetLines: List<String>,
    cursor: DiffCursor,
) {
    targetLines.forEach { content ->
        lines +=
            DiffLine(
                type = DiffLineType.ADDED,
                content = content,
                newLineNumber = cursor.newIndex + 1,
            )
        cursor.newIndex += 1
    }
}

private fun appendRemainingLines(
    lines: MutableList<DiffLine>,
    oldLines: List<String>,
    newLines: List<String>,
    cursor: DiffCursor,
) {
    while (cursor.oldIndex < oldLines.size && cursor.newIndex < newLines.size) {
        lines +=
            DiffLine(
                type = DiffLineType.CONTEXT,
                content = oldLines[cursor.oldIndex],
                oldLineNumber = cursor.oldIndex + 1,
                newLineNumber = cursor.newIndex + 1,
            )
        cursor.oldIndex += 1
        cursor.newIndex += 1
    }

    while (cursor.oldIndex < oldLines.size) {
        lines +=
            DiffLine(
                type = DiffLineType.REMOVED,
                content = oldLines[cursor.oldIndex],
                oldLineNumber = cursor.oldIndex + 1,
            )
        cursor.oldIndex += 1
    }

    while (cursor.newIndex < newLines.size) {
        lines +=
            DiffLine(
                type = DiffLineType.ADDED,
                content = newLines[cursor.newIndex],
                newLineNumber = cursor.newIndex + 1,
            )
        cursor.newIndex += 1
    }
}

private fun collapseToContextHunks(
    lines: List<DiffLine>,
    contextLines: Int,
): List<DiffLine> {
    if (lines.isEmpty()) return emptyList()

    val changedIndexes =
        lines.indices.filter { index ->
            lines[index].type == DiffLineType.ADDED || lines[index].type == DiffLineType.REMOVED
        }

    val hasChanges = changedIndexes.isNotEmpty()
    return if (hasChanges) {
        buildCollapsedHunks(lines = lines, changedIndexes = changedIndexes, contextLines = contextLines)
    } else {
        lines
    }
}

private fun buildCollapsedHunks(
    lines: List<DiffLine>,
    changedIndexes: List<Int>,
    contextLines: Int,
): List<DiffLine> {
    val mergedRanges = mutableListOf<IntRange>()
    changedIndexes.forEach { changedIndex ->
        val start = maxOf(0, changedIndex - contextLines)
        val end = minOf(lines.lastIndex, changedIndex + contextLines)

        val previous = mergedRanges.lastOrNull()
        if (previous == null || start > previous.last + 1) {
            mergedRanges += start..end
        } else {
            mergedRanges[mergedRanges.lastIndex] = previous.first..maxOf(previous.last, end)
        }
    }

    return materializeCollapsedRanges(lines = lines, mergedRanges = mergedRanges)
}

private fun materializeCollapsedRanges(
    lines: List<DiffLine>,
    mergedRanges: List<IntRange>,
): List<DiffLine> {
    val result = mutableListOf<DiffLine>()
    var nextStart = 0

    mergedRanges.forEach { range ->
        if (range.first > nextStart) {
            result += skippedLine(range.first - nextStart)
        }

        for (index in range) {
            result += lines[index]
        }

        nextStart = range.last + 1
    }

    if (nextStart <= lines.lastIndex) {
        result += skippedLine(lines.size - nextStart)
    }

    return result
}

private fun skippedLine(count: Int): DiffLine {
    return DiffLine(
        type = DiffLineType.SKIPPED,
        content = "",
        hiddenUnchangedCount = count,
    )
}
