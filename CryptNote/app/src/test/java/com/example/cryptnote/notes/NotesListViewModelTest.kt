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

    @Test
    fun `tag selection filters notes and updates availableTags`() = runTest {
        val note1 = createNote(id = "1", title = "Work 1", tags = listOf("work"))
        val note2 = createNote(id = "2", title = "Work 2", tags = listOf("work", "crypto"))
        val note3 = createNote(id = "3", title = "Personal", tags = listOf("personal"))
        fakeNoteRepository.notesFlow.value = listOf(note1, note2, note3)

        val job = launch { viewModel.uiState.collect {} }
        advanceTimeBy(350)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.notes.size)
        assertEquals(listOf("crypto", "personal", "work"), state.availableTags)

        viewModel.onTagSelected("work")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals("work", viewModel.uiState.value.selectedTag)
        assertEquals(2, viewModel.uiState.value.notes.size)

        // Toggling same tag unselects it
        viewModel.onTagSelected("work")
        advanceTimeBy(350)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedTag)
        assertEquals(3, viewModel.uiState.value.notes.size)

        job.cancel()
    }

    @Test
    fun `onTogglePin saves note with toggled pinned flag`() = runTest {
        val note = createNote(id = "pinned-test", title = "Pin Me", pinned = false)
        fakeNoteRepository.notesFlow.value = listOf(note)

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onTogglePin(note)
        advanceUntilIdle()

        assertNotNull(fakeNoteRepository.lastSavedNote)
        assertEquals("pinned-test", fakeNoteRepository.lastSavedNote?.id)
        assertEquals(true, fakeNoteRepository.lastSavedNote?.pinned)

        job.cancel()
    }

    @Test
    fun `selectTab switches between NOTES and VAULT and leaving vault locks it`() = runTest {
        val job = launch { viewModel.uiState.collect {} }
        advanceTimeBy(350)
        advanceUntilIdle()

        // Default tab is NOTES (NV-FE-5)
        assertEquals(com.example.cryptnote.ui.notes.NotesTab.NOTES, viewModel.uiState.value.currentTab)

        // Select VAULT
        viewModel.selectTab(com.example.cryptnote.ui.notes.NotesTab.VAULT)
        advanceUntilIdle()
        assertEquals(com.example.cryptnote.ui.notes.NotesTab.VAULT, viewModel.uiState.value.currentTab)

        // Leaving VAULT route (switching to NOTES) locks vault (NV-FE-6)
        viewModel.selectTab(com.example.cryptnote.ui.notes.NotesTab.NOTES)
        advanceUntilIdle()
        assertEquals(com.example.cryptnote.ui.notes.NotesTab.NOTES, viewModel.uiState.value.currentTab)
        assertTrue(viewModel.uiState.value.isVaultLocked)

        job.cancel()
    }

    @Test
    fun `moveToVault calls repository with correct parameters`() = runTest {
        viewModel.moveToVault("test-id-1", toVault = true)
        advanceUntilIdle()

        assertEquals("test-id-1", fakeNoteRepository.lastMovedToVaultId)
        assertEquals(true, fakeNoteRepository.lastMovedToVaultFlag)

        viewModel.moveToVault("test-id-1", toVault = false)
        advanceUntilIdle()

        assertEquals("test-id-1", fakeNoteRepository.lastMovedToVaultId)
        assertEquals(false, fakeNoteRepository.lastMovedToVaultFlag)
    }

    @Test
    fun `quarantineNote calls repository and preserves reason`() = runTest {
        viewModel.quarantineNote("damaged-note-id", "Damaged checksum")
        advanceUntilIdle()

        assertEquals("damaged-note-id", fakeNoteRepository.lastQuarantinedId)
        assertEquals("Damaged checksum", fakeNoteRepository.lastQuarantinedReason)
    }

    @Test
    fun `onDecryptionErrorNoteSelected and onDismissDecryptionErrorDialog updates uiState`() = runTest {
        val job = launch { viewModel.uiState.collect {} }
        advanceTimeBy(350)
        advanceUntilIdle()

        val damagedNote = createNote(id = "err-1", title = "⚠️ Damaged Note", body = "This note is damaged on this device.")
        viewModel.onDecryptionErrorNoteSelected(damagedNote)
        advanceUntilIdle()

        assertEquals("err-1", viewModel.uiState.value.selectedDecryptionErrorNote?.id)

        viewModel.onDismissDecryptionErrorDialog()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedDecryptionErrorNote)
        job.cancel()
    }

    private fun createNote(
        id: String,
        title: String,
        body: String = "",
        conflictGroupId: String? = null,
        tags: List<String> = emptyList(),
        pinned: Boolean = false
    ): Note {
        return Note(
            id = id,
            title = title,
            body = body,
            version = 1,
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            syncStatus = SyncStatus.SYNCED,
            conflictGroupId = conflictGroupId,
            tags = tags,
            pinned = pinned
        )
    }

    private class FakeNoteRepository : NoteRepository {
        val notesFlow = MutableStateFlow<List<Note>>(emptyList())
        val conflictPairsFlow = MutableStateFlow<List<ConflictPair>>(emptyList())
        val vaultNotesFlow = MutableStateFlow<List<Note>>(emptyList())
        var lastDeletedNoteId: String? = null
        var lastSavedNote: Note? = null
        var lastResolvedGroupId: String? = null
        var lastResolvedAction: ConflictResolutionAction? = null
        var lastMovedToVaultId: String? = null
        var lastMovedToVaultFlag: Boolean? = null
        var lastQuarantinedId: String? = null
        var lastQuarantinedReason: String? = null

        override fun getAllNotes(): Flow<List<Note>> = notesFlow
        override fun getNoteById(id: String): Flow<Note?> = flowOf(notesFlow.value.find { it.id == id })
        override suspend fun saveNote(note: Note) { lastSavedNote = note }
        override suspend fun deleteNote(id: String) { lastDeletedNoteId = id }
        override suspend fun restoreNote(id: String) {}
        override fun getConflictNotes(): Flow<List<Note>> = flowOf(emptyList())
        override fun getConflictPairs(): Flow<List<ConflictPair>> = conflictPairsFlow
        override fun getVaultNotes(): Flow<List<Note>> = vaultNotesFlow
        override suspend fun moveToVault(id: String, toVault: Boolean) {
            lastMovedToVaultId = id
            lastMovedToVaultFlag = toVault
        }
        override suspend fun quarantineNote(id: String, reason: String) {
            lastQuarantinedId = id
            lastQuarantinedReason = reason
        }
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
        override suspend fun resetAccount(email: String, passphrase: CharArray): Result<Unit> = Result.success(Unit)
        override suspend fun logout() {
            loggedOut = true
            _authState.value = com.example.cryptnote.data.repository.AuthState.Unauthenticated
        }
        override suspend fun checkSession(): Result<Unit> = Result.success(Unit)
    }
}
