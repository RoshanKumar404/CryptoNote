package com.example.cryptnote.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed interface OpenResult {
    class Opened(val plaintext: ByteArray) : OpenResult
    class UnknownFormat(val formatVersion: Int) : OpenResult
    class KeyMismatch(val noteKeyId: ByteArray) : OpenResult
    object Corrupt : OpenResult
}

object EnvelopeV1 {
    const val FORMAT = 1
    const val ALG_AES_256_GCM = 1
    const val HEADER = 10   // format(1) + alg(1) + keyId(8)
    const val NONCE = 12

    fun seal(
        key: ByteArray,
        keyId: ByteArray,
        noteId: UUID,
        version: Long,
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
        cipher.updateAAD(aad(header, noteId, version))
        return header + nonce + cipher.doFinal(plaintext)
    }

    fun open(key: ByteArray, currentKeyId: ByteArray, noteId: UUID, version: Long, env: ByteArray): OpenResult {
        if (env.size < HEADER + NONCE + 16) return OpenResult.Corrupt
        val format = env[0].toInt() and 0xFF
        if (format != FORMAT) return OpenResult.UnknownFormat(format)
        val header = env.copyOfRange(0, HEADER)
        val keyId = header.copyOfRange(2, HEADER)
        if (!keyId.contentEquals(currentKeyId)) return OpenResult.KeyMismatch(keyId)
        val nonce = env.copyOfRange(HEADER, HEADER + NONCE)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad(header, noteId, version))
            OpenResult.Opened(cipher.doFinal(env, HEADER + NONCE, env.size - HEADER - NONCE))
        } catch (_: GeneralSecurityException) {
            OpenResult.Corrupt
        } catch (_: Exception) {
            OpenResult.Corrupt
        }
    }

    fun aad(header: ByteArray, noteId: UUID, version: Long): ByteArray =
        ByteBuffer.allocate(header.size + 16 + 8).put(header)
            .putLong(noteId.mostSignificantBits).putLong(noteId.leastSignificantBits)
            .putLong(version).array()
}
