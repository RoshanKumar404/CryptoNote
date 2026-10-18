package com.example.cryptnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.token.TokenStorageManager
import com.example.cryptnote.ui.navigation.CryptoNoteNavGraph
import com.example.cryptnote.ui.navigation.Screen
import com.example.cryptnote.ui.theme.CryptNoteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenStorageManager: TokenStorageManager

    @Inject
    lateinit var keySessionManager: KeySessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}