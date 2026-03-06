package com.ayagmar.pimobile.ui.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ayagmar.pimobile.chat.ChatTimelineItem

private const val COLLAPSED_OUTPUT_LENGTH = 280
private const val THINKING_COLLAPSE_THRESHOLD = 280
private const val MAX_ARG_DISPLAY_LENGTH = 100
private const val MAX_INLINE_USER_IMAGE_PREVIEWS = 4
private const val USER_IMAGE_PREVIEW_SIZE_DP = 56
private const val TOOL_HIGHLIGHT_MAX_LENGTH = 1_000
private val CODE_FENCE_REGEX = Regex("```([\\w+-]*)\\r?\\n([\\s\\S]*?)```")
private val STRING_REGEX = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
private val NUMBER_REGEX = Regex("\\b\\d+(?:\\.\\d+)?\\b")
private val HASH_COMMENT_REGEX = Regex("#.*$", setOf(RegexOption.MULTILINE))
private val SLASH_COMMENT_REGEX =
    Regex(
        "//.*$|/\\*.*?\\*/",
        setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
    )
private val KOTLIN_KEYWORD_REGEX =
    Regex(
        "\\b(class|object|interface|fun|val|var|when|if|else|return|suspend|data|sealed|" +
            "private|public|override|import|package)\\b",
    )
private val JAVA_KEYWORD_REGEX =
    Regex(
        "\\b(class|interface|enum|public|private|protected|static|final|void|return|if|" +
            "else|switch|case|new|import|package)\\b",
    )
private val PYTHON_KEYWORD_REGEX =
    Regex(
        "\\b(def|class|import|from|as|if|elif|else|for|while|return|try|except|with|lambda|pass|break|continue)\\b",
    )
private val JS_TS_KEYWORD_REGEX =
    Regex(
        "\\b(function|class|const|let|var|return|if|else|switch|case|import|from|export|async|await|interface|type)\\b",
    )
private val BASH_KEYWORD_REGEX = Regex("\\b(if|then|fi|for|do|done|case|esac|function|export|echo)\\b")
private val GENERIC_KEYWORD_REGEX =
    Regex("\\b(if|else|for|while|return|class|function|import|from|const|let|var|def|public|private)\\b")
private val TOOL_OUTPUT_LANGUAGE_BY_EXTENSION =
    mapOf(
        "kt" to "kotlin",
        "kts" to "kotlin",
        "java" to "java",
        "js" to "javascript",
        "jsx" to "javascript",
        "ts" to "typescript",
        "tsx" to "typescript",
        "py" to "python",
        "json" to "json",
        "jsonl" to "json",
        "xml" to "xml",
        "html" to "xml",
        "svg" to "xml",
        "sh" to "bash",
        "bash" to "bash",
        "sql" to "sql",
        "yml" to "yaml",
        "yaml" to "yaml",
        "go" to "go",
        "rs" to "rust",
        "md" to "markdown",
    )

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
    Card(
        modifier = modifier.widthIn(max = 340.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "You",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = text.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
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
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                ),
        )
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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
                    color = MaterialTheme.colorScheme.onSurface,
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
    if (text.isBlank()) {
        Text(
            text = "(empty)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
        return
    }

    // Fast path: no code fences — render with block-level markdown (headers, lists, etc.)
    if (!text.contains("```")) {
        MarkdownText(
            text = text,
            modifier = modifier,
            baseColor = MaterialTheme.colorScheme.onSurface,
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
                            baseColor = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun AssistantCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val highlighted = highlightCodeBlock(code, language, colors)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
    ) {
        SelectionContainer {
            Text(
                text = highlighted,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp),
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

private fun highlightCodeBlock(
    code: String,
    language: String?,
    colors: androidx.compose.material3.ColorScheme,
): AnnotatedString {
    val text = code.ifBlank { "(empty code block)" }
    val commentPattern = commentRegexFor(language)
    val keywordPattern = keywordRegexFor(language)

    val commentStyle = SpanStyle(color = colors.outline)
    val stringStyle = SpanStyle(color = colors.tertiary)
    val numberStyle = SpanStyle(color = colors.secondary)
    val keywordStyle = SpanStyle(color = colors.primary)

    return buildAnnotatedString {
        append(text)

        applyStyle(STRING_REGEX, stringStyle, text)
        applyStyle(NUMBER_REGEX, numberStyle, text)
        applyStyle(keywordPattern, keywordStyle, text)
        applyStyle(commentPattern, commentStyle, text)
    }
}

private fun AnnotatedString.Builder.applyStyle(
    regex: Regex,
    style: SpanStyle,
    text: String,
) {
    regex.findAll(text).forEach { match ->
        addStyle(style, match.range.first, match.range.last + 1)
    }
}

private fun keywordRegexFor(language: String?): Regex {
    return when (language?.lowercase()) {
        "kotlin", "kt" -> KOTLIN_KEYWORD_REGEX
        "java" -> JAVA_KEYWORD_REGEX
        "python", "py" -> PYTHON_KEYWORD_REGEX
        "javascript", "js", "typescript", "ts", "tsx" -> JS_TS_KEYWORD_REGEX
        "bash", "shell", "sh" -> BASH_KEYWORD_REGEX
        else -> GENERIC_KEYWORD_REGEX
    }
}

private fun commentRegexFor(language: String?): Regex {
    return when (language?.lowercase()) {
        "python", "py", "bash", "shell", "sh", "yaml", "yml" -> HASH_COMMENT_REGEX
        else -> SLASH_COMMENT_REGEX
    }
}

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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
            ),
        border =
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = if (isThinkingComplete) " Thinking" else " Thinking…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                text = displayThinking,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                val displayOutput =
                    if (item.isCollapsed && item.output.length > COLLAPSED_OUTPUT_LENGTH) {
                        item.output.take(COLLAPSED_OUTPUT_LENGTH) + "…"
                    } else {
                        item.output
                    }

                val rawOutput = displayOutput.ifBlank { "(no output yet)" }
                val shouldHighlight = !item.isStreaming && rawOutput.length <= TOOL_HIGHLIGHT_MAX_LENGTH

                SelectionContainer {
                    if (shouldHighlight) {
                        val inferredLanguage = inferLanguageFromToolContext(item)
                        val highlightedOutput =
                            highlightCodeBlock(
                                code = rawOutput,
                                language = inferredLanguage,
                                colors = MaterialTheme.colorScheme,
                            )
                        Text(
                            text = highlightedOutput,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        Text(
                            text = rawOutput,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
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
                                fontFamily = FontFamily.Monospace,
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
                                fontFamily = FontFamily.Monospace,
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

private fun inferLanguageFromToolContext(item: ChatTimelineItem.Tool): String? {
    val path = item.arguments["path"] ?: return null
    val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return TOOL_OUTPUT_LANGUAGE_BY_EXTENSION[extension]
}
