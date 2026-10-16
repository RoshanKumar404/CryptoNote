package com.example.cryptnote.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesListUiState(
    val isLoading: Boolean = false,
    val notes: List<Note> = emptyList(),
    val searchQuery: String = "",
    val conflictCount: Int = 0
)

sealed interface NotesListEvent {
    data class ShowUndoSnackbar(val noteId: String, val noteTitle: String) : NotesListEvent
}

@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private var lastDeletedNote: Note? = null

    private val _eventFlow = MutableSharedFlow<NotesListEvent>()
    val eventFlow: SharedFlow<NotesListEvent> = _eventFlow.asSharedFlow()

    val uiState: StateFlow<NotesListUiState> = combine(
        noteRepository.getAllNotes(),
        noteRepository.getConflictNotes(),
        _searchQuery
    ) { allNotes, conflictNotes, query ->
        val filteredNotes = if (query.isBlank()) {
            allNotes
        } else {
            allNotes.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                    note.body.contains(query, ignoreCase = true)
            }
        }

        NotesListUiState(
            isLoading = false,
            notes = filteredNotes,
            searchQuery = query,
            conflictCount = conflictNotes.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesListUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
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
}
