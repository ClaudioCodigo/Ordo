package dev.claudiocodigo.nexo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.claudiocodigo.nexo.data.repository.RoomServiceOrderRepository
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
}
