package com.example.cryptnote.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.cryptnote.data.local.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the [NoteEntity] table.
 *
 * All queries return [Flow] for reactive UI updates.
 * Write operations are suspend functions for coroutine integration.
 */
@Dao
interface NoteDao {

    /**
     * Observe all non-deleted non-vault notes, ordered by most recently updated first.
     */
    @Query("SELECT * FROM notes WHERE deleted = 0 AND is_vault = 0 ORDER BY updated_at DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    /**
     * Observe all non-deleted vault notes, ordered by most recently updated first.
     */
    @Query("SELECT * FROM notes WHERE deleted = 0 AND is_vault = 1 ORDER BY updated_at DESC")
    fun observeVaultNotes(): Flow<List<NoteEntity>>

    /**
     * Update the is_vault flag of a specific note.
     */
    @Query("UPDATE notes SET is_vault = :isVault WHERE id = :noteId")
    suspend fun updateVaultFlag(noteId: String, isVault: Boolean)

    /**
     * Observe a single note by its ID.
     * Returns null via Flow if the note doesn't exist.
     */
    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun observeNoteById(noteId: String): Flow<NoteEntity?>

    /**
     * Get a single note by ID (non-reactive, for one-shot reads).
     */
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?

    /**
     * Get all notes that have a specific sync status.
     */
    @Query("SELECT * FROM notes WHERE sync_status = :status AND deleted = 0")
    suspend fun getNotesBySyncStatus(status: String): List<NoteEntity>

    /**
     * Insert a new note. If a note with the same ID already exists,
     * replace it (upsert behavior for sync).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    /**
     * Update an existing note.
     */
    @Update
    suspend fun updateNote(note: NoteEntity)

    /**
     * Soft-delete a note by setting the tombstone flag.
     * The note remains in the database for sync purposes.
     */
    @Query("UPDATE notes SET deleted = 1, sync_status = 'pending', updated_at = :updatedAt WHERE id = :noteId")
    suspend fun softDeleteNote(noteId: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * Update the sync status of a specific note.
     */
    @Query("UPDATE notes SET sync_status = :status WHERE id = :noteId")
    suspend fun updateSyncStatus(noteId: String, status: String)

    /**
     * Hard-delete notes that have been soft-deleted and successfully synced.
     * Called periodically to clean up tombstones.
     */
    @Query("DELETE FROM notes WHERE deleted = 1 AND sync_status = 'synced'")
    suspend fun purgeSyncedDeletedNotes()

    /**
     * Get the count of notes with pending sync operations.
     */
    @Query("SELECT COUNT(*) FROM notes WHERE sync_status = 'pending'")
    fun observePendingCount(): Flow<Int>

    /**
     * Get notes belonging to a specific conflict group.
     */
    @Query("SELECT * FROM notes WHERE conflict_group_id = :conflictGroupId AND deleted = 0")
    suspend fun getNotesByConflictGroup(conflictGroupId: String): List<NoteEntity>

    /**
     * Clear conflict group association for a note.
     */
    @Query("UPDATE notes SET conflict_group_id = NULL WHERE id = :noteId")
    suspend fun clearConflictGroup(noteId: String)

    /**
     * Observe all active notes with conflict groups.
     */
    @Query("SELECT * FROM notes WHERE conflict_group_id IS NOT NULL AND deleted = 0")
    fun observeConflictNotes(): Flow<List<NoteEntity>>

    /**
     * Hard-delete a note by ID (used when an unsent local draft is cancelled/deleted before push).
     */
    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)
}
