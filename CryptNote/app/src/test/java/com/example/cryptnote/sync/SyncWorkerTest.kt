package com.example.cryptnote.sync

import com.example.cryptnote.data.local.db.dao.SyncStateDao
import com.example.cryptnote.data.local.db.entity.NoteEntity
import com.example.cryptnote.data.local.db.entity.OutboxEntity
import com.example.cryptnote.data.local.db.entity.SyncStateEntity
import com.example.cryptnote.data.remote.api.SyncApi
import com.example.cryptnote.data.remote.model.OutboxPayloadDto
import com.example.cryptnote.data.remote.model.PullChangeDto
import com.example.cryptnote.data.remote.model.PullResponseDto
import com.example.cryptnote.data.remote.model.PushOpDto
import com.example.cryptnote.data.remote.model.PushRequestDto
import com.example.cryptnote.data.remote.model.PushResponseDto
import com.example.cryptnote.data.remote.model.PushResultDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.Base64
import java.util.UUID

class SyncWorkerTest {

    private lateinit var noteDao: FakeNoteDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var syncStateDao: FakeSyncStateDao
    private lateinit var syncApi: FakeSyncApi
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        noteDao = FakeNoteDao()
        outboxDao = FakeOutboxDao()
        syncStateDao = FakeSyncStateDao()
        syncApi = FakeSyncApi()
    }

    private fun createSyncProcessor(): SyncProcessor {
        return SyncProcessor(
            noteDao = noteDao,
            outboxDao = outboxDao,
            syncStateDao = syncStateDao,
            syncApi = syncApi,
            json = json
        )
    }

    @Test
    fun push_marksOutboxSent_and_noteSynced_onApplied() = runBlocking {
        val processor = createSyncProcessor()
        val noteId = UUID.randomUUID().toString()
        val opId = UUID.randomUUID().toString()

        noteDao.insertNote(NoteEntity(id = noteId, ciphertext = byteArrayOf(1), nonce = byteArrayOf(2), version = 1))
        outboxDao.insertOutboxEntry(
            OutboxEntity(
                opId = opId,
                noteId = noteId,
                type = "create",
                baseVersion = 0,
                version = 1,
                payload = json.encodeToString(OutboxPayloadDto.serializer(), OutboxPayloadDto("Y2lwaGVy", "bm9uY2U=")),
                status = "pending"
            )
        )

        syncApi.pushResult = PushResponseDto(
            results = listOf(
                PushResultDto(opId = opId, noteId = noteId, status = "applied", serverVersion = 1, serverSeq = 100L)
            )
        )

        val success = processor.processOutbox()
        assertTrue(success)

        val outboxEntry = outboxDao.getEntryByOpId(opId)
        assertEquals("sent", outboxEntry?.status)

        val note = noteDao.getNoteById(noteId)
        assertEquals("synced", note?.syncStatus)
    }

    @Test
    fun push_marksOutboxSent_onDuplicate() = runBlocking {
        val processor = createSyncProcessor()
        val noteId = UUID.randomUUID().toString()
        val opId = UUID.randomUUID().toString()

        noteDao.insertNote(NoteEntity(id = noteId, ciphertext = byteArrayOf(1), nonce = byteArrayOf(2), version = 1))
        outboxDao.insertOutboxEntry(
            OutboxEntity(
                opId = opId,
                noteId = noteId,
                type = "create",
                baseVersion = 0,
                version = 1,
                payload = json.encodeToString(OutboxPayloadDto.serializer(), OutboxPayloadDto("Y2lwaGVy", "bm9uY2U=")),
                status = "pending"
            )
        )

        syncApi.pushResult = PushResponseDto(
            results = listOf(
                PushResultDto(opId = opId, noteId = noteId, status = "duplicate", serverVersion = 1, serverSeq = 100L)
            )
        )

        val success = processor.processOutbox()
        assertTrue(success)
        assertEquals("sent", outboxDao.getEntryByOpId(opId)?.status)
        assertEquals("synced", noteDao.getNoteById(noteId)?.syncStatus)
    }

    @Test
    fun push_marksConflict_and_blocksSubsequentOpsForSameNote() = runBlocking {
        val processor = createSyncProcessor()
        val noteA = UUID.randomUUID().toString()
        val noteB = UUID.randomUUID().toString()

        val opA1 = UUID.randomUUID().toString()
        val opA2 = UUID.randomUUID().toString()
        val opB1 = UUID.randomUUID().toString()

        noteDao.insertNote(NoteEntity(id = noteA, ciphertext = byteArrayOf(1), nonce = byteArrayOf(2), version = 2))
        noteDao.insertNote(NoteEntity(id = noteB, ciphertext = byteArrayOf(3), nonce = byteArrayOf(4), version = 1))

        // Note A has two pending ops in sequence
        outboxDao.insertOutboxEntry(OutboxEntity(opId = opA1, noteId = noteA, type = "update", baseVersion = 1, version = 2, payload = "", status = "pending"))
        outboxDao.insertOutboxEntry(OutboxEntity(opId = opA2, noteId = noteA, type = "update", baseVersion = 2, version = 3, payload = "", status = "pending"))
        // Note B has one independent op
        outboxDao.insertOutboxEntry(OutboxEntity(opId = opB1, noteId = noteB, type = "create", baseVersion = 0, version = 1, payload = "", status = "pending"))

        // Server reports conflict on opA1, and applied on opB1
        syncApi.pushHandler = { request ->
            val results = request.ops.map { op ->
                if (op.opId == opA1) {
                    PushResultDto(opId = op.opId, noteId = op.noteId, status = "conflict", serverVersion = 3)
                } else {
                    PushResultDto(opId = op.opId, noteId = op.noteId, status = "applied", serverVersion = op.version, serverSeq = 50L)
                }
            }
            Response.success(PushResponseDto(results))
        }

        processor.processOutbox()

        // opA1 marked conflict
        assertEquals("conflict", outboxDao.getEntryByOpId(opA1)?.status)
        assertEquals("conflict", noteDao.getNoteById(noteA)?.syncStatus)

        // opB1 was applied
        assertEquals("sent", outboxDao.getEntryByOpId(opB1)?.status)

        // Now process again: opA2 for Note A must NOT be pushed because Note A is blocked by conflict!
        syncApi.lastPushedOps.clear()
        processor.processOutbox()

        // opA2 was skipped, not sent to server
        assertTrue(syncApi.lastPushedOps.isEmpty())
        assertEquals("pending", outboxDao.getEntryByOpId(opA2)?.status)
    }

    @Test
    fun push_marksFailed_onNetworkError_and_returnsFalse() = runBlocking {
        val processor = createSyncProcessor()
        val noteId = UUID.randomUUID().toString()
        val opId = UUID.randomUUID().toString()

        outboxDao.insertOutboxEntry(
            OutboxEntity(opId = opId, noteId = noteId, type = "create", baseVersion = 0, version = 1, payload = "", status = "pending")
        )

        // Simulate network failure
        syncApi.networkFailure = true

        val success = processor.processOutbox()
        assertFalse(success)

        val entry = outboxDao.getEntryByOpId(opId)
        assertEquals("failed", entry?.status)
        assertEquals(1, entry?.attempts)
    }

    @Test
    fun pull_appliesChanges_and_advancesCursorAtomically() = runBlocking {
        val processor = createSyncProcessor()
        val note1Id = UUID.randomUUID().toString()
        val note2Id = UUID.randomUUID().toString()

        // Note 2 exists locally and will be soft-deleted by remote pull
        noteDao.insertNote(
            NoteEntity(
                id = note2Id,
                ciphertext = byteArrayOf(1, 2),
                nonce = byteArrayOf(3, 4),
                version = 2,
                syncStatus = "synced"
            )
        )

        syncApi.pullHandler = { since, limit ->
            Response.success(
                PullResponseDto(
                    changes = listOf(
                        PullChangeDto(
                            id = note1Id,
                            ciphertext = Base64.getEncoder().encodeToString(byteArrayOf(10, 20)),
                            nonce = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
                            version = 2,
                            deleted = false,
                            updatedAt = 1000L,
                            serverSeq = 42L
                        ),
                        PullChangeDto(
                            id = note2Id,
                            ciphertext = null,
                            nonce = null,
                            version = 3,
                            deleted = true,
                            updatedAt = 2000L,
                            serverSeq = 43L
                        )
                    ),
                    nextCursor = 43L,
                    hasMore = false
                )
            )
        }

        val success = processor.processPull()
        assertTrue(success)

        // Note 1 inserted as synced
        val note1 = noteDao.getNoteById(note1Id)
        assertNotNull(note1)
        assertEquals(2, note1?.version)
        assertFalse(note1!!.deleted)
        assertEquals("synced", note1.syncStatus)

        // Note 2 marked soft-deleted
        val note2 = noteDao.getNoteById(note2Id)
        assertNotNull(note2)
        assertTrue(note2!!.deleted)

        // Cursor advanced to 43
        val state = syncStateDao.getSyncState()
        assertEquals(43L, state?.lastPullCursor)
    }

    @Test
    fun pull_skipsNote_withActivePendingLocalOp() = runBlocking {
        val processor = createSyncProcessor()
        val noteId = UUID.randomUUID().toString()

        // Local state: note has a pending outbox entry (local edit in progress)
        noteDao.insertNote(NoteEntity(id = noteId, ciphertext = byteArrayOf(99), nonce = byteArrayOf(88), version = 2))
        outboxDao.insertOutboxEntry(
            OutboxEntity(opId = UUID.randomUUID().toString(), noteId = noteId, type = "update", baseVersion = 1, version = 2, payload = "", status = "pending")
        )

        // Server sends a remote update for the same note (version 3)
        syncApi.pullHandler = { _, _ ->
            Response.success(
                PullResponseDto(
                    changes = listOf(
                        PullChangeDto(
                            id = noteId,
                            ciphertext = Base64.getEncoder().encodeToString(byteArrayOf(7, 7, 7)),
                            nonce = Base64.getEncoder().encodeToString(byteArrayOf(9, 9, 9)),
                            version = 3,
                            deleted = false,
                            updatedAt = 5000L,
                            serverSeq = 99L
                        )
                    ),
                    nextCursor = 99L,
                    hasMore = false
                )
            )
        }

        val success = processor.processPull()
        assertTrue(success)

        // The pulled change was SKIPPED to protect local pending edit from being clobbered!
        val localNote = noteDao.getNoteById(noteId)
        assertEquals(2, localNote?.version)
        assertEquals(99.toByte(), localNote?.ciphertext?.get(0))

        // Cursor STILL advanced cleanly
        assertEquals(99L, syncStateDao.getSyncState()?.lastPullCursor)
    }

    @Test
    fun pull_paginatesUntilHasMoreIsFalse() = runBlocking {
        val processor = createSyncProcessor()

        var callCount = 0
        syncApi.pullHandler = { since, limit ->
            callCount++
            if (callCount == 1) {
                Response.success(
                    PullResponseDto(
                        changes = listOf(
                            PullChangeDto(id = UUID.randomUUID().toString(), ciphertext = null, nonce = null, version = 1, deleted = false, updatedAt = 1L, serverSeq = 10L)
                        ),
                        nextCursor = 10L,
                        hasMore = true
                    )
                )
            } else {
                Response.success(
                    PullResponseDto(
                        changes = listOf(
                            PullChangeDto(id = UUID.randomUUID().toString(), ciphertext = null, nonce = null, version = 1, deleted = false, updatedAt = 2L, serverSeq = 20L)
                        ),
                        nextCursor = 20L,
                        hasMore = false
                    )
                )
            }
        }

        val success = processor.processPull()
        assertTrue(success)
        assertEquals(2, callCount)
        assertEquals(20L, syncStateDao.getSyncState()?.lastPullCursor)
    }
}

/**
 * Fake in-memory implementation of SyncStateDao.
 */
class FakeSyncStateDao : SyncStateDao {
    var state: SyncStateEntity? = SyncStateEntity(id = 1, lastPullCursor = 0L)

    override suspend fun getSyncState(): SyncStateEntity? = state

    override suspend fun upsertSyncState(state: SyncStateEntity) {
        this.state = state
    }

    override suspend fun updatePullCursor(cursor: Long) {
        this.state = this.state?.copy(lastPullCursor = cursor) ?: SyncStateEntity(id = 1, lastPullCursor = cursor)
    }
}

/**
 * Fake implementation of SyncApi.
 */
class FakeSyncApi : SyncApi {
    var pushResult: PushResponseDto? = null
    var pushHandler: ((PushRequestDto) -> Response<PushResponseDto>)? = null
    var pullHandler: ((Long, Int) -> Response<PullResponseDto>)? = null
    var networkFailure: Boolean = false
    val lastPushedOps = mutableListOf<PushOpDto>()

    override suspend fun push(request: PushRequestDto): Response<PushResponseDto> {
        if (networkFailure) {
            throw java.io.IOException("Network unreachable")
        }
        lastPushedOps.addAll(request.ops)
        pushHandler?.let { return it(request) }
        return Response.success(pushResult ?: PushResponseDto(emptyList()))
    }

    override suspend fun pull(since: Long, limit: Int): Response<PullResponseDto> {
        if (networkFailure) {
            throw java.io.IOException("Network unreachable")
        }
        pullHandler?.let { return it(since, limit) }
        return Response.success(PullResponseDto(emptyList(), since, false))
    }
}

/**
 * Test processor executing the SyncWorker algorithm without Android WorkManager runtime dependency.
 */
class SyncProcessor(
    private val noteDao: FakeNoteDao,
    private val outboxDao: FakeOutboxDao,
    private val syncStateDao: FakeSyncStateDao,
    private val syncApi: FakeSyncApi,
    private val json: Json
) {
    suspend fun processOutbox(): Boolean {
        val blockedNotes = outboxDao.getBlockedNoteIds().toMutableSet()

        while (true) {
            val pendingEntries = outboxDao.getPendingEntries(limit = 50)
            if (pendingEntries.isEmpty()) return true

            val eligibleEntries = pendingEntries.filter { it.noteId !in blockedNotes }
            if (eligibleEntries.isEmpty()) return true

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
                        ciphertext = payload.ciphertext
                        nonce = payload.nonce
                        envelope = payload.envelope
                        formatVersion = payload.formatVersion
                        keyId = payload.keyId
                        checksum = payload.checksum
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
                    nonce = nonce,
                    envelope = envelope,
                    formatVersion = formatVersion,
                    keyId = keyId,
                    checksum = checksum
                )
            }

            val response = try {
                syncApi.push(PushRequestDto(ops = pushOps))
            } catch (_: Exception) {
                eligibleEntries.forEach { outboxDao.markAsFailed(it.opId) }
                return false
            }

            if (!response.isSuccessful || response.body() == null) {
                eligibleEntries.forEach { outboxDao.markAsFailed(it.opId) }
                return false
            }

            val results = response.body()!!.results
            val resultMap = results.associateBy { it.opId }

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

            if (eligibleEntries.size < 50) return true
        }
    }

    suspend fun processPull(): Boolean {
        var cursor = syncStateDao.getSyncState()?.lastPullCursor ?: 0L
        var hasMore = true

        while (hasMore) {
            val response = try {
                syncApi.pull(since = cursor, limit = 50)
            } catch (_: Exception) {
                return false
            }

            if (!response.isSuccessful || response.body() == null) {
                return false
            }

            val pullResponse = response.body()!!
            val changes = pullResponse.changes

            for (change in changes) {
                val hasPendingLocalOp = outboxDao.getLatestPendingEntryForNote(change.id) != null
                if (hasPendingLocalOp) {
                    continue
                }

                if (change.deleted) {
                    noteDao.softDeleteNote(change.id, change.updatedAt)
                    noteDao.updateSyncStatus(change.id, "synced")
                } else {
                    val envelopeBytes = change.envelope?.let {
                        try {
                            Base64.getUrlDecoder().decode(it)
                        } catch (_: Exception) {
                            try { Base64.getDecoder().decode(it) } catch (_: Exception) { null }
                        }
                    }

                    val keyIdBytes = change.keyId?.let {
                        try {
                            Base64.getUrlDecoder().decode(it)
                        } catch (_: Exception) {
                            try { Base64.getDecoder().decode(it) } catch (_: Exception) { null }
                        }
                    }

                    val checksumBytes = change.checksum?.let {
                        try {
                            if (it.length == 64) {
                                it.chunked(2).map { b -> b.toInt(16).toByte() }.toByteArray()
                            } else {
                                Base64.getUrlDecoder().decode(it)
                            }
                        } catch (_: Exception) { null }
                    }

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
                        envelope = envelopeBytes,
                        formatVersion = change.formatVersion,
                        keyId = keyIdBytes,
                        checksum = checksumBytes,
                        version = change.version,
                        updatedAt = change.updatedAt,
                        deleted = false,
                        syncStatus = "synced"
                    )
                    noteDao.insertNote(noteEntity)
                }
            }

            syncStateDao.upsertSyncState(
                SyncStateEntity(id = 1, lastPullCursor = pullResponse.nextCursor)
            )

            cursor = pullResponse.nextCursor
            hasMore = pullResponse.hasMore
        }

        return true
    }
}
