package com.example.cryptnote.di

import android.content.Context
import androidx.room.Room
import com.example.cryptnote.data.local.db.CryptNoteDatabase
import com.example.cryptnote.data.local.db.dao.NoteDao
import com.example.cryptnote.data.local.db.dao.OutboxDao
import com.example.cryptnote.data.local.db.dao.SyncStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CryptNoteDatabase {
        return Room.databaseBuilder(
            context,
            CryptNoteDatabase::class.java,
            CryptNoteDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideNoteDao(database: CryptNoteDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    @Singleton
    fun provideOutboxDao(database: CryptNoteDatabase): OutboxDao {
        return database.outboxDao()
    }

    @Provides
    @Singleton
    fun provideSyncStateDao(database: CryptNoteDatabase): SyncStateDao {
        return database.syncStateDao()
    }
}
