package dev.claudiocodigo.nexo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.claudiocodigo.nexo.data.preferences.DataStoreRecentServiceOrderPreferences
import dev.claudiocodigo.nexo.data.preferences.RecentServiceOrderPreferences
import dev.claudiocodigo.nexo.data.publication.RoomPublicationCoordinator
import dev.claudiocodigo.nexo.data.repository.RoomCalendarRepository
import dev.claudiocodigo.nexo.data.repository.RoomCalendarSetupRepository
import dev.claudiocodigo.nexo.data.repository.RoomPublicationRepository
import dev.claudiocodigo.nexo.data.repository.RoomServiceOrderRepository
import dev.claudiocodigo.nexo.domain.publication.PublicationCoordinator
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.CalendarRepository
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindServiceOrderRepository(
        roomServiceOrderRepository: RoomServiceOrderRepository
    ): ServiceOrderRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        roomCalendarRepository: RoomCalendarRepository
    ): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindCalendarSetupRepository(
        roomCalendarSetupRepository: RoomCalendarSetupRepository
    ): CalendarSetupRepository

    @Binds
    @Singleton
    abstract fun bindPublicationRepository(
        roomPublicationRepository: RoomPublicationRepository
    ): PublicationRepository

    @Binds
    @Singleton
    abstract fun bindPublicationCoordinator(
        roomPublicationCoordinator: RoomPublicationCoordinator
    ): PublicationCoordinator

    @Binds
    @Singleton
    abstract fun bindRecentServiceOrderPreferences(
        dataStoreRecentServiceOrderPreferences: DataStoreRecentServiceOrderPreferences
    ): RecentServiceOrderPreferences
}
