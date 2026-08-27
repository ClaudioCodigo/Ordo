package dev.claudiocodigo.nexo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.claudiocodigo.nexo.data.repository.RoomCalendarRepository
import dev.claudiocodigo.nexo.data.repository.RoomCalendarSetupRepository
import dev.claudiocodigo.nexo.data.repository.RoomServiceOrderRepository
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
}
