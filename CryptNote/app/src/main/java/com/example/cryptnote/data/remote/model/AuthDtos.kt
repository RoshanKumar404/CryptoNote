package com.example.cryptnote.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request payload for user registration (POST /auth/register).
 * The password field carries the client-derived auth_key (encoded as hex),
 * guaranteeing zero-knowledge separation from the master encryption passphrase.
 */
@Serializable
data class RegisterRequestDto(
    @SerialName("email")
    val email: String,
    @SerialName("password")
    val password: String
)

/**
 * Request payload for user authentication (POST /auth/login).
 */
@Serializable
data class LoginRequestDto(
    @SerialName("email")
    val email: String,
    @SerialName("password")
    val password: String
)

/**
 * Request payload for JWT session refresh (POST /auth/refresh).
 */
@Serializable
data class RefreshRequestDto(
    @SerialName("refreshToken")
    val refreshToken: String
)

/**
 * User account metadata returned in auth responses.
 */
@Serializable
data class UserDto(
    @SerialName("id")
    val id: String,
    @SerialName("email")
    val email: String,
    @SerialName("createdAt")
    val createdAt: String? = null
)

/**
 * Authentication response payload returning JWT access/refresh token pair and user profile.
 */
@Serializable
data class AuthResponseDto(
    @SerialName("accessToken")
    val accessToken: String,
    @SerialName("refreshToken")
    val refreshToken: String,
    @SerialName("user")
    val user: UserDto
)

/**
 * Token pair payload returned by POST /auth/refresh.
 */
@Serializable
data class TokenPairDto(
    @SerialName("accessToken")
    val accessToken: String,
    @SerialName("refreshToken")
    val refreshToken: String
)

/**
 * Response payload for GET /auth/me.
 */
@Serializable
data class UserResponseDto(
    @SerialName("user")
    val user: UserDto
)

/**
 * Standard backend error response format.
 */
@Serializable
data class AuthErrorResponseDto(
    @SerialName("statusCode")
    val statusCode: Int? = null,
    @SerialName("error")
    val error: String? = null,
    @SerialName("message")
    val message: String? = null
)
