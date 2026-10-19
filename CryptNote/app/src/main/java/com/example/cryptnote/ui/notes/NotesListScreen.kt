package com.example.cryptnote.ui.notes

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cryptnote.data.preferences.ThemeMode
import com.example.cryptnote.data.preferences.ThemePreferencesManager
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.ui.common.buildHighlightedString
import com.example.cryptnote.ui.editor.NoteEditorScreen
import com.example.cryptnote.ui.editor.NoteEditorViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NotesListViewModel,
    onNavigateToEditor: (noteId: String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onLockVault: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSyncDetailNote by remember { mutableStateOf<Note?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Adaptive layout support: check screen width for 2-pane tablet mode
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    var tabletSelectedNoteId by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val themePreferencesManager = remember {
        try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.cryptnote.di.SecurityEntryPoint::class.java
            ).themePreferencesManager()
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(viewModel.eventFlow) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is NotesListEvent.ShowUndoSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Deleted \"${event.noteTitle}\"",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreLastDeletedNote()
                    }
                }
                is NotesListEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // Render Theme Switcher Dialog
    if (showThemeDialog && themePreferencesManager != null) {
        ThemeSettingsDialog(
            themePreferencesManager = themePreferencesManager,
            onDismiss = { showThemeDialog = false }
        )
    }

    // Render Conflict Resolution Dialog if user selected a conflict
    uiState.selectedConflictPair?.let { pair ->
        ConflictResolutionDialog(
            conflictPair = pair,
            onResolve = { action -> viewModel.resolveConflict(pair.conflictGroupId, action) },
            onDismiss = { viewModel.onDismissConflictDialog() }
        )
    }

    // Render Sync Status Details Dialog if user tapped a note's sync indicator
    selectedSyncDetailNote?.let { note ->
        SyncStatusDetailDialog(
            note = note,
            onDismiss = { selectedSyncDetailNote = null },
            onResolveConflict = if (note.conflictGroupId != null) {
                {
                    selectedSyncDetailNote = null
                    viewModel.onConflictSelected(note.conflictGroupId)
                }
            } else null
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted Vault",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CryptoNote",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    // Conflicts Badge button
                    if (uiState.conflictCount > 0) {
                        IconButton(
                            onClick = {
                                val firstGroupId = uiState.conflictPairs.firstOrNull()?.conflictGroupId ?: ""
                                viewModel.onConflictSelected(firstGroupId)
                            },
                            modifier = Modifier.semantics { contentDescription = "${uiState.conflictCount} conflicts detected" }
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text(uiState.conflictCount.toString())
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Conflicts detected",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Theme Customizer Button
                    IconButton(
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.semantics { contentDescription = "Theme and Appearance Settings" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Theme and Appearance Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Lock Vault Button
                    IconButton(
                        onClick = onLockVault,
                        modifier = Modifier.semantics { contentDescription = "Lock Vault" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Profile / Account Button
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.semantics { contentDescription = "Profile and Account Settings" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isTablet) {
                        tabletSelectedNoteId = "new"
                    } else {
                        onNavigateToEditor("new")
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.semantics { contentDescription = "Create New Note" }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create New Note")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (isTablet) {
            // Adaptive Two-Pane Layout for Tablets / Wide Screens
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Left Pane: Notes List
                Box(modifier = Modifier.weight(0.42f)) {
                    NotesListContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        selectedNoteId = tabletSelectedNoteId,
                        onNoteClick = { note ->
                            if (note.conflictGroupId != null) {
                                viewModel.onConflictSelected(note.conflictGroupId)
                            } else {
                                tabletSelectedNoteId = note.id
                            }
                        },
                        onSyncStatusClick = { note -> selectedSyncDetailNote = note },
                        onCreateNewNote = { tabletSelectedNoteId = "new" }
                    )
                }

                // Divider between list and detail pane
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Right Pane: Active Note Editor or Detail Placeholder
                Box(modifier = Modifier.weight(0.58f)) {
                    val activeNoteId = tabletSelectedNoteId
                    if (activeNoteId != null) {
                        val editorViewModel: NoteEditorViewModel = hiltViewModel(
                            key = "editor-$activeNoteId"
                        )
                        NoteEditorScreen(
                            viewModel = editorViewModel,
                            onNavigateBack = { tabletSelectedNoteId = null }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Select or create a note",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Choose an encrypted note from the list on the left, or create a new one to view and edit with live Markdown preview.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Standard Phone Single-Pane Layout
            NotesListContent(
                uiState = uiState,
                viewModel = viewModel,
                selectedNoteId = null,
                onNoteClick = { note ->
                    if (note.conflictGroupId != null) {
                        viewModel.onConflictSelected(note.conflictGroupId)
                    } else {
                        onNavigateToEditor(note.id)
                    }
                },
                onSyncStatusClick = { note -> selectedSyncDetailNote = note },
                onCreateNewNote = { onNavigateToEditor("new") },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListContent(
    uiState: NotesListUiState,
    viewModel: NotesListViewModel,
    selectedNoteId: String?,
    onNoteClick: (Note) -> Unit,
    onSyncStatusClick: (Note) -> Unit,
    onCreateNewNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Conflict Warning Banner if conflicts exist
        AnimatedVisibility(
            visible = uiState.conflictCount > 0,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ConflictWarningBanner(
                conflictCount = uiState.conflictCount,
                onReviewClick = {
                    val firstGroupId = uiState.conflictPairs.firstOrNull()?.conflictGroupId ?: ""
                    viewModel.onConflictSelected(firstGroupId)
                }
            )
        }

        // In-Memory Zero-Knowledge Search Bar
        @Suppress("DEPRECATION")
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = { viewModel.onSearchQueryChanged(it) },
            onSearch = {},
            active = false,
            onActiveChange = {},
            placeholder = { Text("Search encrypted notes...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.onSearchQueryChanged("") },
                        modifier = Modifier.semantics { contentDescription = "Clear search" }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {}

        // Tags Filter Row
        if (uiState.availableTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.selectedTag == null,
                    onClick = { viewModel.onTagSelected(null) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                uiState.availableTags.forEach { tag ->
                    FilterChip(
                        selected = uiState.selectedTag == tag,
                        onClick = { viewModel.onTagSelected(tag) },
                        label = { Text("#$tag") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (uiState.notes.isEmpty() && !uiState.isLoading) {
            EmptyNotesView(
                isSearchActive = uiState.searchQuery.isNotEmpty() || uiState.selectedTag != null,
                onCreateNote = onCreateNewNote,
                onClearFilter = {
                    viewModel.onSearchQueryChanged("")
                    viewModel.onTagSelected(null)
                }
            )
        } else {
            val pinnedNotes = remember(uiState.notes) { uiState.notes.filter { it.pinned } }
            val otherNotes = remember(uiState.notes) { uiState.notes.filterNot { it.pinned } }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pinned Section Header (if both pinned and regular notes exist)
                if (pinnedNotes.isNotEmpty() && otherNotes.isNotEmpty()) {
                    item(key = "header_pinned") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PINNED",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                items(
                    items = pinnedNotes,
                    key = { it.id }
                ) { note ->
                    SwipeableNoteCard(
                        note = note,
                        searchQuery = uiState.searchQuery,
                        isSelected = note.id == selectedNoteId,
                        onClick = { onNoteClick(note) },
                        onDelete = { viewModel.deleteNote(note) },
                        onTogglePin = { viewModel.onTogglePin(note) },
                        onSyncStatusClick = { onSyncStatusClick(note) }
                    )
                }

                // Other Notes Header
                if (pinnedNotes.isNotEmpty() && otherNotes.isNotEmpty()) {
                    item(key = "header_others") {
                        Text(
                            text = "OTHER NOTES",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                }

                items(
                    items = otherNotes,
                    key = { it.id }
                ) { note ->
                    SwipeableNoteCard(
                        note = note,
                        searchQuery = uiState.searchQuery,
                        isSelected = note.id == selectedNoteId,
                        onClick = { onNoteClick(note) },
                        onDelete = { viewModel.deleteNote(note) },
                        onTogglePin = { viewModel.onTogglePin(note) },
                        onSyncStatusClick = { onSyncStatusClick(note) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConflictWarningBanner(
    conflictCount: Int,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Conflict warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "$conflictCount Sync Conflict${if (conflictCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Conflicting updates require resolution",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onReviewClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Resolve", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNoteCard(
    note: Note,
    searchQuery: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onSyncStatusClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val isSwipingLeft = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val progress = dismissState.progress
            if (isSwipingLeft && progress > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Note",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        NoteItemCard(
            note = note,
            searchQuery = searchQuery,
            isSelected = isSelected,
            onClick = onClick,
            onTogglePin = onTogglePin,
            onSyncStatusClick = onSyncStatusClick
        )
    }
}

@Composable
fun NoteItemCard(
    note: Note,
    searchQuery: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onTogglePin: () -> Unit = {},
    onSyncStatusClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isConflicted = note.conflictGroupId != null
    val highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    val borderStroke = when {
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        note.pinned -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        else -> null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Note: ${note.title}" },
        shape = RoundedCornerShape(12.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isConflicted -> MaterialTheme.colorScheme.errorContainer
                note.pinned -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val highlightedTitle = buildHighlightedString(
                    text = note.title.ifBlank { "Untitled Note" },
                    query = searchQuery,
                    highlightBgColor = highlightColor,
                    highlightTextColor = highlightTextColor
                )

                Text(
                    text = highlightedTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick pin/unpin toggle button
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier
                            .size(28.dp)
                            .semantics {
                                contentDescription = if (note.pinned) "Unpin note" else "Pin note"
                            }
                    ) {
                        Icon(
                            imageVector = if (note.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (note.pinned) "Unpin note" else "Pin note",
                            tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    if (isConflicted && note.syncStatus != SyncStatus.CONFLICT) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Conflict",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    SyncStatusIndicator(
                        status = note.syncStatus,
                        onClick = onSyncStatusClick
                    )
                }
            }

            if (note.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                val highlightedBody = buildHighlightedString(
                    text = note.body,
                    query = searchQuery,
                    highlightBgColor = highlightColor,
                    highlightTextColor = highlightTextColor
                )
                Text(
                    text = highlightedBody,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tags row on card
            if (note.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    note.tags.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                    if (note.tags.size > 3) {
                        Text(
                            text = "+${note.tags.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = formatTimestamp(note.updatedAt),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

private data class SyncStatusInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val label: String,
    val containerColor: Color,
    val title: String,
    val description: String
)

@Composable
private fun getSyncStatusInfo(status: SyncStatus): SyncStatusInfo {
    return when (status) {
        SyncStatus.PENDING -> SyncStatusInfo(
            icon = Icons.Default.Schedule,
            color = MaterialTheme.colorScheme.secondary,
            label = "Pending",
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            title = "Sync Pending (Offline)",
            description = "Changes are saved locally on your device and encrypted with zero-knowledge AES-256-GCM.\n\nWaiting for an active internet connection to securely upload to your cloud vault."
        )
        SyncStatus.SYNCING -> SyncStatusInfo(
            icon = Icons.Default.Sync,
            color = MaterialTheme.colorScheme.tertiary,
            label = "Syncing",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            title = "Syncing in Progress",
            description = "Currently encrypting and synchronizing changes with your remote cloud vault over an encrypted TLS connection."
        )
        SyncStatus.SYNCED -> SyncStatusInfo(
            icon = Icons.Default.CheckCircle,
            color = MaterialTheme.colorScheme.primary,
            label = "Synced",
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            title = "Fully Synchronized",
            description = "This note is safely encrypted and backed up in your cloud vault. Local and cloud copies are identical and up-to-date."
        )
        SyncStatus.CONFLICT -> SyncStatusInfo(
            icon = Icons.Default.Warning,
            color = MaterialTheme.colorScheme.error,
            label = "Conflict",
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            title = "Sync Conflict Detected",
            description = "A conflicting update was detected from another device or session.\n\nYour local edits have been preserved in this note. Tap 'Resolve Conflict' to review differences and choose which version to keep."
        )
        SyncStatus.FAILED -> SyncStatusInfo(
            icon = Icons.Default.Warning,
            color = MaterialTheme.colorScheme.error,
            label = "Failed",
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            title = "Sync Failed",
            description = "The note could not be synced due to a network or server error. The background sync worker will automatically retry with exponential backoff."
        )
    }
}

@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val info = getSyncStatusInfo(status)
    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val iconModifier = if (status == SyncStatus.SYNCING) {
        Modifier
            .size(13.dp)
            .rotate(rotationAngle)
    } else {
        Modifier.size(13.dp)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(info.containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = info.label,
                tint = info.color,
                modifier = iconModifier
            )
            Text(
                text = info.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = info.color
            )
        }
    }
}

@Composable
fun SyncStatusDetailDialog(
    note: Note,
    onDismiss: () -> Unit,
    onResolveConflict: (() -> Unit)? = null
) {
    val info = getSyncStatusInfo(note.syncStatus)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = info.icon,
                contentDescription = info.label,
                tint = info.color,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = info.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Note: ${note.title.ifBlank { "Untitled" }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version: ${note.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Updated: ${formatTimestamp(note.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (note.syncStatus == SyncStatus.CONFLICT && onResolveConflict != null) {
                Button(
                    onClick = onResolveConflict,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Resolve Conflict")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Got it")
                }
            }
        },
        dismissButton = {
            if (note.syncStatus == SyncStatus.CONFLICT && onResolveConflict != null) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun ThemeSettingsDialog(
    themePreferencesManager: ThemePreferencesManager,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentMode by themePreferencesManager.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val currentColor by themePreferencesManager.themeColor.collectAsStateWithLifecycle(
        initialValue = com.example.cryptnote.data.preferences.ThemeColor.CYBER_TEAL
    )
    val dynamicColor by themePreferencesManager.dynamicColor.collectAsStateWithLifecycle(initialValue = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Appearance & Theme")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "THEME MODE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    themePreferencesManager.setThemeMode(mode)
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = {
                                coroutineScope.launch {
                                    themePreferencesManager.setThemeMode(mode)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "Follow System Default"
                                ThemeMode.LIGHT -> "Light Mode"
                                ThemeMode.DARK -> "Dark Mode (Obsidian)"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "ACCENT COLOR PALETTE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val themeColors = com.example.cryptnote.data.preferences.ThemeColor.entries
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    themeColors.chunked(2).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowColors.forEach { palette ->
                                val isSelected = currentColor == palette && !dynamicColor
                                val paletteColor = Color(palette.hexColor)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                themePreferencesManager.setThemeColor(palette)
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(paletteColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = palette.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Dynamic Theming",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Wallpaper colors (Android 12+)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    themePreferencesManager.setDynamicColor(enabled)
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun EmptyNotesView(
    isSearchActive: Boolean,
    onCreateNote: () -> Unit = {},
    onClearFilter: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Search else Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isSearchActive) "No matching notes found" else "Your encrypted vault is empty",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSearchActive) {
                    "No notes match your current search query or tag filter. Try adjusting your search term."
                } else {
                    "Notes are securely encrypted using AES-256-GCM before writing to storage. Create your first note now."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isSearchActive) {
                OutlinedButton(
                    onClick = onClearFilter,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Search & Filters")
                }
            } else {
                Button(
                    onClick = onCreateNote,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Note")
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return formatter.format(Date(millis))
}
