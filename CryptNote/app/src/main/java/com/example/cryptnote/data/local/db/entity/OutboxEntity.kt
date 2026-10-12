package com.example.cryptnote.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a pending sync operation in the outbox queue.
 *
 * Every local note mutation (create, update, delete) produces an outbox entry
 * within the same database transaction. The sync worker reads from this table
 * to push changes to the backend in order.
 *
 * @property opId Client-generated UUID used as an idempotency key on the server.
 * @property noteId The ID of the note this operation applies to.
 * @property type The operation type: "create", "update", or "delete".
 * @property payload JSON-encoded operation payload (ciphertext + nonce for create/update).
 * @property attempts Number of push attempts (for backoff calculation).
 * @property status Current status: "pending", "sent", or "failed".
 * @property createdAt Epoch milliseconds when this outbox entry was created (used for ordering).
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "op_id")
    val opId: String,

    @ColumnInfo(name = "note_id")
    val noteId: String,

    val type: String,

    val payload: String,

    val attempts: Int = 0,

    val status: String = "pending",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
