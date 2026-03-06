package com.ayagmar.pimobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.ayagmar.pimobile.ui.theme.PiCodeFontFamily
import androidx.compose.ui.unit.dp
import com.ayagmar.pimobile.chat.ChatViewModel
import com.ayagmar.pimobile.corerpc.AvailableModel
import com.ayagmar.pimobile.corerpc.SessionStats
import com.ayagmar.pimobile.sessions.ModelInfo
import com.ayagmar.pimobile.sessions.SessionTreeEntry
import com.ayagmar.pimobile.sessions.SessionTreeSnapshot

private const val MODEL_PICKER_SCROLL_OFFSET_ITEMS = 1
private const val SESSION_PATH_DISPLAY_LENGTH = 40

private val TREE_FILTER_OPTIONS =
    listOf(
        ChatViewModel.TREE_FILTER_DEFAULT to "default",
        ChatViewModel.TREE_FILTER_ALL to "all",
        ChatViewModel.TREE_FILTER_NO_TOOLS to "no-tools",
        ChatViewModel.TREE_FILTER_USER_ONLY to "user-only",
        ChatViewModel.TREE_FILTER_LABELED_ONLY to "labeled-only",
    )

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BashDialog(
    isVisible: Boolean,
    command: String,
    output: String,
    exitCode: Int?,
    isExecuting: Boolean,
    wasTruncated: Boolean,
    fullLogPath: String?,
    history: List<String>,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit,
    onAbort: () -> Unit,
    onSelectHistory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showHistoryDropdown by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = { if (!isExecuting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Run Bash Command",
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // Command input with history dropdown
            Box {
                OutlinedTextField(
                    value = command,
                    onValueChange = onCommandChange,
                    placeholder = { Text("Enter command...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isExecuting,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = PiCodeFontFamily),
                    trailingIcon = {
                        if (history.isNotEmpty() && !isExecuting) {
                            IconButton(onClick = { showHistoryDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = "History",
                                )
                            }
                        }
                    },
                )

                DropdownMenu(
                    expanded = showHistoryDropdown,
                    onDismissRequest = { showHistoryDropdown = false },
                ) {
                    history.forEach { historyCommand ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = historyCommand,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = PiCodeFontFamily,
                                    maxLines = 1,
                                )
                            },
                            onClick = {
                                onSelectHistory(historyCommand)
                                showHistoryDropdown = false
                            },
                        )
                    }
                }
            }

            // Output display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Output",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (output.isNotEmpty()) {
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(output)) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy output",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    SelectionContainer {
                        Text(
                            text = output.ifEmpty { "(no output)" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = PiCodeFontFamily,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }

            // Exit code and truncation info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (exitCode != null) {
                    val exitColor =
                        if (exitCode == 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "Exit: $exitCode",
                                color = exitColor,
                            )
                        },
                    )
                }

                if (wasTruncated && fullLogPath != null) {
                    TextButton(
                        onClick = { clipboardManager.setText(AnnotatedString(fullLogPath)) },
                    ) {
                        Text(
                            text = "Output truncated (copy path)",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                if (isExecuting) {
                    Button(
                        onClick = onAbort,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).padding(end = 4.dp),
                        )
                        Text("Abort")
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Button(
                        onClick = onExecute,
                        enabled = command.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).padding(end = 4.dp),
                        )
                        Text("Execute")
                    }
                }
            }
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionStatsSheet(
    isVisible: Boolean,
    stats: SessionStats?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val clipboardManager = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Session Statistics",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (stats == null) {
                Text(
                    text = "No statistics available",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Token stats
                    StatsSection(title = "Tokens") {
                        StatRow("Input Tokens", formatNumber(stats.inputTokens))
                        StatRow("Output Tokens", formatNumber(stats.outputTokens))
                        StatRow("Cache Read", formatNumber(stats.cacheReadTokens))
                        StatRow("Cache Write", formatNumber(stats.cacheWriteTokens))
                    }

                    // Cost
                    StatsSection(title = "Cost") {
                        StatRow("Total Cost", formatCost(stats.totalCost))
                    }

                    // Messages
                    StatsSection(title = "Messages") {
                        StatRow("Total", stats.messageCount.toString())
                        StatRow("User", stats.userMessageCount.toString())
                        StatRow("Assistant", stats.assistantMessageCount.toString())
                        StatRow("Tool Results", stats.toolResultCount.toString())
                    }

                    // Session path
                    stats.sessionPath?.let { path ->
                        StatsSection(title = "Session File") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = path.takeLast(SESSION_PATH_DISPLAY_LENGTH),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = PiCodeFontFamily,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(path)) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy path",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = PiCodeFontFamily,
        )
    }
}

@Suppress("MagicNumber")
internal fun formatNumber(value: Long): String {
    return when {
        value >= 1_000_000 -> String.format(java.util.Locale.US, "%.2fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

@Suppress("MagicNumber")
internal fun formatCost(value: Double): String {
    return String.format(java.util.Locale.US, "$%.4f", value)
}

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(
    isVisible: Boolean,
    models: List<AvailableModel>,
    currentModel: ModelInfo?,
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectModel: (AvailableModel) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredModels =
        remember(models, query) {
            if (query.isBlank()) {
                models
            } else {
                models.filter { model ->
                    model.name.contains(query, ignoreCase = true) ||
                        model.provider.contains(query, ignoreCase = true) ||
                        model.id.contains(query, ignoreCase = true)
                }
            }
        }

    val groupedModels =
        remember(filteredModels) {
            filteredModels.groupBy { it.provider }
        }
    val listState = rememberLazyListState()
    val selectedModelIndex =
        remember(groupedModels, currentModel) {
            if (currentModel == null) {
                -1
            } else {
                var index = 0
                var foundIndex = -1
                groupedModels.forEach { (_, modelsInGroup) ->
                    index += 1 // provider header item
                    modelsInGroup.forEach { model ->
                        if (
                            foundIndex < 0 &&
                            model.id == currentModel.id &&
                            model.provider == currentModel.provider
                        ) {
                            foundIndex = index
                        }
                        index += 1
                    }
                }
                foundIndex
            }
        }

    LaunchedEffect(selectedModelIndex, isVisible) {
        if (isVisible && selectedModelIndex >= 0) {
            listState.scrollToItem((selectedModelIndex - MODEL_PICKER_SCROLL_OFFSET_ITEMS).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            // Header
            Text(
                text = "Select Model",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search models...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredModels.isEmpty()) {
                Text(
                    text = "No models found",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    groupedModels.forEach { (provider, modelsInGroup) ->
                        item {
                            Text(
                                text = provider.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(
                            items = modelsInGroup,
                            key = { model -> "${model.provider}:${model.id}" },
                        ) { model ->
                            ModelItem(
                                model = model,
                                isSelected =
                                    currentModel?.id == model.id &&
                                        currentModel.provider == model.provider,
                                onClick = { onSelectModel(model) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun ModelItem(
    model: AvailableModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors =
            if (isSelected) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            } else {
                CardDefaults.cardColors()
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (model.supportsThinking) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "Thinking",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                model.contextWindow?.let { ctx ->
                    Text(
                        text = "Context: ${formatNumber(ctx.toLong())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                model.inputCostPer1k?.let { cost ->
                    Text(
                        text = "In: \$${String.format(java.util.Locale.US, "%.4f", cost)}/1k",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                model.outputCostPer1k?.let { cost ->
                    Text(
                        text = "Out: \$${String.format(java.util.Locale.US, "%.4f", cost)}/1k",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TreeNavigationSheet(
    isVisible: Boolean,
    tree: SessionTreeSnapshot?,
    selectedFilter: String,
    isLoading: Boolean,
    errorMessage: String?,
    onFilterChange: (String) -> Unit,
    onForkFromEntry: (String) -> Unit,
    onJumpAndContinue: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = tree?.entries.orEmpty()
    val depthByEntry = remember(entries) { computeDepthMap(entries) }
    val childCountByEntry = remember(entries) { computeChildCountMap(entries) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            // Header
            Text(
                text = "Session tree",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            tree?.sessionPath?.let { sessionPath ->
                Text(
                    text = truncatePath(sessionPath),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Scrollable filter chips to avoid overflow
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = TREE_FILTER_OPTIONS,
                    key = { (filter, _) -> filter },
                ) { (filter, label) ->
                    FilterChip(
                        selected = filter == selectedFilter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                entries.isEmpty() -> {
                    Text(
                        text = "No tree data available",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                    ) {
                        items(
                            items = entries,
                            key = { entry -> entry.entryId },
                        ) { entry ->
                            TreeEntryRow(
                                entry = entry,
                                depth = depthByEntry[entry.entryId] ?: 0,
                                childCount = childCountByEntry[entry.entryId] ?: 0,
                                isCurrent = tree?.currentLeafId == entry.entryId,
                                onForkFromEntry = onForkFromEntry,
                                onJumpAndContinue = onJumpAndContinue,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("MagicNumber", "LongMethod", "LongParameterList")
@Composable
private fun TreeEntryRow(
    entry: SessionTreeEntry,
    depth: Int,
    childCount: Int,
    isCurrent: Boolean,
    onForkFromEntry: (String) -> Unit,
    onJumpAndContinue: (String) -> Unit,
) {
    val indent = (depth * 8).dp
    val isMessage = entry.entryType == "message"
    val containerColor =
        when {
            isCurrent -> MaterialTheme.colorScheme.primaryContainer
            isMessage -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    val contentColor =
        when {
            isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }

    Card(
        modifier = Modifier.fillMaxWidth().padding(start = indent),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val typeIcon = treeEntryIcon(entry.entryType)
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = contentColor.copy(alpha = 0.7f),
                    )
                    val label =
                        buildString {
                            append(entry.entryType.replace('_', ' '))
                            entry.role?.let { append(" · $it") }
                        }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }

                if (isCurrent) {
                    Text(
                        text = "● current",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (isMessage) {
                Text(
                    text = entry.preview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = contentColor,
                )
            }

            if (entry.isBookmarked && !entry.label.isNullOrBlank()) {
                Text(
                    text = "🔖 ${entry.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (childCount > 1) {
                    Text(
                        text = "↳ $childCount branches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    TextButton(
                        onClick = { onJumpAndContinue(entry.entryId) },
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp,
                        ),
                    ) {
                        Text("Jump", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = { onForkFromEntry(entry.entryId) },
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp,
                        ),
                    ) {
                        Text("Fork", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun treeEntryIcon(entryType: String): ImageVector {
    return when (entryType) {
        "message" -> Icons.Default.Description
        "model_change" -> Icons.Default.Refresh
        "thinking_level_change" -> Icons.Default.Menu
        else -> Icons.Default.PlayArrow
    }
}

@Suppress("ReturnCount")
private fun computeDepthMap(entries: List<SessionTreeEntry>): Map<String, Int> {
    val byId = entries.associateBy { it.entryId }
    val memo = mutableMapOf<String, Int>()

    fun depth(
        entryId: String,
        stack: MutableSet<String>,
    ): Int {
        memo[entryId]?.let { return it }
        if (!stack.add(entryId)) {
            return 0
        }

        val entry = byId[entryId]
        val resolvedDepth =
            when {
                entry == null -> 0
                entry.parentId == null -> 0
                else -> depth(entry.parentId, stack) + 1
            }

        stack.remove(entryId)
        memo[entryId] = resolvedDepth
        return resolvedDepth
    }

    entries.forEach { entry -> depth(entry.entryId, mutableSetOf()) }
    return memo
}

private fun computeChildCountMap(entries: List<SessionTreeEntry>): Map<String, Int> {
    return entries
        .groupingBy { it.parentId }
        .eachCount()
        .mapNotNull { (parentId, count) ->
            parentId?.let { it to count }
        }.toMap()
}

private fun truncatePath(path: String): String {
    if (path.length <= SESSION_PATH_DISPLAY_LENGTH) {
        return path
    }
    val head = SESSION_PATH_DISPLAY_LENGTH / 2
    val tail = SESSION_PATH_DISPLAY_LENGTH - head - 1
    return "${path.take(head)}…${path.takeLast(tail)}"
}
