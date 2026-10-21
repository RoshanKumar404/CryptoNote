package com.example.cryptnote.ui.notes

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.ui.editor.NoteEditorScreen
import com.example.cryptnote.ui.editor.NoteEditorViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var showGreetingEditDialog by remember { mutableStateOf(false) }

    // Adaptive layout support: check screen width for 2-pane tablet mode
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    var tabletSelectedNoteId by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
    val customGreetingName = themePreferencesManager?.userGreetingName
        ?.collectAsStateWithLifecycle(initialValue = "")?.value ?: ""

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
                is NotesListEvent.RequestBiometricUnlock -> {
                    val activity = context as? androidx.fragment.app.FragmentActivity
                    val biometricManager = try {
                        EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            com.example.cryptnote.di.SecurityEntryPoint::class.java
                        ).biometricAuthManager()
                    } catch (_: Exception) { null }

                    if (activity != null && biometricManager != null) {
                        biometricManager.authenticate(
                            activity = activity,
                            cryptoObject = androidx.biometric.BiometricPrompt.CryptoObject(event.cipher),
                            onSuccess = { result ->
                                result.cryptoObject?.cipher?.let { verifiedCipher ->
                                    viewModel.onBiometricUnlockSuccess(verifiedCipher)
                                }
                            },
                            onError = { _, _ -> },
                            onFailed = {}
                        )
                    }
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

    // Render Decryption Error Dialog if user tapped an unreadable note
    uiState.selectedDecryptionErrorNote?.let { note ->
        DecryptionErrorDialog(
            note = note,
            onDelete = {
                viewModel.quarantineNote(note.id, "User requested quarantine of unreadable note")
            },
            onRefresh = {
                viewModel.refreshNotes()
                viewModel.onDismissDecryptionErrorDialog()
            },
            onDismiss = { viewModel.onDismissDecryptionErrorDialog() }
        )
    }

    // Render Unlock Vault Dialog (NV-FE-5, NV-FE-6)
    if (uiState.showUnlockVaultDialog) {
        UnlockVaultDialog(
            errorMessage = uiState.unlockVaultError,
            canBiometric = viewModel.canUnlockWithBiometric(),
            onUnlockWithPassphrase = { passphrase ->
                viewModel.unlockVaultWithPassphrase(passphrase)
            },
            onTriggerBiometric = {
                viewModel.triggerBiometricUnlock()
            },
            onDismiss = { viewModel.dismissUnlockVaultDialog() }
        )
    }

    // Render Decrypt Diagnostics Sheet (NV-FE-9)
    if (uiState.showDiagnosticsSheet) {
        DecryptDiagnosticsSheet(
            diagnostics = uiState.recentDiagnostics,
            onDismiss = { viewModel.toggleDiagnosticsSheet(false) }
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

    // Render Greeting Personalization Dialog
    if (showGreetingEditDialog && themePreferencesManager != null) {
        var greetingInput by remember { mutableStateOf(customGreetingName) }
        AlertDialog(
            onDismissRequest = { showGreetingEditDialog = false },
            title = { Text("Personalize Greeting", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter your name to personalize your daily greeting on the home screen:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = greetingInput,
                        onValueChange = { greetingInput = it },
                        placeholder = { Text("e.g. Roshan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            themePreferencesManager.setUserGreetingName(greetingInput)
                        }
                        showGreetingEditDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGreetingEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val onlineColor = Color(0xFF4CAF50)
    val offlineColor = MaterialTheme.colorScheme.error
    var borderActiveColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(uiState.isOnline) {
        val targetColor = if (uiState.isOnline) onlineColor else offlineColor
        val msg = if (uiState.isOnline) "Online" else "Offline"

        snackbarHostState.currentSnackbarData?.dismiss()
        borderActiveColor = targetColor

        val job = launch {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Indefinite
            )
        }
        delay(1000)
        borderActiveColor = Color.Transparent
        snackbarHostState.currentSnackbarData?.dismiss()
        job.cancel()
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = borderActiveColor,
        animationSpec = tween(durationMillis = 300),
        label = "SystemOnlineBorderAnimation"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (animatedBorderColor != Color.Transparent) {
                    Modifier.border(width = 2.5.dp, color = animatedBorderColor)
                } else Modifier
            ),
        topBar = {
            Column {
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
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (uiState.isOnline) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = animatedBorderColor,
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.isOnline) "Online" else "Offline",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (uiState.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
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

                        // Decrypt Diagnostics Button (NV-FE-9)
                        IconButton(
                            onClick = { viewModel.toggleDiagnosticsSheet(true) },
                            modifier = Modifier.semantics { contentDescription = "Decryption Diagnostics" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Decryption Diagnostics",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

                        // Vault Lock/App Lock Button
                        IconButton(
                            onClick = {
                                if (uiState.currentTab == NotesTab.VAULT && !uiState.isVaultLocked) {
                                    viewModel.lockVaultNow()
                                } else {
                                    onLockVault()
                                }
                            },
                            modifier = Modifier.semantics { contentDescription = "Lock Vault" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Vault",
                                tint = if (uiState.currentTab == NotesTab.VAULT && !uiState.isVaultLocked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
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
                if (animatedBorderColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(animatedBorderColor)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == NotesTab.NOTES,
                    onClick = { viewModel.selectTab(NotesTab.NOTES) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Notes"
                        )
                    },
                    label = { Text("Notes") }
                )
                NavigationBarItem(
                    selected = uiState.currentTab == NotesTab.VAULT,
                    onClick = { viewModel.selectTab(NotesTab.VAULT) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.isVaultLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Vault"
                        )
                    },
                    label = { Text("Vault") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (uiState.currentTab == NotesTab.VAULT && uiState.isVaultLocked) {
                        viewModel.openUnlockVaultDialog()
                    } else if (isTablet) {
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
                            if (note.title.startsWith("⚠️")) {
                                viewModel.onDecryptionErrorNoteSelected(note)
                            } else if (note.conflictGroupId != null) {
                                viewModel.onConflictSelected(note.conflictGroupId)
                            } else {
                                tabletSelectedNoteId = note.id
                            }
                        },
                        onSyncStatusClick = { note -> selectedSyncDetailNote = note },
                        onCreateNewNote = { tabletSelectedNoteId = "new" },
                        animatedBorderColor = animatedBorderColor
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
                    if (note.title.startsWith("⚠️")) {
                        viewModel.onDecryptionErrorNoteSelected(note)
                    } else if (note.conflictGroupId != null) {
                        viewModel.onConflictSelected(note.conflictGroupId)
                    } else {
                        onNavigateToEditor(note.id)
                    }
                },
                onSyncStatusClick = { note -> selectedSyncDetailNote = note },
                onCreateNewNote = { onNavigateToEditor("new") },
                customGreetingName = customGreetingName,
                onEditGreeting = { showGreetingEditDialog = true },
                animatedBorderColor = animatedBorderColor,
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
    customGreetingName: String = "",
    onEditGreeting: () -> Unit = {},
    animatedBorderColor: Color = Color(0xFF4CAF50),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (animatedBorderColor != Color.Transparent) {
                    Modifier.border(width = 2.5.dp, color = animatedBorderColor)
                } else Modifier
            )
    ) {
        // Offline Warning Banner
        AnimatedVisibility(
            visible = !uiState.isOnline,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Offline indicator",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "You are offline — notes are saved locally and will sync when reconnected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

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

        val isVaultTab = uiState.currentTab == NotesTab.VAULT
        val currentDisplayNotes = if (isVaultTab) uiState.vaultNotes else uiState.notes

        // Greeting Header Banner (Home / Notes tab)
        if (!isVaultTab && uiState.searchQuery.isEmpty() && uiState.selectedTag == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { onEditGreeting() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = getFormattedDateDay(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = getGreetingText(customGreetingName),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { onEditGreeting() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Update Greeting Name",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshNotes() },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    if (isVaultTab) {
                        viewModel.onVaultTouched()
                    }
                }
        ) {
            if (isVaultTab && uiState.isVaultLocked) {
                // NV-FE-5: Vault tab queries is_vault = 1 and shows locked rows: date + ciphertext preview, no title
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "vault_locked_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Secure Vault is Locked",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Private notes are encrypted and isolated. Verify your passphrase or biometric to decrypt and access vault items.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.openUnlockVaultDialog() },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Unlock Vault")
                                }
                            }
                        }
                    }

                    if (currentDisplayNotes.isEmpty()) {
                        item(key = "empty_vault_locked") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Your private vault is empty. Move notes to Vault from the Notes tab.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        item(key = "locked_vault_header") {
                            Text(
                                text = "LOCKED ITEMS (${currentDisplayNotes.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }

                        items(
                            items = currentDisplayNotes,
                            key = { it.id }
                        ) { note ->
                            LockedVaultNoteCard(
                                note = note,
                                onClick = { viewModel.openUnlockVaultDialog() }
                            )
                        }
                    }
                }
            } else if (currentDisplayNotes.isEmpty() && !uiState.isLoading) {
                EmptyNotesView(
                    isSearchActive = uiState.searchQuery.isNotEmpty() || uiState.selectedTag != null,
                    onCreateNote = onCreateNewNote,
                    onClearFilter = {
                        viewModel.onSearchQueryChanged("")
                        viewModel.onTagSelected(null)
                    }
                )
            } else {
                val pinnedNotes = remember(currentDisplayNotes) { currentDisplayNotes.filter { it.pinned } }
                val otherNotes = remember(currentDisplayNotes) { currentDisplayNotes.filterNot { it.pinned } }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isVaultTab) {
                        item(key = "vault_unlocked_banner") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LockOpen,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Vault Unlocked",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Auto-locks after 1m idle",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { viewModel.lockVaultNow() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Lock Vault", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }

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
                            isVaultTab = isVaultTab,
                            onClick = { onNoteClick(note) },
                            onDelete = { viewModel.deleteNote(note) },
                            onTogglePin = { viewModel.onTogglePin(note) },
                            onSyncStatusClick = { onSyncStatusClick(note) },
                            onMoveToVault = { viewModel.moveToVault(note.id, toVault = !isVaultTab) }
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
                            isVaultTab = isVaultTab,
                            onClick = { onNoteClick(note) },
                            onDelete = { viewModel.deleteNote(note) },
                            onTogglePin = { viewModel.onTogglePin(note) },
                            onSyncStatusClick = { onSyncStatusClick(note) },
                            onMoveToVault = { viewModel.moveToVault(note.id, toVault = !isVaultTab) }
                        )
                    }
                }
            }
        }
    }
}
}

private fun getGreetingText(customName: String): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greetingPrefix = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..22 -> "Good Evening"
        else -> "Good Night"
    }
    return if (customName.isNotBlank()) "$greetingPrefix, $customName 👋" else "$greetingPrefix 👋"
}

private fun getFormattedDateDay(): String {
    val sdf = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date())
}
