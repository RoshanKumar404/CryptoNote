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
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isPreviewMode: Boolean = false,
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
                            tags = existing.tags,
                            isPinned = existing.pinned,
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

    fun onAddTag(rawTag: String) {
        val cleanTag = rawTag.trim().removePrefix("#")
        if (cleanTag.isNotBlank() && !_uiState.value.tags.contains(cleanTag)) {
            _uiState.update { it.copy(tags = it.tags + cleanTag) }
            scheduleAutoSave()
        }
    }

    fun onRemoveTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags - tag) }
        scheduleAutoSave()
    }

    fun onTogglePin() {
        _uiState.update { it.copy(isPinned = !it.isPinned) }
        scheduleAutoSave()
    }

    fun onTogglePreview() {
        _uiState.update { it.copy(isPreviewMode = !it.isPreviewMode) }
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
        // Only save if there is content or tags to persist
        if (currentState.title.isBlank() && currentState.body.isBlank() && currentState.tags.isEmpty()) {
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        val note = Note(
            id = currentState.noteId,
            title = currentState.title.trim(),
            body = currentState.body.trim(),
            tags = currentState.tags,
            pinned = currentState.isPinned,
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
