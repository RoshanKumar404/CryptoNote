package com.example.cryptnote.domain.repository

import com.example.cryptnote.domain.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing notes.
 *
 * Implements clean architecture boundary between domain logic
 * and data storage (Room DB + encryption layer).
 */
interface NoteRepository {

    /**
     * Observes all active (non-deleted) notes.
     */
    fun getAllNotes(): Flow<List<Note>>

    /**
     * Observes a single note by ID.
     */
    fun getNoteById(id: String): Flow<Note?>

    /**
     * Saves a note (inserts new or updates existing).
     */
    suspend fun saveNote(note: Note)

    /**
     * Soft-deletes a note by ID.
     */
    suspend fun deleteNote(id: String)

    /**
     * Observes notes involved in synchronization conflicts.
     */
    fun getConflictNotes(): Flow<List<Note>>

    /**
     * Observes paired conflicting notes grouped by conflictGroupId.
     */
    fun getConflictPairs(): Flow<List<com.example.cryptnote.domain.model.ConflictPair>>

    /**
     * Observes notes stored in the encrypted vault (is_vault = 1).
     */
    fun getVaultNotes(): Flow<List<Note>>

    /**
     * Moves a note between standard Notes and the Vault.
     * Updates isVault in both the local entity and re-encrypts payload via outbox.
     */
    suspend fun moveToVault(id: String, toVault: Boolean)

    /**
     * Moves a damaged/unreadable note to the quarantine table and removes it from active list.
     */
    suspend fun quarantineNote(id: String, reason: String)

    /**
     * Resolves a synchronization conflict for notes identified by [conflictGroupId].
     */
    suspend fun resolveConflict(
        conflictGroupId: String,
        action: com.example.cryptnote.domain.model.ConflictResolutionAction
    )
}
