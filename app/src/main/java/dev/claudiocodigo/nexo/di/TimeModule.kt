package dev.claudiocodigo.nexo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import dev.claudiocodigo.nexo.domain.time.SystemClockProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    abstract fun bindClockProvider(provider: SystemClockProvider): ClockProvider
}
