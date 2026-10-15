package com.example.cryptnote.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a pending sync operation in the outbox queue.
 *
 * Every local note mutation (create, update, delete) produces an outbox entry
 * within the same database transaction. The sync worker reads from this table
 * to push changes to the backend in strict local sequence order.
 *
 * @property seq Auto-incrementing local sequence key ensuring deterministic FIFO processing.
 * @property opId Client-generated UUID used as an idempotency key on the server.
 * @property noteId The ID of the note this operation applies to.
 * @property type The operation type: "create", "update", or "delete".
 * @property baseVersion Version of the note this edit was based on (0 for create).
 * @property version Proposed new version of the note (baseVersion + 1).
 * @property payload JSON-encoded operation payload (ciphertext + nonce for create/update).
 * @property attempts Number of push attempts (for backoff calculation).
 * @property status Current status: "pending", "sent", "conflict", or "failed".
 * @property createdAt Epoch milliseconds when this outbox entry was created (informational).
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["op_id"], unique = true),
        Index(value = ["note_id", "seq"])
    ]
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val seq: Long = 0L,

    @ColumnInfo(name = "op_id")
    val opId: String,

    @ColumnInfo(name = "note_id")
    val noteId: String,

    val type: String,

    @ColumnInfo(name = "base_version")
    val baseVersion: Int = 0,

    val version: Int = 1,

    val payload: String,

    val attempts: Int = 0,

    val status: String = "pending",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
