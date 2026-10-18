package com.example.cryptnote.data.repository

import androidx.room.withTransaction
import com.example.cryptnote.data.local.db.CryptNoteDatabase
import com.example.cryptnote.data.local.db.dao.NoteDao
import com.example.cryptnote.data.local.db.dao.OutboxDao
import com.example.cryptnote.data.local.db.entity.NoteEntity
import com.example.cryptnote.data.local.db.entity.OutboxEntity
import com.example.cryptnote.domain.model.ConflictPair
import com.example.cryptnote.domain.model.ConflictResolutionAction
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.NoteContent
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.domain.repository.NoteRepository
import com.example.cryptnote.security.CryptoManager
import com.example.cryptnote.security.KeySessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [NoteRepository] backing storage with Room SQLite.
 *
 * Enforces atomic note mutations paired with transactional outbox queuing,
 * and transparent on-device AES-256-GCM encryption before writing ciphertext to SQLite.
 */
@Singleton
class RoomNoteRepository @Inject constructor(
    private val database: CryptNoteDatabase,
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val cryptoManager: CryptoManager,
    private val keySessionManager: KeySessionManager
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

    override fun getConflictPairs(): Flow<List<ConflictPair>> {
        return noteDao.observeConflictNotes().map { entities ->
            val domainNotes = entities.map { it.toDomainModel() }
            domainNotes
                .filter { it.conflictGroupId != null }
                .groupBy { it.conflictGroupId!! }
                .mapNotNull { (groupId, groupNotes) ->
                    if (groupNotes.size >= 2) {
                        val local = groupNotes.find { it.title.startsWith("(conflict)", ignoreCase = true) }
                            ?: groupNotes.maxByOrNull { it.updatedAt } ?: groupNotes[0]
                        val server = groupNotes.find { it.id != local.id } ?: groupNotes[1]
                        ConflictPair(
                            conflictGroupId = groupId,
                            localNote = local,
                            serverNote = server
                        )
                    } else null
                }
        }
    }

    override suspend fun resolveConflict(
        conflictGroupId: String,
        action: ConflictResolutionAction
    ) {
        database.withTransaction {
            val entities = noteDao.getNotesByConflictGroup(conflictGroupId)
            if (entities.isEmpty()) return@withTransaction

            val domainNotes = entities.map { it.toDomainModel() }
            val local = domainNotes.find { it.title.startsWith("(conflict)", ignoreCase = true) }
                ?: domainNotes.maxByOrNull { it.updatedAt } ?: domainNotes[0]
            val server = domainNotes.find { it.id != local.id } ?: domainNotes.getOrNull(1) ?: local

            val canonicalId = if (server.id != local.id) server.id else local.id
            val forkId = if (server.id != local.id) local.id else null

            when (action) {
                is ConflictResolutionAction.KeepLocal -> {
                    if (forkId != null) {
                        noteDao.deleteNoteById(forkId)
                        outboxDao.deleteEntriesForNote(forkId)
                    }
                    val cleanTitle = local.title.removePrefix("(conflict)").removePrefix("(Conflict)").trim()
                    val updatedCanonical = local.copy(
                        id = canonicalId,
                        title = cleanTitle,
                        conflictGroupId = null
                    )
                    saveNote(updatedCanonical)
                    noteDao.clearConflictGroup(canonicalId)
                }
                is ConflictResolutionAction.KeepServer -> {
                    if (forkId != null) {
                        noteDao.deleteNoteById(forkId)
                        outboxDao.deleteEntriesForNote(forkId)
                    }
                    noteDao.clearConflictGroup(canonicalId)
                    noteDao.updateSyncStatus(canonicalId, SyncStatus.SYNCED.name.lowercase())
                }
                is ConflictResolutionAction.CustomMerge -> {
                    if (forkId != null) {
                        noteDao.deleteNoteById(forkId)
                        outboxDao.deleteEntriesForNote(forkId)
                    }
                    val mergedNote = server.copy(
                        id = canonicalId,
                        title = action.mergedTitle,
                        body = action.mergedBody,
                        conflictGroupId = null
                    )
                    saveNote(mergedNote)
                    noteDao.clearConflictGroup(canonicalId)
                }
            }
        }
    }

    override suspend fun saveNote(note: Note) {
        val encryptionKey = keySessionManager.getEncryptionKey()

        database.withTransaction {
            val existing = noteDao.getNoteById(note.id)
            val latestPending = outboxDao.getLatestPendingEntryForNote(note.id)
            val now = System.currentTimeMillis()

            val isUnsentCreate = latestPending != null && latestPending.type == "create"
            val targetVersion = if (isUnsentCreate) {
                1
            } else {
                (existing?.version ?: 0) + 1
            }
            val baseVersion = if (isUnsentCreate) 0 else (existing?.version ?: 0)

            val jsonPayload = Json.encodeToString(
                NoteContent.serializer(),
                NoteContent(title = note.title, body = note.body)
            )

            val encrypted = cryptoManager.encryptNote(
                noteId = note.id,
                version = targetVersion,
                plaintextJson = jsonPayload,
                encryptionKey = encryptionKey
            )

            val noteEntity = NoteEntity(
                id = note.id,
                ciphertext = encrypted.ciphertext,
                nonce = encrypted.nonce,
                version = targetVersion,
                updatedAt = now,
                deleted = false,
                syncStatus = SyncStatus.PENDING.name.lowercase(),
                conflictGroupId = note.conflictGroupId
            )
            noteDao.insertNote(noteEntity)

            val payloadJson = Json.encodeToString(
                com.example.cryptnote.data.remote.model.OutboxPayloadDto.serializer(),
                com.example.cryptnote.data.remote.model.OutboxPayloadDto(
                    ciphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext),
                    nonce = Base64.getEncoder().encodeToString(encrypted.nonce)
                )
            )

            // Outbox Coalescing rules per SPEC-2 Section 6:
            if (isUnsentCreate) {
                // Rule 2: Unsent create followed by updates coalesces into a single create (base_version = 0, version = 1)
                outboxDao.updateOutboxEntry(
                    latestPending.copy(
                        payload = payloadJson,
                        createdAt = now
                    )
                )
            } else if (latestPending != null && latestPending.type == "update") {
                // Rule 1: Consecutive unsent updates coalesce (keep first baseVersion, latest version, latest payload)
                outboxDao.updateOutboxEntry(
                    latestPending.copy(
                        version = targetVersion,
                        payload = payloadJson,
                        createdAt = now
                    )
                )
            } else {
                val opType = if (existing == null) "create" else "update"
                val outboxEntity = OutboxEntity(
                    opId = UUID.randomUUID().toString(),
                    noteId = note.id,
                    type = opType,
                    baseVersion = baseVersion,
                    version = targetVersion,
                    payload = payloadJson,
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
            val latestPending = outboxDao.getLatestPendingEntryForNote(id)
            val now = System.currentTimeMillis()

            if (latestPending != null && latestPending.type == "create") {
                // Rule 3: Unsent create followed by delete cancels out completely (both deleted from notes and outbox)
                noteDao.deleteNoteById(id)
                outboxDao.deleteEntriesForNote(id)
            } else if (latestPending != null && latestPending.type == "update") {
                // Rule 4: Unsent update followed by delete coalesces into a single delete operation
                val newVersion = latestPending.version
                noteDao.softDeleteNote(id, now)
                outboxDao.updateOutboxEntry(
                    latestPending.copy(
                        type = "delete",
                        version = newVersion,
                        payload = "",
                        createdAt = now
                    )
                )
            } else {
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
    }

    private fun NoteEntity.toDomainModel(): Note {
        var title = ""
        var body = ""

        try {
            if (keySessionManager.isUnlocked.value) {
                val encryptionKey = keySessionManager.getEncryptionKey()
                val decryptedJson = cryptoManager.decryptNote(
                    noteId = this.id,
                    version = this.version,
                    ciphertext = this.ciphertext,
                    nonce = this.nonce,
                    encryptionKey = encryptionKey
                )
                val content = Json.decodeFromString(NoteContent.serializer(), decryptedJson)
                title = content.title
                body = content.body
            } else {
                title = "🔒 Encrypted Note"
                body = "Vault is locked. Unlock to decrypt."
            }
        } catch (_: Exception) {
            // Fallback for dev-mode plaintext notes created before FE-3
            try {
                val jsonString = String(this.ciphertext, Charsets.UTF_8)
                val content = Json.decodeFromString(NoteContent.serializer(), jsonString)
                title = content.title
                body = content.body
            } catch (_: Exception) {
                title = "⚠️ Decryption Error"
                body = ""
            }
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
