package io.github.alaabataineh92.termuxvmfixer

import android.app.Application
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class TermuxVmFixerApp : Application() {
    val logShare: LogShare
        get() = EntryPointAccessors
            .fromApplication(this, LogShareEntryPoint::class.java)
            .logShare()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LogShareEntryPoint {
    fun logShare(): LogShare
}
