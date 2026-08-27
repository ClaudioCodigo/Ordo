package dev.claudiocodigo.nexo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.claudiocodigo.nexo.data.security.AndroidKeystoreCredentialStore
import dev.claudiocodigo.nexo.domain.caldav.CredentialStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindCredentialStore(
        androidKeystoreCredentialStore: AndroidKeystoreCredentialStore
    ): CredentialStore
}
