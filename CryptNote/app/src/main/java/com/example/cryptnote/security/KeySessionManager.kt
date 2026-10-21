package com.example.cryptnote.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the in-memory lifecycle of decrypted master and derived keys.
 * Ensures keys remain in memory only while unlocked and are zeroed immediately on lock.
 */
interface KeySessionManager {
    val isUnlocked: StateFlow<Boolean>
    val isSetup: Boolean
    val isBiometricEnabled: Boolean

    fun setupPassphrase(passphrase: CharArray, salt: ByteArray? = null): Result<Unit>
    fun initializeWithMasterKey(masterKey: ByteArray, salt: ByteArray): Result<Unit>
    fun unlock(passphrase: CharArray): Result<Unit>
    fun lock()
    fun getEncryptionKey(): ByteArray
    fun getAuthKey(): ByteArray
    fun getKeyId(): ByteArray

    fun canUnlockWithBiometric(): Boolean
    fun setupBiometric(cipher: javax.crypto.Cipher): Result<Unit>
    fun unlockWithBiometric(cipher: javax.crypto.Cipher): Result<Unit>
    fun disableBiometric()
    fun getBiometricIv(): ByteArray?
}

@Singleton
class KeySessionManagerImpl @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val keyWrapper: KeyWrapper,
    private val keyStorageManager: KeyStorageManager
) : KeySessionManager {

    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    override val isSetup: Boolean
        get() = keyStorageManager.isSetup()

    private var activeEncryptionKey: ByteArray? = null
    private var activeAuthKey: ByteArray? = null
    private var activeKeyId: ByteArray? = null

    @Synchronized
    override fun setupPassphrase(passphrase: CharArray, salt: ByteArray?): Result<Unit> {
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) {
            return Result.failure(
                IllegalArgumentException("Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters")
            )
        }

        val effectiveSalt = salt ?: cryptoManager.generateSalt(16)
        val masterKey = cryptoManager.deriveMasterKey(passphrase, effectiveSalt)

        try {
            val derived = cryptoManager.splitMasterKey(masterKey)
            val wrapped = keyWrapper.wrapKey(masterKey)

            val kcv = computeKcv(masterKey)
            keyStorageManager.storeVault(
                salt = effectiveSalt,
                wrappedKey = wrapped.wrappedKey,
                iv = wrapped.iv,
                kcv = kcv,
                keyVersion = 1
            )

            // Clear previous active keys if any
            lock()

            activeEncryptionKey = derived.encryptionKey
            activeAuthKey = derived.authKey
            activeKeyId = cryptoManager.deriveKeyId(masterKey)
            _isUnlocked.value = true

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            masterKey.fill(0)
        }
    }

    @Synchronized
    override fun initializeWithMasterKey(masterKey: ByteArray, salt: ByteArray): Result<Unit> {
        try {
            val derived = cryptoManager.splitMasterKey(masterKey)
            val wrapped = keyWrapper.wrapKey(masterKey)

            val kcv = computeKcv(masterKey)
            keyStorageManager.storeVault(
                salt = salt,
                wrappedKey = wrapped.wrappedKey,
                iv = wrapped.iv,
                kcv = kcv,
                keyVersion = 1
            )

            lock()

            activeEncryptionKey = derived.encryptionKey
            activeAuthKey = derived.authKey
            activeKeyId = cryptoManager.deriveKeyId(masterKey)
            _isUnlocked.value = true

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    @Synchronized
    override fun unlock(passphrase: CharArray): Result<Unit> {
        val salt = keyStorageManager.getSalt()
            ?: return Result.failure(IllegalStateException("Vault has not been initialized"))
        val wrappedKey = keyStorageManager.getWrappedKey()
            ?: return Result.failure(IllegalStateException("Wrapped key not found"))
        val iv = keyStorageManager.getWrapperIv()
            ?: return Result.failure(IllegalStateException("Wrapper IV not found"))

        val candidateMasterKey = cryptoManager.deriveMasterKey(passphrase, salt)
        val unwrappedMasterKey = try {
            keyWrapper.unwrapKey(wrappedKey, iv)
        } catch (e: Exception) {
            candidateMasterKey.fill(0)
            return Result.failure(IllegalArgumentException("Failed to unwrap master key: ${e.message}"))
        }

        try {
            // Constant-time comparison between derived candidate and Keystore unwrapped master key
            val matches = MessageDigest.isEqual(candidateMasterKey, unwrappedMasterKey)
            if (!matches) {
                return Result.failure(IllegalArgumentException("Wrong passphrase"))
            }

            // Verify Key Check Value (KCV) per NV-FE-2
            val storedKcv = keyStorageManager.getKcv()
            if (storedKcv != null) {
                val candidateKcv = computeKcv(candidateMasterKey)
                if (!MessageDigest.isEqual(candidateKcv, storedKcv)) {
                    return Result.failure(IllegalArgumentException("Wrong passphrase"))
                }
            }

            val derived = cryptoManager.splitMasterKey(candidateMasterKey)

            // Wipe prior active keys
            lock()

            activeEncryptionKey = derived.encryptionKey
            activeAuthKey = derived.authKey
            activeKeyId = cryptoManager.deriveKeyId(candidateMasterKey)
            _isUnlocked.value = true

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            candidateMasterKey.fill(0)
            unwrappedMasterKey.fill(0)
        }
    }

    @Synchronized
    override fun lock() {
        activeEncryptionKey?.fill(0)
        activeEncryptionKey = null

        activeAuthKey?.fill(0)
        activeAuthKey = null

        activeKeyId?.fill(0)
        activeKeyId = null

        _isUnlocked.value = false
    }

    @Synchronized
    override fun getEncryptionKey(): ByteArray {
        val key = activeEncryptionKey
        if (!_isUnlocked.value || key == null) {
            throw IllegalStateException("Crypto vault is currently locked. Unlock with passphrase first.")
        }
        return key
    }

    @Synchronized
    override fun getAuthKey(): ByteArray {
        val key = activeAuthKey
        if (!_isUnlocked.value || key == null) {
            throw IllegalStateException("Crypto vault is currently locked. Unlock with passphrase first.")
        }
        return key
    }

    @Synchronized
    override fun getKeyId(): ByteArray {
        val keyId = activeKeyId
        if (!_isUnlocked.value || keyId == null) {
            throw IllegalStateException("Crypto vault is currently locked. Unlock with passphrase first.")
        }
        return keyId
    }

    override val isBiometricEnabled: Boolean
        get() = keyStorageManager.isBiometricEnabled()

    override fun canUnlockWithBiometric(): Boolean {
        return keyStorageManager.isBiometricEnabled() &&
                keyStorageManager.getBiometricWrappedKey() != null
    }

    override fun getBiometricIv(): ByteArray? {
        return keyStorageManager.getBiometricIv() ?: ByteArray(0)
    }

    override fun disableBiometric() {
        keyStorageManager.clearBiometric()
    }

    @Synchronized
    override fun setupBiometric(cipher: javax.crypto.Cipher): Result<Unit> {
        val wrappedKey = keyStorageManager.getWrappedKey()
            ?: return Result.failure(IllegalStateException("Vault master key not found"))
        val iv = keyStorageManager.getWrapperIv()
            ?: return Result.failure(IllegalStateException("Wrapper IV not found"))

        val masterKey = try {
            keyWrapper.unwrapKey(wrappedKey, iv)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Failed to unwrap master key for biometric: ${e.message}"))
        }

        try {
            val biometricWrapped = cipher.doFinal(masterKey)
            val cipherIv = cipher.iv ?: ByteArray(0)
            keyStorageManager.storeBiometricVault(biometricWrapped, cipherIv)
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            masterKey.fill(0)
        }
    }

    @Synchronized
    override fun unlockWithBiometric(cipher: javax.crypto.Cipher): Result<Unit> {
        val biometricWrapped = keyStorageManager.getBiometricWrappedKey()
            ?: return Result.failure(IllegalStateException("Biometric vault data not found"))

        val masterKey = try {
            cipher.doFinal(biometricWrapped)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Biometric decryption failed: ${e.message}"))
        }

        try {
            val derived = cryptoManager.splitMasterKey(masterKey)

            // Wipe prior active keys
            lock()

            activeEncryptionKey = derived.encryptionKey
            activeAuthKey = derived.authKey
            activeKeyId = cryptoManager.deriveKeyId(masterKey)
            _isUnlocked.value = true

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            masterKey.fill(0)
        }
    }

    private fun computeKcv(masterKey: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("cryptnote/kcv/v1".toByteArray(Charsets.UTF_8))
        digest.update(masterKey)
        return digest.digest().copyOf(8)
    }

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}
