package dev.claudiocodigo.nexo.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.claudiocodigo.nexo.data.local.NexoDatabase
import dev.claudiocodigo.nexo.data.local.NexoDatabaseMigrations
import dev.claudiocodigo.nexo.data.local.dao.CalendarAccountDao
import dev.claudiocodigo.nexo.data.local.dao.CalendarDao
import dev.claudiocodigo.nexo.data.local.dao.CalendarSyncStateDao
import dev.claudiocodigo.nexo.data.local.dao.RemoteEventDao
import dev.claudiocodigo.nexo.data.local.dao.ServiceOrderDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexoDatabase {
        return Room.databaseBuilder(
            context,
            NexoDatabase::class.java,
            NexoDatabase.DATABASE_NAME
        )
            .addMigrations(NexoDatabaseMigrations.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideServiceOrderDao(database: NexoDatabase): ServiceOrderDao {
        return database.serviceOrderDao()
    }

    @Provides
    fun provideCalendarAccountDao(database: NexoDatabase): CalendarAccountDao {
        return database.calendarAccountDao()
    }

    @Provides
    fun provideCalendarDao(database: NexoDatabase): CalendarDao {
        return database.calendarDao()
    }

    @Provides
    fun provideRemoteEventDao(database: NexoDatabase): RemoteEventDao {
        return database.remoteEventDao()
    }

    @Provides
    fun provideCalendarSyncStateDao(database: NexoDatabase): CalendarSyncStateDao {
        return database.calendarSyncStateDao()
    }
}
