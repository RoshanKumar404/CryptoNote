package com.example.cryptnote.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed interface OpenResult {
    class Opened(val plaintext: ByteArray, val isLegacyAad: Boolean = false) : OpenResult
    class UnknownFormat(val formatVersion: Int) : OpenResult
    class KeyMismatch(val noteKeyId: ByteArray) : OpenResult
    object Corrupt : OpenResult
}

object EnvelopeV1 {
    const val FORMAT = 1
    const val ALG_AES_256_GCM = 1
    const val HEADER = 10   // format(1) + alg(1) + keyId(8)
    const val NONCE = 12

    /**
     * Modern seal: version is NOT included in the AES-GCM AAD.
     * The AAD authenticates header || noteId (26 bytes).
     * This decouples cryptographic payload integrity from sync versioning,
     * ensuring that outbox coalescing and pull version increments never break decryption.
     */
    fun seal(
        key: ByteArray,
        keyId: ByteArray,
        noteId: UUID,
        plaintext: ByteArray,
        fixedNonce: ByteArray? = null
    ): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes (256 bits)" }
        require(keyId.size == 8) { "KeyId must be 8 bytes" }
        val nonce = fixedNonce ?: ByteArray(NONCE).also { SecureRandom().nextBytes(it) }
        require(nonce.size == NONCE) { "Nonce must be 12 bytes" }

        val header = byteArrayOf(FORMAT.toByte(), ALG_AES_256_GCM.toByte()) + keyId
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad(header, noteId))
        return header + nonce + cipher.doFinal(plaintext)
    }

    /**
     * Overload for backwards compatibility with callers passing version.
     * If includeVersionInAad is false (default), seals using modern version-independent AAD.
     * If includeVersionInAad is true, seals using legacy AAD (header || noteId || version).
     */
    fun seal(
        key: ByteArray,
        keyId: ByteArray,
        noteId: UUID,
        version: Long,
        plaintext: ByteArray,
        fixedNonce: ByteArray? = null,
        includeVersionInAad: Boolean = false
    ): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes (256 bits)" }
        require(keyId.size == 8) { "KeyId must be 8 bytes" }
        val nonce = fixedNonce ?: ByteArray(NONCE).also { SecureRandom().nextBytes(it) }
        require(nonce.size == NONCE) { "Nonce must be 12 bytes" }

        val header = byteArrayOf(FORMAT.toByte(), ALG_AES_256_GCM.toByte()) + keyId
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val aadBytes = if (includeVersionInAad) aadLegacy(header, noteId, version) else aad(header, noteId)
        cipher.updateAAD(aadBytes)
        return header + nonce + cipher.doFinal(plaintext)
    }

    /**
     * Opens an EnvelopeV1.
     * Attempt 1: Modern version-independent AAD (header || noteId).
     * Attempt 2: Legacy fallback with stored version in AAD (header || noteId || version).
     * Attempt 3: Candidate versions (1..version+16) if the version drifted during legacy coalescing.
     */
    fun open(key: ByteArray, currentKeyId: ByteArray, noteId: UUID, version: Long, env: ByteArray): OpenResult {
        if (env.size < HEADER + NONCE + 16) return OpenResult.Corrupt
        val format = env[0].toInt() and 0xFF
        if (format != FORMAT) return OpenResult.UnknownFormat(format)
        val header = env.copyOfRange(0, HEADER)
        val keyId = header.copyOfRange(2, HEADER)
        if (!keyId.contentEquals(currentKeyId)) return OpenResult.KeyMismatch(keyId)
        val nonce = env.copyOfRange(HEADER, HEADER + NONCE)

        // Attempt 1: Modern version-independent AAD
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad(header, noteId))
            val plaintext = cipher.doFinal(env, HEADER + NONCE, env.size - HEADER - NONCE)
            return OpenResult.Opened(plaintext, isLegacyAad = false)
        } catch (_: GeneralSecurityException) {
            // Modern decrypt failed; try legacy fallback
        } catch (_: Exception) {
            // Try legacy fallback
        }

        // Attempt 2: Legacy AAD with version
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aadLegacy(header, noteId, version))
            val plaintext = cipher.doFinal(env, HEADER + NONCE, env.size - HEADER - NONCE)
            return OpenResult.Opened(plaintext, isLegacyAad = true)
        } catch (_: GeneralSecurityException) {
            // Attempt 3: Try candidate versions in case legacy version was coalesced/drifted
            val maxCandidate = (version + 16).coerceAtLeast(16L)
            for (v in 1L..maxCandidate) {
                if (v == version) continue
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                    cipher.updateAAD(aadLegacy(header, noteId, v))
                    val plaintext = cipher.doFinal(env, HEADER + NONCE, env.size - HEADER - NONCE)
                    return OpenResult.Opened(plaintext, isLegacyAad = true)
                } catch (_: Exception) {
                    // Continue probing candidate versions
                }
            }
            return OpenResult.Corrupt
        } catch (_: Exception) {
            return OpenResult.Corrupt
        }
    }

    /** Modern version-independent AAD: Header (10B) || NoteUUID (16B) = 26 bytes */
    fun aad(header: ByteArray, noteId: UUID): ByteArray =
        ByteBuffer.allocate(header.size + 16).put(header)
            .putLong(noteId.mostSignificantBits).putLong(noteId.leastSignificantBits)
            .array()

    /** Legacy AAD: Header (10B) || NoteUUID (16B) || Version (8B) = 34 bytes */
    fun aadLegacy(header: ByteArray, noteId: UUID, version: Long): ByteArray =
        ByteBuffer.allocate(header.size + 16 + 8).put(header)
            .putLong(noteId.mostSignificantBits).putLong(noteId.leastSignificantBits)
            .putLong(version).array()
}
