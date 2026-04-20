package com.ayagmar.pimobile.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayagmar.pimobile.coresessions.SessionIndexRepository
import com.ayagmar.pimobile.coresessions.SessionRecord
import com.ayagmar.pimobile.hosts.HostProfileStore
import com.ayagmar.pimobile.hosts.HostTokenStore
import com.ayagmar.pimobile.sessions.CwdSessionGroupUiState
import com.ayagmar.pimobile.sessions.SessionAction
import com.ayagmar.pimobile.sessions.SessionController
import com.ayagmar.pimobile.sessions.SessionCwdPreferenceStore
import com.ayagmar.pimobile.sessions.SessionsUiState
import com.ayagmar.pimobile.sessions.SessionsViewModel
import com.ayagmar.pimobile.sessions.SessionsViewModelFactory
import com.ayagmar.pimobile.sessions.formatCwdTail
import com.ayagmar.pimobile.ui.components.PiButton
import com.ayagmar.pimobile.ui.components.PiCard
import com.ayagmar.pimobile.ui.components.PiSpacing
import com.ayagmar.pimobile.ui.components.PiTextField
import com.ayagmar.pimobile.ui.components.PiTopBar
import kotlinx.coroutines.delay

@Suppress("LongParameterList")
@Composable
fun SessionsRoute(
    profileStore: HostProfileStore,
    tokenStore: HostTokenStore,
    repository: SessionIndexRepository,
    sessionController: SessionController,
    cwdPreferenceStore: SessionCwdPreferenceStore,
    onNavigateToChat: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val factory =
        remember(profileStore, tokenStore, repository, sessionController, cwdPreferenceStore) {
            SessionsViewModelFactory(
                profileStore = profileStore,
                tokenStore = tokenStore,
                repository = repository,
                sessionController = sessionController,
                cwdPreferenceStore = cwdPreferenceStore,
            )
        }
    val sessionsViewModel: SessionsViewModel = viewModel(factory = factory)
    val uiState by sessionsViewModel.uiState.collectAsStateWithLifecycle()
    var transientStatusMessage by remember { mutableStateOf<String?>(null) }

    // Refresh hosts when screen is resumed (e.g., after adding a host)
    LaunchedEffect(Unit) {
        sessionsViewModel.refreshHosts()
    }

    // Navigate to chat when session is successfully resumed
    LaunchedEffect(sessionsViewModel) {
        sessionsViewModel.navigateToChat.collect {
            onNavigateToChat()
        }
    }

    LaunchedEffect(sessionsViewModel) {
        sessionsViewModel.messages.collect { message ->
            transientStatusMessage = message
        }
    }

    LaunchedEffect(transientStatusMessage) {
        if (transientStatusMessage != null) {
            delay(STATUS_MESSAGE_DURATION_MS)
            transientStatusMessage = null
        }
    }

    SessionsScreen(
        state = uiState,
        transientStatusMessage = transientStatusMessage,
        onOpenDrawer = onOpenDrawer,
        callbacks =
            SessionsScreenCallbacks(
                onHostSelected = sessionsViewModel::onHostSelected,
                onSearchChanged = sessionsViewModel::onSearchQueryChanged,
                onCwdSelected = sessionsViewModel::onCwdSelected,
                onToggleFlatView = sessionsViewModel::toggleFlatView,
                onRefreshClick = sessionsViewModel::refreshSessions,
                onNewSession = sessionsViewModel::newSession,
                onNewSessionWithCwd = sessionsViewModel::newSessionWithCwd,
                onResumeClick = sessionsViewModel::resumeSession,
                onRename = { name -> sessionsViewModel.runSessionAction(SessionAction.Rename(name)) },
                onFork = sessionsViewModel::requestForkMessages,
                onForkMessageSelected = sessionsViewModel::forkFromSelectedMessage,
                onDismissForkDialog = sessionsViewModel::dismissForkPicker,
                onExport = { sessionsViewModel.runSessionAction(SessionAction.Export) },
                onCompact = { sessionsViewModel.runSessionAction(SessionAction.Compact) },
            ),
    )
}

private data class SessionsScreenCallbacks(
    val onHostSelected: (String) -> Unit,
    val onSearchChanged: (String) -> Unit,
    val onCwdSelected: (String) -> Unit,
    val onToggleFlatView: () -> Unit,
    val onRefreshClick: () -> Unit,
    val onNewSession: () -> Unit,
    val onNewSessionWithCwd: (String) -> Unit,
    val onResumeClick: (SessionRecord) -> Unit,
    val onRename: (String) -> Unit,
    val onFork: () -> Unit,
    val onForkMessageSelected: (String) -> Unit,
    val onDismissForkDialog: () -> Unit,
    val onExport: () -> Unit,
    val onCompact: () -> Unit,
)

private data class ActiveSessionActionCallbacks(
    val onRename: () -> Unit,
    val onFork: () -> Unit,
    val onExport: () -> Unit,
    val onCompact: () -> Unit,
)

private data class SessionsListCallbacks(
    val onCwdSelected: (String) -> Unit,
    val onAddCustomCwd: () -> Unit,
    val onResumeClick: (SessionRecord) -> Unit,
    val actions: ActiveSessionActionCallbacks,
)

private data class RenameDialogUiState(
    val isVisible: Boolean,
    val activeSession: SessionRecord?,
    val draft: String,
    val onDraftChange: (String) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
private fun SessionsScreen(
    state: SessionsUiState,
    transientStatusMessage: String?,
    onOpenDrawer: () -> Unit,
    callbacks: SessionsScreenCallbacks,
) {
    var renameDraft by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    val activeSession =
        remember(state.groups, state.activeSessionPath) {
            findSessionByPath(state.groups, state.activeSessionPath)
        }
    var showCustomCwdSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(PiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PiSpacing.sm),
    ) {
        SessionsHeader(
            state = state,
            activeSession = activeSession,
            onOpenDrawer = onOpenDrawer,
            callbacks = callbacks,
        )

        HostSelector(
            state = state,
            onHostSelected = callbacks.onHostSelected,
        )

        PiTextField(
            value = state.query,
            onValueChange = callbacks.onSearchChanged,
            label = "Search sessions",
        )

        StatusMessages(
            errorMessage = state.errorMessage,
            statusMessage = transientStatusMessage,
        )

        SessionsContent(
            state = state,
            callbacks = callbacks,
            activeSessionActions =
                ActiveSessionActionCallbacks(
                    onRename = {
                        renameDraft = activeSession?.displayName.orEmpty()
                        showRenameDialog = true
                    },
                    onFork = callbacks.onFork,
                    onExport = callbacks.onExport,
                    onCompact = callbacks.onCompact,
                ),
            onAddCustomCwd = { showCustomCwdSheet = true },
        )
    }

    SessionsDialogs(
        state = state,
        callbacks = callbacks,
        renameDialog =
            RenameDialogUiState(
                isVisible = showRenameDialog,
                activeSession = activeSession,
                draft = renameDraft,
                onDraftChange = { renameDraft = it },
                onDismiss = { showRenameDialog = false },
            ),
    )

    if (showCustomCwdSheet) {
        CustomCwdSheet(
            recentCwds = state.recentCwds,
            isBusy = state.isResuming,
            onDismiss = { showCustomCwdSheet = false },
            onStartSession = { cwd ->
                showCustomCwdSheet = false
                callbacks.onNewSessionWithCwd(cwd)
            },
        )
    }
}

@Composable
private fun SessionsDialogs(
    state: SessionsUiState,
    callbacks: SessionsScreenCallbacks,
    renameDialog: RenameDialogUiState,
) {
    if (renameDialog.isVisible) {
        RenameSessionDialog(
            currentSession = renameDialog.activeSession,
            name = renameDialog.draft,
            isBusy = state.isPerformingAction,
            onNameChange = renameDialog.onDraftChange,
            onDismiss = renameDialog.onDismiss,
            onConfirm = {
                callbacks.onRename(renameDialog.draft)
                renameDialog.onDismiss()
            },
        )
    }

    if (state.isForkPickerVisible) {
        ForkPickerDialog(
            isLoading = state.isLoadingForkMessages,
            candidates = state.forkCandidates,
            onDismiss = callbacks.onDismissForkDialog,
            onSelect = callbacks.onForkMessageSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomCwdSheet(
    recentCwds: List<String>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onStartSession: (String) -> Unit,
) {
    var cwdDraft by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PiSpacing.sm),
        ) {
            Text(
                text = "New session in custom directory",
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = "Enter the absolute path of the working directory on the remote host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PiTextField(
                value = cwdDraft,
                onValueChange = { cwdDraft = it },
                label = "Directory path",
                placeholder = "/home/user/projects/my-app",
            )

            if (recentCwds.isNotEmpty()) {
                Text(
                    text = "Recent directories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items = recentCwds, key = { it }) { cwd ->
                        FilterChip(
                            selected = cwdDraft == cwd,
                            onClick = { cwdDraft = cwd },
                            label = {
                                Text(
                                    text = formatCwdTail(cwd),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            PiButton(
                label = if (isBusy) "Starting..." else "Start Session",
                enabled = cwdDraft.isNotBlank() && !isBusy,
                onClick = { onStartSession(cwdDraft) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SessionsHeader(
    state: SessionsUiState,
    activeSession: SessionRecord?,
    onOpenDrawer: () -> Unit,
    callbacks: SessionsScreenCallbacks,
) {
    PiTopBar(
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation",
                )
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.headlineSmall,
                )
                activeSession?.let { session ->
                    Text(
                        text = "Active: ${session.displayTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PiSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = callbacks.onToggleFlatView) {
                    Text(if (state.isFlatView) "Grouped" else "Flat")
                }
                TextButton(onClick = callbacks.onRefreshClick, enabled = !state.isRefreshing) {
                    Text(if (state.isRefreshing) "Refreshing" else "Refresh")
                }
                PiButton(
                    label = "New",
                    onClick = callbacks.onNewSession,
                )
            }
        },
    )
}

@Composable
private fun StatusMessages(
    errorMessage: String?,
    statusMessage: String?,
) {
    errorMessage?.let { message ->
        SelectionContainer {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    statusMessage?.let { message ->
        SelectionContainer {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SessionsContent(
    state: SessionsUiState,
    callbacks: SessionsScreenCallbacks,
    activeSessionActions: ActiveSessionActionCallbacks,
    onAddCustomCwd: () -> Unit,
) {
    when {
        state.isLoading -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.hosts.isEmpty() -> {
            Text(
                text = "No hosts configured yet.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        state.groups.isEmpty() -> {
            Text(
                text = "No sessions found for this host.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        else -> {
            if (state.isFlatView) {
                FlatSessionsList(
                    groups = state.groups,
                    activeSessionPath = state.activeSessionPath,
                    isBusy = state.isResuming || state.isPerformingAction,
                    onResumeClick = callbacks.onResumeClick,
                    actions = activeSessionActions,
                    onAddCustomCwd = onAddCustomCwd,
                )
            } else {
                SessionsList(
                    groups = state.groups,
                    selectedCwd = state.selectedCwd,
                    activeSessionPath = state.activeSessionPath,
                    isBusy = state.isResuming || state.isPerformingAction,
                    callbacks =
                        SessionsListCallbacks(
                            onCwdSelected = callbacks.onCwdSelected,
                            onAddCustomCwd = onAddCustomCwd,
                            onResumeClick = callbacks.onResumeClick,
                            actions = activeSessionActions,
                        ),
                )
            }
        }
    }
}

@Composable
private fun HostSelector(
    state: SessionsUiState,
    onHostSelected: (String) -> Unit,
) {
    if (state.hosts.isEmpty()) {
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = state.hosts, key = { host -> host.id }) { host ->
            FilterChip(
                selected = host.id == state.selectedHostId,
                onClick = { onHostSelected(host.id) },
                label = { Text(host.name) },
            )
        }
    }
}

@Composable
private fun SessionsList(
    groups: List<CwdSessionGroupUiState>,
    selectedCwd: String?,
    activeSessionPath: String?,
    isBusy: Boolean,
    callbacks: SessionsListCallbacks,
) {
    val resolvedSelectedCwd =
        remember(groups, selectedCwd) {
            selectedCwd?.takeIf { target -> groups.any { group -> group.cwd == target } }
                ?: groups.firstOrNull()?.cwd
        }

    val selectedGroup =
        remember(groups, resolvedSelectedCwd) {
            groups.firstOrNull { group -> group.cwd == resolvedSelectedCwd }
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CwdChipSelector(
            groups = groups,
            selectedCwd = resolvedSelectedCwd,
            onCwdSelected = callbacks.onCwdSelected,
            onAddCustomCwd = callbacks.onAddCustomCwd,
        )

        selectedGroup?.let { group ->
            Text(
                text = group.cwd,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = group.sessions, key = { session -> session.sessionPath }) { session ->
                    SessionCard(
                        session = session,
                        isActive = activeSessionPath == session.sessionPath,
                        isBusy = isBusy,
                        onResumeClick = { callbacks.onResumeClick(session) },
                        actions = callbacks.actions,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CwdChipSelector(
    groups: List<CwdSessionGroupUiState>,
    selectedCwd: String?,
    onCwdSelected: (String) -> Unit,
    onAddCustomCwd: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = groups, key = { group -> group.cwd }) { group ->
            val shortLabel = formatCwdTail(group.cwd)
            FilterChip(
                selected = group.cwd == selectedCwd,
                onClick = { onCwdSelected(group.cwd) },
                label = {
                    Text(
                        text = "$shortLabel (${group.sessions.size})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }

        item(key = "__add_custom_cwd__") {
            FilterChip(
                selected = false,
                onClick = onAddCustomCwd,
                label = { Text("+") },
            )
        }
    }
}

@Composable
private fun FlatSessionsList(
    groups: List<CwdSessionGroupUiState>,
    activeSessionPath: String?,
    isBusy: Boolean,
    onResumeClick: (SessionRecord) -> Unit,
    actions: ActiveSessionActionCallbacks,
    onAddCustomCwd: () -> Unit,
) {
    // Flatten all sessions and sort by updatedAt (most recent first)
    val allSessions =
        remember(groups) {
            groups.flatMap { it.sessions }.sortedByDescending { it.updatedAt }
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "__add_custom_cwd_flat__") {
                FilterChip(
                    selected = false,
                    onClick = onAddCustomCwd,
                    label = { Text("+ Custom directory") },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = allSessions, key = { session -> session.sessionPath }) { session ->
                SessionCard(
                    session = session,
                    isActive = activeSessionPath == session.sessionPath,
                    isBusy = isBusy,
                    onResumeClick = { onResumeClick(session) },
                    actions = actions,
                    showCwd = true,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun SessionCard(
    session: SessionRecord,
    isActive: Boolean,
    isBusy: Boolean,
    onResumeClick: () -> Unit,
    actions: ActiveSessionActionCallbacks,
    showCwd: Boolean = false,
) {
    PiCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PiSpacing.xs)) {
            SessionCardSummary(session = session, showCwd = showCwd)
            SessionCardFooter(
                session = session,
                isActive = isActive,
                isBusy = isBusy,
                onResumeClick = onResumeClick,
            )

            if (isActive) {
                SessionActionsRow(
                    isBusy = isBusy,
                    onRenameClick = actions.onRename,
                    onForkClick = actions.onFork,
                    onExportClick = actions.onExport,
                    onCompactClick = actions.onCompact,
                )
            }
        }
    }
}

@Composable
private fun SessionCardSummary(
    session: SessionRecord,
    showCwd: Boolean,
) {
    Text(
        text = session.displayTitle,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )

    session.displaySubtitle?.let { subtitle ->
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (showCwd && session.cwd.isNotBlank()) {
        Text(
            text = session.cwd,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    SessionMetadataRow(session)

    Text(
        text = session.sessionPath,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    session.firstUserMessagePreview
        ?.takeIf { preview -> preview != session.displaySubtitle }
        ?.let { preview ->
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
}

@Composable
private fun SessionCardFooter(
    session: SessionRecord,
    isActive: Boolean,
    isBusy: Boolean,
    onResumeClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Updated ${session.updatedAt.compactIsoTimestamp()}",
            style = MaterialTheme.typography.bodySmall,
        )

        PiButton(
            label = if (isActive) "Open" else "Resume",
            enabled = !isBusy,
            onClick = onResumeClick,
        )
    }
}

@Composable
private fun SessionMetadataRow(session: SessionRecord) {
    val metadata =
        buildList {
            session.messageCount?.let { count -> add("$count msgs") }
            session.lastModel?.takeIf { it.isNotBlank() }?.let { model -> add(model) }
        }

    if (metadata.isEmpty()) {
        return
    }

    Text(
        text = metadata.joinToString(" • "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun findSessionByPath(
    groups: List<CwdSessionGroupUiState>,
    sessionPath: String?,
): SessionRecord? {
    if (sessionPath == null) {
        return null
    }

    return groups
        .asSequence()
        .flatMap { group -> group.sessions.asSequence() }
        .firstOrNull { session -> session.sessionPath == sessionPath }
}

private fun String.compactIsoTimestamp(): String {
    return removeSuffix("Z").replace('T', ' ').substringBefore('.')
}

private const val STATUS_MESSAGE_DURATION_MS = 3_000L
