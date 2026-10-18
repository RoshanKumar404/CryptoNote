package com.example.cryptnote.di

import com.example.cryptnote.data.repository.AuthRepository
import com.example.cryptnote.data.repository.AuthRepositoryImpl
import com.example.cryptnote.security.token.EncryptedTokenStorageManagerImpl
import com.example.cryptnote.security.token.TokenStorageManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindTokenStorageManager(
        impl: EncryptedTokenStorageManagerImpl
    ): TokenStorageManager

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}
