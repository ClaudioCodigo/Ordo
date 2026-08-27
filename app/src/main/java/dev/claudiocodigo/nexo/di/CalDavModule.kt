package dev.claudiocodigo.nexo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.claudiocodigo.nexo.data.caldav.NextcloudCalDavDiscoveryClient
import dev.claudiocodigo.nexo.data.caldav.NextcloudCalDavReadClient
import dev.claudiocodigo.nexo.data.caldav.RoomCalendarSyncCoordinator
import dev.claudiocodigo.nexo.domain.caldav.CalDavDiscoveryClient
import dev.claudiocodigo.nexo.domain.caldav.CalDavReadClient
import dev.claudiocodigo.nexo.domain.caldav.CalendarSyncCoordinator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalDavModule {

    @Binds
    @Singleton
    abstract fun bindCalDavDiscoveryClient(
        nextcloudCalDavDiscoveryClient: NextcloudCalDavDiscoveryClient
    ): CalDavDiscoveryClient

    @Binds
    @Singleton
    abstract fun bindCalDavReadClient(
        nextcloudCalDavReadClient: NextcloudCalDavReadClient
    ): CalDavReadClient

    @Binds
    @Singleton
    abstract fun bindCalendarSyncCoordinator(
        roomCalendarSyncCoordinator: RoomCalendarSyncCoordinator
    ): CalendarSyncCoordinator
}
