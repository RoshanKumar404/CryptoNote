package com.example.cryptnote.security

import java.util.Arrays

/**
 * Container for cryptographic keys derived from user passphrase and salt via Argon2id + HKDF.
 *
 * @property authKey 256-bit key used for zero-knowledge server authentication (never encrypts notes).
 * @property encryptionKey 256-bit key used for local AES-256-GCM note encryption.
 */
data class DerivedKeys(
    val authKey: ByteArray,
    val encryptionKey: ByteArray
) {
    /**
     * Securely clears key material from memory by filling byte arrays with zeros.
     */
    fun zeroize() {
        authKey.fill(0)
        encryptionKey.fill(0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DerivedKeys

        if (!authKey.contentEquals(other.authKey)) return false
        if (!encryptionKey.contentEquals(other.encryptionKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authKey.contentHashCode()
        result = 31 * result + encryptionKey.contentHashCode()
        return result
    }
}

/**
 * Output of AES-256-GCM encryption for a note.
 *
 * @property ciphertext Encrypted payload including the 128-bit authentication tag.
 * @property nonce 12-byte random cryptographic initialization vector.
 */
data class EncryptedNotePayload(
    val ciphertext: ByteArray,
    val nonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedNotePayload

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!nonce.contentEquals(other.nonce)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }
}

/**
 * Output of Android Keystore key wrapping for the master key.
 *
 * @property wrappedKey The ciphertext of the master key wrapped with Keystore AES-256 key.
 * @property iv The initialization vector used by the Keystore AES cipher.
 */
data class WrappedKeyResult(
    val wrappedKey: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WrappedKeyResult

        if (!wrappedKey.contentEquals(other.wrappedKey)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = wrappedKey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
