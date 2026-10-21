package io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect

import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.Logger
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.Capability
import io.github.alaabataineh92.termuxvmfixer.core.model.CommandResult
import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.data.privilege.session.BaseDeviceSession
import dadb.AdbShellResponse
import dadb.Dadb
import java.io.File
import java.time.Duration

class DadbDeviceSession(
    private val dadb: Dadb,
    private val endpoint: AdbEndpoint,
    logSink: LogSink = NoOpLogSink,
) : BaseDeviceSession() {

    override val logger: Logger = logSink.logger("Dadb")

    override val method: PrivilegeMethod = PrivilegeMethod.DIRECT_ADB

    override val capabilities: Set<Capability> = setOf(
        Capability.SHELL_UID,
        Capability.RUN_AS_TERMUX,
        Capability.FORCE_STOP,
        Capability.READ_OTHER_APKS,
    )

    val address: String get() = endpoint.display

    override suspend fun executeRaw(command: String, timeout: Duration): CommandResult {
        val startedAt = System.currentTimeMillis()

        return try {

            val response: AdbShellResponse = dadb.shell(command)

            val elapsed = System.currentTimeMillis() - startedAt
            logger.debug(tag) {
                "shell[$address]: done in ${elapsed}ms exit=${response.exitCode} " +
                    "stdout=${response.output.length}B stderr=${response.errorOutput.length}B"
            }

            CommandResult(
                exitCode = response.exitCode,
                stdout = response.output,
                stderr = response.errorOutput,
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startedAt
            logger.error(tag, e) {
                "shell[$address]: FAILED after ${elapsed}ms: " +
                    "${e::class.java.simpleName}: ${e.message}"
            }
            CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "the ADB command failed",
            )
        }
    }

    override suspend fun writeBytes(file: File, remotePath: String) {
        val expected = file.length()
        val startedAt = System.currentTimeMillis()

        logger.info(tag) { "write[$address]: $expected bytes -> $remotePath" }

        try {

            dadb.push(file, remotePath, MODE, file.lastModified() / 1000)
        } catch (e: Exception) {
            logger.error(tag, e) {
                "write[$address]: FAILED pushing $remotePath after " +
                    "${System.currentTimeMillis() - startedAt}ms: ${e.message}"
            }
            error("could not write $remotePath: ${e.message}")
        }

        val elapsed = System.currentTimeMillis() - startedAt
        val kbps = if (elapsed > 0) expected * 1000 / elapsed / 1024 else 0

        logger.info(tag) {
            "write[$address]: sent $expected bytes in ${elapsed}ms (~$kbps KB/s)"
        }
    }

    fun pullBinary(remotePath: String, destination: File): Boolean = try {
        destination.parentFile?.mkdirs()

        dadb.pull(destination, remotePath)

        destination.isFile && destination.length() > 0
    } catch (e: Exception) {
        logger.error("Dadb", e) { "binary pull of $remotePath failed: ${e.message}" }
        false
    }

    override fun close() {
        runCatching { dadb.close() }
    }

    private companion object {

        const val MODE = 0x81A4
    }
}
