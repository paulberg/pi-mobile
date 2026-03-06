package com.ayagmar.pimobile.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayagmar.pimobile.chat.ExtensionWidget
import com.ayagmar.pimobile.corenet.ConnectionState
import com.ayagmar.pimobile.corerpc.SessionStats
import com.ayagmar.pimobile.sessions.ModelInfo

private const val STATUS_VALUE_MAX_LENGTH = 180
private const val EXTENSION_STATUS_PILL_MAX_LENGTH = 56
private const val MAX_COMPACT_EXTENSION_STATUS_ITEMS = 2
private const val CONTEXT_PERCENT_FACTOR = 100.0
private const val CONTEXT_PERCENT_MIN = 0
private const val CONTEXT_PERCENT_MAX = 100
private val THINKING_LEVEL_OPTIONS = listOf("off", "minimal", "low", "medium", "high", "xhigh")
private val LOW_SIGNAL_STATUS_TOKENS =
    setOf(
        "idle",
        "ready",
        "ok",
        "connected",
        "none",
        "no updates",
        "synced",
    )

internal data class ExtensionStatusEntry(
    val key: String,
    val value: String,
    val isLowSignal: Boolean,
    val isChanged: Boolean,
)

internal data class ExtensionStatusPresentation(
    val visibleEntries: List<ExtensionStatusEntry>,
    val hiddenCount: Int,
    val activeCount: Int,
    val quietCount: Int,
    val changedCount: Int,
)

@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
internal fun ChatHeader(
    isRunActive: Boolean,
    isSyncingSession: Boolean,
    sessionCoherencyWarning: String?,
    extensionTitle: String?,
    connectionState: ConnectionState,
    currentModel: ModelInfo?,
    thinkingLevel: String?,
    contextUsageLabel: String,
    errorMessage: String?,
    callbacks: ChatCallbacks,
) {
    val isCompact = isRunActive
    var showSecondaryActionsMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Top row: Title and minimal actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title = extensionTitle ?: "Chat"
                Text(
                    text = title,
                    style =
                        if (isCompact) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                )

                // Subtle connection status
                if (!isCompact && extensionTitle == null) {
                    val statusText =
                        when (connectionState) {
                            ConnectionState.CONNECTED -> "●"
                            ConnectionState.CONNECTING -> "○"
                            else -> "○"
                        }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            when (connectionState) {
                                ConnectionState.CONNECTED ->
                                    MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            },
                    )
                }
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSyncingSession) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = callbacks.onSyncNow) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync now",
                        )
                    }
                }

                IconButton(onClick = { showSecondaryActionsMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                    )
                }

                DropdownMenu(
                    expanded = showSecondaryActionsMenu,
                    onDismissRequest = { showSecondaryActionsMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Tree") },
                        onClick = {
                            showSecondaryActionsMenu = false
                            callbacks.onShowTreeSheet()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Bash") },
                        onClick = {
                            showSecondaryActionsMenu = false
                            callbacks.onShowBashDialog()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Stats") },
                        onClick = {
                            showSecondaryActionsMenu = false
                            callbacks.onShowStatsSheet()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Compact now") },
                        onClick = {
                            showSecondaryActionsMenu = false
                            callbacks.onCompactSession()
                        },
                    )
                }
            }
        }

        sessionCoherencyWarning?.let { warning ->
            Text(
                text = warning,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Compact model/thinking controls
        ModelThinkingControls(
            currentModel = currentModel,
            thinkingLevel = thinkingLevel,
            onSetThinkingLevel = callbacks.onSetThinkingLevel,
            onShowModelPicker = callbacks.onShowModelPicker,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = callbacks.onShowStatsSheet,
                label = { Text(contextUsageLabel) },
            )
            TextButton(onClick = callbacks.onRefreshStats) {
                Text("Refresh")
            }
        }

        // Error message if any
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun ModelThinkingControls(
    currentModel: ModelInfo?,
    thinkingLevel: String?,
    onSetThinkingLevel: (String) -> Unit,
    onShowModelPicker: () -> Unit,
) {
    var showThinkingMenu by remember { mutableStateOf(false) }

    val modelText = currentModel?.name ?: "Select model"
    val thinkingText = thinkingLevel?.uppercase() ?: "OFF"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onShowModelPicker,
            modifier = Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    horizontal = 12.dp,
                    vertical = 6.dp,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = modelText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }

        // Thinking level selector
        Box(modifier = Modifier.wrapContentWidth()) {
            OutlinedButton(
                onClick = { showThinkingMenu = true },
                contentPadding =
                    PaddingValues(
                        horizontal = 12.dp,
                        vertical = 6.dp,
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = thinkingText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            DropdownMenu(
                expanded = showThinkingMenu,
                onDismissRequest = { showThinkingMenu = false },
            ) {
                THINKING_LEVEL_OPTIONS.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level.replaceFirstChar { it.uppercase() }) },
                        onClick = {
                            onSetThinkingLevel(level)
                            showThinkingMenu = false
                        },
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
internal fun ExtensionStatusStrip(statuses: Map<String, String>) {
    if (statuses.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    var previousStatuses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var hasPreviousSnapshot by remember { mutableStateOf(false) }

    val comparisonSnapshot =
        if (hasPreviousSnapshot) {
            previousStatuses
        } else {
            statuses.mapValues { (_, value) -> value.trim() }
        }

    val presentation =
        remember(statuses, comparisonSnapshot, expanded) {
            buildExtensionStatusPresentation(
                statuses = statuses,
                previousStatuses = comparisonSnapshot,
                expanded = expanded,
            )
        }

    LaunchedEffect(statuses) {
        previousStatuses = statuses.mapValues { (_, value) -> value.trim() }
        hasPreviousSnapshot = true
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Extension status",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${presentation.activeCount} active · ${presentation.quietCount} quiet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }

            if (!expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val first = presentation.visibleEntries.firstOrNull()
                    if (first != null) {
                        StatusPill(entry = first)
                    }
                    if (presentation.hiddenCount > 0) {
                        Text(
                            text = "+${presentation.hiddenCount} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presentation.visibleEntries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = if (entry.isChanged) "•" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                text = "${entry.key}: ${entry.value.take(STATUS_VALUE_MAX_LENGTH)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (presentation.changedCount > 0) {
                Text(
                    text = "${presentation.changedCount} update(s) since last refresh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(entry: ExtensionStatusEntry) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color =
            if (entry.isLowSignal) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
    ) {
        Text(
            text = "${entry.key}: ${entry.value.take(EXTENSION_STATUS_PILL_MAX_LENGTH)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun ExtensionWidgets(
    widgets: Map<String, ExtensionWidget>,
    placement: String,
) {
    val matchingWidgets = widgets.values.filter { it.placement == placement }

    matchingWidgets.forEach { widget ->
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                widget.lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun formatContextUsageLabel(
    stats: SessionStats?,
    currentModel: ModelInfo?,
): String {
    val statsSnapshot = stats ?: return "Ctx --"

    val explicitUsedTokens = statsSnapshot.contextUsedTokens?.coerceAtLeast(0L)
    val explicitWindowTokens = statsSnapshot.contextWindowTokens?.takeIf { it > 0 }
    val explicitPercent = statsSnapshot.contextUsagePercent?.coerceIn(CONTEXT_PERCENT_MIN, CONTEXT_PERCENT_MAX)

    val fallbackUsedTokens = (statsSnapshot.inputTokens + statsSnapshot.outputTokens).coerceAtLeast(0L)
    val fallbackWindowTokens = currentModel?.contextWindow?.takeIf { it > 0 }?.toLong()
    val approximateWindowTokens = explicitWindowTokens ?: fallbackWindowTokens

    val contextUsage =
        buildContextUsageCoreLabel(
            explicitUsedTokens = explicitUsedTokens,
            explicitWindowTokens = explicitWindowTokens,
            explicitPercent = explicitPercent,
            fallbackUsedTokens = fallbackUsedTokens,
            fallbackWindowTokens = approximateWindowTokens,
        )

    val compactionLabel =
        statsSnapshot.compactionCount
            .takeIf { it > 0 }
            ?.let { count -> " · C$count" }
            .orEmpty()

    val costLabel =
        statsSnapshot.totalCost
            .takeIf { it > 0.0 }
            ?.let { cost -> " · ${formatCompactCost(cost)}" }
            .orEmpty()

    return contextUsage + compactionLabel + costLabel
}

private fun buildContextUsageCoreLabel(
    explicitUsedTokens: Long?,
    explicitWindowTokens: Long?,
    explicitPercent: Int?,
    fallbackUsedTokens: Long,
    fallbackWindowTokens: Long?,
): String {
    val explicitPercentLabel =
        when {
            explicitPercent == null -> null
            explicitUsedTokens != null && explicitWindowTokens != null ->
                formatExactContextUsage(
                    percent = explicitPercent,
                    usedTokens = explicitUsedTokens,
                    windowTokens = explicitWindowTokens,
                )

            else -> "Ctx $explicitPercent%"
        }

    if (explicitPercentLabel != null) {
        return explicitPercentLabel
    }

    val explicitUsageLabel =
        when {
            explicitUsedTokens != null && explicitWindowTokens != null -> {
                val computedPercent = computeContextPercent(explicitUsedTokens, explicitWindowTokens)
                formatExactContextUsage(
                    percent = computedPercent,
                    usedTokens = explicitUsedTokens,
                    windowTokens = explicitWindowTokens,
                )
            }

            explicitUsedTokens != null -> "Ctx ${formatNumber(explicitUsedTokens)}"
            fallbackWindowTokens != null ->
                "Ctx ~${formatNumber(fallbackUsedTokens)}/${formatNumber(fallbackWindowTokens)}"

            else -> "Ctx ~${formatNumber(fallbackUsedTokens)}"
        }

    return explicitUsageLabel
}

private fun computeContextPercent(
    usedTokens: Long,
    windowTokens: Long,
): Int {
    return ((usedTokens * CONTEXT_PERCENT_FACTOR) / windowTokens.toDouble())
        .toInt()
        .coerceIn(CONTEXT_PERCENT_MIN, CONTEXT_PERCENT_MAX)
}

private fun formatExactContextUsage(
    percent: Int,
    usedTokens: Long,
    windowTokens: Long,
): String {
    return "Ctx $percent% · ${formatNumber(usedTokens)}/${formatNumber(windowTokens)}"
}

@Suppress("MagicNumber")
private fun formatCompactCost(value: Double): String {
    val pattern =
        when {
            value >= 1.0 -> "$%.2f"
            value >= 0.1 -> "$%.3f"
            else -> "$%.4f"
        }
    return String.format(java.util.Locale.US, pattern, value)
}

internal fun buildExtensionStatusPresentation(
    statuses: Map<String, String>,
    previousStatuses: Map<String, String>,
    expanded: Boolean,
): ExtensionStatusPresentation {
    if (statuses.isEmpty()) {
        return ExtensionStatusPresentation(
            visibleEntries = emptyList(),
            hiddenCount = 0,
            activeCount = 0,
            quietCount = 0,
            changedCount = 0,
        )
    }

    val entries =
        statuses
            .toSortedMap()
            .map { (key, rawValue) ->
                val value = rawValue.trim().ifEmpty { "(empty)" }
                ExtensionStatusEntry(
                    key = key,
                    value = value,
                    isLowSignal = isLowSignalExtensionStatus(value),
                    isChanged = previousStatuses[key] != value,
                )
            }

    val changed = entries.filter { it.isChanged }
    val active = entries.filterNot { it.isLowSignal }
    val quietCount = entries.size - active.size

    val compactCandidates =
        when {
            changed.isNotEmpty() -> changed
            active.isNotEmpty() -> active
            else -> entries
        }

    val visibleEntries = if (expanded) entries else compactCandidates.take(MAX_COMPACT_EXTENSION_STATUS_ITEMS)

    return ExtensionStatusPresentation(
        visibleEntries = visibleEntries,
        hiddenCount = if (expanded) 0 else (entries.size - visibleEntries.size).coerceAtLeast(0),
        activeCount = active.size,
        quietCount = quietCount,
        changedCount = changed.size,
    )
}

internal fun isLowSignalExtensionStatus(value: String): Boolean {
    val normalized = value.trim().lowercase()
    return LOW_SIGNAL_STATUS_TOKENS.any { token -> normalized.contains(token) }
}
