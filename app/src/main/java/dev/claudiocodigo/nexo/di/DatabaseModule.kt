package dev.claudiocodigo.nexo.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.claudiocodigo.nexo.data.local.NexoDatabase
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
        ).build()
    }

    @Provides
    fun provideServiceOrderDao(database: NexoDatabase): ServiceOrderDao {
        return database.serviceOrderDao()
    }
}
