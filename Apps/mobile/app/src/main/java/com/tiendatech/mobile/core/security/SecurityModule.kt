package com.tiendatech.mobile.core.security

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindTokenCipher(implementation: AndroidKeystoreTokenCipher): TokenCipher

    @Binds
    @Singleton
    abstract fun bindKeyValueStorage(
        implementation: SharedPreferencesKeyValueStorage
    ): KeyValueStorage

    @Binds
    @Singleton
    abstract fun bindSessionTokenProvider(
        implementation: SecureSessionStore
    ): SessionTokenProvider

    @Binds
    @Singleton
    abstract fun bindSessionStore(implementation: SecureSessionStore): SessionStore
}
