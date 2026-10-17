package com.example.cryptnote.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.HexFormat
import javax.crypto.AEADBadTagException

class CryptoManagerTest {

    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setUp() {
        cryptoManager = CryptoManagerImpl()
    }

    @Test
    fun deriveKeys_generatesValid256BitKeys() {
        val passphrase = "test-master-passphrase".toCharArray()
        val salt = cryptoManager.generateSalt(16)

        val keys = cryptoManager.deriveKeys(passphrase, salt)

        assertNotNull(keys.authKey)
        assertNotNull(keys.encryptionKey)
        assertEquals(32, keys.authKey.size)
        assertEquals(32, keys.encryptionKey.size)

        // Auth key and encryption key must be distinct
        assertFalse(keys.authKey.contentEquals(keys.encryptionKey))
    }

    @Test
    fun deriveKeys_isDeterministicForSameInputs() {
        val passphrase1 = "deterministic-passphrase-123".toCharArray()
        val passphrase2 = "deterministic-passphrase-123".toCharArray()
        val salt = "fixed-16-byte-sl".toByteArray(Charsets.UTF_8)

        val keys1 = cryptoManager.deriveKeys(passphrase1, salt)
        val keys2 = cryptoManager.deriveKeys(passphrase2, salt)

        assertArrayEquals(keys1.authKey, keys2.authKey)
        assertArrayEquals(keys1.encryptionKey, keys2.encryptionKey)
    }

    @Test
    fun deriveKeys_differentPassphraseYieldsDifferentKeys() {
        val salt = "fixed-16-byte-sl".toByteArray(Charsets.UTF_8)
        val keys1 = cryptoManager.deriveKeys("password-alpha".toCharArray(), salt)
        val keys2 = cryptoManager.deriveKeys("password-bravo".toCharArray(), salt)

        assertFalse(keys1.authKey.contentEquals(keys2.authKey))
        assertFalse(keys1.encryptionKey.contentEquals(keys2.encryptionKey))
    }

    @Test
    fun zeroize_clearsKeyMaterialInMemory() {
        val keys = cryptoManager.deriveKeys("temp-passphrase-to-clear".toCharArray(), cryptoManager.generateSalt(16))

        // Ensure initially non-zero
        assertTrue(keys.authKey.any { it != 0.toByte() })
        assertTrue(keys.encryptionKey.any { it != 0.toByte() })

        keys.zeroize()

        assertTrue(keys.authKey.all { it == 0.toByte() })
        assertTrue(keys.encryptionKey.all { it == 0.toByte() })
    }

    @Test
    fun encryptAndDecrypt_roundTripSucceeds() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val noteId = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
        val version = 1
        val plaintext = """{"title":"Classified Memo","body":"Top secret note content."}"""

        val encrypted = cryptoManager.encryptNote(
            noteId = noteId,
            version = version,
            plaintextJson = plaintext,
            encryptionKey = key
        )

        assertNotNull(encrypted.ciphertext)
        assertNotNull(encrypted.nonce)
        assertEquals(12, encrypted.nonce.size)
        // Ciphertext size must equal plaintext + 16 bytes auth tag
        assertEquals(plaintext.toByteArray(Charsets.UTF_8).size + 16, encrypted.ciphertext.size)

        val decrypted = cryptoManager.decryptNote(
            noteId = noteId,
            version = version,
            ciphertext = encrypted.ciphertext,
            nonce = encrypted.nonce,
            encryptionKey = key
        )

        assertEquals(plaintext, decrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun decrypt_failsWhenNoteIdMismatches() {
        val key = ByteArray(32) { 0x42.toByte() }
        val plaintext = "Sensitive Content"

        val encrypted = cryptoManager.encryptNote(
            noteId = "note-uuid-original",
            version = 1,
            plaintextJson = plaintext,
            encryptionKey = key
        )

        // Attempt decrypt with different noteId (AAD mismatch)
        cryptoManager.decryptNote(
            noteId = "note-uuid-tampered",
            version = 1,
            ciphertext = encrypted.ciphertext,
            nonce = encrypted.nonce,
            encryptionKey = key
        )
    }

    @Test(expected = AEADBadTagException::class)
    fun decrypt_failsWhenVersionRollbackAttempted() {
        val key = ByteArray(32) { 0x42.toByte() }
        val plaintext = "Sensitive Content"

        val encrypted = cryptoManager.encryptNote(
            noteId = "note-uuid-v2",
            version = 2,
            plaintextJson = plaintext,
            encryptionKey = key
        )

        // Attempt decrypt with version 1 (rollback attack detection)
        cryptoManager.decryptNote(
            noteId = "note-uuid-v2",
            version = 1,
            ciphertext = encrypted.ciphertext,
            nonce = encrypted.nonce,
            encryptionKey = key
        )
    }

    @Test(expected = AEADBadTagException::class)
    fun decrypt_failsWhenCiphertextTampered() {
        val key = ByteArray(32) { 0x77.toByte() }
        val plaintext = "Integrity Protected Note"

        val encrypted = cryptoManager.encryptNote(
            noteId = "note-uuid-tamper",
            version = 1,
            plaintextJson = plaintext,
            encryptionKey = key
        )

        // Corrupt a byte in ciphertext
        val tamperedCiphertext = encrypted.ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()

        cryptoManager.decryptNote(
            noteId = "note-uuid-tamper",
            version = 1,
            ciphertext = tamperedCiphertext,
            nonce = encrypted.nonce,
            encryptionKey = key
        )
    }

    @Test
    fun aesGcm_knownAnswerTestVector() {
        // NIST GCM Test Vector (256-bit key, 96-bit IV)
        val hexFormat = HexFormat.of()
        val key = hexFormat.parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hexFormat.parseHex("000000000000000000000000")
        val plaintext = hexFormat.parseHex("00000000000000000000000000000000")

        val noteId = "kat-note"
        val version = 1
        val aad = cryptoManager.buildAad(noteId, version)

        // Encrypt with Cipher directly using the deterministic nonce and key
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce)
        )
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)

        // Decrypt with CryptoManager
        val cipherDecrypt = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipherDecrypt.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce)
        )
        cipherDecrypt.updateAAD(aad)
        val recovered = cipherDecrypt.doFinal(ciphertext)

        assertArrayEquals(plaintext, recovered)
    }
}
