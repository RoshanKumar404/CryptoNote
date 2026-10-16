package com.example.cryptnote.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class NoteEditorUiState(
    val noteId: String = "",
    val title: String = "",
    val body: String = "",
    val lastSavedAt: Long = 0L,
    val isSaving: Boolean = false,
    val isInitialized: Boolean = false
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteIdParam: String? = savedStateHandle.get<String>("noteId")
    private val isNewNote: Boolean = noteIdParam == null || noteIdParam == "new"
    private val noteId: String = if (isNewNote) UUID.randomUUID().toString() else noteIdParam!!

    private val _uiState = MutableStateFlow(NoteEditorUiState(noteId = noteId))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null
    private var initialLoaded = false

    init {
        if (!isNewNote) {
            viewModelScope.launch {
                val existing = noteRepository.getNoteById(noteId).firstOrNull()
                if (existing != null) {
                    _uiState.update {
                        it.copy(
                            title = existing.title,
                            body = existing.body,
                            lastSavedAt = existing.updatedAt,
                            isInitialized = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(isInitialized = true) }
                }
                initialLoaded = true
            }
        } else {
            _uiState.update { it.copy(isInitialized = true) }
            initialLoaded = true
        }
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
        scheduleAutoSave()
    }

    fun onBodyChanged(newBody: String) {
        _uiState.update { it.copy(body = newBody) }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        if (!initialLoaded) return

        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(2000) // 2-second debounce auto-save per PRD F1
            saveInternal()
        }
    }

    fun saveImmediately() {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            saveInternal()
        }
    }

    private suspend fun saveInternal() {
        val currentState = _uiState.value
        // Only save if there is content to persist
        if (currentState.title.isBlank() && currentState.body.isBlank()) {
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        val note = Note(
            id = currentState.noteId,
            title = currentState.title.trim(),
            body = currentState.body.trim(),
            updatedAt = System.currentTimeMillis()
        )

        noteRepository.saveNote(note)

        _uiState.update {
            it.copy(
                isSaving = false,
                lastSavedAt = note.updatedAt
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure changes are persisted when ViewModel is dismantled
        saveImmediately()
    }
}
