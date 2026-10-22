package com.example.cryptnote.notes

import com.example.cryptnote.domain.model.ConflictPair
import com.example.cryptnote.domain.model.ConflictResolutionAction
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.domain.repository.NoteRepository
import com.example.cryptnote.domain.usecase.ResolveConflictUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolveConflictUseCaseTest {

    private lateinit var fakeRepository: FakeNoteRepository
    private lateinit var resolveConflictUseCase: ResolveConflictUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeNoteRepository()
        resolveConflictUseCase = ResolveConflictUseCase(fakeRepository)
    }

    @Test
    fun `blank conflictGroupId throws IllegalArgumentException`() = runTest {
        var threw = false
        try {
            resolveConflictUseCase("   ", ConflictResolutionAction.KeepLocal)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `keep local action forwards to repository`() = runTest {
        resolveConflictUseCase("group-123", ConflictResolutionAction.KeepLocal)
        assertEquals("group-123", fakeRepository.lastResolvedGroupId)
        assertEquals(ConflictResolutionAction.KeepLocal, fakeRepository.lastResolvedAction)
    }

    @Test
    fun `keep server action forwards to repository`() = runTest {
        resolveConflictUseCase("group-456", ConflictResolutionAction.KeepServer)
        assertEquals("group-456", fakeRepository.lastResolvedGroupId)
        assertEquals(ConflictResolutionAction.KeepServer, fakeRepository.lastResolvedAction)
    }

    @Test
    fun `custom merge action forwards merged fields to repository`() = runTest {
        val mergeAction = ConflictResolutionAction.CustomMerge(
            mergedTitle = "Consolidated Title",
            mergedBody = "Consolidated Body"
        )
        resolveConflictUseCase("group-789", mergeAction)
        assertEquals("group-789", fakeRepository.lastResolvedGroupId)
        assertEquals(mergeAction, fakeRepository.lastResolvedAction)
    }

    private class FakeNoteRepository : NoteRepository {
        var lastResolvedGroupId: String? = null
        var lastResolvedAction: ConflictResolutionAction? = null

        override fun getAllNotes(): Flow<List<Note>> = flowOf(emptyList())
        override fun getNoteById(id: String): Flow<Note?> = flowOf(null)
        override suspend fun saveNote(note: Note) {}
        override suspend fun deleteNote(id: String) {}
        override suspend fun restoreNote(id: String) {}
        override fun getConflictNotes(): Flow<List<Note>> = flowOf(emptyList())
        override fun getConflictPairs(): Flow<List<ConflictPair>> = flowOf(emptyList())
        override fun getVaultNotes(): Flow<List<Note>> = flowOf(emptyList())
        override suspend fun moveToVault(id: String, toVault: Boolean) {}
        override suspend fun quarantineNote(id: String, reason: String) {}

        override suspend fun resolveConflict(
            conflictGroupId: String,
            action: ConflictResolutionAction
        ) {
            lastResolvedGroupId = conflictGroupId
            lastResolvedAction = action
        }
    }
}
