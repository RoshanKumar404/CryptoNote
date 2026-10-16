package com.example.cryptnote.di

import com.example.cryptnote.data.repository.RoomNoteRepository
import com.example.cryptnote.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNoteRepository(
        roomNoteRepository: RoomNoteRepository
    ): NoteRepository
}
