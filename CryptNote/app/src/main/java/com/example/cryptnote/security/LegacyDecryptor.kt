package com.example.cryptnote.security

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Format 0 (legacy) decryptor. Wraps the initial v0 decryption algorithm
 * that used String noteId + Int version AAD without envelope header.
 *
 * Contract: Never delete this class; all legacy rows are decoded through here.
 */
object LegacyDecryptor {

    const val FORMAT = 0

    fun buildLegacyAad(noteId: String, version: Int): ByteArray {
        val noteIdBytes = noteId.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(noteIdBytes.size + 4)
        buffer.put(noteIdBytes)
        buffer.putInt(version)
        return buffer.array()
    }

    fun decrypt(
        noteId: String,
        version: Int,
        ciphertext: ByteArray,
        nonce: ByteArray,
        encryptionKey: ByteArray
    ): Result<ByteArray> {
        return try {
            val aad = buildLegacyAad(noteId, version)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(encryptionKey, "AES")
            val gcmSpec = GCMParameterSpec(128, nonce)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(aad)
            val decryptedBytes = cipher.doFinal(ciphertext)
            Result.success(decryptedBytes)
        } catch (e: GeneralSecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
