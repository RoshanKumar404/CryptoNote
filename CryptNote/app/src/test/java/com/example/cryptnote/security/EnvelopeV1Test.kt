package com.example.cryptnote.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EnvelopeV1Test {

    private val cryptoManager = CryptoManagerImpl()

    @Test
    fun frozenTestVector_passesCodecCheck() {
        // Frozen SPEC-3 vector:
        // MK: 32 bytes of 0x01
        val mk = ByteArray(32) { 1.toByte() }
        val keyId = cryptoManager.deriveKeyId(mk)
        val derivedKeys = cryptoManager.splitMasterKey(mk)
        val encKey = derivedKeys.encryptionKey

        val noteId = UUID.fromString("12345678-1234-5678-1234-567812345678")
        val version = 1L
        val plaintext = "Hello CryptoNote Envelope V1".toByteArray(Charsets.UTF_8)
        val fixedNonce = ByteArray(12) { 2.toByte() }

        val sealed = EnvelopeV1.seal(
            key = encKey,
            keyId = keyId,
            noteId = noteId,
            version = version,
            plaintext = plaintext,
            fixedNonce = fixedNonce
        )

        // Expected hex from SPEC-3 test vector
        val expectedHex = "01013d158e58ca752a50020202020202020202020202b2da0b39908e92bbebf849e5536e74ae5533b31886f8d0bba24a4c3e73d5f5ccc0e353430d138c1d96a74743"
        val actualHex = sealed.joinToString("") { "%02x".format(it) }
        assertEquals(expectedHex, actualHex)

        // Open sealed envelope
        val openResult = EnvelopeV1.open(
            key = encKey,
            currentKeyId = keyId,
            noteId = noteId,
            version = version,
            env = sealed
        )

        assertTrue(openResult is OpenResult.Opened)
        assertArrayEquals(plaintext, (openResult as OpenResult.Opened).plaintext)
    }

    @Test
    fun goldenFixtureResource_opensSuccessfully() {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/v1_golden_fixture.json")
            ?: throw IllegalStateException("Resource not found")
        val jsonText = stream.bufferedReader().use { it.readText() }

        // Minimal manual parse
        val mkHex = Regex("\"master_key_hex\":\\s*\"([^\"]+)\"").find(jsonText)!!.groupValues[1]
        val noteIdStr = Regex("\"note_id\":\\s*\"([^\"]+)\"").find(jsonText)!!.groupValues[1]
        val ver = Regex("\"version\":\\s*(\\d+)").find(jsonText)!!.groupValues[1].toLong()
        val envB64 = Regex("\"envelope_base64url\":\\s*\"([^\"]+)\"").find(jsonText)!!.groupValues[1]
        val expectedText = Regex("\"expected_plaintext\":\\s*\"([^\"]+)\"").find(jsonText)!!.groupValues[1]

        val mk = mkHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val keyId = cryptoManager.deriveKeyId(mk)
        val encKey = cryptoManager.splitMasterKey(mk).encryptionKey
        val noteId = UUID.fromString(noteIdStr)
        val envBytes = java.util.Base64.getUrlDecoder().decode(envB64)

        val result = EnvelopeV1.open(encKey, keyId, noteId, ver, envBytes)
        assertTrue(result is OpenResult.Opened)
        assertEquals(expectedText, String((result as OpenResult.Opened).plaintext, Charsets.UTF_8))
    }

    @Test
    fun open_returnsKeyMismatch_whenKeyIdDiffers() {
        val mk = ByteArray(32) { 1.toByte() }
        val keyId = cryptoManager.deriveKeyId(mk)
        val wrongKeyId = ByteArray(8) { 9.toByte() }
        val encKey = cryptoManager.splitMasterKey(mk).encryptionKey

        val noteId = UUID.randomUUID()
        val sealed = EnvelopeV1.seal(encKey, keyId, noteId, 1L, "Secret".toByteArray())

        val result = EnvelopeV1.open(encKey, wrongKeyId, noteId, 1L, sealed)
        assertTrue(result is OpenResult.KeyMismatch)
        assertArrayEquals(keyId, (result as OpenResult.KeyMismatch).noteKeyId)
    }

    @Test
    fun open_returnsUnknownFormat_whenFormatByteDiffers() {
        val mk = ByteArray(32) { 1.toByte() }
        val keyId = cryptoManager.deriveKeyId(mk)
        val encKey = cryptoManager.splitMasterKey(mk).encryptionKey

        val noteId = UUID.randomUUID()
        val sealed = EnvelopeV1.seal(encKey, keyId, noteId, 1L, "Secret".toByteArray())
        sealed[0] = 2.toByte() // format 2

        val result = EnvelopeV1.open(encKey, keyId, noteId, 1L, sealed)
        assertTrue(result is OpenResult.UnknownFormat)
        assertEquals(2, (result as OpenResult.UnknownFormat).formatVersion)
    }

    @Test
    fun open_returnsCorrupt_whenOneByteFlippedInCiphertext() {
        val mk = ByteArray(32) { 1.toByte() }
        val keyId = cryptoManager.deriveKeyId(mk)
        val encKey = cryptoManager.splitMasterKey(mk).encryptionKey

        val noteId = UUID.randomUUID()
        val sealed = EnvelopeV1.seal(encKey, keyId, noteId, 1L, "Secret".toByteArray())
        // Flip byte in ciphertext payload
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()

        val result = EnvelopeV1.open(encKey, keyId, noteId, 1L, sealed)
        assertTrue(result is OpenResult.Corrupt)
    }

    @Test
    fun legacyDecryptor_opensFormat0Notes() {
        val key = ByteArray(32) { 3.toByte() }
        val noteId = "legacy-note-uuid-1234"
        val version = 2
        val plaintext = "Legacy note content"

        val encrypted = cryptoManager.encryptNote(
            noteId = noteId,
            version = version,
            plaintextJson = plaintext,
            encryptionKey = key
        )

        val result = LegacyDecryptor.decrypt(
            noteId = noteId,
            version = version,
            ciphertext = encrypted.ciphertext,
            nonce = encrypted.nonce,
            encryptionKey = key
        )

        assertTrue(result.isSuccess)
        assertEquals(plaintext, String(result.getOrThrow(), Charsets.UTF_8))
    }
}
