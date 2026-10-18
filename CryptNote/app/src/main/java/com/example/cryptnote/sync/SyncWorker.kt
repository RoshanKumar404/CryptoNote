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
    private val json: Json
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
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

                if (entry.type != "delete" && entry.payload.isNotBlank()) {
                    try {
                        val payload = json.decodeFromString(OutboxPayloadDto.serializer(), entry.payload)
                        ciphertext = payload.ciphertext
                        nonce = payload.nonce
                    } catch (_: Exception) {
                        ciphertext = entry.payload
                        nonce = ""
                    }
                }

                PushOpDto(
                    opId = entry.opId,
                    noteId = entry.noteId,
                    type = entry.type,
                    baseVersion = entry.baseVersion,
                    version = entry.version,
                    ciphertext = ciphertext,
                    nonce = nonce
                )
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
                            outboxDao.markAsConflict(entry.opId)
                            noteDao.updateSyncStatus(entry.noteId, "conflict")
                            blockedNotes.add(entry.noteId)
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
                return false
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

                    if (change.deleted) {
                        val existing = noteDao.getNoteById(change.id)
                        if (existing != null) {
                            noteDao.softDeleteNote(change.id, change.updatedAt)
                            noteDao.updateSyncStatus(change.id, "synced")
                        }
                    } else {
                        val ciphertextBytes = change.ciphertext?.let {
                            try { Base64.getDecoder().decode(it) } catch (_: Exception) { it.toByteArray(Charsets.UTF_8) }
                        } ?: ByteArray(0)

                        val nonceBytes = change.nonce?.let {
                            try { Base64.getDecoder().decode(it) } catch (_: Exception) { ByteArray(0) }
                        } ?: ByteArray(0)

                        val noteEntity = NoteEntity(
                            id = change.id,
                            ciphertext = ciphertextBytes,
                            nonce = nonceBytes,
                            version = change.version,
                            updatedAt = change.updatedAt,
                            deleted = false,
                            syncStatus = "synced"
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
