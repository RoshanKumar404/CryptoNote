package com.example.cryptnote.di

import com.example.cryptnote.security.biometric.BiometricAuthManager
import com.example.cryptnote.security.clipboard.ClipboardAutoClearManager
import com.example.cryptnote.security.lock.InactivityLockManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SecurityEntryPoint {
    fun biometricAuthManager(): BiometricAuthManager
    fun inactivityLockManager(): InactivityLockManager
    fun clipboardAutoClearManager(): ClipboardAutoClearManager
    fun themePreferencesManager(): com.example.cryptnote.data.preferences.ThemePreferencesManager
    fun vaultLock(): com.example.cryptnote.security.VaultLock
    fun decryptDiagnostics(): com.example.cryptnote.security.DecryptDiagnostics
}
