package com.example.cryptnote.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeySessionManagerTest {

    private lateinit var cryptoManager: CryptoManager
    private lateinit var keyWrapper: KeyWrapper
    private lateinit var keyStorageManager: KeyStorageManager
    private lateinit var keySessionManager: KeySessionManager

    @Before
    fun setUp() {
        cryptoManager = CryptoManagerImpl()
        keyWrapper = JvmKeyWrapper()
        keyStorageManager = InMemoryKeyStorageManager()
        keySessionManager = KeySessionManagerImpl(
            cryptoManager = cryptoManager,
            keyWrapper = keyWrapper,
            keyStorageManager = keyStorageManager
        )
    }

    @Test
    fun setupPassphrase_rejectsLessThan8Chars() {
        val result = keySessionManager.setupPassphrase("short".toCharArray())
        assertTrue(result.isFailure)
        assertFalse(keySessionManager.isSetup)
        assertFalse(keySessionManager.isUnlocked.value)
    }

    @Test
    fun setupPassphrase_initializesVaultAndUnlocks() {
        val passphrase = "strong-master-passphrase-2026".toCharArray()
        val result = keySessionManager.setupPassphrase(passphrase)

        assertTrue(result.isSuccess)
        assertTrue(keySessionManager.isSetup)
        assertTrue(keySessionManager.isUnlocked.value)

        val encKey = keySessionManager.getEncryptionKey()
        val authKey = keySessionManager.getAuthKey()

        assertNotNull(encKey)
        assertNotNull(authKey)
        assertEquals(32, encKey.size)
        assertEquals(32, authKey.size)
    }

    @Test
    fun lock_zeroesKeysAndMarksLocked() {
        val passphrase = "strong-master-passphrase-2026".toCharArray()
        keySessionManager.setupPassphrase(passphrase)
        assertTrue(keySessionManager.isUnlocked.value)

        keySessionManager.lock()
        assertFalse(keySessionManager.isUnlocked.value)
    }

    @Test(expected = IllegalStateException::class)
    fun getEncryptionKey_throwsWhenLocked() {
        val passphrase = "strong-master-passphrase-2026".toCharArray()
        keySessionManager.setupPassphrase(passphrase)
        keySessionManager.lock()

        keySessionManager.getEncryptionKey()
    }

    @Test
    fun unlock_failsWithWrongPassphrase() {
        val correct = "correct-passphrase-xyz".toCharArray()
        val wrong = "wrong-passphrase-abc".toCharArray()

        keySessionManager.setupPassphrase(correct)
        keySessionManager.lock()
        assertFalse(keySessionManager.isUnlocked.value)

        val unlockResult = keySessionManager.unlock(wrong)
        assertTrue(unlockResult.isFailure)
        assertFalse(keySessionManager.isUnlocked.value)
    }

    @Test
    fun unlock_succeedsWithCorrectPassphrase() {
        val correct = "correct-passphrase-xyz".toCharArray()

        keySessionManager.setupPassphrase(correct)
        val initialEncKey = keySessionManager.getEncryptionKey().copyOf()
        keySessionManager.lock()
        assertFalse(keySessionManager.isUnlocked.value)

        val unlockResult = keySessionManager.unlock(correct)
        assertTrue(unlockResult.isSuccess)
        assertTrue(keySessionManager.isUnlocked.value)

        val restoredEncKey = keySessionManager.getEncryptionKey()
        org.junit.Assert.assertArrayEquals(initialEncKey, restoredEncKey)
    }

    @Test
    fun biometric_setupAndUnlock_succeeds() {
        val passphrase = "master-passphrase-biometric".toCharArray()
        keySessionManager.setupPassphrase(passphrase)
        val initialEncKey = keySessionManager.getEncryptionKey().copyOf()

        val testBiometricSecretKey = javax.crypto.spec.SecretKeySpec(ByteArray(32) { 7 }, "AES")
        val encryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, testBiometricSecretKey)

        val setupResult = keySessionManager.setupBiometric(encryptCipher)
        assertTrue(setupResult.isSuccess)
        assertTrue(keySessionManager.isBiometricEnabled)
        assertTrue(keySessionManager.canUnlockWithBiometric())

        val iv = keySessionManager.getBiometricIv()
        assertNotNull(iv)

        // Lock vault
        keySessionManager.lock()
        assertFalse(keySessionManager.isUnlocked.value)

        // Decrypt with authorized cipher
        val decryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, testBiometricSecretKey, javax.crypto.spec.GCMParameterSpec(128, iv))

        val unlockResult = keySessionManager.unlockWithBiometric(decryptCipher)
        assertTrue(unlockResult.isSuccess)
        assertTrue(keySessionManager.isUnlocked.value)

        val restoredEncKey = keySessionManager.getEncryptionKey()
        org.junit.Assert.assertArrayEquals(initialEncKey, restoredEncKey)

        // Test disable biometric
        keySessionManager.disableBiometric()
        assertFalse(keySessionManager.isBiometricEnabled)
        assertFalse(keySessionManager.canUnlockWithBiometric())
    }
}
