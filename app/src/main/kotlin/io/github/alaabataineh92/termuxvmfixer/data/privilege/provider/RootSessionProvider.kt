package io.github.alaabataineh92.termuxvmfixer.data.privilege.provider

import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.Capability
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.ProviderStatus
import io.github.alaabataineh92.termuxvmfixer.core.model.Reason
import io.github.alaabataineh92.termuxvmfixer.core.model.SessionProvider
import io.github.alaabataineh92.termuxvmfixer.core.model.SetupStep
import io.github.alaabataineh92.termuxvmfixer.core.model.TransportNote
import io.github.alaabataineh92.termuxvmfixer.data.privilege.root.RootDeviceSession
import io.github.alaabataineh92.termuxvmfixer.data.privilege.session.ProcessRunner
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootSessionProvider @Inject constructor(
    private val logSink: LogSink,
) : SessionProvider {

    override val method: PrivilegeMethod = PrivilegeMethod.ROOT

    override val capabilities: Set<Capability> = setOf(
        Capability.SHELL_UID,
        Capability.RUN_AS_TERMUX,
        Capability.FORCE_STOP,
        Capability.ROOT,
        Capability.READ_OTHER_APKS,
    )

    override val rank: Int = 100

    override suspend fun probe(): ProviderStatus {
        val su = findSu()
            ?: return ProviderStatus.Unavailable(Reason.SuAbsent)

        val result = ProcessRunner.run(
            command = listOf(su, "-c", "id"),
            timeout = Duration.ofSeconds(20),
        )

        return when {
            result.timedOut -> ProviderStatus.NeedsSetup(
                reason = Reason.RootTimedOut,
                steps = listOf(SetupStep.ALLOW_ROOT_ON_SCREEN),
            )

            result.stdout.contains("uid=0") -> ProviderStatus.Available(

                note = TransportNote.SuInstalled(),
            )

            else -> ProviderStatus.NeedsSetup(
                reason = Reason.RootRefused,
                steps = listOf(SetupStep.GRANT_ROOT_ACCESS),
            )
        }
    }

    override suspend fun open(): Result<DeviceSession> = runCatching {
        val su = findSu() ?: error("no su binary found")
        RootDeviceSession(su, logSink.logger("Root")) as DeviceSession
    }

    private fun findSu(): String? = ProcessRunner.findExecutable(
        listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/debug_ramdisk/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/magisk/su",
        ),
    )
}
