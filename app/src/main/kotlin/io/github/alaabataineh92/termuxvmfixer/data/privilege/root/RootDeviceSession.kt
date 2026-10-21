package io.github.alaabataineh92.termuxvmfixer.data.privilege.root

import io.github.alaabataineh92.termuxvmfixer.core.common.log.Logger
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogger
import io.github.alaabataineh92.termuxvmfixer.core.model.Capability
import io.github.alaabataineh92.termuxvmfixer.core.model.CommandResult
import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.data.privilege.session.BaseDeviceSession
import io.github.alaabataineh92.termuxvmfixer.data.privilege.session.ProcessRunner
import java.io.File
import java.time.Duration

class RootDeviceSession(
    private val suPath: String,
    override val logger: Logger = NoOpLogger,
) : BaseDeviceSession() {

    override val method: PrivilegeMethod = PrivilegeMethod.ROOT

    override val capabilities: Set<Capability> = setOf(
        Capability.SHELL_UID,
        Capability.RUN_AS_TERMUX,
        Capability.FORCE_STOP,
        Capability.ROOT,
        Capability.READ_OTHER_APKS,
    )

    override fun hasRoot(): Boolean = true

    override suspend fun executeRaw(command: String, timeout: Duration): CommandResult =
        ProcessRunner.run(
            command = listOf(suPath, "-c", command),
            timeout = timeout,
        )

    override suspend fun writeBytes(file: File, remotePath: String) {
        val result = ProcessRunner.run(
            command = listOf(suPath, "-c", "cat > $remotePath"),
            timeout = Duration.ofMinutes(5),
            stdin = file.readBytes(),
        )
        if (!result.isSuccess) {
            error("could not write $remotePath: ${result.stderr.take(200)}")
        }

        executeRaw("chmod 666 $remotePath", Duration.ofSeconds(30))
    }
}
