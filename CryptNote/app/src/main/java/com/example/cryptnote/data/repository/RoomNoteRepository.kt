package com.example.cryptnote.data.repository

import androidx.room.withTransaction
import com.example.cryptnote.data.local.db.CryptNoteDatabase
import com.example.cryptnote.data.local.db.dao.NoteDao
import com.example.cryptnote.data.local.db.dao.OutboxDao
import com.example.cryptnote.data.local.db.dao.QuarantineDao
import com.example.cryptnote.data.local.db.entity.NoteEntity
import com.example.cryptnote.data.local.db.entity.OutboxEntity
import com.example.cryptnote.data.local.db.entity.QuarantineEntity
import com.example.cryptnote.data.remote.model.OutboxPayloadDto
import com.example.cryptnote.domain.model.ConflictPair
import com.example.cryptnote.domain.model.ConflictResolutionAction
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.NoteContent
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.domain.repository.NoteRepository
import com.example.cryptnote.security.CryptoManager
import com.example.cryptnote.security.DecryptDiagnostics
import com.example.cryptnote.security.EnvelopeV1
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.LegacyDecryptor
import com.example.cryptnote.security.OpenResult
import com.example.cryptnote.security.VaultLock
import com.example.cryptnote.security.VaultState
import com.example.cryptnote.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [NoteRepository] backing storage with Room SQLite.
 *
 * Implements NV-FE-3:
 * - Sealed EnvelopeV1 with HKDF key_id and SHA-256 checksums
 * - Transparent migrate-on-read for format 0 legacy notes
 * - Quarantine preservation before note deletion
 * - Notes vs Vault separation
 */
@Singleton
class RoomNoteRepository @Inject constructor(
    private val database: CryptNoteDatabase,
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val quarantineDao: QuarantineDao,
    private val cryptoManager: CryptoManager,
    private val keySessionManager: KeySessionManager,
    private val syncScheduler: SyncScheduler,
    private val vaultLock: VaultLock,
    private val decryptDiagnostics: DecryptDiagnostics
) : NoteRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val migratingNotes = ConcurrentHashMap.newKeySet<String>()

    override fun getAllNotes(): Flow<List<Note>> {
        return noteDao.observeAllNotes().map { entities ->
            entities.map { entity -> entity.toDomainModel(entities) }
        }
    }

    override fun getVaultNotes(): Flow<List<Note>> {
        return combine(
            noteDao.observeVaultNotes(),
            vaultLock.state
        ) { entities, _ ->
            entities.map { entity -> entity.toDomainModel(entities) }
        }
    }

    override fun getNoteById(id: String): Flow<Note?> {
        return noteDao.observeNoteById(id).map { entity ->
            entity?.toDomainModel()
        }
    }


    override fun getConflictNotes(): Flow<List<Note>> {
        return noteDao.observeConflictNotes().map { entities ->
            entities.map { it.toDomainModel(entities) }
        }
    }

    override fun getConflictPairs(): Flow<List<ConflictPair>> {
        return noteDao.observeConflictNotes().map { entities ->
            val domainNotes = entities.map { it.toDomainModel(entities) }
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
                    } else if (groupNotes.isNotEmpty()) {
                        val solo = groupNotes.first()
                        val dummyServer = solo.copy(
                            id = "${solo.id}-server-placeholder",
                            title = "${solo.title} (Cloud Out of Sync)",
                            body = "Remote version rejected or not downloaded yet. You can keep local edits or discard this note to resolve."
                        )
                        ConflictPair(
                            conflictGroupId = groupId,
                            localNote = solo,
                            serverNote = dummyServer
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

            val canonicalId = if (server.id != local.id && !server.id.endsWith("-server-placeholder")) server.id else local.id
            val forkId = if (server.id != local.id && !server.id.endsWith("-server-placeholder")) local.id else null

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
        syncScheduler.scheduleImmediate()
    }

    override suspend fun saveNote(note: Note) {
        val encryptionKey = keySessionManager.getEncryptionKey()
        val keyId = keySessionManager.getKeyId()

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
                NoteContent(
                    title = note.title,
                    body = note.body,
                    tags = note.tags,
                    pinned = note.pinned,
                    isVault = note.isVault
                )
            )

            val noteUuid = try {
                UUID.fromString(note.id)
            } catch (_: Exception) {
                UUID.nameUUIDFromBytes(note.id.toByteArray(Charsets.UTF_8))
            }

            val envelope = EnvelopeV1.seal(
                key = encryptionKey,
                keyId = keyId,
                noteId = noteUuid,
                version = targetVersion.toLong(),
                plaintext = jsonPayload.toByteArray(Charsets.UTF_8)
            )

            val checksum = MessageDigest.getInstance("SHA-256").digest(envelope)

            val noteEntity = NoteEntity(
                id = note.id,
                ciphertext = ByteArray(0),
                nonce = ByteArray(0),
                version = targetVersion,
                updatedAt = now,
                deleted = false,
                syncStatus = SyncStatus.PENDING.name.lowercase(),
                conflictGroupId = note.conflictGroupId,
                envelope = envelope,
                formatVersion = EnvelopeV1.FORMAT,
                keyId = keyId,
                checksum = checksum,
                isVault = note.isVault
            )
            noteDao.insertNote(noteEntity)

            val envelopeB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(envelope)
            val keyIdB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(keyId)
            val checksumB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(checksum)

            val payloadJson = Json.encodeToString(
                OutboxPayloadDto.serializer(),
                OutboxPayloadDto(
                    envelope = envelopeB64Url,
                    formatVersion = EnvelopeV1.FORMAT,
                    keyId = keyIdB64Url,
                    checksum = checksumB64Url
                )
            )

            // Outbox Coalescing rules per SPEC-2 Section 6:
            if (isUnsentCreate) {
                outboxDao.updateOutboxEntry(
                    latestPending.copy(
                        payload = payloadJson,
                        createdAt = now
                    )
                )
            } else if (latestPending != null && latestPending.type == "update") {
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
        syncScheduler.scheduleImmediate()
    }

    override suspend fun deleteNote(id: String) {
        database.withTransaction {
            val existing = noteDao.getNoteById(id) ?: return@withTransaction
            val latestPending = outboxDao.getLatestPendingEntryForNote(id)
            val now = System.currentTimeMillis()

            // Per NV-FE-4: Quarantine envelope before deleting
            val envBytes = existing.envelope ?: (existing.nonce + existing.ciphertext)
            if (envBytes.isNotEmpty()) {
                quarantineDao.insert(
                    QuarantineEntity(
                        noteId = id,
                        envelope = envBytes,
                        reason = "User deleted note",
                        savedAt = now
                    )
                )
            }

            if (latestPending != null && latestPending.type == "create") {
                noteDao.deleteNoteById(id)
                outboxDao.deleteEntriesForNote(id)
            } else if (latestPending != null && latestPending.type == "update") {
                val newVersion = latestPending.baseVersion + 1
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
        syncScheduler.scheduleImmediate()
    }

    override suspend fun moveToVault(id: String, toVault: Boolean) {
        if (!keySessionManager.isUnlocked.value) {
            throw IllegalStateException("Vault is locked")
        }

        database.withTransaction {
            val existing = noteDao.getNoteById(id) ?: return@withTransaction
            val encryptionKey = keySessionManager.getEncryptionKey()
            val keyId = keySessionManager.getKeyId()
            val decryptedJson = tryDecryptNote(existing, encryptionKey)
            val content = jsonSerializer.decodeFromString(NoteContent.serializer(), decryptedJson)
            val updatedContent = content.copy(isVault = toVault)
            val updatedJson = jsonSerializer.encodeToString(NoteContent.serializer(), updatedContent)
            val now = System.currentTimeMillis()

            val latestPending = outboxDao.getLatestPendingEntryForNote(id)
            val isUnsentCreate = latestPending != null && latestPending.type == "create"
            val targetVersion = if (isUnsentCreate) 1 else existing.version + 1
            val baseVersion = if (isUnsentCreate) 0 else existing.version

            val noteUuid = try {
                UUID.fromString(id)
            } catch (_: Exception) {
                UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8))
            }

            val envelope = EnvelopeV1.seal(
                key = encryptionKey,
                keyId = keyId,
                noteId = noteUuid,
                version = targetVersion.toLong(),
                plaintext = updatedJson.toByteArray(Charsets.UTF_8)
            )

            val checksum = MessageDigest.getInstance("SHA-256").digest(envelope)

            val updatedEntity = existing.copy(
                envelope = envelope,
                formatVersion = EnvelopeV1.FORMAT,
                keyId = keyId,
                checksum = checksum,
                isVault = toVault,
                version = targetVersion,
                updatedAt = now,
                syncStatus = "pending"
            )
            noteDao.insertNote(updatedEntity)

            val envelopeB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(envelope)
            val keyIdB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(keyId)
            val checksumB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(checksum)

            val payloadJson = Json.encodeToString(
                OutboxPayloadDto.serializer(),
                OutboxPayloadDto(
                    envelope = envelopeB64Url,
                    formatVersion = EnvelopeV1.FORMAT,
                    keyId = keyIdB64Url,
                    checksum = checksumB64Url
                )
            )

            if (isUnsentCreate) {
                outboxDao.updateOutboxEntry(latestPending.copy(payload = payloadJson, createdAt = now))
            } else if (latestPending != null && latestPending.type == "update") {
                outboxDao.updateOutboxEntry(latestPending.copy(version = targetVersion, payload = payloadJson, createdAt = now))
            } else {
                val outboxEntity = OutboxEntity(
                    opId = UUID.randomUUID().toString(),
                    noteId = id,
                    type = "update",
                    baseVersion = baseVersion,
                    version = targetVersion,
                    payload = payloadJson,
                    status = "pending",
                    createdAt = now
                )
                outboxDao.insertOutboxEntry(outboxEntity)
            }
        }
        syncScheduler.scheduleImmediate()
    }

    override suspend fun quarantineNote(id: String, reason: String) {
        database.withTransaction {
            val existing = noteDao.getNoteById(id) ?: return@withTransaction
            val envBytes = existing.envelope ?: (existing.nonce + existing.ciphertext)
            if (envBytes.isNotEmpty()) {
                quarantineDao.insert(
                    QuarantineEntity(
                        noteId = id,
                        envelope = envBytes,
                        reason = reason,
                        savedAt = System.currentTimeMillis()
                    )
                )
            }
            noteDao.deleteNoteById(id)
            outboxDao.deleteEntriesForNote(id)
        }
    }

    private fun scheduleMigrateOnRead(entity: NoteEntity, plaintext: ByteArray) {
        if (!migratingNotes.add(entity.id)) return
        repositoryScope.launch {
            try {
                database.withTransaction {
                    val current = noteDao.getNoteById(entity.id) ?: return@withTransaction
                    if (current.formatVersion > 0 && current.envelope != null) {
                        return@withTransaction
                    }
                    val encryptionKey = keySessionManager.getEncryptionKey()
                    val keyId = keySessionManager.getKeyId()
                    val targetVersion = current.version + 1
                    val baseVersion = current.version
                    val now = System.currentTimeMillis()

                    val noteUuid = try {
                        UUID.fromString(current.id)
                    } catch (_: Exception) {
                        UUID.nameUUIDFromBytes(current.id.toByteArray(Charsets.UTF_8))
                    }

                    val envelope = EnvelopeV1.seal(
                        key = encryptionKey,
                        keyId = keyId,
                        noteId = noteUuid,
                        version = targetVersion.toLong(),
                        plaintext = plaintext
                    )
                    val checksum = MessageDigest.getInstance("SHA-256").digest(envelope)

                    val migratedEntity = current.copy(
                        envelope = envelope,
                        formatVersion = EnvelopeV1.FORMAT,
                        keyId = keyId,
                        checksum = checksum,
                        version = targetVersion,
                        updatedAt = now,
                        syncStatus = "pending"
                    )
                    noteDao.insertNote(migratedEntity)

                    val envelopeB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(envelope)
                    val keyIdB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(keyId)
                    val checksumB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(checksum)

                    val payloadJson = Json.encodeToString(
                        OutboxPayloadDto.serializer(),
                        OutboxPayloadDto(
                            envelope = envelopeB64Url,
                            formatVersion = EnvelopeV1.FORMAT,
                            keyId = keyIdB64Url,
                            checksum = checksumB64Url
                        )
                    )

                    val outboxEntity = OutboxEntity(
                        opId = UUID.randomUUID().toString(),
                        noteId = current.id,
                        type = "update",
                        baseVersion = baseVersion,
                        version = targetVersion,
                        payload = payloadJson,
                        status = "pending",
                        createdAt = now
                    )
                    outboxDao.insertOutboxEntry(outboxEntity)
                }
                syncScheduler.scheduleImmediate()
            } catch (_: Exception) {
                // Ignore migration failure
            } finally {
                migratingNotes.remove(entity.id)
            }
        }
    }

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun tryDecryptNote(
        entity: NoteEntity,
        encryptionKey: ByteArray,
        allEntities: List<NoteEntity> = emptyList()
    ): String {
        val currentKeyId = try { keySessionManager.getKeyId() } catch (_: Exception) { ByteArray(8) }
        val keyIdHex = if (entity.keyId != null) {
            entity.keyId.take(4).joinToString("") { "%02x".format(it) }
        } else {
            "legacy"
        }
        val appVersion = "1.0.0"

        val noteUuid = try {
            UUID.fromString(entity.id)
        } catch (_: Exception) {
            UUID.nameUUIDFromBytes(entity.id.toByteArray(Charsets.UTF_8))
        }

        // Case 1: EnvelopeV1 (format 1)
        if (entity.envelope != null && entity.envelope.isNotEmpty()) {
            when (val res = EnvelopeV1.open(
                key = encryptionKey,
                currentKeyId = currentKeyId,
                noteId = noteUuid,
                version = entity.version.toLong(),
                env = entity.envelope
            )) {
                is OpenResult.Opened -> {
                    decryptDiagnostics.record(
                        noteId = entity.id,
                        keyIdPrefixHex = keyIdHex,
                        formatVersion = 1,
                        appVersion = appVersion,
                        resultClass = "Opened"
                    )
                    return String(res.plaintext, Charsets.UTF_8)
                }
                is OpenResult.UnknownFormat -> {
                    decryptDiagnostics.record(
                        noteId = entity.id,
                        keyIdPrefixHex = keyIdHex,
                        formatVersion = res.formatVersion,
                        appVersion = appVersion,
                        resultClass = "UnknownFormat"
                    )
                    throw IllegalStateException("Unknown format version: ${res.formatVersion}")
                }
                is OpenResult.KeyMismatch -> {
                    val noteKeyIdPrefix = res.noteKeyId.take(4).joinToString("") { "%02x".format(it) }
                    decryptDiagnostics.record(
                        noteId = entity.id,
                        keyIdPrefixHex = noteKeyIdPrefix,
                        formatVersion = 1,
                        appVersion = appVersion,
                        resultClass = "KeyMismatch"
                    )
                    throw SecurityException("Note encrypted under different key")
                }
                is OpenResult.Corrupt -> {
                    decryptDiagnostics.record(
                        noteId = entity.id,
                        keyIdPrefixHex = keyIdHex,
                        formatVersion = 1,
                        appVersion = appVersion,
                        resultClass = "Corrupt"
                    )
                    throw java.security.GeneralSecurityException("Envelope corrupt or authentication tag mismatch")
                }
            }
        }

        // Case 2: Legacy format 0 (ciphertext + nonce)
        if (entity.ciphertext.isNotEmpty() && entity.nonce.isNotEmpty()) {
            val standardRes = LegacyDecryptor.decrypt(
                noteId = entity.id,
                version = entity.version,
                ciphertext = entity.ciphertext,
                nonce = entity.nonce,
                encryptionKey = encryptionKey
            )
            if (standardRes.isSuccess) {
                val plaintextBytes = standardRes.getOrThrow()
                scheduleMigrateOnRead(entity, plaintextBytes)
                decryptDiagnostics.record(
                    noteId = entity.id,
                    keyIdPrefixHex = "legacy",
                    formatVersion = 0,
                    appVersion = appVersion,
                    resultClass = "LegacySuccess"
                )
                return String(plaintextBytes, Charsets.UTF_8)
            }

            // Step 2b: Try version variations on same noteId
            val candidateVersions = listOf(entity.version - 1, 1, 2, entity.version + 1, 0)
                .distinct()
                .filter { it >= 0 }

            for (v in candidateVersions) {
                val res = LegacyDecryptor.decrypt(
                    noteId = entity.id,
                    version = v,
                    ciphertext = entity.ciphertext,
                    nonce = entity.nonce,
                    encryptionKey = encryptionKey
                )
                if (res.isSuccess) {
                    val plaintextBytes = res.getOrThrow()
                    scheduleMigrateOnRead(entity, plaintextBytes)
                    decryptDiagnostics.record(
                        noteId = entity.id,
                        keyIdPrefixHex = "legacy",
                        formatVersion = 0,
                        appVersion = appVersion,
                        resultClass = "LegacyCandidateSuccess"
                    )
                    return String(plaintextBytes, Charsets.UTF_8)
                }
            }

            // Step 2c: If this note is part of a conflict group, check siblings
            if (entity.conflictGroupId != null) {
                val siblings = allEntities.filter { it.conflictGroupId == entity.conflictGroupId && it.id != entity.id }
                for (sibling in siblings) {
                    val candidateSiblingVersions = listOf(sibling.version, entity.version, 1, 2, 0)
                    for (v in candidateSiblingVersions) {
                        val res = LegacyDecryptor.decrypt(
                            noteId = sibling.id,
                            version = v,
                            ciphertext = entity.ciphertext,
                            nonce = entity.nonce,
                            encryptionKey = encryptionKey
                        )
                        if (res.isSuccess) {
                            val plaintextBytes = res.getOrThrow()
                            scheduleMigrateOnRead(entity, plaintextBytes)
                            decryptDiagnostics.record(
                                noteId = entity.id,
                                keyIdPrefixHex = "legacy",
                                formatVersion = 0,
                                appVersion = appVersion,
                                resultClass = "LegacySiblingSuccess"
                            )
                            return String(plaintextBytes, Charsets.UTF_8)
                        }
                    }
                }
            }
        }

        // Step 4: Check if ciphertext was stored as plaintext UTF-8 JSON (dev legacy)
        if (entity.ciphertext.isNotEmpty()) {
            try {
                val jsonString = String(entity.ciphertext, Charsets.UTF_8)
                jsonSerializer.decodeFromString(NoteContent.serializer(), jsonString)
                decryptDiagnostics.record(
                    noteId = entity.id,
                    keyIdPrefixHex = "dev_plain",
                    formatVersion = 0,
                    appVersion = appVersion,
                    resultClass = "DevPlaintext"
                )
                return jsonString
            } catch (_: Exception) {
                // Not plaintext JSON
            }
        }

        decryptDiagnostics.record(
            noteId = entity.id,
            keyIdPrefixHex = keyIdHex,
            formatVersion = entity.formatVersion,
            appVersion = appVersion,
            resultClass = "Corrupt"
        )
        throw java.security.GeneralSecurityException("AEAD authentication tag mismatch or missing envelope")
    }

    private fun NoteEntity.toDomainModel(allEntities: List<NoteEntity> = emptyList()): Note {
        var title = ""
        var body = ""
        var tags = emptyList<String>()
        var pinned = false
        var noteIsVault = this.isVault

        val rawBytes = this.envelope ?: (this.nonce + this.ciphertext)
        val preview = if (rawBytes.isNotEmpty()) {
            val hex = rawBytes.take(12).joinToString("") { "%02x".format(it) }
            "$hex..."
        } else null

        val status = try {
            SyncStatus.valueOf(this.syncStatus.uppercase())
        } catch (_: Exception) {
            SyncStatus.PENDING
        }

        // NV-FE-5 & NV-FE-6: Locked Vault items never expose decrypted titles or bodies
        if (this.isVault && vaultLock.state.value != VaultState.Unlocked) {
            return Note(
                id = this.id,
                title = "",
                body = "",
                version = this.version,
                updatedAt = this.updatedAt,
                deleted = this.deleted,
                syncStatus = status,
                conflictGroupId = this.conflictGroupId,
                tags = emptyList(),
                pinned = false,
                isVault = true,
                ciphertextPreview = preview
            )
        }

        try {
            if (keySessionManager.isUnlocked.value) {
                val encryptionKey = keySessionManager.getEncryptionKey()
                val decryptedJson = tryDecryptNote(this, encryptionKey, allEntities)
                val content = jsonSerializer.decodeFromString(NoteContent.serializer(), decryptedJson)
                title = content.title
                body = content.body
                tags = content.tags
                pinned = content.pinned
                noteIsVault = content.isVault || this.isVault
            } else {
                if (this.isVault) {
                    title = "🔒 Locked Vault Item"
                    body = "Vault is locked. Unlock to decrypt."
                } else {
                    title = "🔒 Encrypted Note"
                    body = "Unlock to decrypt."
                }
            }
        } catch (e: Exception) {
            when {
                e.message?.contains("Unknown format") == true -> {
                    title = "⚠️ Unsupported Version"
                    body = "This note was saved by a newer version. Update the app."
                }
                e.message?.contains("different key") == true -> {
                    title = "⚠️ Key Mismatch"
                    body = "This note was saved under a different key."
                }
                else -> {
                    title = "⚠️ Damaged Note"
                    body = "This note could not be decrypted with your current vault key."
                }
            }
        }

        return Note(
            id = this.id,
            title = title,
            body = body,
            version = this.version,
            updatedAt = this.updatedAt,
            deleted = this.deleted,
            syncStatus = status,
            conflictGroupId = this.conflictGroupId,
            tags = tags,
            pinned = pinned,
            isVault = noteIsVault,
            ciphertextPreview = preview
        )
    }
}
