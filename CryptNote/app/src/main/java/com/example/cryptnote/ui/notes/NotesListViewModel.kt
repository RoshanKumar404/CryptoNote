package com.example.cryptnote.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptnote.domain.model.ConflictPair
import com.example.cryptnote.domain.model.ConflictResolutionAction
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.repository.NoteRepository
import com.example.cryptnote.domain.usecase.ResolveConflictUseCase
import com.example.cryptnote.domain.usecase.SearchNotesUseCase
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

data class NotesListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val notes: List<Note> = emptyList(),
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList(),
    val conflictCount: Int = 0,
    val conflictPairs: List<ConflictPair> = emptyList(),
    val selectedConflictPair: ConflictPair? = null,
    val selectedDecryptionErrorNote: Note? = null
)

sealed interface NotesListEvent {
    data class ShowUndoSnackbar(val noteId: String, val noteTitle: String) : NotesListEvent
    data class ShowMessage(val message: String) : NotesListEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val authRepository: com.example.cryptnote.data.repository.AuthRepository,
    private val searchNotesUseCase: SearchNotesUseCase,
    private val resolveConflictUseCase: ResolveConflictUseCase,
    private val syncScheduler: com.example.cryptnote.sync.SyncScheduler? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _selectedConflictPair = MutableStateFlow<ConflictPair?>(null)
    private val _selectedDecryptionErrorNote = MutableStateFlow<Note?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private var lastDeletedNote: Note? = null

    private val _eventFlow = MutableSharedFlow<NotesListEvent>()
    val eventFlow: SharedFlow<NotesListEvent> = _eventFlow.asSharedFlow()

    private val _debouncedQuery = _searchQuery.debounce { query ->
        if (query.isEmpty()) 0L else 300L
    }

    private val _allNotes = noteRepository.getAllNotes()

    private val _filteredNotes = combine(
        _allNotes,
        _debouncedQuery,
        _selectedTag
    ) { notes, query, tag ->
        searchNotesUseCase(notes, query, tag)
    }

    private val _availableTags = _allNotes.map { notes ->
        notes.filterNot { it.deleted }
            .flatMap { it.tags }
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val uiState: StateFlow<NotesListUiState> = combine(
        combine(_filteredNotes, _availableTags, _searchQuery) { notes, tags, query ->
            Triple(notes, tags, query)
        },
        combine(noteRepository.getConflictPairs(), _selectedConflictPair) { pairs, selectedPair ->
            Pair(pairs, selectedPair)
        },
        combine(_isRefreshing, _selectedDecryptionErrorNote) { isRef, decNote ->
            Pair(isRef, decNote)
        }
    ) { (notes, tags, query), (conflictPairs, selectedPair), (isRef, decNote) ->
        NotesListUiState(
            isLoading = false,
            isRefreshing = isRef,
            notes = notes,
            searchQuery = query,
            selectedTag = _selectedTag.value,
            availableTags = tags,
            conflictCount = conflictPairs.size,
            conflictPairs = conflictPairs,
            selectedConflictPair = selectedPair,
            selectedDecryptionErrorNote = decNote
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesListUiState(isLoading = true)
    )

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
