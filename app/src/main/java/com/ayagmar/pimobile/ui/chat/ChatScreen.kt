package com.ayagmar.pimobile.ui.chat

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayagmar.pimobile.chat.ChatTimelineItem
import com.ayagmar.pimobile.chat.ChatUiState
import com.ayagmar.pimobile.chat.ChatViewModel
import com.ayagmar.pimobile.chat.ChatViewModelFactory
import com.ayagmar.pimobile.chat.ImageEncoder
import com.ayagmar.pimobile.chat.PendingImage
import com.ayagmar.pimobile.corerpc.AvailableModel
import com.ayagmar.pimobile.perf.StreamingFrameMetrics
import com.ayagmar.pimobile.sessions.SessionController
import com.ayagmar.pimobile.sessions.SlashCommandInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun readTextFromUri(
    context: android.content.Context,
    uri: Uri,
): String? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
            reader?.readText()
        }
    }.getOrNull()
}

private fun resolveDocumentDisplayName(
    context: android.content.Context,
    uri: Uri,
): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }

            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex == -1) {
                null
            } else {
                cursor.getString(nameIndex)
            }
        }
    }.getOrNull()
}

private const val STREAMING_FRAME_LOG_TAG = "StreamingFrameMetrics"

internal data class ChatCallbacks(
    val onToggleToolExpansion: (String) -> Unit,
    val onToggleThinkingExpansion: (String) -> Unit,
    val onToggleDiffExpansion: (String) -> Unit,
    val onToggleToolArgumentsExpansion: (String) -> Unit,
    val onLoadOlderMessages: () -> Unit,
    val onInputTextChanged: (String) -> Unit,
    val onSendPrompt: () -> Unit,
    val onAbort: () -> Unit,
    val onSteer: (String) -> Unit,
    val onFollowUp: (String) -> Unit,
    val onRemovePendingQueueItem: (String) -> Unit,
    val onClearPendingQueueItems: () -> Unit,
    val onSetThinkingLevel: (String) -> Unit,
    val onAbortRetry: () -> Unit,
    val onSendExtensionUiResponse: (String, String?, Boolean?, Boolean) -> Unit,
    val onDismissExtensionRequest: () -> Unit,
    val onClearNotification: (Int) -> Unit,
    val onShowCommandPalette: () -> Unit,
    val onHideCommandPalette: () -> Unit,
    val onCommandsQueryChanged: (String) -> Unit,
    val onCommandSelected: (SlashCommandInfo) -> Unit,
    val onCopyLastResponse: () -> Unit,
    // Bash callbacks
    val onShowBashDialog: () -> Unit,
    val onHideBashDialog: () -> Unit,
    val onBashCommandChanged: (String) -> Unit,
    val onExecuteBash: () -> Unit,
    val onAbortBash: () -> Unit,
    val onSelectBashHistory: (String) -> Unit,
    // Session stats callbacks
    val onShowStatsSheet: () -> Unit,
    val onHideStatsSheet: () -> Unit,
    val onRefreshStats: () -> Unit,
    // Model picker callbacks
    val onShowModelPicker: () -> Unit,
    val onHideModelPicker: () -> Unit,
    val onModelsQueryChanged: (String) -> Unit,
    val onSelectModel: (AvailableModel) -> Unit,
    val onSyncNow: () -> Unit,
    val onCompactSession: () -> Unit,
    // Tree navigation callbacks
    val onShowTreeSheet: () -> Unit,
    val onHideTreeSheet: () -> Unit,
    val onForkFromTreeEntry: (String) -> Unit,
    val onJumpAndContinueFromTreeEntry: (String) -> Unit,
    val onTreeFilterChanged: (String) -> Unit,
    // Image attachment callbacks
    val onAddImage: (PendingImage) -> Unit,
    val onRemoveImage: (Int) -> Unit,
)

@Suppress("LongMethod")
@Composable
fun ChatRoute(
    sessionController: SessionController,
    showExtensionStatusStrip: Boolean,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val routeScope = rememberCoroutineScope()
    val imageEncoder = remember { ImageEncoder(context) }
    val factory =
        remember(sessionController, imageEncoder) {
            ChatViewModelFactory(
                sessionController = sessionController,
                imageEncoder = imageEncoder,
            )
        }
    val chatViewModel: ChatViewModel = viewModel(factory = factory)
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val importSessionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            routeScope.launch {
                val fileName = resolveDocumentDisplayName(context, uri) ?: "imported-session.jsonl"
                val jsonlContent =
                    withContext(Dispatchers.IO) {
                        readTextFromUri(context, uri)
                    }

                if (jsonlContent == null) {
                    chatViewModel.onImportSessionReadFailed("Failed to read selected JSONL file")
                    return@launch
                }

                chatViewModel.importSessionJsonl(
                    fileName = fileName,
                    jsonlContent = jsonlContent,
                )
            }
        }

    LaunchedEffect(uiState.pendingClipboardText) {
        val pendingText = uiState.pendingClipboardText ?: return@LaunchedEffect
        val copied =
            runCatching {
                clipboardManager.setText(AnnotatedString(pendingText))
            }.isSuccess
        chatViewModel.consumePendingClipboardText(copySucceeded = copied)
    }

    LaunchedEffect(uiState.pendingImportRequestToken) {
        uiState.pendingImportRequestToken ?: return@LaunchedEffect
        chatViewModel.consumePendingImportRequest()
        importSessionLauncher.launch(arrayOf("*/*"))
    }

    val callbacks =
        remember(chatViewModel) {
            ChatCallbacks(
                onToggleToolExpansion = chatViewModel::toggleToolExpansion,
                onToggleThinkingExpansion = chatViewModel::toggleThinkingExpansion,
                onToggleDiffExpansion = chatViewModel::toggleDiffExpansion,
                onToggleToolArgumentsExpansion = chatViewModel::toggleToolArgumentsExpansion,
                onLoadOlderMessages = chatViewModel::loadOlderMessages,
                onInputTextChanged = chatViewModel::onInputTextChanged,
                onSendPrompt = chatViewModel::sendPrompt,
                onAbort = chatViewModel::abort,
                onSteer = chatViewModel::steer,
                onFollowUp = chatViewModel::followUp,
                onRemovePendingQueueItem = chatViewModel::removePendingQueueItem,
                onClearPendingQueueItems = chatViewModel::clearPendingQueueItems,
                onSetThinkingLevel = chatViewModel::setThinkingLevel,
                onAbortRetry = chatViewModel::abortRetry,
                onSendExtensionUiResponse = chatViewModel::sendExtensionUiResponse,
                onDismissExtensionRequest = chatViewModel::dismissExtensionRequest,
                onClearNotification = chatViewModel::clearNotification,
                onShowCommandPalette = chatViewModel::showCommandPalette,
                onHideCommandPalette = chatViewModel::hideCommandPalette,
                onCommandsQueryChanged = chatViewModel::onCommandsQueryChanged,
                onCommandSelected = chatViewModel::onCommandSelected,
                onCopyLastResponse = chatViewModel::copyLastResponse,
                onShowBashDialog = chatViewModel::showBashDialog,
                onHideBashDialog = chatViewModel::hideBashDialog,
                onBashCommandChanged = chatViewModel::onBashCommandChanged,
                onExecuteBash = chatViewModel::executeBash,
                onAbortBash = chatViewModel::abortBash,
                onSelectBashHistory = chatViewModel::selectBashHistoryItem,
                onShowStatsSheet = chatViewModel::showStatsSheet,
                onHideStatsSheet = chatViewModel::hideStatsSheet,
                onRefreshStats = chatViewModel::refreshSessionStats,
                onShowModelPicker = chatViewModel::showModelPicker,
                onHideModelPicker = chatViewModel::hideModelPicker,
                onModelsQueryChanged = chatViewModel::onModelsQueryChanged,
                onSelectModel = chatViewModel::selectModel,
                onSyncNow = chatViewModel::syncNow,
                onCompactSession = chatViewModel::compactNow,
                onShowTreeSheet = chatViewModel::showTreeSheet,
                onHideTreeSheet = chatViewModel::hideTreeSheet,
                onForkFromTreeEntry = chatViewModel::forkFromTreeEntry,
                onJumpAndContinueFromTreeEntry = chatViewModel::jumpAndContinueFromTreeEntry,
                onTreeFilterChanged = chatViewModel::setTreeFilter,
                onAddImage = chatViewModel::addImage,
                onRemoveImage = chatViewModel::removeImage,
            )
        }

    ChatScreen(
        state = uiState,
        callbacks = callbacks,
        showExtensionStatusStrip = showExtensionStatusStrip,
    )
}

@Suppress("LongMethod")
@Composable
private fun ChatScreen(
    state: ChatUiState,
    callbacks: ChatCallbacks,
    showExtensionStatusStrip: Boolean,
) {
    StreamingFrameMetrics(
        isStreaming = state.isStreaming,
        onJankDetected = { droppedFrame ->
            Log.d(
                STREAMING_FRAME_LOG_TAG,
                "jank severity=${droppedFrame.severity} " +
                    "frame=${droppedFrame.frameTimeMs}ms dropped=${droppedFrame.expectedFrames}",
            )
        },
    )

    ChatScreenContent(
        state = state,
        callbacks = callbacks,
        showExtensionStatusStrip = showExtensionStatusStrip,
    )

    ExtensionUiDialogs(
        request = state.activeExtensionRequest,
        onSendResponse = callbacks.onSendExtensionUiResponse,
        onDismiss = callbacks.onDismissExtensionRequest,
    )

    NotificationsDisplay(
        notifications = state.notifications,
        onClear = callbacks.onClearNotification,
    )

    CommandPalette(
        isVisible = state.isCommandPaletteVisible,
        commands = state.commands,
        query = state.commandsQuery,
        isLoading = state.isLoadingCommands,
        onQueryChange = callbacks.onCommandsQueryChanged,
        onCommandSelected = callbacks.onCommandSelected,
        onDismiss = callbacks.onHideCommandPalette,
    )

    BashDialog(
        isVisible = state.isBashDialogVisible,
        command = state.bashCommand,
        output = state.bashOutput,
        exitCode = state.bashExitCode,
        isExecuting = state.isBashExecuting,
        wasTruncated = state.bashWasTruncated,
        fullLogPath = state.bashFullLogPath,
        history = state.bashHistory,
        onCommandChange = callbacks.onBashCommandChanged,
        onExecute = callbacks.onExecuteBash,
        onAbort = callbacks.onAbortBash,
        onSelectHistory = callbacks.onSelectBashHistory,
        onDismiss = callbacks.onHideBashDialog,
    )

    SessionStatsSheet(
        isVisible = state.isStatsSheetVisible,
        stats = state.sessionStats,
        isLoading = state.isLoadingStats,
        onRefresh = callbacks.onRefreshStats,
        onDismiss = callbacks.onHideStatsSheet,
    )

    ModelPickerSheet(
        isVisible = state.isModelPickerVisible,
        models = state.availableModels,
        currentModel = state.currentModel,
        query = state.modelsQuery,
        isLoading = state.isLoadingModels,
        onQueryChange = callbacks.onModelsQueryChanged,
        onSelectModel = callbacks.onSelectModel,
        onDismiss = callbacks.onHideModelPicker,
    )

    TreeNavigationSheet(
        isVisible = state.isTreeSheetVisible,
        tree = state.sessionTree,
        selectedFilter = state.treeFilter,
        isLoading = state.isLoadingTree,
        errorMessage = state.treeErrorMessage,
        onFilterChange = callbacks.onTreeFilterChanged,
        onForkFromEntry = callbacks.onForkFromTreeEntry,
        onJumpAndContinue = callbacks.onJumpAndContinueFromTreeEntry,
        onDismiss = callbacks.onHideTreeSheet,
    )
}

@Suppress("LongMethod")
@Composable
private fun ChatScreenContent(
    state: ChatUiState,
    callbacks: ChatCallbacks,
    showExtensionStatusStrip: Boolean,
) {
    val hasStreamingTimelineItem =
        remember(state.timeline) {
            state.timeline.any { item ->
                when (item) {
                    is ChatTimelineItem.Assistant -> item.isStreaming
                    is ChatTimelineItem.Tool -> item.isStreaming
                    is ChatTimelineItem.User -> false
                }
            }
        }
    val isRunActive = state.isStreaming || state.isRetrying || hasStreamingTimelineItem

    var runStartedAtMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(isRunActive) {
        if (isRunActive) {
            if (runStartedAtMs == null) {
                runStartedAtMs = System.currentTimeMillis()
            }
        } else {
            runStartedAtMs = null
        }
    }

    val elapsedSeconds by
        produceState(
            initialValue = 0L,
            key1 = isRunActive,
            key2 = runStartedAtMs,
        ) {
            val startedAt = runStartedAtMs
            if (!isRunActive || startedAt == null) {
                value = 0L
                return@produceState
            }

            while (true) {
                value = ((System.currentTimeMillis() - startedAt).coerceAtLeast(0L)) / RUN_PROGRESS_TICK_MS
                delay(RUN_PROGRESS_TICK_MS)
            }
        }

    val runPhase =
        remember(state.isRetrying, state.timeline) {
            inferLiveRunPhase(
                isRetrying = state.isRetrying,
                timeline = state.timeline,
            )
        }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(if (isRunActive) 8.dp else 12.dp),
    ) {
        ChatHeader(
            isRunActive = isRunActive,
            isSyncingSession = state.isSyncingSession,
            sessionCoherencyWarning = state.sessionCoherencyWarning,
            extensionTitle = state.extensionTitle,
            connectionState = state.connectionState,
            currentModel = state.currentModel,
            thinkingLevel = state.thinkingLevel,
            contextUsageLabel = formatContextUsageLabel(state.sessionStats, state.currentModel),
            errorMessage = state.errorMessage,
            callbacks = callbacks,
        )

        // Extension widgets (above editor)
        ExtensionWidgets(
            widgets = state.extensionWidgets,
            placement = "aboveEditor",
        )

        Box(modifier = Modifier.weight(1f)) {
            ChatBody(
                isLoading = state.isLoading,
                timeline = state.timeline,
                hasOlderMessages = state.hasOlderMessages,
                hiddenHistoryCount = state.hiddenHistoryCount,
                expandedToolArguments = state.expandedToolArguments,
                isRunActive = isRunActive,
                runPhase = runPhase,
                runElapsedSeconds = elapsedSeconds,
                callbacks = callbacks,
            )
        }

        // Extension widgets (below editor)
        ExtensionWidgets(
            widgets = state.extensionWidgets,
            placement = "belowEditor",
        )

        PromptControls(
            isStreaming = isRunActive,
            isRetrying = state.isRetrying,
            pendingQueueItems = state.pendingQueueItems,
            steeringMode = state.steeringMode,
            followUpMode = state.followUpMode,
            inputText = state.inputText,
            pendingImages = state.pendingImages,
            callbacks =
                PromptControlsCallbacks(
                    onInputTextChanged = callbacks.onInputTextChanged,
                    onSendPrompt = callbacks.onSendPrompt,
                    onShowCommandPalette = callbacks.onShowCommandPalette,
                    onAddImage = callbacks.onAddImage,
                    onRemoveImage = callbacks.onRemoveImage,
                    onAbort = callbacks.onAbort,
                    onAbortRetry = callbacks.onAbortRetry,
                    onSteer = callbacks.onSteer,
                    onFollowUp = callbacks.onFollowUp,
                    onRemovePendingQueueItem = callbacks.onRemovePendingQueueItem,
                    onClearPendingQueueItems = callbacks.onClearPendingQueueItems,
                ),
        )

        if (showExtensionStatusStrip) {
            ExtensionStatusStrip(statuses = state.extensionStatuses)
        }
    }
}
