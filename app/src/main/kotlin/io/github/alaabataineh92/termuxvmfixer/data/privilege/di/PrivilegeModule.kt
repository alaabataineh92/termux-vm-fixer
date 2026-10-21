package io.github.alaabataineh92.termuxvmfixer.data.privilege.di

import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.SessionProvider
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.PlatformPropertyReader
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.PropertyReader
import io.github.alaabataineh92.termuxvmfixer.data.privilege.provider.DirectAdbSessionProvider
import io.github.alaabataineh92.termuxvmfixer.data.privilege.provider.RootSessionProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrivilegeModule {
    @Provides
    @Singleton
    fun provideProviderMap(
        directAdb: DirectAdbSessionProvider,
        root: RootSessionProvider,
    ): Map<PrivilegeMethod, @JvmSuppressWildcards Provider<SessionProvider>> = mapOf(
        PrivilegeMethod.DIRECT_ADB to Provider<SessionProvider> { directAdb },
        PrivilegeMethod.ROOT to Provider<SessionProvider> { root },
    )

    @Provides
    @Singleton
    fun providePropertyReader(): PropertyReader = PlatformPropertyReader()
}
