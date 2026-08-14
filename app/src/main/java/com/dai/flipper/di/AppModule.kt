package com.dai.flipper.di

import android.content.Context
import com.dai.flipper.data.database.AuditDao
import com.dai.flipper.data.database.ChatDao
import com.dai.flipper.data.database.DaiDatabase
import com.dai.flipper.data.db.DeviceDao
import com.dai.flipper.data.db.DeviceDatabase
import com.dai.flipper.data.db.NoteDao
import com.dai.flipper.data.db.SightingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ==================== Dai Database ====================

    @Provides
    @Singleton
    fun provideDaiDatabase(@ApplicationContext context: Context): DaiDatabase {
        return DaiDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideAuditDao(database: DaiDatabase): AuditDao {
        return database.auditDao()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: DaiDatabase): ChatDao {
        return database.chatDao()
    }

    // ==================== Device Tracker Database ====================

    @Provides
    @Singleton
    fun provideDeviceDatabase(@ApplicationContext context: Context): DeviceDatabase {
        return DeviceDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideDeviceDao(database: DeviceDatabase): DeviceDao {
        return database.deviceDao()
    }

    @Provides
    @Singleton
    fun provideSightingDao(database: DeviceDatabase): SightingDao {
        return database.sightingDao()
    }

    @Provides
    @Singleton
    fun provideNoteDao(database: DeviceDatabase): NoteDao {
        return database.noteDao()
    }
}
