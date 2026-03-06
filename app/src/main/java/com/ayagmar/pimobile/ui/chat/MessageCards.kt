package com.ayagmar.pimobile.ui.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.ayagmar.pimobile.ui.theme.LocalChatColors
import com.ayagmar.pimobile.ui.theme.PiCodeFontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ayagmar.pimobile.chat.ChatTimelineItem

private const val COLLAPSED_OUTPUT_LENGTH = 280
private const val COLLAPSED_HEAD_LINES = 3
private const val COLLAPSED_TAIL_LINES = 2
private const val SMART_TRUNCATION_MIN_LINES = 8
private const val THINKING_COLLAPSE_THRESHOLD = 280
private const val THINKING_EXPAND_ANIM_MS = 250
private const val THINKING_DASH_INTERVAL_PX = 8f
private const val THINKING_DASH_PHASE_PX = 0f
private const val THINKING_BORDER_WIDTH_PX = 2.5f
private const val THINKING_BORDER_RADIUS_PX = 12f
private const val MAX_ARG_DISPLAY_LENGTH = 100
private const val MAX_INLINE_USER_IMAGE_PREVIEWS = 4
private const val USER_IMAGE_PREVIEW_SIZE_DP = 56
private const val TOOL_HIGHLIGHT_MAX_LENGTH = 1_000
private const val LINE_NUMBER_THRESHOLD = 6
private val CODE_FENCE_REGEX = Regex("```([\\w+-]*)\\r?\\n([\\s\\S]*?)```")

private sealed interface AssistantMessageBlock {
    data class Paragraph(
        val text: String,
    ) : AssistantMessageBlock

    data class Code(
        val code: String,
        val language: String?,
    ) : AssistantMessageBlock
}

private data class ToolDisplayInfo(
    val icon: ImageVector,
    val color: Color,
)

@Suppress("LongParameterList")
@Composable
internal fun ChatTimelineRow(
    item: ChatTimelineItem,
    expandedToolArguments: Set<String>,
    onToggleToolExpansion: (String) -> Unit,
    onToggleThinkingExpansion: (String) -> Unit,
    onToggleDiffExpansion: (String) -> Unit,
    onToggleToolArgumentsExpansion: (String) -> Unit,
    onPreviewImage: (String) -> Unit,
) {
    when (item) {
        is ChatTimelineItem.User -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                UserCard(
                    text = item.text,
                    imageCount = item.imageCount,
                    imageUris = item.imageUris,
                    onImageClick = onPreviewImage,
                )
            }
        }

        is ChatTimelineItem.Assistant -> {
            AssistantCard(
                item = item,
                onToggleThinkingExpansion = onToggleThinkingExpansion,
            )
        }

        is ChatTimelineItem.Tool -> {
            ToolCard(
                item = item,
                isArgumentsExpanded = item.id in expandedToolArguments,
                onToggleToolExpansion = onToggleToolExpansion,
                onToggleDiffExpansion = onToggleDiffExpansion,
                onToggleArgumentsExpansion = onToggleToolArgumentsExpansion,
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun UserCard(
    text: String,
    imageCount: Int,
    imageUris: List<String>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chatColors = LocalChatColors.current
    Card(
        modifier = modifier.widthIn(max = 340.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = chatColors.userContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "You",
                style = MaterialTheme.typography.titleSmall,
                color = chatColors.onUserContainer,
            )
            Text(
                text = text.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodyMedium,
                color = chatColors.onUserContainer,
            )

            if (imageUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(
                        items = imageUris.take(MAX_INLINE_USER_IMAGE_PREVIEWS),
                        key = { index, uri -> "$uri-$index" },
                    ) { _, uriString ->
                        UserImagePreview(
                            uriString = uriString,
                            onClick = { onImageClick(uriString) },
                        )
                    }

                    val remaining = imageUris.size - MAX_INLINE_USER_IMAGE_PREVIEWS
                    if (remaining > 0) {
                        item(key = "more-images") {
                            Box(
                                modifier =
                                    Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = "+$remaining", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            if (imageCount > 0) {
                Text(
                    text = if (imageCount == 1) "📎 1 image attached" else "📎 $imageCount images attached",
                    style = MaterialTheme.typography.labelMedium,
                    color = chatColors.onUserContainer,
                )
            }
        }
    }
}

@Composable
private fun UserImagePreview(
    uriString: String,
    onClick: () -> Unit,
) {
    val uri = remember(uriString) { Uri.parse(uriString) }
    var loadFailed by remember(uriString) { mutableStateOf(false) }

    if (loadFailed) {
        Box(
            modifier =
                Modifier
                    .size(USER_IMAGE_PREVIEW_SIZE_DP.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "IMG",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    AsyncImage(
        model = uri,
        contentDescription = "Sent image preview",
        modifier =
            Modifier
                .size(USER_IMAGE_PREVIEW_SIZE_DP.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        contentScale = ContentScale.Crop,
        onError = {
            loadFailed = true
        },
    )
}

@Composable
private fun AssistantCard(
    item: ChatTimelineItem.Assistant,
    onToggleThinkingExpansion: (String) -> Unit,
) {
    val chatColors = LocalChatColors.current
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = chatColors.assistantAccent,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                ),
        )
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = chatColors.assistantContainer,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val title = if (item.isStreaming) "Assistant (streaming)" else "Assistant"
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = chatColors.onAssistantContainer,
                )

                AssistantMessageContent(
                    text = item.text,
                    modifier = Modifier.fillMaxWidth(),
                )

                ThinkingBlock(
                    thinking = item.thinking,
                    isThinkingComplete = item.isThinkingComplete,
                    isThinkingExpanded = item.isThinkingExpanded,
                    itemId = item.id,
                    onToggleThinkingExpansion = onToggleThinkingExpansion,
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageContent(
    text: String,
    modifier: Modifier = Modifier,
) {
    val chatColors = LocalChatColors.current
    if (text.isBlank()) {
        Text(
            text = "(empty)",
            style = MaterialTheme.typography.bodyMedium,
            color = chatColors.onAssistantContainer,
            modifier = modifier,
        )
        return
    }

    // Fast path: no code fences — render with block-level markdown (headers, lists, etc.)
    if (!text.contains("```")) {
        MarkdownText(
            text = text,
            modifier = modifier,
            baseColor = chatColors.onAssistantContainer,
        )
        return
    }

    val blocks = remember(text) { parseAssistantMessageBlocks(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is AssistantMessageBlock.Paragraph -> {
                    if (block.text.isNotBlank()) {
                        MarkdownText(
                            text = block.text,
                            baseColor = chatColors.onAssistantContainer,
                        )
                    }
                }

                is AssistantMessageBlock.Code -> {
                    AssistantCodeBlock(
                        code = block.code,
                        language = block.language,
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun AssistantCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val syntaxLanguage = remember(language) { codeFenceLanguageToSyntax(language) }
    val text = code.ifBlank { "(empty code block)" }
    val colors = MaterialTheme.colorScheme
    val syntaxColors = remember(colors) {
        SyntaxHighlightColors(
            comment = colors.outline,
            string = colors.tertiary,
            number = colors.secondary,
            keyword = colors.primary,
        )
    }
    val spans = remember(text, syntaxLanguage) {
        PrismHighlighter.highlight(content = text, language = syntaxLanguage)
    }
    val lines = remember(text) { text.lines() }
    val showLineNumbers = lines.size >= LINE_NUMBER_THRESHOLD
    val gutterWidth = if (showLineNumbers) (lines.size.toString().length * 8 + 12).dp else 0.dp
    val clipboardManager = LocalClipboardManager.current

    val chatColors = LocalChatColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = chatColors.codeSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row: language chip + copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = syntaxLanguage.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(text)) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(14.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            // Code content with optional line numbers
            SelectionContainer {
                if (showLineNumbers) {
                    Row(modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                        // Line number gutter
                        Column(modifier = Modifier.width(gutterWidth)) {
                            lines.forEachIndexed { index, _ ->
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = PiCodeFontFamily,
                                    color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        // Code text
                        Text(
                            text = buildPrismHighlightedString(text, spans, colors.onSurface, syntaxColors),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = PiCodeFontFamily,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Text(
                        text = buildPrismHighlightedString(text, spans, colors.onSurface, syntaxColors),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = PiCodeFontFamily,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun buildPrismHighlightedString(
    text: String,
    spans: List<HighlightSpan>,
    baseColor: Color,
    syntaxColors: SyntaxHighlightColors,
): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = baseColor), 0, text.length)
        spans.forEach { span ->
            addStyle(
                style = highlightKindStyle(span.kind, syntaxColors),
                start = span.start,
                end = span.end,
            )
        }
    }
}

private fun parseAssistantMessageBlocks(text: String): List<AssistantMessageBlock> {
    if (text.isBlank()) return emptyList()

    val blocks = mutableListOf<AssistantMessageBlock>()
    var cursor = 0

    CODE_FENCE_REGEX.findAll(text).forEach { match ->
        val matchStart = match.range.first
        val matchEndExclusive = match.range.last + 1

        if (matchStart > cursor) {
            val paragraph = text.substring(cursor, matchStart).trim()
            if (paragraph.isNotEmpty()) {
                blocks += AssistantMessageBlock.Paragraph(paragraph)
            }
        }

        val language = match.groupValues[1].takeIf { it.isNotBlank() }
        val code = match.groupValues[2]
        blocks += AssistantMessageBlock.Code(code = code.trimEnd(), language = language)
        cursor = matchEndExclusive
    }

    if (cursor < text.length) {
        val paragraph = text.substring(cursor).trim()
        if (paragraph.isNotEmpty()) {
            blocks += AssistantMessageBlock.Paragraph(paragraph)
        }
    }

    return blocks
}


@Suppress("LongMethod")
@Composable
private fun ThinkingBlock(
    thinking: String?,
    isThinkingComplete: Boolean,
    isThinkingExpanded: Boolean,
    itemId: String,
    onToggleThinkingExpansion: (String) -> Unit,
) {
    if (thinking == null) return

    val shouldCollapse = thinking.length > THINKING_COLLAPSE_THRESHOLD
    val displayThinking =
        if (!isThinkingExpanded && shouldCollapse) {
            thinking.take(THINKING_COLLAPSE_THRESHOLD) + "…"
        } else {
            thinking
        }

    val chatColors = LocalChatColors.current
    val borderColor = chatColors.thinkingBorder
    val dashEffect = remember {
        PathEffect.dashPathEffect(
            floatArrayOf(THINKING_DASH_INTERVAL_PX, THINKING_DASH_INTERVAL_PX),
            THINKING_DASH_PHASE_PX,
        )
    }

    val wordCount = remember(thinking) {
        thinking.split(Regex("\\s+")).count { it.isNotEmpty() }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(THINKING_BORDER_RADIUS_PX, THINKING_BORDER_RADIUS_PX),
                    style = Stroke(
                        width = THINKING_BORDER_WIDTH_PX,
                        pathEffect = dashEffect,
                    ),
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = chatColors.thinkingContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .animateContentSize(animationSpec = tween(THINKING_EXPAND_ANIM_MS)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = chatColors.onThinkingContainer,
                )
                Text(
                    text = if (isThinkingComplete) "Thinking" else "Thinking\u2026",
                    style = MaterialTheme.typography.labelSmall,
                    color = chatColors.onThinkingContainer,
                )
                if (isThinkingComplete) {
                    Text(
                        text = "\u00B7 $wordCount words",
                        style = MaterialTheme.typography.labelSmall,
                        color = chatColors.onThinkingContainer.copy(alpha = 0.6f),
                    )
                }
            }
            Text(
                text = displayThinking,
                style = MaterialTheme.typography.bodyMedium,
                color = chatColors.onThinkingContainer,
            )

            if (shouldCollapse || isThinkingExpanded) {
                TextButton(
                    onClick = { onToggleThinkingExpansion(itemId) },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        if (isThinkingExpanded) "Show less" else "Show more",
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun ToolCard(
    item: ChatTimelineItem.Tool,
    isArgumentsExpanded: Boolean,
    onToggleToolExpansion: (String) -> Unit,
    onToggleDiffExpansion: (String) -> Unit,
    onToggleArgumentsExpansion: (String) -> Unit,
) {
    val isEditTool = item.toolName == "edit" && item.editDiff != null
    val toolInfo = getToolInfo(item.toolName)
    val clipboardManager = LocalClipboardManager.current

    val chatColors = LocalChatColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = chatColors.toolContainer,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Tool header with icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tool icon with color
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(toolInfo.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = toolInfo.icon,
                        contentDescription = item.toolName,
                        tint = toolInfo.color,
                        modifier = Modifier.size(18.dp),
                    )
                }

                val suffix =
                    when {
                        item.isError -> "(error)"
                        item.isStreaming -> "(running)"
                        else -> ""
                    }

                Text(
                    text = "${item.toolName} $suffix".trim(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )

                if (item.isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            // Collapsible arguments section
            if (item.arguments.isNotEmpty()) {
                ToolArgumentsSection(
                    arguments = item.arguments,
                    isExpanded = isArgumentsExpanded,
                    onToggleExpand = { onToggleArgumentsExpansion(item.id) },
                    onCopy = {
                        val argsJson = item.arguments.entries.joinToString("\n") { (k, v) -> "\"$k\": \"$v\"" }
                        clipboardManager.setText(AnnotatedString("{\n$argsJson\n}"))
                    },
                )
            }

            // Show diff viewer for edit tools, otherwise show standard output
            if (isEditTool && item.editDiff != null) {
                DiffViewer(
                    diffInfo = item.editDiff,
                    isCollapsed = !item.isDiffExpanded,
                    onToggleCollapse = { onToggleDiffExpansion(item.id) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                ToolOutputContent(
                    item = item,
                    onToggleToolExpansion = onToggleToolExpansion,
                )
            }

            // Linear progress bar for streaming tools
            if (item.isStreaming) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(2.dp),
                )
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun ToolOutputContent(
    item: ChatTimelineItem.Tool,
    onToggleToolExpansion: (String) -> Unit,
) {
    val outputLines = remember(item.output) { item.output.lines() }
    val totalLines = outputLines.size
    val useSmartTruncation = item.isCollapsed &&
        totalLines >= SMART_TRUNCATION_MIN_LINES &&
        item.output.length > COLLAPSED_OUTPUT_LENGTH

    if (useSmartTruncation) {
        // Smart truncation: show head + ... + tail
        val headLines = outputLines.take(COLLAPSED_HEAD_LINES)
        val tailLines = outputLines.takeLast(COLLAPSED_TAIL_LINES)
        val hiddenCount = totalLines - COLLAPSED_HEAD_LINES - COLLAPSED_TAIL_LINES

        val headText = headLines.joinToString("\n")
        val tailText = tailLines.joinToString("\n")

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            SelectionContainer {
                ToolOutputText(
                    text = headText,
                    item = item,
                )
            }

            TextButton(
                onClick = { onToggleToolExpansion(item.id) },
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("$hiddenCount lines hidden \u2014 show all")
            }

            SelectionContainer {
                ToolOutputText(
                    text = tailText,
                    item = item,
                )
            }
        }
    } else {
        val displayOutput = if (item.isCollapsed && item.output.length > COLLAPSED_OUTPUT_LENGTH) {
            item.output.take(COLLAPSED_OUTPUT_LENGTH) + "\u2026"
        } else {
            item.output
        }
        val rawOutput = displayOutput.ifBlank { "(no output yet)" }

        SelectionContainer {
            ToolOutputText(
                text = rawOutput,
                item = item,
            )
        }

        if (item.output.length > COLLAPSED_OUTPUT_LENGTH) {
            TextButton(onClick = { onToggleToolExpansion(item.id) }) {
                Icon(
                    imageVector = if (item.isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(if (item.isCollapsed) "Expand" else "Collapse")
            }
        }
    }
}

@Composable
private fun ToolOutputText(
    text: String,
    item: ChatTimelineItem.Tool,
) {
    val shouldHighlight = !item.isStreaming && text.length <= TOOL_HIGHLIGHT_MAX_LENGTH

    if (shouldHighlight) {
        val inferredLanguage = inferLanguageFromToolContext(item)
        val syntaxColors = SyntaxHighlightColors(
            comment = MaterialTheme.colorScheme.outline,
            string = MaterialTheme.colorScheme.tertiary,
            number = MaterialTheme.colorScheme.secondary,
            keyword = MaterialTheme.colorScheme.primary,
        )
        val spans = remember(text, inferredLanguage) {
            PrismHighlighter.highlight(content = text, language = inferredLanguage)
        }
        Text(
            text = buildPrismHighlightedString(text, spans, MaterialTheme.colorScheme.onSurface, syntaxColors),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = PiCodeFontFamily,
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = PiCodeFontFamily,
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun ToolArgumentsSection(
    arguments: Map<String, String>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Arguments (${arguments.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy arguments",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isExpanded) {
            SelectionContainer {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    arguments.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = PiCodeFontFamily,
                            )
                            Text(
                                text = "=",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val displayValue =
                                if (value.length > MAX_ARG_DISPLAY_LENGTH) {
                                    value.take(MAX_ARG_DISPLAY_LENGTH) + "…"
                                } else {
                                    value
                                }
                            Text(
                                text = displayValue,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = PiCodeFontFamily,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get tool icon and color based on tool name.
 */
@Composable
private fun getToolInfo(toolName: String): ToolDisplayInfo {
    val colors = MaterialTheme.colorScheme
    return when (toolName) {
        "read" -> ToolDisplayInfo(Icons.Default.Description, colors.primary)
        "write" -> ToolDisplayInfo(Icons.Default.Edit, colors.secondary)
        "edit" -> ToolDisplayInfo(Icons.Default.Edit, colors.tertiary)
        "bash" -> ToolDisplayInfo(Icons.Default.Terminal, colors.error)
        "grep", "rg", "find" -> ToolDisplayInfo(Icons.Default.Search, colors.primary)
        "ls" -> ToolDisplayInfo(Icons.Default.Folder, colors.secondary)
        else -> ToolDisplayInfo(Icons.Default.Terminal, colors.outline)
    }
}

private fun inferLanguageFromToolContext(item: ChatTimelineItem.Tool): SyntaxLanguage {
    val path = item.arguments["path"] ?: return SyntaxLanguage.PLAIN
    return detectSyntaxLanguageFromPath(path)
}
