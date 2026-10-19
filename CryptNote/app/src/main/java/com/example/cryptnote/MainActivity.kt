package com.example.cryptnote

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.lock.InactivityLockManager
import com.example.cryptnote.security.token.TokenStorageManager
import com.example.cryptnote.ui.navigation.CryptoNoteNavGraph
import com.example.cryptnote.ui.navigation.Screen
import com.example.cryptnote.ui.theme.CryptNoteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var tokenStorageManager: TokenStorageManager

    @Inject
    lateinit var keySessionManager: KeySessionManager

    @Inject
    lateinit var inactivityLockManager: InactivityLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Anti-Forensics: Prevent screen capture, screenshots, and task switcher preview leaks
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()
        setContent {
            CryptNoteTheme {
                val startDestination = when {
                    !tokenStorageManager.hasSession() -> Screen.Auth.route
                    !keySessionManager.isUnlocked.value -> Screen.VaultAuth.route
                    else -> Screen.NotesList.route
                }
                CryptoNoteNavGraph(startDestination = startDestination)
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        inactivityLockManager.onUserInteraction()
    }

    override fun onStop() {
        super.onStop()
        inactivityLockManager.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        inactivityLockManager.onAppForegrounded()
    }
}