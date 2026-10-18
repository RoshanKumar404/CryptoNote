package com.example.cryptnote.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.ui.common.buildHighlightedString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NotesListViewModel,
    onNavigateToEditor: (noteId: String) -> Unit,
    onLockVault: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSyncDetailNote by remember { mutableStateOf<Note?>(null) }

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
                            contentDescription = "Encrypted",
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
                    if (uiState.conflictCount > 0) {
                        IconButton(onClick = {
                            val firstGroupId = uiState.conflictPairs.firstOrNull()?.conflictGroupId ?: ""
                            viewModel.onConflictSelected(firstGroupId)
                        }) {
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
                    IconButton(onClick = onLockVault) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelLarge
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
                onClick = { onNavigateToEditor("new") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Note")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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

            // In-Memory Search Bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search encrypted notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.notes.isEmpty() && !uiState.isLoading) {
                EmptyNotesView(isSearchActive = uiState.searchQuery.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.notes,
                        key = { it.id }
                    ) { note ->
                        SwipeableNoteCard(
                            note = note,
                            searchQuery = uiState.searchQuery,
                            onClick = {
                                if (note.conflictGroupId != null) {
                                    viewModel.onConflictSelected(note.conflictGroupId)
                                } else {
                                    onNavigateToEditor(note.id)
                                }
                            },
                            onDelete = { viewModel.deleteNote(note) },
                            onSyncStatusClick = { selectedSyncDetailNote = note }
                        )
                    }
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Sync Conflict Detected",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "$conflictCount note(s) have conflicting edits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }

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
    onClick: () -> Unit,
    onDelete: () -> Unit,
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        NoteItemCard(
            note = note,
            searchQuery = searchQuery,
            onClick = onClick,
            onSyncStatusClick = onSyncStatusClick
        )
    }
}

@Composable
fun NoteItemCard(
    note: Note,
    searchQuery: String,
    onClick: () -> Unit,
    onSyncStatusClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isConflicted = note.conflictGroupId != null
    val highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConflicted) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Note: ${note.title.ifBlank { "Untitled Note" }}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Status: ${note.syncStatus.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = info.color,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Version: v${note.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Security: AES-256-GCM Zero-Knowledge",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Last Saved: ${formatTimestamp(note.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (note.syncStatus == SyncStatus.CONFLICT && onResolveConflict != null) {
                Button(
                    onClick = {
                        onDismiss()
                        onResolveConflict()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
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
fun EmptyNotesView(isSearchActive: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (isSearchActive) Icons.Default.Search else Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isSearchActive) "No matching notes" else "No encrypted notes yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSearchActive) {
                    "Try searching for something else"
                } else {
                    "Tap the + button below to create your first zero-knowledge encrypted note."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 18.sp
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return formatter.format(Date(millis))
}
