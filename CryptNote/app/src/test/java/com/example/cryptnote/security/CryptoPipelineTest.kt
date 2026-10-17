package com.example.cryptnote.security

import com.example.cryptnote.data.local.db.entity.NoteEntity
import com.example.cryptnote.domain.model.Note
import com.example.cryptnote.domain.model.NoteContent
import com.example.cryptnote.domain.model.SyncStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class CryptoPipelineTest {

    private lateinit var cryptoManager: CryptoManager
    private lateinit var keySessionManager: KeySessionManager

    @Before
    fun setUp() {
        cryptoManager = CryptoManagerImpl()
        val keyWrapper = JvmKeyWrapper()
        val keyStorageManager = InMemoryKeyStorageManager()
        keySessionManager = KeySessionManagerImpl(
            cryptoManager = cryptoManager,
            keyWrapper = keyWrapper,
            keyStorageManager = keyStorageManager
        )
    }

    @Test
    fun databaseDump_containsZeroPlaintext() {
        val passphrase = "vault-super-secure-key".toCharArray()
        keySessionManager.setupPassphrase(passphrase)
        val encryptionKey = keySessionManager.getEncryptionKey()

        val noteId = UUID.randomUUID().toString()
        val secretTitle = "Swiss Bank Account PIN 8932"
        val secretBody = "Passphrase to secret safe is ALPHA-BRAVO-CHARLIE"

        val jsonPayload = Json.encodeToString(
            NoteContent.serializer(),
            NoteContent(title = secretTitle, body = secretBody)
        )

        val encrypted = cryptoManager.encryptNote(
            noteId = noteId,
            version = 1,
            plaintextJson = jsonPayload,
            encryptionKey = encryptionKey
        )

        val entity = NoteEntity(
            id = noteId,
            ciphertext = encrypted.ciphertext,
            nonce = encrypted.nonce,
            version = 1,
            updatedAt = System.currentTimeMillis(),
            deleted = false,
            syncStatus = "pending"
        )

        // Inspection: Ensure raw database entity ciphertext does NOT contain any plaintext substring
        val ciphertextStringAscii = String(entity.ciphertext, Charsets.ISO_8859_1)
        val ciphertextStringUtf8 = String(entity.ciphertext, Charsets.UTF_8)

        assertFalse("Ciphertext must not leak secret title", ciphertextStringAscii.contains(secretTitle))
        assertFalse("Ciphertext must not leak secret body", ciphertextStringAscii.contains(secretBody))
        assertFalse("Ciphertext UTF8 must not leak secret title", ciphertextStringUtf8.contains(secretTitle))
        assertFalse("Ciphertext UTF8 must not leak secret body", ciphertextStringUtf8.contains(secretBody))

        // Raw bytes verification
        assertFalse("Ciphertext must not contain plaintext raw bytes", entity.ciphertext.contentEquals(jsonPayload.toByteArray()))

        // Read and decrypt with unlocked vault
        val decryptedJson = cryptoManager.decryptNote(
            noteId = entity.id,
            version = entity.version,
            ciphertext = entity.ciphertext,
            nonce = entity.nonce,
            encryptionKey = keySessionManager.getEncryptionKey()
        )

        val restoredContent = Json.decodeFromString(NoteContent.serializer(), decryptedJson)
        assertEquals(secretTitle, restoredContent.title)
        assertEquals(secretBody, restoredContent.body)
    }

    @Test
    fun pipeline_handlesLockAndUnlockTransitions() {
        val passphrase = "user-personal-passphrase".toCharArray()
        keySessionManager.setupPassphrase(passphrase)

        assertTrue(keySessionManager.isUnlocked.value)
        val keyBeforeLock = keySessionManager.getEncryptionKey()
        assertNotNull(keyBeforeLock)

        // Lock vault
        keySessionManager.lock()
        assertFalse(keySessionManager.isUnlocked.value)

        // Attempting to read key while locked throws
        var exceptionThrown = false
        try {
            keySessionManager.getEncryptionKey()
        } catch (_: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue("Must throw IllegalStateException when locked", exceptionThrown)

        // Unlock with correct passphrase
        val unlockResult = keySessionManager.unlock(passphrase)
        assertTrue(unlockResult.isSuccess)
        assertTrue(keySessionManager.isUnlocked.value)
        assertNotNull(keySessionManager.getEncryptionKey())
    }
}
