package io.github.alaabataineh92.termuxvmfixer.data.fix.di

import io.github.alaabataineh92.termuxvmfixer.core.device.AssetFixArtifacts
import io.github.alaabataineh92.termuxvmfixer.core.device.log.AppLogger
import io.github.alaabataineh92.termuxvmfixer.data.fix.archive.GnuTarWriter
import io.github.alaabataineh92.termuxvmfixer.data.fix.bootstrap.BootstrapReader
import io.github.alaabataineh92.termuxvmfixer.data.fix.content.FixArtifacts
import io.github.alaabataineh92.termuxvmfixer.data.fix.engine.FixEngine
import io.github.alaabataineh92.termuxvmfixer.data.fix.symlink.SymlinkScriptGenerator
import io.github.alaabataineh92.termuxvmfixer.data.fix.verify.VerifyParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FixModule {

    @Provides
    @Singleton
    fun provideFixArtifacts(impl: AssetFixArtifacts): FixArtifacts = impl

    @Provides
    fun provideFixEngine(
        artifacts: FixArtifacts,
        appLogger: AppLogger,
    ): FixEngine = FixEngine(
        bootstrapReader = BootstrapReader(),
        symlinkGenerator = SymlinkScriptGenerator(),
        tarWriter = GnuTarWriter(),
        artifacts = artifacts,
        verifyParser = VerifyParser(),
        logger = appLogger.logger,
        io = Dispatchers.IO,
    )
}
