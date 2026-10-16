package com.example.cryptnote.domain.model

/**
 * Domain representation of a note used throughout the UI and business logic.
 *
 * @property id Unique identifier (UUID).
 * @property title Decrypted title of the note.
 * @property body Decrypted body/content of the note.
 * @property version Note version for optimistic concurrency and conflict detection.
 * @property updatedAt Timestamp of last modification in epoch milliseconds.
 * @property deleted Soft-delete flag (tombstone).
 * @property syncStatus Current synchronization status with remote server.
 */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val conflictGroupId: String? = null
)
