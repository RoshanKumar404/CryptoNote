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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesListUiState(
    val isLoading: Boolean = false,
    val notes: List<Note> = emptyList(),
    val searchQuery: String = "",
    val conflictCount: Int = 0,
    val conflictPairs: List<ConflictPair> = emptyList(),
    val selectedConflictPair: ConflictPair? = null
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
    private val resolveConflictUseCase: ResolveConflictUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedConflictPair = MutableStateFlow<ConflictPair?>(null)
    private var lastDeletedNote: Note? = null

    private val _eventFlow = MutableSharedFlow<NotesListEvent>()
    val eventFlow: SharedFlow<NotesListEvent> = _eventFlow.asSharedFlow()

    private val _debouncedQuery = _searchQuery.debounce { query ->
        if (query.isEmpty()) 0L else 300L
    }

    private val _filteredNotes = combine(
        noteRepository.getAllNotes(),
        _debouncedQuery
    ) { notes, query ->
        searchNotesUseCase(notes, query)
    }

    val uiState: StateFlow<NotesListUiState> = combine(
        _filteredNotes,
        noteRepository.getConflictPairs(),
        _searchQuery,
        _selectedConflictPair
    ) { filteredNotes, conflictPairs, rawQuery, selectedPair ->
        NotesListUiState(
            isLoading = false,
            notes = filteredNotes,
            searchQuery = rawQuery,
            conflictCount = conflictPairs.size,
            conflictPairs = conflictPairs,
            selectedConflictPair = selectedPair
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesListUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
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
