package com.example.cryptnote.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ThemeColor(val displayName: String, val hexColor: Long) {
    CYBER_TEAL("Cyber Teal", 0xFF2DD4BF),
    NEON_BLUE("Neon Blue", 0xFF38BDF8),
    ELECTRIC_PURPLE("Electric Purple", 0xFFA855F7),
    EMERALD_MATRIX("Emerald Matrix", 0xFF10B981),
    SUNSET_AMBER("Sunset Amber", 0xFFF59E0B),
    CRIMSON_ROSE("Crimson Rose", 0xFFF43F5E)
}

/**
 * Manages user appearance preferences: ThemeMode (System, Light, Dark),
 * Accent Color Palette, Material 3 Dynamic Color switching, and Greeting Name.
 */
@Singleton
class ThemePreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val themeColorKey = stringPreferencesKey("theme_color")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val userGreetingKey = stringPreferencesKey("user_greeting_name")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[themeModeKey] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val themeColor: Flow<ThemeColor> = context.dataStore.data.map { preferences ->
        val colorStr = preferences[themeColorKey] ?: ThemeColor.CYBER_TEAL.name
        try {
            ThemeColor.valueOf(colorStr)
        } catch (_: Exception) {
            ThemeColor.CYBER_TEAL
        }
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[dynamicColorKey] ?: false
    }

    val userGreetingName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[userGreetingKey] ?: ""
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }

    suspend fun setThemeColor(color: ThemeColor) {
        context.dataStore.edit { preferences ->
            preferences[themeColorKey] = color.name
            preferences[dynamicColorKey] = false
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[dynamicColorKey] = enabled
        }
    }

    suspend fun setUserGreetingName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[userGreetingKey] = name.trim()
        }
    }
}
