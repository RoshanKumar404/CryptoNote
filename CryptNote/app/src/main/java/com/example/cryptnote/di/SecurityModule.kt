package com.example.cryptnote.di

import com.example.cryptnote.security.AndroidKeystoreWrapper
import com.example.cryptnote.security.CryptoManager
import com.example.cryptnote.security.CryptoManagerImpl
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.KeySessionManagerImpl
import com.example.cryptnote.security.KeyStorageManager
import com.example.cryptnote.security.KeyStorageManagerImpl
import com.example.cryptnote.security.KeyWrapper
import com.example.cryptnote.security.biometric.AndroidBiometricAuthManager
import com.example.cryptnote.security.biometric.AndroidBiometricKeyManager
import com.example.cryptnote.security.biometric.BiometricAuthManager
import com.example.cryptnote.security.biometric.BiometricKeyManager
import com.example.cryptnote.security.clipboard.AndroidClipboardAutoClearManager
import com.example.cryptnote.security.clipboard.ClipboardAutoClearManager
import com.example.cryptnote.security.lock.InactivityLockManager
import com.example.cryptnote.security.lock.InactivityLockManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindCryptoManager(
        cryptoManagerImpl: CryptoManagerImpl
    ): CryptoManager

    @Binds
    @Singleton
    abstract fun bindKeyWrapper(
        androidKeystoreWrapper: AndroidKeystoreWrapper
    ): KeyWrapper

    @Binds
    @Singleton
    abstract fun bindKeyStorageManager(
        keyStorageManagerImpl: KeyStorageManagerImpl
    ): KeyStorageManager

    @Binds
    @Singleton
    abstract fun bindKeySessionManager(
        keySessionManagerImpl: KeySessionManagerImpl
    ): KeySessionManager

    @Binds
    @Singleton
    abstract fun bindBiometricAuthManager(
        androidBiometricAuthManager: AndroidBiometricAuthManager
    ): BiometricAuthManager

    @Binds
    @Singleton
    abstract fun bindBiometricKeyManager(
        androidBiometricKeyManager: AndroidBiometricKeyManager
    ): BiometricKeyManager

    @Binds
    @Singleton
    abstract fun bindInactivityLockManager(
        inactivityLockManagerImpl: InactivityLockManagerImpl
    ): InactivityLockManager

    @Binds
    @Singleton
    abstract fun bindClipboardAutoClearManager(
        androidClipboardAutoClearManager: AndroidClipboardAutoClearManager
    ): ClipboardAutoClearManager

    companion object {
        @Provides
        @Singleton
        fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    }
}
