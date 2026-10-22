package com.example.cryptnote.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cryptnote.data.local.db.CryptNoteDatabase
import com.example.cryptnote.data.local.db.dao.NoteDao
import com.example.cryptnote.data.local.db.dao.OutboxDao
import com.example.cryptnote.data.local.db.dao.SyncStateDao
import com.example.cryptnote.data.local.db.entity.NoteEntity
import com.example.cryptnote.data.local.db.entity.OutboxEntity
import com.example.cryptnote.data.local.db.entity.SyncStateEntity
import com.example.cryptnote.data.remote.api.SyncApi
import com.example.cryptnote.data.remote.model.OutboxPayloadDto
import com.example.cryptnote.data.remote.model.PushOpDto
import com.example.cryptnote.data.remote.model.PushRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID

/**
 * Background worker responsible for synchronizing local notes with the cloud server.
 *
 * Implements the offline-first synchronization protocol defined in SPEC-2:
 * 1. Pushes pending outbox entries in strict local sequence order (seq ASC), preserving per-note serialization.
 * 2. Incremental pull: fetches modified notes since the local pull cursor.
 * 3. Atomic commit: pulled changes and cursor updates commit together in a single Room transaction.
 * 4. Isolation: skips applying pulled changes to notes that have pending local mutations.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: CryptNoteDatabase,
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val syncStateDao: SyncStateDao,
    private val syncApi: SyncApi,
    private val tokenStorageManager: com.example.cryptnote.security.token.TokenStorageManager,
    private val keySessionManager: com.example.cryptnote.security.KeySessionManager,
    private val json: Json
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!tokenStorageManager.hasSession()) {
            return Result.success()
        }
        return try {
            // Step 1: Push pending mutations from the local outbox to the backend
            val pushSuccess = processOutbox()
            if (!pushSuccess) {
                return Result.retry()
            }

            // Step 2: Incrementally pull remote changes and advance the local cursor
            val pullSuccess = processPull()
            if (!pullSuccess) {
                return Result.retry()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Reads pending outbox entries in chronological sequence order (seq ASC) and pushes them in batches.
     * Failed or conflicted operations for note A block subsequent operations for note A without stalling note B.
     */
    suspend fun processOutbox(): Boolean {
        // Reset previously failed entries back to pending for retry
        outboxDao.resetFailedEntries(maxAttempts = 10)

        // Track notes currently blocked by earlier failed or conflicted operations
        val blockedNotes = outboxDao.getBlockedNoteIds().toMutableSet()

        while (true) {
            val pendingEntries = outboxDao.getPendingEntries(limit = BATCH_SIZE)
            if (pendingEntries.isEmpty()) {
                return true
            }

            // Filter out entries belonging to blocked notes to preserve strict per-note FIFO order
            val eligibleEntries = mutableListOf<OutboxEntity>()
            for (entry in pendingEntries) {
                if (entry.noteId in blockedNotes) {
                    continue
                }
                eligibleEntries.add(entry)
            }

            if (eligibleEntries.isEmpty()) {
                // All remaining pending entries are blocked by earlier failed ops on the same notes
                return true
            }

            // Map eligible outbox entries to remote wire format
            val pushOps = eligibleEntries.map { entry ->
                var ciphertext: String? = null
                var nonce: String? = null
                var envelope: String? = null
                var formatVersion: Int? = null
                var keyId: String? = null
                var checksum: String? = null

                if (entry.type != "delete" && entry.payload.isNotBlank()) {
                    try {
                        val payload = json.decodeFromString(OutboxPayloadDto.serializer(), entry.payload)
                        if (!payload.envelope.isNullOrBlank()) {
                            envelope = payload.envelope
                            formatVersion = payload.formatVersion
                            keyId = payload.keyId
                            checksum = payload.checksum
                        } else {
                            ciphertext = payload.ciphertext
                            nonce = payload.nonce
                            formatVersion = 0
                        }
                    } catch (_: Exception) {
                        ciphertext = entry.payload
                        nonce = Base64.getEncoder().encodeToString(ByteArray(12))
                        formatVersion = 0
                    }
                }

                if (entry.type != "delete" && envelope.isNullOrBlank()) {
                    if (ciphertext.isNullOrBlank()) ciphertext = "e30="
                    if (nonce.isNullOrBlank()) nonce = Base64.getEncoder().encodeToString(ByteArray(12))
                }

                PushOpDto(
                    opId = entry.opId,
                    noteId = entry.noteId,
                    type = entry.type,
                    baseVersion = entry.baseVersion,
                    version = entry.version,
                    ciphertext = ciphertext,
                    nonce = nonce,
                    envelope = envelope,
                    formatVersion = formatVersion,
                    keyId = keyId,
                    checksum = checksum
                )
            }

            // Update notes to syncing state to trigger visual indicators
            eligibleEntries.forEach { entry ->
                noteDao.updateSyncStatus(entry.noteId, "syncing")
            }

            val response = try {
                syncApi.push(PushRequestDto(ops = pushOps))
            } catch (e: Exception) {
                // Network failure: mark eligible entries as failed to trigger exponential backoff
                eligibleEntries.forEach { outboxDao.markAsFailed(it.opId) }
                return false
            }

            if (!response.isSuccessful || response.body() == null) {
                eligibleEntries.forEach { outboxDao.markAsFailed(it.opId) }
                return false
            }

            val results = response.body()!!.results
            val resultMap = results.associateBy { it.opId }

            database.withTransaction {
                for (entry in eligibleEntries) {
                    if (entry.noteId in blockedNotes) {
                        continue
                    }
                    val res = resultMap[entry.opId] ?: continue
                    when (res.status.lowercase()) {
                        "applied", "duplicate" -> {
                            outboxDao.markAsSent(entry.opId)
                            noteDao.updateSyncStatus(entry.noteId, "synced")
                        }
                        "conflict" -> {
                            val existing = noteDao.getNoteById(entry.noteId)
                            if (existing != null) {
                                val conflictGroupId = existing.conflictGroupId ?: UUID.randomUUID().toString()
                                // 1. Tag the original note with conflict status and conflictGroupId
                                noteDao.insertNote(existing.copy(conflictGroupId = conflictGroupId, syncStatus = "conflict"))

                                // 2. Fork local edits into a side-by-side conflict note with a new UUID
                                val forkId = UUID.randomUUID().toString()
                                val forkEntity = existing.copy(
                                    id = forkId,
                                    conflictGroupId = conflictGroupId,
                                    syncStatus = "conflict"
                                )
                                noteDao.insertNote(forkEntity)

                                // 3. Enqueue a "create" operation for the fork so local edits reach the server
                                val forkOpId = UUID.randomUUID().toString()
                                outboxDao.insertOutboxEntry(
                                    OutboxEntity(
                                        opId = forkOpId,
                                        noteId = forkId,
                                        type = "create",
                                        baseVersion = 0,
                                        version = 1,
                                        payload = entry.payload,
                                        status = "pending",
                                        createdAt = System.currentTimeMillis()
                                    )
                                )

                                // 4. Mark the conflicted op on the original note as handled so the queue is unblocked
                                outboxDao.markAsSent(entry.opId)
                            } else {
                                outboxDao.markAsConflict(entry.opId)
                                blockedNotes.add(entry.noteId)
                                noteDao.updateSyncStatus(entry.noteId, "conflict")
                            }
                        }
                        else -> {
                            outboxDao.markAsFailed(entry.opId)
                            blockedNotes.add(entry.noteId)
                        }
                    }
                }
            }

            // If we processed fewer than BATCH_SIZE entries, the queue has been drained
            if (eligibleEntries.size < BATCH_SIZE) {
                return true
            }
        }
    }

    /**
     * Pulls remote changes incrementally using cursor-based pagination.
     * Applies changes and advances lastPullCursor in a single atomic Room transaction per page.
     */
    suspend fun processPull(): Boolean {
        var cursor = syncStateDao.getSyncState()?.lastPullCursor ?: 0L
        var hasMore = true

        while (hasMore) {
            val response = try {
                syncApi.pull(since = cursor, limit = BATCH_SIZE)
            } catch (e: Exception) {
                return false
            }

            if (!response.isSuccessful || response.body() == null) {
                return true
            }

            val pullResponse = response.body()!!
            val changes = pullResponse.changes

            // Apply all changes in this page and advance the cursor within a single Room database transaction
            database.withTransaction {
                for (change in changes) {
                    // Skip applying remote changes to notes that have active pending outbox operations
                    val hasPendingLocalOp = outboxDao.getLatestPendingEntryForNote(change.id) != null
                    if (hasPendingLocalOp) {
                        continue
                    }

                    val existing = noteDao.getNoteById(change.id)
                    // Rollback guard per SPEC-2 & FE-3: reject pulled change if local note version is strictly higher
                    if (existing != null && change.version < existing.version) {
                        continue
                    }

                    if (change.deleted) {
                        if (existing != null) {
                            noteDao.softDeleteNote(change.id, change.updatedAt)
                            noteDao.updateSyncStatus(change.id, "synced")
                        }
                    } else {
                        val checksumBytes = change.checksum?.let {
                            try {
                                if (it.length == 64) {
                                    it.chunked(2).map { b -> b.toInt(16).toByte() }.toByteArray()
                                } else {
                                    Base64.getUrlDecoder().decode(it)
                                }
                            } catch (_: Exception) { null }
                        }

                        val rawCiphertextBytes = change.ciphertext?.let {
                            try {
                                Base64.getDecoder().decode(it)
                            } catch (_: Exception) {
                                try { Base64.getUrlDecoder().decode(it) } catch (_: Exception) { it.toByteArray(Charsets.UTF_8) }
                            }
                        } ?: ByteArray(0)

                        val isFullEnvelope = rawCiphertextBytes.size >= 38 && rawCiphertextBytes[0].toInt() == 1 && rawCiphertextBytes[1].toInt() == 1

                        val envelopeBytes: ByteArray?
                        val keyIdBytes: ByteArray?
                        val nonceBytes: ByteArray
                        val ciphertextBytes: ByteArray
                        val formatVersion: Int

                        if (isFullEnvelope) {
                            envelopeBytes = rawCiphertextBytes
                            keyIdBytes = rawCiphertextBytes.copyOfRange(2, 10)
                            nonceBytes = rawCiphertextBytes.copyOfRange(10, 22)
                            ciphertextBytes = rawCiphertextBytes.copyOfRange(22, rawCiphertextBytes.size)
                            formatVersion = 1
                        } else {
                            envelopeBytes = change.envelope?.let {
                                try { Base64.getUrlDecoder().decode(it) } catch (_: Exception) { try { Base64.getDecoder().decode(it) } catch (_: Exception) { null } }
                            }
                            keyIdBytes = change.keyId?.let {
                                try { Base64.getUrlDecoder().decode(it) } catch (_: Exception) { try { Base64.getDecoder().decode(it) } catch (_: Exception) { null } }
                            }
                            nonceBytes = change.nonce?.let {
                                try { Base64.getDecoder().decode(it) } catch (_: Exception) { ByteArray(0) }
                            } ?: ByteArray(0)
                            ciphertextBytes = rawCiphertextBytes
                            formatVersion = change.formatVersion
                        }

                        val conflictGroupId = existing?.conflictGroupId
                        var isVault = existing?.isVault ?: false
                        val syncStatus = if (conflictGroupId != null) "conflict" else "synced"

                        val currentKeyId = try {
                            keySessionManager.getKeyId()
                        } catch (_: Exception) { null }

                        val effectiveKeyIdBytes = keyIdBytes ?: currentKeyId

                        val finalEnvelopeBytes = if (envelopeBytes == null && formatVersion == 1 && effectiveKeyIdBytes != null && effectiveKeyIdBytes.size == 8 && ciphertextBytes.isNotEmpty() && nonceBytes.size == 12) {
                            byteArrayOf(1, 1) + effectiveKeyIdBytes + nonceBytes + ciphertextBytes
                        } else {
                            envelopeBytes
                        }

                        if (existing == null && finalEnvelopeBytes != null && keySessionManager.isUnlocked.value && effectiveKeyIdBytes != null) {
                            try {
                                val encKey = keySessionManager.getEncryptionKey()
                                val noteUuid = try { UUID.fromString(change.id) } catch (_: Exception) { UUID.nameUUIDFromBytes(change.id.toByteArray(Charsets.UTF_8)) }
                                val openResult = com.example.cryptnote.security.EnvelopeV1.open(encKey, effectiveKeyIdBytes, noteUuid, change.version.toLong(), finalEnvelopeBytes)
                                if (openResult is com.example.cryptnote.security.OpenResult.Opened) {
                                    val content = json.decodeFromString(com.example.cryptnote.domain.model.NoteContent.serializer(), openResult.plaintext.toString(Charsets.UTF_8))
                                    isVault = content.isVault
                                }
                            } catch (_: Exception) {
                                isVault = false
                            }
                        }

                        val noteEntity = NoteEntity(
                            id = change.id,
                            ciphertext = ciphertextBytes,
                            nonce = nonceBytes,
                            envelope = finalEnvelopeBytes,
                            formatVersion = formatVersion,
                            keyId = keyIdBytes,
                            checksum = checksumBytes,
                            isVault = isVault,
                            version = change.version,
                            updatedAt = change.updatedAt,
                            deleted = false,
                            syncStatus = syncStatus,
                            conflictGroupId = conflictGroupId
                        )
                        noteDao.insertNote(noteEntity)
                    }
                }

                // Advance pull cursor atomically with the page application
                syncStateDao.upsertSyncState(
                    SyncStateEntity(id = 1, lastPullCursor = pullResponse.nextCursor)
                )
            }

            cursor = pullResponse.nextCursor
            hasMore = pullResponse.hasMore
        }

        return true
    }

    companion object {
        const val BATCH_SIZE = 50
    }
}
