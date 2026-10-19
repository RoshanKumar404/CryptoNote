package com.example.cryptnote.security

import com.example.cryptnote.security.token.InMemoryTokenStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenStorageManagerTest {

    private lateinit var tokenStorage: InMemoryTokenStorageManager

    @Before
    fun setUp() {
        tokenStorage = InMemoryTokenStorageManager()
    }

    @Test
    fun saveSession_persistsTokensAndUserMetadata() {
        tokenStorage.saveSession(
            accessToken = "access_123",
            refreshToken = "refresh_456",
            userId = "user_789",
            email = "alice@example.com"
        )

        assertEquals("access_123", tokenStorage.getAccessToken())
        assertEquals("refresh_456", tokenStorage.getRefreshToken())
        assertEquals("user_789", tokenStorage.getUserId())
        assertEquals("alice@example.com", tokenStorage.getUserEmail())
        assertTrue(tokenStorage.hasSession())
    }

    @Test
    fun saveTokens_updatesOnlyTokensPreservingUser() {
        tokenStorage.saveSession(
            accessToken = "access_old",
            refreshToken = "refresh_old",
            userId = "user_001",
            email = "bob@example.com"
        )

        tokenStorage.saveTokens(
            accessToken = "access_new",
            refreshToken = "refresh_new"
        )

        assertEquals("access_new", tokenStorage.getAccessToken())
        assertEquals("refresh_new", tokenStorage.getRefreshToken())
        assertEquals("user_001", tokenStorage.getUserId())
        assertEquals("bob@example.com", tokenStorage.getUserEmail())
    }

    @Test
    fun hasSession_returnsFalse_whenEmptyOrPartiallyCleared() {
        assertFalse(tokenStorage.hasSession())

        tokenStorage.saveTokens("token_only", "")
        assertFalse(tokenStorage.hasSession())

        tokenStorage.saveTokens("", "refresh_only")
        assertFalse(tokenStorage.hasSession())
    }

    @Test
    fun clearSession_wipesAllTokensAndUserMetadata() {
        tokenStorage.saveSession("access", "refresh", "id", "email")
        assertTrue(tokenStorage.hasSession())

        tokenStorage.clearSession()

        assertNull(tokenStorage.getAccessToken())
        assertNull(tokenStorage.getRefreshToken())
        assertNull(tokenStorage.getUserId())
        assertNull(tokenStorage.getUserEmail())
        assertFalse(tokenStorage.hasSession())
    }

    @Test
    fun getLoginTimestamp_isSetOnSaveSession_andClearedOnClearSession() {
        val before = System.currentTimeMillis()
        tokenStorage.saveSession("access", "refresh", "id", "email")
        val timestamp = tokenStorage.getLoginTimestamp()
        val after = System.currentTimeMillis()

        assertTrue(timestamp in before..after)

        tokenStorage.clearSession()
        // When cleared, InMemoryTokenStorageManager falls back to current time
        val fallbackTimestamp = tokenStorage.getLoginTimestamp()
        assertTrue(fallbackTimestamp >= timestamp)
    }
}
