package com.example.cryptnote.data.remote.api

import com.example.cryptnote.data.remote.model.PullResponseDto
import com.example.cryptnote.data.remote.model.PushRequestDto
import com.example.cryptnote.data.remote.model.PushResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for CryptoNote synchronization endpoints.
 * Corresponds to BE-3 (POST /sync/push, GET /sync/pull).
 */
interface SyncApi {

    /**
     * Dispatches a batch of pending local mutations to the server.
     */
    @POST("sync/push")
    suspend fun push(
        @Body request: PushRequestDto
    ): Response<PushResponseDto>

    /**
     * Pulls notes changed on the server since the given sequence cursor.
     */
    @GET("sync/pull")
    suspend fun pull(
        @Query("since") since: Long,
        @Query("limit") limit: Int = 50
    ): Response<PullResponseDto>
}
