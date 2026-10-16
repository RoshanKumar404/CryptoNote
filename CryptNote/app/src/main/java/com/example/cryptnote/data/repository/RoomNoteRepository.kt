package com.example.cryptnote.data.repository

import androidx.room.withTransaction
import com.example.cryptnote.data.local.db.CryptNoteDatabase
import com.example.cryptnote.data.local.db.dao.NoteDao
import com.example.cryptnote.data.local.db.dao.OutboxDao
import com.example.cryptnote.data.local.db.entity.NoteEntity
import com.example.cryptnote.data.local.db.entity.OutboxEntity
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [NoteRepository] backing storage with Room SQLite.
 *
 * Enforces atomic note mutations paired with transactional outbox queuing.
 * In FE-2 (dev mode before FE-3 encryption lands), notes are serialized as
 * canonical JSON payloads inside the ciphertext column.
 */
@Singleton
class RoomNoteRepository @Inject constructor(
    private val database: CryptNoteDatabase,
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao
) : NoteRepository {

    override fun getAllNotes(): Flow<List<Note>> {
        return noteDao.observeAllNotes().map { entities ->
            entities.map { entity -> entity.toDomainModel() }
        }
    }

    override fun getNoteById(id: String): Flow<Note?> {
        return noteDao.observeNoteById(id).map { entity ->
            entity?.toDomainModel()
        }
    }

    override fun getConflictNotes(): Flow<List<Note>> {
        return noteDao.observeConflictNotes().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun saveNote(note: Note) {
        database.withTransaction {
            val existing = noteDao.getNoteById(note.id)
            val baseVersion = existing?.version ?: 0
            val newVersion = baseVersion + 1
            val now = System.currentTimeMillis()

            val jsonPayload = JSONObject().apply {
                put("title", note.title)
                put("body", note.body)
            }.toString()

            val ciphertextBytes = jsonPayload.toByteArray(Charsets.UTF_8)
            val nonceBytes = ByteArray(12) // Deterministic placeholder nonce for FE-2 dev mode

            val noteEntity = NoteEntity(
                id = note.id,
                ciphertext = ciphertextBytes,
                nonce = nonceBytes,
                version = newVersion,
                updatedAt = now,
                deleted = false,
                syncStatus = SyncStatus.PENDING.name.lowercase(),
                conflictGroupId = note.conflictGroupId
            )
            noteDao.insertNote(noteEntity)

            val opType = if (existing == null) "create" else "update"
            val payloadBase64 = Base64.getEncoder().encodeToString(ciphertextBytes)

            // Outbox Coalescing check per SPEC-2:
            val latestPending = outboxDao.getLatestPendingEntryForNote(note.id)
            if (latestPending != null && latestPending.type == "update" && opType == "update") {
                // Coalesce with existing pending update
                outboxDao.updateOutboxEntry(
                    latestPending.copy(
                        version = newVersion,
                        payload = payloadBase64,
                        createdAt = now
                    )
                )
            } else {
                val outboxEntity = OutboxEntity(
                    opId = UUID.randomUUID().toString(),
                    noteId = note.id,
                    type = opType,
                    baseVersion = baseVersion,
                    version = newVersion,
                    payload = payloadBase64,
                    status = "pending",
                    createdAt = now
                )
                outboxDao.insertOutboxEntry(outboxEntity)
            }
        }
    }

    override suspend fun deleteNote(id: String) {
        database.withTransaction {
            val existing = noteDao.getNoteById(id) ?: return@withTransaction
            val now = System.currentTimeMillis()
            val baseVersion = existing.version
            val newVersion = baseVersion + 1

            noteDao.softDeleteNote(id, now)

            val outboxEntity = OutboxEntity(
                opId = UUID.randomUUID().toString(),
                noteId = id,
                type = "delete",
                baseVersion = baseVersion,
                version = newVersion,
                payload = "",
                status = "pending",
                createdAt = now
            )
            outboxDao.insertOutboxEntry(outboxEntity)
        }
    }

    private fun NoteEntity.toDomainModel(): Note {
        var title = ""
        var body = ""

        try {
            val jsonString = String(this.ciphertext, Charsets.UTF_8)
            val json = JSONObject(jsonString)
            title = json.optString("title", "")
            body = json.optString("body", "")
        } catch (_: Exception) {
            // Fallback for empty or corrupted payloads
        }

        val status = try {
            SyncStatus.valueOf(this.syncStatus.uppercase())
        } catch (_: Exception) {
            SyncStatus.PENDING
        }

        return Note(
            id = this.id,
            title = title,
            body = body,
            version = this.version,
            updatedAt = this.updatedAt,
            deleted = this.deleted,
            syncStatus = status,
            conflictGroupId = this.conflictGroupId
        )
    }
}
