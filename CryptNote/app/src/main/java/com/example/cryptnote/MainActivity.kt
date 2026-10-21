package com.example.cryptnote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cryptnote.data.preferences.ThemeMode
import com.example.cryptnote.data.preferences.ThemePreferencesManager
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.VaultLock
import com.example.cryptnote.security.lock.InactivityLockManager
import com.example.cryptnote.security.token.TokenStorageManager
import com.example.cryptnote.ui.navigation.CryptoNoteNavGraph
import com.example.cryptnote.ui.navigation.Screen
import com.example.cryptnote.ui.theme.CryptNoteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var tokenStorageManager: TokenStorageManager

    @Inject
    lateinit var keySessionManager: KeySessionManager

    @Inject
    lateinit var inactivityLockManager: InactivityLockManager

    @Inject
    lateinit var vaultLock: VaultLock

    @Inject
    lateinit var themePreferencesManager: ThemePreferencesManager

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                vaultLock.lock()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)

        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferencesManager.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val themeColor by themePreferencesManager.themeColor.collectAsStateWithLifecycle(
                initialValue = com.example.cryptnote.data.preferences.ThemeColor.CYBER_TEAL
            )
            val dynamicColor by themePreferencesManager.dynamicColor.collectAsStateWithLifecycle(
                initialValue = false
            )

            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            CryptNoteTheme(darkTheme = isDark, dynamicColor = dynamicColor, themeColor = themeColor) {
                val targetDestination = when {
                    !tokenStorageManager.hasSession() -> Screen.Auth.route
                    !keySessionManager.isUnlocked.value -> Screen.VaultAuth.route
                    else -> Screen.NotesList.route
                }
                CryptoNoteNavGraph(targetDestination = targetDestination)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
            // Already unregistered
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        inactivityLockManager.onUserInteraction()
        vaultLock.touch()
    }

    override fun onStop() {
        super.onStop()
        inactivityLockManager.onAppBackgrounded()
        vaultLock.lock()
    }

    override fun onResume() {
        super.onResume()
        inactivityLockManager.onAppForegrounded()
    }
}
