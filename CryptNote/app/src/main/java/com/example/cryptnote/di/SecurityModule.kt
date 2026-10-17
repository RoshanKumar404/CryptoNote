package com.example.cryptnote.di

import com.example.cryptnote.security.AndroidKeystoreWrapper
import com.example.cryptnote.security.CryptoManager
import com.example.cryptnote.security.CryptoManagerImpl
import com.example.cryptnote.security.KeySessionManager
import com.example.cryptnote.security.KeySessionManagerImpl
import com.example.cryptnote.security.KeyStorageManager
import com.example.cryptnote.security.KeyStorageManagerImpl
import com.example.cryptnote.security.KeyWrapper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
}
