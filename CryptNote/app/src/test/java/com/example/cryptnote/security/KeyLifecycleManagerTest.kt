package com.example.cryptnote.security

import com.example.cryptnote.data.remote.api.KeysApi
import com.example.cryptnote.data.remote.model.GetKeysResponseDto
import com.example.cryptnote.data.remote.model.PutKeysRequestDto
import com.example.cryptnote.data.remote.model.PutKeysResponseDto
import com.example.cryptnote.data.remote.model.WrappedKeyRecordDto
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class FakeKeysApi : KeysApi {
    var getKeysResponse: Response<GetKeysResponseDto>? = null
    var shouldThrow: Boolean = false

    override suspend fun getKeys(): Response<GetKeysResponseDto> {
        if (shouldThrow) throw RuntimeException("Network offline")
        return getKeysResponse ?: Response.success(GetKeysResponseDto(exists = false))
    }

    override suspend fun putKeys(request: PutKeysRequestDto): Response<PutKeysResponseDto> {
        return Response.success(PutKeysResponseDto(keyVersion = 1, status = "created"))
    }
}

class KeyLifecycleManagerTest {

    private lateinit var fakeKeysApi: FakeKeysApi
    private lateinit var keyLifecycleManager: KeyLifecycleManager

    @Before
    fun setUp() {
        fakeKeysApi = FakeKeysApi()
        keyLifecycleManager = KeyLifecycleManagerImpl(fakeKeysApi)
    }

    @Test
    fun decideSetup_returnsCreateNew_whenServerHasNoKeys() = runBlocking {
        fakeKeysApi.getKeysResponse = Response.success(GetKeysResponseDto(exists = false))

        val decision = keyLifecycleManager.decideSetup()
        assertEquals(SetupDecision.CreateNew, decision)
    }

    @Test
    fun decideSetup_returnsRestore_whenServerHasExistingKeys() = runBlocking {
        val record = WrappedKeyRecordDto(
            keyVersion = 1,
            passWrappedMk = "wrapped",
            passSalt = "salt",
            recoveryWrappedMk = "recovery"
        )
        fakeKeysApi.getKeysResponse = Response.success(GetKeysResponseDto(exists = true, latest = record))

        val decision = keyLifecycleManager.decideSetup()
        assertEquals(SetupDecision.Restore, decision)
    }

    @Test
    fun decideSetup_returnsBlocked_whenNetworkFails() = runBlocking {
        fakeKeysApi.shouldThrow = true

        val decision = keyLifecycleManager.decideSetup()
        assertEquals(SetupDecision.Blocked, decision)
    }

    @Test
    fun decideSetup_returnsBlocked_whenServerErrorOccurs() = runBlocking {
        fakeKeysApi.getKeysResponse = Response.error(500, "Server Error".toResponseBody())

        val decision = keyLifecycleManager.decideSetup()
        assertEquals(SetupDecision.Blocked, decision)
    }

    @Test
    fun kcv_validatesCorrectKey_andRejectsWrongKey() {
        val masterKey = ByteArray(32) { 1.toByte() }
        val wrongKey = ByteArray(32) { 2.toByte() }

        val kcv = keyLifecycleManager.computeKcv(masterKey)
        assertEquals(8, kcv.size)

        // Correct key must match
        assertTrue(keyLifecycleManager.verifyKcv(masterKey, kcv))

        // Wrong key must fail
        assertFalse(keyLifecycleManager.verifyKcv(wrongKey, kcv))

        // Null KCV (legacy record) must pass
        assertTrue(keyLifecycleManager.verifyKcv(wrongKey, null))
    }
}
