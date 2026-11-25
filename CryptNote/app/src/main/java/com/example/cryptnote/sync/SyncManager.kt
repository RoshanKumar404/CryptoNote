package com.example.cryptnote.sync

import androidx.room.withTransaction
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
import com.example.cryptnote.domain.model.NoteContent
import com.example.cryptnote.security.EnvelopeV1
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.OpenResult
import com.example.cryptnote.security.token.TokenStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core synchronization manager that handles bidirectional synchronization between
 * the local Room database and the cloud backend.
 *
 * Implements:
 * 1. Fast foreground coroutine execution protected by a Mutex.
 * 2. Strict per-note serialization and outbox push.
 * 3. Incremental cursor-based pull with atomic transaction commit.
 * 4. Multi-device conflict resolution and dynamic vault status reconciliation.
 */
@Singleton
class SyncManager @Inject constructor(
    private val database: CryptNoteDatabase,
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val syncStateDao: SyncStateDao,
    private val syncApi: SyncApi,
    private val tokenStorageManager: TokenStorageManager,
    private val keySessionManager: KeySessionManager,
    private val json: Json
) {
    private val syncMutex = Mutex()

    /**
     * Executes a full synchronization cycle: push pending outbox entries first,
     * then pull remote changes.
     * Returns true if both phases succeeded, or false on error or if another sync is already in flight.
     */
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        if (!tokenStorageManager.hasSession()) {
            return@withContext false
        }

        // Avoid concurrent conflicting sync executions
        if (!syncMutex.tryLock()) {
            return@withContext false
        }

        try {
            val pushSuccess = processOutbox()
            if (!pushSuccess) {
                return@withContext false
            }

            val pullSuccess = processPull()
            pullSuccess
        } catch (e: Exception) {
            false
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * Reads pending outbox entries in sequence order (seq ASC) and pushes them in batches.
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

            val eligibleEntries = mutableListOf<OutboxEntity>()
            for (entry in pendingEntries) {
                if (entry.noteId in blockedNotes) {
                    continue
                }
                eligibleEntries.add(entry)
            }

            if (eligibleEntries.isEmpty()) {
                return true
            }

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

                val effectiveFormatVersion = if (entry.type == "delete") 0 else (formatVersion ?: 1)

                PushOpDto(
                    opId = entry.opId,
                    noteId = entry.noteId,
                    type = entry.type,
                    baseVersion = entry.baseVersion,
                    version = entry.version,
                    ciphertext = ciphertext,
                    nonce = nonce,
                    envelope = envelope,
                    formatVersion = effectiveFormatVersion,
                    keyId = keyId,
                    checksum = checksum
                )
            }

            eligibleEntries.forEach { entry ->
                noteDao.updateSyncStatus(entry.noteId, "syncing")
            }

            val response = try {
                syncApi.push(PushRequestDto(ops = pushOps))
            } catch (e: Exception) {
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
                            val isUnlocked = keySessionManager.isUnlocked.value
                            var forkSuccess = false

                            if (existing != null && isUnlocked) {
                                try {
                                    val encKey = keySessionManager.getEncryptionKey()
                                    val currentKeyId = keySessionManager.getKeyId()
                                    val noteUuid = try {
                                        UUID.fromString(entry.noteId)
                                    } catch (_: Exception) {
                                        UUID.nameUUIDFromBytes(entry.noteId.toByteArray(Charsets.UTF_8))
                                    }

                                    // Decrypt the rejected mutation's payload
                                    var plaintextBytes: ByteArray? = null
                                    if (entry.payload.isNotBlank()) {
                                        try {
                                            val payloadDto = json.decodeFromString(OutboxPayloadDto.serializer(), entry.payload)
                                            val envB64 = payloadDto.envelope
                                            if (!envB64.isNullOrBlank()) {
                                                val envBytes = try {
                                                    Base64.getUrlDecoder().decode(envB64)
                                                } catch (_: Exception) {
                                                    Base64.getDecoder().decode(envB64)
                                                }
                                                val openRes = EnvelopeV1.open(encKey, currentKeyId, noteUuid, entry.version.toLong(), envBytes)
                                                if (openRes is OpenResult.Opened) {
                                                    plaintextBytes = openRes.plaintext
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }

                                    // Fallback: decrypt existing local entity if payload decode failed
                                    if (plaintextBytes == null && existing.envelope != null) {
                                        val openRes = EnvelopeV1.open(encKey, currentKeyId, noteUuid, existing.version.toLong(), existing.envelope)
                                        if (openRes is OpenResult.Opened) {
                                            plaintextBytes = openRes.plaintext
                                        }
                                    }

                                    if (plaintextBytes != null) {
                                        val content = json.decodeFromString(NoteContent.serializer(), String(plaintextBytes, Charsets.UTF_8))
                                        val forkTitle = if (content.title.startsWith("(conflict)", ignoreCase = true)) {
                                            content.title
                                        } else {
                                            "(conflict) ${content.title}"
                                        }
                                        val forkContent = content.copy(title = forkTitle)
                                        val forkJson = json.encodeToString(NoteContent.serializer(), forkContent)

                                        val forkId = UUID.randomUUID().toString()
                                        val forkUuid = UUID.fromString(forkId)
                                        val conflictGroupId = existing.conflictGroupId ?: UUID.randomUUID().toString()

                                        // Mark original note with conflictGroupId and status conflict
                                        noteDao.updateConflictGroup(entry.noteId, conflictGroupId)
                                        noteDao.updateSyncStatus(entry.noteId, "conflict")

                                        // Seal fork with fork's own UUID to guarantee cryptographic AAD integrity
                                        val forkEnvelope = EnvelopeV1.seal(
                                            key = encKey,
                                            keyId = currentKeyId,
                                            noteId = forkUuid,
                                            version = 1L,
                                            plaintext = forkJson.toByteArray(Charsets.UTF_8)
                                        )
                                        val forkChecksum = MessageDigest.getInstance("SHA-256").digest(forkEnvelope)
                                        val forkNonceBytes = if (forkEnvelope.size >= 22) forkEnvelope.copyOfRange(10, 22) else ByteArray(0)
                                        val forkCipherBytes = if (forkEnvelope.size >= 22) forkEnvelope.copyOfRange(22, forkEnvelope.size) else ByteArray(0)

                                        val forkEntity = NoteEntity(
                                            id = forkId,
                                            ciphertext = forkCipherBytes,
                                            nonce = forkNonceBytes,
                                            envelope = forkEnvelope,
                                            formatVersion = EnvelopeV1.FORMAT,
                                            keyId = currentKeyId,
                                            checksum = forkChecksum,
                                            isVault = forkContent.isVault,
                                            version = 1,
                                            updatedAt = System.currentTimeMillis(),
                                            deleted = false,
                                            syncStatus = "pending",
                                            conflictGroupId = conflictGroupId
                                        )
                                        noteDao.insertNote(forkEntity)

                                        val forkEnvelopeB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(forkEnvelope)
                                        val forkKeyIdB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(currentKeyId)
                                        val forkChecksumB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(forkChecksum)
                                        val forkEnvelopeB64 = Base64.getEncoder().encodeToString(forkEnvelope)
                                        val forkNonceB64 = Base64.getEncoder().encodeToString(forkNonceBytes)

                                        val forkPayloadJson = json.encodeToString(
                                            OutboxPayloadDto.serializer(),
                                            OutboxPayloadDto(
                                                ciphertext = forkEnvelopeB64,
                                                nonce = forkNonceB64,
                                                envelope = forkEnvelopeB64Url,
                                                formatVersion = EnvelopeV1.FORMAT,
                                                keyId = forkKeyIdB64Url,
                                                checksum = forkChecksumB64Url
                                            )
                                        )

                                        val forkOpId = UUID.randomUUID().toString()
                                        outboxDao.insertOutboxEntry(
                                            OutboxEntity(
                                                opId = forkOpId,
                                                noteId = forkId,
                                                type = "create",
                                                baseVersion = 0,
                                                version = 1,
                                                payload = forkPayloadJson,
                                                status = "pending",
                                                createdAt = System.currentTimeMillis()
                                            )
                                        )

                                        outboxDao.markAsSent(entry.opId)
                                        forkSuccess = true
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("SyncManager", "Failed to fork conflicted note ${entry.noteId}", e)
                                }
                            }

                            if (!forkSuccess) {
                                // Vault is locked or decryption failed: mark entry as conflict so it can be retried or resolved once unlocked
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

            if (eligibleEntries.size < BATCH_SIZE) {
                return true
            }
        }
    }

    /**
     * Pulls remote changes incrementally using cursor-based pagination and reconciles local state.
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

            database.withTransaction {
                for (change in changes) {
                    val hasPendingLocalOp = outboxDao.getLatestPendingEntryForNote(change.id) != null
                    if (hasPendingLocalOp) {
                        continue
                    }

                    val existing = noteDao.getNoteById(change.id)
                    if (existing != null && change.version < existing.version) {
                        continue
                    }

                    if (change.deleted) {
                        if (existing != null) {
                            noteDao.markRemoteDeleted(change.id, change.updatedAt)
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

                        // Determine accurate isVault status by inspecting decrypted content
                        if (finalEnvelopeBytes != null && keySessionManager.isUnlocked.value && effectiveKeyIdBytes != null) {
                            try {
                                val encKey = keySessionManager.getEncryptionKey()
                                val noteUuid = try { UUID.fromString(change.id) } catch (_: Exception) { UUID.nameUUIDFromBytes(change.id.toByteArray(Charsets.UTF_8)) }
                                val openResult = EnvelopeV1.open(encKey, effectiveKeyIdBytes, noteUuid, change.version.toLong(), finalEnvelopeBytes)
                                if (openResult is OpenResult.Opened) {
                                    val content = json.decodeFromString(NoteContent.serializer(), openResult.plaintext.toString(Charsets.UTF_8))
                                    isVault = content.isVault
                                }
                            } catch (_: Exception) {
                                // Keep existing?.isVault fallback
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
