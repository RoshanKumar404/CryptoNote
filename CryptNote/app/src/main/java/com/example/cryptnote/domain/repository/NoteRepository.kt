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
}
