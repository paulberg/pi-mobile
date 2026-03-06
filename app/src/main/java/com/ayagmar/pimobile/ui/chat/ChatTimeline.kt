package com.ayagmar.pimobile.ui.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ayagmar.pimobile.chat.ChatTimelineItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val CHAT_RUN_PROGRESS_TAG = "chat_run_progress"
internal const val CHAT_JUMP_TO_LATEST_TAG = "chat_jump_to_latest"
private const val AUTO_SCROLL_BOTTOM_THRESHOLD_ITEMS = 2
private const val AUTO_SCROLL_ANIMATION_MIN_INTERVAL_MS = 120L
private const val STREAMING_AUTO_SCROLL_CHECK_INTERVAL_MS = 90L
private const val CHAT_TIMELINE_BOTTOM_ANCHOR_KEY = "chat_timeline_bottom_anchor"
internal const val RUN_PROGRESS_TICK_MS = 1_000L

@Composable
internal fun LiveRunProgressIndicator(
    phase: LiveRunPhase,
    elapsedSeconds: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .testTag(CHAT_RUN_PROGRESS_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text =
                if (phase == LiveRunPhase.WORKING) {
                    "Working · waiting for activity · ${formatRunElapsed(elapsedSeconds)}"
                } else {
                    "Working · ${phase.label} · ${formatRunElapsed(elapsedSeconds)}"
                },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InlineRunProgressCard(
    phase: LiveRunPhase,
    elapsedSeconds: Long,
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
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Assistant",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LiveRunProgressIndicator(
                    phase = phase,
                    elapsedSeconds = elapsedSeconds,
                    modifier = Modifier,
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
internal fun ChatBody(
    isLoading: Boolean,
    timeline: List<ChatTimelineItem>,
    hasOlderMessages: Boolean,
    hiddenHistoryCount: Int,
    expandedToolArguments: Set<String>,
    isRunActive: Boolean,
    runPhase: LiveRunPhase,
    runElapsedSeconds: Long,
    callbacks: ChatCallbacks,
) {
    val hasStreamingTimelineItem =
        remember(timeline) {
            timeline.any { item ->
                when (item) {
                    is ChatTimelineItem.Assistant -> item.isStreaming
                    is ChatTimelineItem.Tool -> item.isStreaming
                    is ChatTimelineItem.User -> false
                }
            }
        }
    val showInlineRunProgress = isRunActive && !hasStreamingTimelineItem

    if (isLoading) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
    } else if (timeline.isEmpty() && !showInlineRunProgress) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Send a prompt to start a conversation with Pi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        ChatTimeline(
            timeline = timeline,
            hasOlderMessages = hasOlderMessages,
            hiddenHistoryCount = hiddenHistoryCount,
            expandedToolArguments = expandedToolArguments,
            isRunActive = isRunActive,
            showInlineRunProgress = showInlineRunProgress,
            runPhase = runPhase,
            runElapsedSeconds = runElapsedSeconds,
            onLoadOlderMessages = callbacks.onLoadOlderMessages,
            onToggleToolExpansion = callbacks.onToggleToolExpansion,
            onToggleThinkingExpansion = callbacks.onToggleThinkingExpansion,
            onToggleDiffExpansion = callbacks.onToggleDiffExpansion,
            onToggleToolArgumentsExpansion = callbacks.onToggleToolArgumentsExpansion,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun ChatTimeline(
    timeline: List<ChatTimelineItem>,
    hasOlderMessages: Boolean,
    hiddenHistoryCount: Int,
    expandedToolArguments: Set<String>,
    isRunActive: Boolean,
    showInlineRunProgress: Boolean,
    runPhase: LiveRunPhase,
    runElapsedSeconds: Long,
    onLoadOlderMessages: () -> Unit,
    onToggleToolExpansion: (String) -> Unit,
    onToggleThinkingExpansion: (String) -> Unit,
    onToggleDiffExpansion: (String) -> Unit,
    onToggleToolArgumentsExpansion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val autoScrollUi =
        rememberTimelineAutoScrollUi(
            listState = listState,
            timeline = timeline,
            showInlineRunProgress = showInlineRunProgress,
            isRunActive = isRunActive,
        )

    Box(modifier = modifier.fillMaxWidth()) {
        ChatTimelineList(
            listState = listState,
            timeline = timeline,
            hasOlderMessages = hasOlderMessages,
            hiddenHistoryCount = hiddenHistoryCount,
            expandedToolArguments = expandedToolArguments,
            showInlineRunProgress = showInlineRunProgress,
            runPhase = runPhase,
            runElapsedSeconds = runElapsedSeconds,
            onLoadOlderMessages = onLoadOlderMessages,
            onToggleToolExpansion = onToggleToolExpansion,
            onToggleThinkingExpansion = onToggleThinkingExpansion,
            onToggleDiffExpansion = onToggleDiffExpansion,
            onToggleToolArgumentsExpansion = onToggleToolArgumentsExpansion,
            onPreviewImage = { uri ->
                previewImageUri = uri
            },
        )

        AnimatedVisibility(
            visible = autoScrollUi.shouldShowJumpToLatest,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        ) {
            OutlinedButton(
                onClick = autoScrollUi.onJumpToLatest,
                modifier = Modifier.testTag(CHAT_JUMP_TO_LATEST_TAG),
            ) {
                Text("Jump to latest")
            }
        }

        previewImageUri?.let { uri ->
            ImagePreviewDialog(
                uriString = uri,
                onDismiss = { previewImageUri = null },
            )
        }
    }
}

private data class TimelineAutoScrollUi(
    val shouldShowJumpToLatest: Boolean,
    val onJumpToLatest: () -> Unit,
)

@Suppress("LongMethod")
@Composable
private fun rememberTimelineAutoScrollUi(
    listState: LazyListState,
    timeline: List<ChatTimelineItem>,
    showInlineRunProgress: Boolean,
    isRunActive: Boolean,
): TimelineAutoScrollUi {
    val coroutineScope = rememberCoroutineScope()
    val contentItemsCount = timeline.size + if (showInlineRunProgress) 1 else 0
    val renderedItemsCount = contentItemsCount + 1 // includes bottom anchor item
    val latestTimelineActivityKey =
        remember(timeline, showInlineRunProgress) {
            buildLatestTimelineActivityKey(
                timeline = timeline,
                showInlineRunProgress = showInlineRunProgress,
            )
        }
    val isNearBottom = rememberIsNearBottom(listState)
    var shouldStickToBottom by
        rememberShouldStickToBottom(
            listState = listState,
            isNearBottom = isNearBottom,
            renderedItemsCount = renderedItemsCount,
        )

    val shouldAutoScrollToBottom = shouldStickToBottom || isNearBottom

    RunActivityAutoScroll(
        listState = listState,
        latestTimelineActivityKey = latestTimelineActivityKey,
        renderedItemsCount = renderedItemsCount,
        shouldAutoScrollToBottom = shouldAutoScrollToBottom,
    )

    RunStreamingAutoScroll(
        listState = listState,
        isRunActive = isRunActive,
        shouldAutoScrollToBottom = shouldAutoScrollToBottom,
        renderedItemsCount = renderedItemsCount,
    )

    return TimelineAutoScrollUi(
        shouldShowJumpToLatest = renderedItemsCount > 1 && !shouldAutoScrollToBottom,
        onJumpToLatest = {
            shouldStickToBottom = true
            coroutineScope.launch {
                listState.animateScrollToItem(renderedItemsCount - 1)
            }
        },
    )
}

private fun buildLatestTimelineActivityKey(
    timeline: List<ChatTimelineItem>,
    showInlineRunProgress: Boolean,
): String {
    val tail = timeline.lastOrNull()
    val tailKey =
        when (tail) {
            is ChatTimelineItem.Assistant -> {
                "assistant:${tail.id}:${tail.text.length}:${tail.thinking?.length ?: 0}:${tail.isStreaming}"
            }

            is ChatTimelineItem.Tool -> {
                "tool:${tail.id}:${tail.output.length}:${tail.isStreaming}:${tail.isCollapsed}"
            }

            is ChatTimelineItem.User -> "user:${tail.id}:${tail.text.length}:${tail.imageCount}"
            null -> "empty"
        }

    return "$tailKey:inline=$showInlineRunProgress:count=${timeline.size}"
}

@Composable
private fun rememberIsNearBottom(listState: LazyListState): Boolean {
    val isNearBottom by
        remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val lastItemIndex = layoutInfo.totalItemsCount - 1

                lastItemIndex <= 0 || lastVisibleIndex >= lastItemIndex - AUTO_SCROLL_BOTTOM_THRESHOLD_ITEMS
            }
        }

    return isNearBottom
}

@Composable
private fun rememberShouldStickToBottom(
    listState: LazyListState,
    isNearBottom: Boolean,
    renderedItemsCount: Int,
): MutableState<Boolean> {
    val shouldStickToBottom = remember { mutableStateOf(true) }

    LaunchedEffect(listState.isScrollInProgress, isNearBottom, renderedItemsCount) {
        if (renderedItemsCount <= 1) {
            shouldStickToBottom.value = true
            return@LaunchedEffect
        }

        if (listState.isScrollInProgress) {
            shouldStickToBottom.value = isNearBottom
        }
    }

    return shouldStickToBottom
}

@Composable
private fun RunActivityAutoScroll(
    listState: LazyListState,
    latestTimelineActivityKey: String,
    renderedItemsCount: Int,
    shouldAutoScrollToBottom: Boolean,
) {
    var lastAutoScrollAtMs by remember { mutableStateOf(0L) }

    LaunchedEffect(latestTimelineActivityKey, renderedItemsCount, shouldAutoScrollToBottom) {
        if (renderedItemsCount <= 0 || !shouldAutoScrollToBottom) {
            return@LaunchedEffect
        }

        val targetIndex = renderedItemsCount - 1
        val now = System.currentTimeMillis()

        when {
            lastAutoScrollAtMs == 0L -> listState.scrollToItem(targetIndex)
            now - lastAutoScrollAtMs >= AUTO_SCROLL_ANIMATION_MIN_INTERVAL_MS ->
                listState.animateScrollToItem(targetIndex)

            else -> listState.scrollToItem(targetIndex)
        }

        lastAutoScrollAtMs = now
    }
}

@Composable
private fun RunStreamingAutoScroll(
    listState: LazyListState,
    isRunActive: Boolean,
    shouldAutoScrollToBottom: Boolean,
    renderedItemsCount: Int,
) {
    LaunchedEffect(
        isRunActive,
        shouldAutoScrollToBottom,
        renderedItemsCount,
        listState.isScrollInProgress,
    ) {
        val shouldRunStreamingAutoScrollLoop =
            isRunActive &&
                shouldAutoScrollToBottom &&
                renderedItemsCount > 0 &&
                !listState.isScrollInProgress
        if (!shouldRunStreamingAutoScrollLoop) {
            return@LaunchedEffect
        }

        while (true) {
            val targetIndex = renderedItemsCount - 1
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisibleIndex < targetIndex) {
                listState.scrollToItem(targetIndex)
            }
            delay(STREAMING_AUTO_SCROLL_CHECK_INTERVAL_MS)
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ChatTimelineList(
    listState: LazyListState,
    timeline: List<ChatTimelineItem>,
    hasOlderMessages: Boolean,
    hiddenHistoryCount: Int,
    expandedToolArguments: Set<String>,
    showInlineRunProgress: Boolean,
    runPhase: LiveRunPhase,
    runElapsedSeconds: Long,
    onLoadOlderMessages: () -> Unit,
    onToggleToolExpansion: (String) -> Unit,
    onToggleThinkingExpansion: (String) -> Unit,
    onToggleDiffExpansion: (String) -> Unit,
    onToggleToolArgumentsExpansion: (String) -> Unit,
    onPreviewImage: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasOlderMessages) {
            item(key = "load-older-messages") {
                TextButton(
                    onClick = onLoadOlderMessages,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Load older messages ($hiddenHistoryCount hidden)")
                }
            }
        }

        items(items = timeline, key = { item -> item.id }) { item ->
            ChatTimelineRow(
                item = item,
                expandedToolArguments = expandedToolArguments,
                onToggleToolExpansion = onToggleToolExpansion,
                onToggleThinkingExpansion = onToggleThinkingExpansion,
                onToggleDiffExpansion = onToggleDiffExpansion,
                onToggleToolArgumentsExpansion = onToggleToolArgumentsExpansion,
                onPreviewImage = onPreviewImage,
            )
        }

        if (showInlineRunProgress) {
            item(key = "inline-run-progress") {
                InlineRunProgressCard(
                    phase = runPhase,
                    elapsedSeconds = runElapsedSeconds,
                )
            }
        }

        item(key = CHAT_TIMELINE_BOTTOM_ANCHOR_KEY) {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    uriString: String,
    onDismiss: () -> Unit,
) {
    val uri = remember(uriString) { Uri.parse(uriString) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Image preview",
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentScale = ContentScale.Fit,
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close image preview",
                    tint = Color.White,
                )
            }
        }
    }
}
