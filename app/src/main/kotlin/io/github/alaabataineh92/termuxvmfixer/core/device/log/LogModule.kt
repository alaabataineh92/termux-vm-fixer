package io.github.alaabataineh92.termuxvmfixer.core.device.log

import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LogModule {

    @Provides
    @Singleton
    fun provideLogSink(appLogger: AppLogger): LogSink = appLogger
}
