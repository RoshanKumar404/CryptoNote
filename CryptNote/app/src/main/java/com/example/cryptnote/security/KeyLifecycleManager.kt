package com.example.cryptnote.security

import com.example.cryptnote.data.remote.api.KeysApi
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SetupDecision {
    object CreateNew : SetupDecision
    object Restore : SetupDecision
    object Blocked : SetupDecision
}

interface KeyLifecycleManager {
    suspend fun decideSetup(): SetupDecision
    fun computeKcv(masterKey: ByteArray): ByteArray
    fun verifyKcv(candidateMasterKey: ByteArray, expectedKcv: ByteArray?): Boolean
}

@Singleton
class KeyLifecycleManagerImpl @Inject constructor(
    private val keysApi: KeysApi
) : KeyLifecycleManager {

    override suspend fun decideSetup(): SetupDecision {
        return try {
            val response = keysApi.getKeys()
            if (response.isSuccessful && response.body() != null) {
                if (response.body()!!.exists) {
                    SetupDecision.Restore
                } else {
                    SetupDecision.CreateNew
                }
            } else {
                SetupDecision.Blocked
            }
        } catch (_: Exception) {
            SetupDecision.Blocked // Offline or network error: never create a key on a guess!
        }
    }

    override fun computeKcv(masterKey: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(KCV_INFO)
        digest.update(masterKey)
        return digest.digest().copyOf(8)
    }

    override fun verifyKcv(candidateMasterKey: ByteArray, expectedKcv: ByteArray?): Boolean {
        if (expectedKcv == null || expectedKcv.isEmpty()) {
            return true // Legacy record without KCV
        }
        val computed = computeKcv(candidateMasterKey)
        return MessageDigest.isEqual(computed, expectedKcv)
    }

    companion object {
        private val KCV_INFO = "cryptnote/kcv/v1".toByteArray(Charsets.UTF_8)
    }
}
