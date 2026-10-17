package com.example.cryptnote.security

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface defining high-level cryptographic operations for CryptoNote.
 */
interface CryptoManager {
    fun generateSalt(length: Int = 16): ByteArray
    fun generateNonce(length: Int = 12): ByteArray
    fun deriveKeys(passphrase: CharArray, salt: ByteArray): DerivedKeys
    fun deriveMasterKey(passphrase: CharArray, salt: ByteArray): ByteArray
    fun splitMasterKey(masterKey: ByteArray): DerivedKeys
    fun encryptNote(
        noteId: String,
        version: Int,
        plaintextJson: String,
        encryptionKey: ByteArray
    ): EncryptedNotePayload
    fun decryptNote(
        noteId: String,
        version: Int,
        ciphertext: ByteArray,
        nonce: ByteArray,
        encryptionKey: ByteArray
    ): String
    fun buildAad(noteId: String, version: Int): ByteArray
}

/**
 * Production implementation of [CryptoManager] utilizing BouncyCastle Argon2id,
 * RFC 5869 HKDF-SHA256, and hardware-accelerated AES-256-GCM.
 */
@Singleton
class CryptoManagerImpl @Inject constructor() : CryptoManager {

    private val secureRandom = SecureRandom()

    override fun generateSalt(length: Int): ByteArray {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        return salt
    }

    override fun generateNonce(length: Int): ByteArray {
        val nonce = ByteArray(length)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * Derives a 32-byte master key from a passphrase and salt using Argon2id
     * with OWASP-aligned parameters (iterations=3, memory=64MB, parallelism=1).
     */
    override fun deriveMasterKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val passphraseBytes = charArrayToUtf8Bytes(passphrase)
        val masterKey = ByteArray(ARGON2_OUTPUT_LENGTH)

        try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withIterations(ARGON2_ITERATIONS)
                .withMemoryAsKB(ARGON2_MEMORY_KB)
                .withParallelism(ARGON2_PARALLELISM)
                .build()

            val generator = Argon2BytesGenerator()
            generator.init(params)
            generator.generateBytes(passphraseBytes, masterKey)

            return masterKey
        } finally {
            passphraseBytes.fill(0)
        }
    }

    /**
     * Expands a 32-byte master key into authKey and encryptionKey using HKDF-SHA256.
     * Information tags:
     * - "auth" -> 32-byte auth key
     * - "encrypt" -> 32-byte encryption key
     */
    override fun splitMasterKey(masterKey: ByteArray): DerivedKeys {
        val authKey = hkdfExpand(masterKey, INFO_AUTH, KEY_LENGTH)
        val encryptionKey = hkdfExpand(masterKey, INFO_ENCRYPT, KEY_LENGTH)
        return DerivedKeys(authKey = authKey, encryptionKey = encryptionKey)
    }

    /**
     * Derives both auth and encryption keys directly from passphrase and salt.
     * The intermediate master key is automatically zeroed out in memory.
     */
    override fun deriveKeys(passphrase: CharArray, salt: ByteArray): DerivedKeys {
        val masterKey = deriveMasterKey(passphrase, salt)
        try {
            return splitMasterKey(masterKey)
        } finally {
            masterKey.fill(0)
        }
    }

    /**
     * Encrypts plaintext JSON payload using AES-256-GCM with a fresh random 12-byte nonce
     * and binds noteId + version into the Additional Authenticated Data (AAD) to prevent
     * ciphertext swapping, replay, or rollback attacks.
     */
    override fun encryptNote(
        noteId: String,
        version: Int,
        plaintextJson: String,
        encryptionKey: ByteArray
    ): EncryptedNotePayload {
        require(encryptionKey.size == KEY_LENGTH) { "Encryption key must be 32 bytes (256-bit)" }

        val nonce = generateNonce(NONCE_LENGTH)
        val plaintextBytes = plaintextJson.toByteArray(StandardCharsets.UTF_8)
        val aad = buildAad(noteId, version)

        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(encryptionKey, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plaintextBytes)

            return EncryptedNotePayload(ciphertext = ciphertext, nonce = nonce)
        } finally {
            plaintextBytes.fill(0)
        }
    }

    /**
     * Decrypts AES-256-GCM ciphertext, verifying the GCM authentication tag
     * and the AAD (noteId + version). Throws an exception if ciphertext or metadata was altered.
     */
    override fun decryptNote(
        noteId: String,
        version: Int,
        ciphertext: ByteArray,
        nonce: ByteArray,
        encryptionKey: ByteArray
    ): String {
        require(encryptionKey.size == KEY_LENGTH) { "Encryption key must be 32 bytes (256-bit)" }
        require(nonce.size == NONCE_LENGTH) { "Nonce must be 12 bytes" }

        val aad = buildAad(noteId, version)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val keySpec = SecretKeySpec(encryptionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(aad)
        val decryptedBytes = cipher.doFinal(ciphertext)

        return try {
            String(decryptedBytes, StandardCharsets.UTF_8)
        } finally {
            decryptedBytes.fill(0)
        }
    }

    /**
     * Constructs Additional Authenticated Data (AAD) binding noteId and version.
     */
    override fun buildAad(noteId: String, version: Int): ByteArray {
        val noteIdBytes = noteId.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(noteIdBytes.size + 4)
        buffer.put(noteIdBytes)
        buffer.putInt(version)
        return buffer.array()
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        // HKDF with null salt treats prk as pseudorandom key for expand step
        hkdf.init(HKDFParameters(prk, null, info))
        val okm = ByteArray(outLen)
        hkdf.generateBytes(okm, 0, outLen)
        return okm
    }

    private fun charArrayToUtf8Bytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
    }

    companion object {
        const val ARGON2_ITERATIONS = 3
        const val ARGON2_MEMORY_KB = 65536 // 64 MB
        const val ARGON2_PARALLELISM = 1
        const val ARGON2_OUTPUT_LENGTH = 32 // 256 bits

        const val KEY_LENGTH = 32 // 256 bits
        const val NONCE_LENGTH = 12 // 96 bits recommended for GCM
        const val GCM_TAG_LENGTH_BITS = 128
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

        val INFO_AUTH = "auth".toByteArray(StandardCharsets.UTF_8)
        val INFO_ENCRYPT = "encrypt".toByteArray(StandardCharsets.UTF_8)
    }
}
