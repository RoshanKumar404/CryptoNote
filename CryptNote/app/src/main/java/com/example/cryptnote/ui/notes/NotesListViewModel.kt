package com.example.cryptnote.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptnote.domain.model.ConflictPair
import com.example.cryptnote.domain.model.ConflictResolutionAction
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.repository.NoteRepository
import com.example.cryptnote.domain.usecase.ResolveConflictUseCase
import com.example.cryptnote.domain.usecase.SearchNotesUseCase
import com.example.cryptnote.security.DecryptDiagnosticEntry
import com.example.cryptnote.security.DecryptDiagnostics
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.VaultLock
import com.example.cryptnote.security.VaultState
import com.example.cryptnote.security.biometric.BiometricAuthManager
import com.example.cryptnote.security.biometric.BiometricKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NotesTab {
    NOTES,
    VAULT
}

data class NotesListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentTab: NotesTab = NotesTab.NOTES,
    val notes: List<Note> = emptyList(),
    val vaultNotes: List<Note> = emptyList(),
    val isVaultLocked: Boolean = true,
    val isVaultUnlocking: Boolean = false,
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList(),
    val conflictCount: Int = 0,
    val conflictPairs: List<ConflictPair> = emptyList(),
    val selectedConflictPair: ConflictPair? = null,
    val selectedDecryptionErrorNote: Note? = null,
    val showUnlockVaultDialog: Boolean = false,
    val unlockVaultError: String? = null,
    val showDiagnosticsSheet: Boolean = false,
    val recentDiagnostics: List<DecryptDiagnosticEntry> = emptyList()
)

sealed interface NotesListEvent {
    data class ShowUndoSnackbar(val noteId: String, val noteTitle: String) : NotesListEvent
    data class ShowMessage(val message: String) : NotesListEvent
    data class RequestBiometricUnlock(val cipher: javax.crypto.Cipher) : NotesListEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val authRepository: com.example.cryptnote.data.repository.AuthRepository,
    private val searchNotesUseCase: SearchNotesUseCase,
    private val resolveConflictUseCase: ResolveConflictUseCase,
    private val syncScheduler: com.example.cryptnote.sync.SyncScheduler? = null,
    private val vaultLock: VaultLock? = null,
    private val keySessionManager: KeySessionManager? = null,
    private val decryptDiagnostics: DecryptDiagnostics? = null,
    private val biometricAuthManager: BiometricAuthManager? = null,
    private val biometricKeyManager: BiometricKeyManager? = null
) : ViewModel() {

    private val effectiveVaultLock: VaultLock = vaultLock ?: VaultLock(viewModelScope, {})

    private val _currentTab = MutableStateFlow(NotesTab.NOTES)
    val currentTab: StateFlow<NotesTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _selectedConflictPair = MutableStateFlow<ConflictPair?>(null)
    private val _selectedDecryptionErrorNote = MutableStateFlow<Note?>(null)
    private val _showUnlockVaultDialog = MutableStateFlow(false)
    private val _unlockVaultError = MutableStateFlow<String?>(null)
    private val _showDiagnosticsSheet = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private var lastDeletedNote: Note? = null

    private val _eventFlow = MutableSharedFlow<NotesListEvent>()
    val eventFlow: SharedFlow<NotesListEvent> = _eventFlow.asSharedFlow()

    private val _debouncedQuery = _searchQuery.debounce { query ->
        if (query.isEmpty()) 0L else 300L
    }

    private val _allNotes = noteRepository.getAllNotes()
    private val _allVaultNotes = noteRepository.getVaultNotes()

    private val _filteredNotes = combine(
        _allNotes,
        _debouncedQuery,
        _selectedTag
    ) { notes, query, tag ->
        searchNotesUseCase(notes, query, tag)
    }

    private val _filteredVaultNotes = combine(
        _allVaultNotes,
        _debouncedQuery,
        _selectedTag,
        effectiveVaultLock.state
    ) { notes, query, tag, state ->
        if (state == VaultState.Unlocked) {
            searchNotesUseCase(notes, query, tag)
        } else {
            // While locked, vault notes never appear in search (NV-FE-5)
            notes
        }
    }

    private val _availableTags = _allNotes.map { notes ->
        notes.filterNot { it.deleted }
            .flatMap { it.tags }
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    private data class DialogState(
        val isRefreshing: Boolean,
        val decNote: Note?,
        val showUnlock: Boolean,
        val unlockErr: String?,
        val showDiag: Boolean
    )

    val uiState: StateFlow<NotesListUiState> = combine(
        combine(_filteredNotes, _filteredVaultNotes, _currentTab) { notes, vaultNotes, tab ->
            Triple(notes, vaultNotes, tab)
        },
        combine(effectiveVaultLock.state, _availableTags, _searchQuery) { vState, tags, query ->
            Triple(vState, tags, query)
        },
        combine(noteRepository.getConflictPairs(), _selectedConflictPair) { pairs, selectedPair ->
            Pair(pairs, selectedPair)
        },
        combine(
            _isRefreshing,
            _selectedDecryptionErrorNote,
            _showUnlockVaultDialog,
            _unlockVaultError,
            _showDiagnosticsSheet
        ) { isRef, decNote, showUnlock, unlockErr, showDiag ->
            DialogState(isRef, decNote, showUnlock, unlockErr, showDiag)
        }
    ) { (notes, vaultNotes, tab), (vState, tags, query), (conflictPairs, selectedPair), dialogState ->
        NotesListUiState(
            isLoading = false,
            isRefreshing = dialogState.isRefreshing,
            currentTab = tab,
            notes = notes,
            vaultNotes = vaultNotes,
            isVaultLocked = vState != VaultState.Unlocked,
            isVaultUnlocking = vState == VaultState.Unlocking,
            searchQuery = query,
            selectedTag = _selectedTag.value,
            availableTags = tags,
            conflictCount = conflictPairs.size,
            conflictPairs = conflictPairs,
            selectedConflictPair = selectedPair,
            selectedDecryptionErrorNote = dialogState.decNote,
            showUnlockVaultDialog = dialogState.showUnlock,
            unlockVaultError = dialogState.unlockErr,
            showDiagnosticsSheet = dialogState.showDiag,
            recentDiagnostics = decryptDiagnostics?.getRecent() ?: emptyList()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesListUiState(isLoading = true)
    )

    fun selectTab(tab: NotesTab) {
        if (_currentTab.value == NotesTab.VAULT && tab != NotesTab.VAULT) {
            // Leaving the Vault route: per NV-FE-6, lock immediately
            effectiveVaultLock.lock()
        }
        _currentTab.value = tab
        if (tab == NotesTab.VAULT) {
            effectiveVaultLock.touch()
        }
    }

    fun onVaultTouched() {
        if (_currentTab.value == NotesTab.VAULT) {
            effectiveVaultLock.touch()
        }
    }

    fun lockVaultNow() {
        effectiveVaultLock.lock()
    }

    fun openUnlockVaultDialog() {
        _unlockVaultError.value = null
        _showUnlockVaultDialog.value = true
    }

    fun dismissUnlockVaultDialog() {
        _showUnlockVaultDialog.value = false
        _unlockVaultError.value = null
    }

    fun unlockVaultWithPassphrase(passphrase: CharArray) {
        val ksm = keySessionManager
        if (ksm == null) {
            effectiveVaultLock.onUnlocked()
            _showUnlockVaultDialog.value = false
            return
        }
        viewModelScope.launch {
            effectiveVaultLock.onUnlocking()
            val result = ksm.unlock(passphrase)
            result.fold(
                onSuccess = {
                    effectiveVaultLock.onUnlocked()
                    _showUnlockVaultDialog.value = false
                    _unlockVaultError.value = null
                },
                onFailure = { error ->
                    effectiveVaultLock.lock()
                    _unlockVaultError.value = error.message ?: "Incorrect passphrase"
                }
            )
        }
    }

    fun canUnlockWithBiometric(): Boolean {
        return keySessionManager?.canUnlockWithBiometric() == true && biometricKeyManager != null
    }

    fun triggerBiometricUnlock() {
        val ksm = keySessionManager ?: return
        val bkm = biometricKeyManager ?: return
        if (!ksm.canUnlockWithBiometric()) return
        val iv = ksm.getBiometricIv() ?: ByteArray(0)
        try {
            val cipher = bkm.createDecryptCipher(iv)
            viewModelScope.launch {
                _eventFlow.emit(NotesListEvent.RequestBiometricUnlock(cipher))
            }
        } catch (e: Exception) {
            _unlockVaultError.value = "Biometric initialization error: ${e.message}"
        }
    }

    fun onBiometricUnlockSuccess(cipher: javax.crypto.Cipher) {
        val ksm = keySessionManager ?: return
        viewModelScope.launch {
            effectiveVaultLock.onUnlocking()
            val result = ksm.unlockWithBiometric(cipher)
            result.fold(
                onSuccess = {
                    effectiveVaultLock.onUnlocked()
                    _showUnlockVaultDialog.value = false
                    _unlockVaultError.value = null
                },
                onFailure = { error ->
                    effectiveVaultLock.lock()
                    _unlockVaultError.value = error.message ?: "Biometric verification failed"
                }
            )
        }
    }

    fun moveToVault(noteId: String, toVault: Boolean) {
        viewModelScope.launch {
            try {
                noteRepository.moveToVault(noteId, toVault)
                val msg = if (toVault) "Moved note to Vault" else "Moved note out of Vault"
                _eventFlow.emit(NotesListEvent.ShowMessage(msg))
            } catch (e: Exception) {
                _eventFlow.emit(NotesListEvent.ShowMessage("Failed to move note: ${e.message}"))
            }
        }
    }

    fun quarantineNote(noteId: String, reason: String) {
        viewModelScope.launch {
            try {
                noteRepository.quarantineNote(noteId, reason)
                _selectedDecryptionErrorNote.value = null
                _eventFlow.emit(NotesListEvent.ShowMessage("Note moved to quarantine"))
            } catch (e: Exception) {
                _eventFlow.emit(NotesListEvent.ShowMessage("Failed to quarantine note: ${e.message}"))
            }
        }
    }

    fun toggleDiagnosticsSheet(show: Boolean) {
        _showDiagnosticsSheet.value = show
    }

    fun refreshNotes() {
        viewModelScope.launch {
            _isRefreshing.value = true
            syncScheduler?.scheduleImmediate()
            kotlinx.coroutines.delay(1200)
            _isRefreshing.value = false
        }
    }

    fun onDecryptionErrorNoteSelected(note: Note) {
        _selectedDecryptionErrorNote.value = note
    }

    fun onDismissDecryptionErrorDialog() {
        _selectedDecryptionErrorNote.value = null
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTagSelected(tag: String?) {
        _selectedTag.value = if (_selectedTag.value == tag) null else tag
    }

    fun onTogglePin(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(
                pinned = !note.pinned,
                updatedAt = System.currentTimeMillis()
            )
            noteRepository.saveNote(updated)
        }
    }

    fun onConflictSelected(conflictGroupId: String) {
        viewModelScope.launch {
            val pairs = noteRepository.getConflictPairs().first()
            val found = pairs.find { it.conflictGroupId == conflictGroupId }
                ?: pairs.firstOrNull()
            _selectedConflictPair.value = found
        }
    }

    fun selectConflictPair(pair: ConflictPair) {
        _selectedConflictPair.value = pair
    }

    fun onDismissConflictDialog() {
        _selectedConflictPair.value = null
    }

    fun resolveConflict(conflictGroupId: String, action: ConflictResolutionAction) {
        viewModelScope.launch {
            try {
                resolveConflictUseCase(conflictGroupId, action)
                _selectedConflictPair.value = null
                _eventFlow.emit(NotesListEvent.ShowMessage("Conflict resolved"))
            } catch (e: Exception) {
                _eventFlow.emit(NotesListEvent.ShowMessage("Failed to resolve conflict: ${e.message}"))
            }
        }
    }

    fun deleteNote(note: Note) {
        lastDeletedNote = note
        viewModelScope.launch {
            noteRepository.deleteNote(note.id)
            _eventFlow.emit(
                NotesListEvent.ShowUndoSnackbar(
                    noteId = note.id,
                    noteTitle = note.title.ifBlank { "Untitled Note" }
                )
            )
        }
    }

    fun restoreLastDeletedNote() {
        val noteToRestore = lastDeletedNote ?: return
        viewModelScope.launch {
            noteRepository.saveNote(noteToRestore)
            lastDeletedNote = null
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
