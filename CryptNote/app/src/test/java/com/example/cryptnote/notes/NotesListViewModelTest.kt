package com.example.cryptnote.notes

import com.example.cryptnote.domain.model.ConflictPair
import com.example.cryptnote.domain.model.ConflictResolutionAction
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.domain.repository.NoteRepository
import com.example.cryptnote.domain.usecase.ResolveConflictUseCase
import com.example.cryptnote.domain.usecase.SearchNotesUseCase
import com.example.cryptnote.ui.notes.NotesListEvent
import com.example.cryptnote.ui.notes.NotesListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeNoteRepository: FakeNoteRepository
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var searchNotesUseCase: SearchNotesUseCase
    private lateinit var resolveConflictUseCase: ResolveConflictUseCase
    private lateinit var viewModel: NotesListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeNoteRepository = FakeNoteRepository()
        fakeAuthRepository = FakeAuthRepository()
        searchNotesUseCase = SearchNotesUseCase()
        resolveConflictUseCase = ResolveConflictUseCase(fakeNoteRepository)

        viewModel = NotesListViewModel(
            noteRepository = fakeNoteRepository,
            authRepository = fakeAuthRepository,
            searchNotesUseCase = searchNotesUseCase,
            resolveConflictUseCase = resolveConflictUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState loads notes and conflict pairs`() = runTest {
        val note1 = createNote(id = "1", title = "Note 1")
        val note2 = createNote(id = "2", title = "Note 2")
        fakeNoteRepository.notesFlow.value = listOf(note1, note2)

        val conflictPair = ConflictPair(
            conflictGroupId = "group-1",
            localNote = note1,
            serverNote = note2
        )
        fakeNoteRepository.conflictPairsFlow.value = listOf(conflictPair)

        val job = launch { viewModel.uiState.collect {} }
        advanceTimeBy(350)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.notes.size)
        assertEquals(1, state.conflictCount)
        assertEquals(1, state.conflictPairs.size)
        assertNull(state.selectedConflictPair)

        job.cancel()
    }

    @Test
    fun `search query filters notes with 300ms debounce`() = runTest {
        val note1 = createNote(id = "1", title = "Bitcoin Wallet")
        val note2 = createNote(id = "2", title = "Groceries")
        fakeNoteRepository.notesFlow.value = listOf(note1, note2)

        val job = launch { viewModel.uiState.collect {} }
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.notes.size)

        // Change search query
        viewModel.onSearchQueryChanged("bitcoin")
        assertEquals("bitcoin", viewModel.searchQuery.value)

        // Before debounce duration expires, still previous list
        advanceTimeBy(100)
        assertEquals(2, viewModel.uiState.value.notes.size)

        // After 300ms debounce completes
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals("bitcoin", viewModel.uiState.value.searchQuery)
        val filtered = viewModel.uiState.value.notes
        assertEquals(1, filtered.size)
        assertEquals("Bitcoin Wallet", filtered[0].title)

        job.cancel()
    }

    @Test
    fun `onConflictSelected and onDismissConflictDialog updates selected pair`() = runTest {
        val noteA = createNote(id = "a", title = "Local")
        val noteB = createNote(id = "b", title = "Server")
        val pair = ConflictPair("group-xyz", noteA, noteB)
        fakeNoteRepository.conflictPairsFlow.value = listOf(pair)

        val job = launch { viewModel.uiState.collect {} }
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.onConflictSelected("group-xyz")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.selectedConflictPair)
        assertEquals("group-xyz", viewModel.uiState.value.selectedConflictPair?.conflictGroupId)

        viewModel.onDismissConflictDialog()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedConflictPair)

        job.cancel()
    }

    @Test
    fun `resolveConflict delegates to use case and clears dialog`() = runTest {
        val noteA = createNote(id = "a", title = "Local")
        val noteB = createNote(id = "b", title = "Server")
        val pair = ConflictPair("group-xyz", noteA, noteB)
        fakeNoteRepository.conflictPairsFlow.value = listOf(pair)

        val job = launch { viewModel.uiState.collect {} }
        advanceTimeBy(350)
        advanceUntilIdle()

        viewModel.onConflictSelected("group-xyz")
        advanceUntilIdle()
        viewModel.resolveConflict("group-xyz", ConflictResolutionAction.KeepLocal)
        advanceUntilIdle()

        assertEquals("group-xyz", fakeNoteRepository.lastResolvedGroupId)
        assertEquals(ConflictResolutionAction.KeepLocal, fakeNoteRepository.lastResolvedAction)
        assertNull(viewModel.uiState.value.selectedConflictPair)

        job.cancel()
    }

    @Test
    fun `deleteNote emits ShowUndoSnackbar and delete from repository`() = runTest {
        val note = createNote(id = "target", title = "Delete Me")
        fakeNoteRepository.notesFlow.value = listOf(note)

        val job = launch { viewModel.uiState.collect {} }
        advanceTimeBy(350)
        advanceUntilIdle()

        var receivedEvent: NotesListEvent? = null
        val eventJob = launch {
            viewModel.eventFlow.collect { receivedEvent = it }
        }

        viewModel.deleteNote(note)
        advanceUntilIdle()

        assertTrue(receivedEvent is NotesListEvent.ShowUndoSnackbar)
        val snackbarEvent = receivedEvent as NotesListEvent.ShowUndoSnackbar
        assertEquals("target", snackbarEvent.noteId)
        assertEquals("Delete Me", snackbarEvent.noteTitle)
        assertEquals("target", fakeNoteRepository.lastDeletedNoteId)

        job.cancel()
        eventJob.cancel()
    }

    private fun createNote(
        id: String,
        title: String,
        body: String = "",
        conflictGroupId: String? = null
    ): Note {
        return Note(
            id = id,
            title = title,
            body = body,
            version = 1,
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            syncStatus = SyncStatus.SYNCED,
            conflictGroupId = conflictGroupId
        )
    }

    private class FakeNoteRepository : NoteRepository {
        val notesFlow = MutableStateFlow<List<Note>>(emptyList())
        val conflictPairsFlow = MutableStateFlow<List<ConflictPair>>(emptyList())
        var lastDeletedNoteId: String? = null
        var lastSavedNote: Note? = null
        var lastResolvedGroupId: String? = null
        var lastResolvedAction: ConflictResolutionAction? = null

        override fun getAllNotes(): Flow<List<Note>> = notesFlow
        override fun getNoteById(id: String): Flow<Note?> = flowOf(notesFlow.value.find { it.id == id })
        override suspend fun saveNote(note: Note) { lastSavedNote = note }
        override suspend fun deleteNote(id: String) { lastDeletedNoteId = id }
        override fun getConflictNotes(): Flow<List<Note>> = flowOf(emptyList())
        override fun getConflictPairs(): Flow<List<ConflictPair>> = conflictPairsFlow
        override suspend fun resolveConflict(
            conflictGroupId: String,
            action: ConflictResolutionAction
        ) {
            lastResolvedGroupId = conflictGroupId
            lastResolvedAction = action
        }
    }

    private class FakeAuthRepository : com.example.cryptnote.data.repository.AuthRepository {
        private val _authState = MutableStateFlow<com.example.cryptnote.data.repository.AuthState>(
            com.example.cryptnote.data.repository.AuthState.Unauthenticated
        )
        override val authState: kotlinx.coroutines.flow.StateFlow<com.example.cryptnote.data.repository.AuthState> = _authState

        var loggedOut = false

        override fun hasActiveSession(): Boolean = false
        override suspend fun register(email: String, passphrase: CharArray): Result<Unit> = Result.success(Unit)
        override suspend fun login(email: String, passphrase: CharArray): Result<Unit> = Result.success(Unit)
        override suspend fun logout() {
            loggedOut = true
            _authState.value = com.example.cryptnote.data.repository.AuthState.Unauthenticated
        }
        override suspend fun checkSession(): Result<Unit> = Result.success(Unit)
    }
}
