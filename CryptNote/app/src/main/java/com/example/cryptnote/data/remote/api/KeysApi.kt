package com.example.cryptnote.data.remote.api

import com.example.cryptnote.data.remote.model.GetKeysResponseDto
import com.example.cryptnote.data.remote.model.PutKeysRequestDto
import com.example.cryptnote.data.remote.model.PutKeysResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * Retrofit interface for remote Keys API (NV-BE-1, NV-FE-2).
 * Operates on wrapped master keys stored append-only on the backend.
 */
interface KeysApi {

    @GET("keys")
    suspend fun getKeys(): Response<GetKeysResponseDto>

    @PUT("keys")
    suspend fun putKeys(@Body request: PutKeysRequestDto): Response<PutKeysResponseDto>
}
