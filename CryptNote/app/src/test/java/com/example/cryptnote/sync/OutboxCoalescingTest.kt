package com.example.cryptnote.sync

import com.example.cryptnote.data.local.db.dao.NoteDao
import com.example.cryptnote.data.local.db.dao.OutboxDao
import com.example.cryptnote.data.local.db.entity.NoteEntity
import com.example.cryptnote.data.local.db.entity.OutboxEntity
import com.example.cryptnote.data.remote.model.OutboxPayloadDto
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.NoteContent
import com.example.cryptnote.domain.model.SyncStatus
import com.example.cryptnote.domain.repository.NoteRepository
import com.example.cryptnote.security.CryptoManager
import com.example.cryptnote.security.CryptoManagerImpl
import com.example.cryptnote.security.InMemoryKeyStorageManager
import com.example.cryptnote.security.JvmKeyWrapper
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.KeySessionManagerImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.UUID

class OutboxCoalescingTest {

    private lateinit var noteDao: FakeNoteDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var cryptoManager: CryptoManager
    private lateinit var keySessionManager: KeySessionManager

    @Before
    fun setUp() {
        noteDao = FakeNoteDao()
        outboxDao = FakeOutboxDao()
        cryptoManager = CryptoManagerImpl()
        val keyWrapper = JvmKeyWrapper()
        val keyStorageManager = InMemoryKeyStorageManager()
        keySessionManager = KeySessionManagerImpl(
            cryptoManager = cryptoManager,
            keyWrapper = keyWrapper,
            keyStorageManager = keyStorageManager
        )
        keySessionManager.setupPassphrase("test-passphrase-123".toCharArray())
    }

    private fun createRepository(): TestNoteRepository {
        return TestNoteRepository(
            noteDao = noteDao,
            outboxDao = outboxDao,
            cryptoManager = cryptoManager,
            keySessionManager = keySessionManager
        )
    }

    @Test
    fun rule1_consecutiveUpdates_coalesce_intoSingleUpdateWithFirstBaseVersionAndLatestVersion() = runBlocking {
        val repo = createRepository()
        val noteId = UUID.randomUUID().toString()

        // Setup: Note was already synced previously at version 1
        noteDao.insertNote(
            NoteEntity(
                id = noteId,
                ciphertext = "initial".toByteArray(),
                nonce = ByteArray(12),
                version = 1,
                syncStatus = "synced"
            )
        )

        // First edit: should create an outbox update op (baseVersion = 1, version = 2)
        repo.saveNote(Note(id = noteId, title = "Edit 1", body = "Body 1", version = 1))
        var pending = outboxDao.getPendingEntries()
        assertEquals(1, pending.size)
        assertEquals("update", pending[0].type)
        assertEquals(1, pending[0].baseVersion)
        assertEquals(2, pending[0].version)

        // Second edit while still unsent: should COALESCE with the first update
        repo.saveNote(Note(id = noteId, title = "Edit 2", body = "Body 2", version = 2))
        pending = outboxDao.getPendingEntries()
        // Still only 1 pending outbox entry!
        assertEquals(1, pending.size)
        assertEquals("update", pending[0].type)
        assertEquals(1, pending[0].baseVersion) // Preserves initial baseVersion
        assertEquals(3, pending[0].version)     // Latest version

        // Verify payload holds Edit 2 content
        val payload = Json.decodeFromString(OutboxPayloadDto.serializer(), pending[0].payload)
        val decrypted = cryptoManager.decryptNote(
            noteId = noteId,
            version = 3,
            ciphertext = Base64.getDecoder().decode(payload.ciphertext),
            nonce = Base64.getDecoder().decode(payload.nonce),
            encryptionKey = keySessionManager.getEncryptionKey()
        )
        val content = Json.decodeFromString(NoteContent.serializer(), decrypted)
        assertEquals("Edit 2", content.title)
        assertEquals("Body 2", content.body)
    }

    @Test
    fun rule2_unsentCreate_followedByUpdate_coalescesIntoSingleCreateOp() = runBlocking {
        val repo = createRepository()
        val noteId = UUID.randomUUID().toString()

        // 1. Initial creation (baseVersion = 0, version = 1)
        repo.saveNote(Note(id = noteId, title = "Draft Title", body = "Draft Body"))
        var pending = outboxDao.getPendingEntries()
        assertEquals(1, pending.size)
        assertEquals("create", pending[0].type)
        assertEquals(0, pending[0].baseVersion)
        assertEquals(1, pending[0].version)

        // 2. User continues editing the unsent draft
        repo.saveNote(Note(id = noteId, title = "Draft Title Updated", body = "Draft Body Updated", version = 1))
        pending = outboxDao.getPendingEntries()
        // Still only 1 entry, and its type remains CREATE with baseVersion = 0, version = 1
        assertEquals(1, pending.size)
        assertEquals("create", pending[0].type)
        assertEquals(0, pending[0].baseVersion)
        assertEquals(1, pending[0].version)

        // Verify updated payload
        val payload = Json.decodeFromString(OutboxPayloadDto.serializer(), pending[0].payload)
        val decrypted = cryptoManager.decryptNote(
            noteId = noteId,
            version = 1,
            ciphertext = Base64.getDecoder().decode(payload.ciphertext),
            nonce = Base64.getDecoder().decode(payload.nonce),
            encryptionKey = keySessionManager.getEncryptionKey()
        )
        val content = Json.decodeFromString(NoteContent.serializer(), decrypted)
        assertEquals("Draft Title Updated", content.title)
    }

    @Test
    fun rule3_unsentCreate_followedByDelete_cancelsOutCompletely() = runBlocking {
        val repo = createRepository()
        val noteId = UUID.randomUUID().toString()

        // 1. User creates a draft offline
        repo.saveNote(Note(id = noteId, title = "Mistake Note", body = "Oops"))
        assertEquals(1, outboxDao.getPendingEntries().size)
        assertNotNull(noteDao.getNoteById(noteId))

        // 2. User immediately deletes the unsent draft before sync
        repo.deleteNote(noteId)

        // Invariant 3: Both outbox and notes entries are cancelled out and removed!
        assertEquals(0, outboxDao.getPendingEntries().size)
        assertNull(noteDao.getNoteById(noteId))
    }

    @Test
    fun rule4_unsentUpdate_followedByDelete_coalescesIntoSingleDeleteOp() = runBlocking {
        val repo = createRepository()
        val noteId = UUID.randomUUID().toString()

        // Setup: Note exists on server at version 2
        noteDao.insertNote(
            NoteEntity(
                id = noteId,
                ciphertext = "server_v2".toByteArray(),
                nonce = ByteArray(12),
                version = 2,
                syncStatus = "synced"
            )
        )

        // 1. User edits locally (pending update: base 2, version 3)
        repo.saveNote(Note(id = noteId, title = "Local Edit", body = "Content", version = 2))
        assertEquals(1, outboxDao.getPendingEntries().size)
        assertEquals("update", outboxDao.getPendingEntries()[0].type)

        // 2. User deletes the note before update was pushed
        repo.deleteNote(noteId)

        // Invariant 4: Outbox coalesces into a single delete operation
        val pending = outboxDao.getPendingEntries()
        assertEquals(1, pending.size)
        assertEquals("delete", pending[0].type)
        assertEquals(2, pending[0].baseVersion)
        assertEquals(3, pending[0].version)
        assertEquals("", pending[0].payload)

        // Local note marked soft-deleted
        val noteInDb = noteDao.getNoteById(noteId)
        assertNotNull(noteInDb)
        assertTrue(noteInDb!!.deleted)
    }
}

/**
 * Fake in-memory implementation of NoteDao.
 */
class FakeNoteDao : NoteDao {
    val notes = mutableMapOf<String, NoteEntity>()

    override fun observeAllNotes(): Flow<List<NoteEntity>> =
        flowOf(notes.values.filter { !it.deleted }.sortedByDescending { it.updatedAt })

    override fun observeNoteById(noteId: String): Flow<NoteEntity?> =
        flowOf(notes[noteId])

    override suspend fun getNoteById(noteId: String): NoteEntity? = notes[noteId]

    override suspend fun getNotesBySyncStatus(status: String): List<NoteEntity> =
        notes.values.filter { it.syncStatus == status && !it.deleted }

    override suspend fun insertNote(note: NoteEntity) {
        notes[note.id] = note
    }

    override suspend fun updateNote(note: NoteEntity) {
        notes[note.id] = note
    }

    override suspend fun softDeleteNote(noteId: String, updatedAt: Long) {
        notes[noteId]?.let {
            notes[noteId] = it.copy(deleted = true, syncStatus = "pending", updatedAt = updatedAt)
        }
    }

    override suspend fun updateSyncStatus(noteId: String, status: String) {
        notes[noteId]?.let {
            notes[noteId] = it.copy(syncStatus = status)
        }
    }

    override suspend fun purgeSyncedDeletedNotes() {
        notes.entries.removeAll { it.value.deleted && it.value.syncStatus == "synced" }
    }

    override fun observePendingCount(): Flow<Int> =
        flowOf(notes.values.count { it.syncStatus == "pending" })

    override suspend fun getNotesByConflictGroup(conflictGroupId: String): List<NoteEntity> =
        notes.values.filter { it.conflictGroupId == conflictGroupId && !it.deleted }

    override suspend fun clearConflictGroup(noteId: String) {
        notes[noteId]?.let {
            notes[noteId] = it.copy(conflictGroupId = null)
        }
    }

    override fun observeConflictNotes(): Flow<List<NoteEntity>> =
        flowOf(notes.values.filter { it.conflictGroupId != null && !it.deleted })

    override suspend fun deleteNoteById(noteId: String) {
        notes.remove(noteId)
    }
}

/**
 * Fake in-memory implementation of OutboxDao with monotonic sequence numbers.
 */
class FakeOutboxDao : OutboxDao {
    val entries = mutableMapOf<String, OutboxEntity>()
    private var seqCounter = 1L

    override suspend fun insertOutboxEntry(entry: OutboxEntity): Long {
        val seq = if (entry.seq == 0L) seqCounter++ else entry.seq
        val newEntry = entry.copy(seq = seq)
        entries[newEntry.opId] = newEntry
        return seq
    }

    override suspend fun updateOutboxEntry(entry: OutboxEntity) {
        entries[entry.opId] = entry
    }

    override suspend fun getPendingEntries(limit: Int): List<OutboxEntity> =
        entries.values.filter { it.status == "pending" }
            .sortedBy { it.seq }
            .take(limit)

    override suspend fun getLatestPendingEntryForNote(noteId: String): OutboxEntity? =
        entries.values.filter { it.noteId == noteId && it.status == "pending" }
            .maxByOrNull { it.seq }

    override suspend fun markAsSent(opId: String) {
        entries[opId]?.let {
            entries[opId] = it.copy(status = "sent")
        }
    }

    override suspend fun markAsConflict(opId: String) {
        entries[opId]?.let {
            entries[opId] = it.copy(status = "conflict")
        }
    }

    override suspend fun markAsFailed(opId: String) {
        entries[opId]?.let {
            entries[opId] = it.copy(status = "failed", attempts = it.attempts + 1)
        }
    }

    override suspend fun resetFailedEntries(maxAttempts: Int) {
        entries.values.filter { it.status == "failed" && it.attempts < maxAttempts }.forEach {
            entries[it.opId] = it.copy(status = "pending")
        }
    }

    override suspend fun deleteEntryByOpId(opId: String) {
        entries.remove(opId)
    }

    override suspend fun purgeSentEntries(olderThan: Long) {
        entries.entries.removeAll { it.value.status == "sent" && it.value.createdAt < olderThan }
    }

    override suspend fun getPendingCount(): Int =
        entries.values.count { it.status == "pending" }

    override suspend fun getEntryByOpId(opId: String): OutboxEntity? = entries[opId]

    override suspend fun getBlockedNoteIds(): List<String> =
        entries.values.filter { it.status in listOf("failed", "conflict") }
            .map { it.noteId }
            .distinct()

    override suspend fun deleteEntriesForNote(noteId: String) {
        entries.entries.removeAll { it.value.noteId == noteId }
    }
}

/**
 * Isolated test repository exercising the exact saveNote and deleteNote algorithms
 * without Android SQLite dependency.
 */
class TestNoteRepository(
    private val noteDao: FakeNoteDao,
    private val outboxDao: FakeOutboxDao,
    private val cryptoManager: CryptoManager,
    private val keySessionManager: KeySessionManager
) {
    suspend fun saveNote(note: Note) {
        val encryptionKey = keySessionManager.getEncryptionKey()
        val existing = noteDao.getNoteById(note.id)
        val latestPending = outboxDao.getLatestPendingEntryForNote(note.id)
        val now = System.currentTimeMillis()

        val isUnsentCreate = latestPending != null && latestPending.type == "create"
        val targetVersion = if (isUnsentCreate) 1 else (existing?.version ?: 0) + 1
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
            syncStatus = "pending",
            conflictGroupId = note.conflictGroupId
        )
        noteDao.insertNote(noteEntity)

        val payloadJson = Json.encodeToString(
            OutboxPayloadDto.serializer(),
            OutboxPayloadDto(
                ciphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext),
                nonce = Base64.getEncoder().encodeToString(encrypted.nonce)
            )
        )

        if (isUnsentCreate) {
            outboxDao.updateOutboxEntry(
                latestPending.copy(payload = payloadJson, createdAt = now)
            )
        } else if (latestPending != null && latestPending.type == "update") {
            outboxDao.updateOutboxEntry(
                latestPending.copy(version = targetVersion, payload = payloadJson, createdAt = now)
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

    suspend fun deleteNote(id: String) {
        val existing = noteDao.getNoteById(id) ?: return
        val latestPending = outboxDao.getLatestPendingEntryForNote(id)
        val now = System.currentTimeMillis()

        if (latestPending != null && latestPending.type == "create") {
            noteDao.deleteNoteById(id)
            outboxDao.deleteEntriesForNote(id)
        } else if (latestPending != null && latestPending.type == "update") {
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
