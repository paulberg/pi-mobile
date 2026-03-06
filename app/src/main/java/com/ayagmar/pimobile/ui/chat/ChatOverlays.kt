package com.ayagmar.pimobile.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ayagmar.pimobile.chat.ChatViewModel
import com.ayagmar.pimobile.chat.ExtensionNotification
import com.ayagmar.pimobile.chat.ExtensionUiRequest
import com.ayagmar.pimobile.sessions.SlashCommandInfo
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionUiDialogs(
    request: ExtensionUiRequest?,
    onSendResponse: (String, String?, Boolean?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    when (request) {
        is ExtensionUiRequest.Select -> {
            SelectSheet(
                request = request,
                onConfirm = { value ->
                    onSendResponse(request.requestId, value, null, false)
                },
                onDismiss = onDismiss,
            )
        }

        is ExtensionUiRequest.Confirm -> {
            ConfirmSheet(
                request = request,
                onConfirm = { confirmed ->
                    onSendResponse(request.requestId, null, confirmed, false)
                },
                onDismiss = onDismiss,
            )
        }

        is ExtensionUiRequest.Input -> {
            InputSheet(
                request = request,
                onConfirm = { value ->
                    onSendResponse(request.requestId, value, null, false)
                },
                onDismiss = onDismiss,
            )
        }

        is ExtensionUiRequest.Editor -> {
            EditorSheet(
                request = request,
                onConfirm = { value ->
                    onSendResponse(request.requestId, value, null, false)
                },
                onDismiss = onDismiss,
            )
        }

        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectSheet(
    request: ExtensionUiRequest.Select,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            request.options.forEach { option ->
                TextButton(
                    onClick = { onConfirm(option) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(option)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(
    request: ExtensionUiRequest.Confirm,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = request.message,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = { onConfirm(false) }) {
                    Text("No")
                }
                Button(onClick = { onConfirm(true) }) {
                    Text("Yes")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSheet(
    request: ExtensionUiRequest.Input,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var text by rememberSaveable(request.requestId) { mutableStateOf("") }

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
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = request.placeholder?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(text) },
                    enabled = text.isNotBlank(),
                ) {
                    Text("OK")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(
    request: ExtensionUiRequest.Editor,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable(request.requestId) { mutableStateOf(request.prefill) }

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
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                singleLine = false,
                maxLines = 10,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(onClick = { onConfirm(text) }) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
internal fun NotificationsDisplay(
    notifications: List<ExtensionNotification>,
    onClear: (Int) -> Unit,
) {
    val latestNotification = notifications.lastOrNull() ?: return
    val index = notifications.lastIndex

    LaunchedEffect(index) {
        delay(NOTIFICATION_AUTO_DISMISS_MS)
        onClear(index)
    }

    val color =
        when (latestNotification.type) {
            "error" -> MaterialTheme.colorScheme.error
            "warning" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

    val containerColor =
        when (latestNotification.type) {
            "error" -> MaterialTheme.colorScheme.errorContainer
            "warning" -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Snackbar(
            action = {
                TextButton(onClick = { onClear(index) }) {
                    Text("Dismiss")
                }
            },
            containerColor = containerColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                text = latestNotification.message,
                color = color,
            )
        }
    }
}

private data class PaletteCommandItem(
    val command: SlashCommandInfo,
    val support: CommandSupport,
)

private enum class CommandSupport {
    SUPPORTED,
    BRIDGE_BACKED,
    UNSUPPORTED,
}

private val commandSupportOrder =
    listOf(
        CommandSupport.SUPPORTED,
        CommandSupport.BRIDGE_BACKED,
        CommandSupport.UNSUPPORTED,
    )

private val CommandSupport.groupLabel: String
    get() =
        when (this) {
            CommandSupport.SUPPORTED -> "Supported"
            CommandSupport.BRIDGE_BACKED -> "Bridge-backed"
            CommandSupport.UNSUPPORTED -> "Unsupported"
        }

private val CommandSupport.badge: String
    get() =
        when (this) {
            CommandSupport.SUPPORTED -> "supported"
            CommandSupport.BRIDGE_BACKED -> "bridge-backed"
            CommandSupport.UNSUPPORTED -> "unsupported"
        }

@Composable
private fun CommandSupport.color(): Color {
    return when (this) {
        CommandSupport.SUPPORTED -> MaterialTheme.colorScheme.primary
        CommandSupport.BRIDGE_BACKED -> MaterialTheme.colorScheme.tertiary
        CommandSupport.UNSUPPORTED -> MaterialTheme.colorScheme.error
    }
}

private fun commandSupport(command: SlashCommandInfo): CommandSupport {
    return when (command.source) {
        ChatViewModel.COMMAND_SOURCE_BUILTIN_BRIDGE_BACKED -> CommandSupport.BRIDGE_BACKED
        ChatViewModel.COMMAND_SOURCE_BUILTIN_UNSUPPORTED -> CommandSupport.UNSUPPORTED
        else -> CommandSupport.SUPPORTED
    }
}

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommandPalette(
    isVisible: Boolean,
    commands: List<SlashCommandInfo>,
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onCommandSelected: (SlashCommandInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredCommands =
        remember(commands, query) {
            if (query.isBlank()) {
                commands
            } else {
                commands.filter { command ->
                    command.name.contains(query, ignoreCase = true) ||
                        command.description?.contains(query, ignoreCase = true) == true
                }
            }
        }

    val filteredPaletteCommands =
        remember(filteredCommands) {
            filteredCommands.map { command ->
                PaletteCommandItem(
                    command = command,
                    support = commandSupport(command),
                )
            }
        }

    val groupedCommands =
        remember(filteredPaletteCommands) {
            filteredPaletteCommands.groupBy { item -> item.support }
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
                text = "Commands",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search commands...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredPaletteCommands.isEmpty()) {
                Text(
                    text = "No commands found",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    commandSupportOrder.forEach { support ->
                        val commandsInGroup = groupedCommands[support].orEmpty()
                        if (commandsInGroup.isEmpty()) {
                            return@forEach
                        }

                        item {
                            Text(
                                text = support.groupLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        items(
                            items = commandsInGroup,
                            key = { item -> "${item.command.source}:${item.command.name}" },
                        ) { item ->
                            CommandItem(
                                command = item.command,
                                support = item.support,
                                onClick = { onCommandSelected(item.command) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandItem(
    command: SlashCommandInfo,
    support: CommandSupport,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "/${command.name}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = support.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = support.color(),
                )
            }
            command.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (support == CommandSupport.SUPPORTED) {
                Text(
                    text = "Source: ${command.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private const val NOTIFICATION_AUTO_DISMISS_MS = 4_000L
