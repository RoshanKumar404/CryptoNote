package com.example.cryptnote.data.remote.api

import com.example.cryptnote.data.remote.model.AuthResponseDto
import com.example.cryptnote.data.remote.model.LoginRequestDto
import com.example.cryptnote.data.remote.model.RefreshRequestDto
import com.example.cryptnote.data.remote.model.RegisterRequestDto
import com.example.cryptnote.data.remote.model.TokenPairDto
import com.example.cryptnote.data.remote.model.UserResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface for CryptoNote authentication endpoints.
 * Corresponds to BE-2 (POST /auth/register, POST /auth/login, POST /auth/refresh, GET /auth/me).
 */
interface AuthApi {

    /**
     * Registers a new user account with email and derived zero-knowledge auth key.
     */
    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequestDto
    ): Response<AuthResponseDto>

    /**
     * Authenticates an existing user account with email and derived zero-knowledge auth key.
     */
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): Response<AuthResponseDto>

    /**
     * Exchanges a valid refresh token for a refreshed JWT access and refresh token pair.
     */
    @POST("auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequestDto
    ): Response<TokenPairDto>

    /**
     * Retrieves the authenticated user's profile metadata.
     */
    @GET("auth/me")
    suspend fun getMe(): Response<UserResponseDto>
}
