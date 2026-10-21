package io.github.alaabataineh92.termuxvmfixer.data.privilege.session

import io.github.alaabataineh92.termuxvmfixer.core.common.log.Logger
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogger
import io.github.alaabataineh92.termuxvmfixer.core.model.CommandResult
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import io.github.alaabataineh92.termuxvmfixer.core.model.PathState
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import kotlinx.coroutines.delay
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class BaseDeviceSession : DeviceSession {

    protected open val logger: Logger = NoOpLogger

    protected val tag: String get() = method.name.lowercase().replace('_', '-')

    protected abstract suspend fun executeRaw(command: String, timeout: Duration): CommandResult

    protected open fun hasRoot(): Boolean = false

    override suspend fun shell(command: String, timeout: Duration): CommandResult {
        logger.debug(tag) { "shell (timeout ${timeout.toSeconds()}s): $command" }
        val startedAt = System.currentTimeMillis()
        val result = executeRaw(command, timeout)
        val elapsed = System.currentTimeMillis() - startedAt

        logger.debug(tag) {
            "shell done in ${elapsed}ms: exit=${result.exitCode} " +
                "timedOut=${result.timedOut} " +
                "stdout=${result.stdout.length}B stderr=${result.stderr.length}B"
        }

        if (result.timedOut) {
            logger.warn(tag) { "shell TIMED OUT after ${timeout.toSeconds()}s: $command" }
        } else if (result.exitCode != 0) {
            logger.warn(tag) { "shell exit ${result.exitCode}: $command" }
        }
        if (result.stderr.isNotBlank()) {
            logger.trace(tag) { "stderr: ${result.stderr.trim()}" }
        }

        return result
    }

    override suspend fun asTermux(command: String, timeout: Duration): CommandResult {
        val startedAt = System.currentTimeMillis()
        val result = executeRaw("run-as ${TermuxPaths.PACKAGE} $command", timeout)
        val elapsed = System.currentTimeMillis() - startedAt

        if (!result.isSuccess && result.stderr.isNotBlank()) {
            logger.debug(tag) {
                "run-as failed after ${elapsed}ms: ${result.stderr.lineSequence().first()}"
            }
        }
        return result
    }

    override suspend fun forceStop(): CommandResult {
        logger.info(tag) { "force-stopping ${TermuxPaths.PACKAGE}" }

        val stop = executeRaw("am force-stop ${TermuxPaths.PACKAGE}", STOP_TIMEOUT)
        logger.debug(tag) { "am force-stop: exit=${stop.exitCode} ${stop.combined.trim()}" }

        val deadline = System.currentTimeMillis() + STOP_WAIT.toMillis()
        var polls = 0

        while (System.currentTimeMillis() < deadline) {
            polls++
            val running = executeRaw("pidof ${TermuxPaths.PACKAGE}", SHORT_TIMEOUT)

            if (running.exitCode != 0) {
                logger.info(tag) { "Termux stopped after $polls poll(s)" }
                return stop
            }
            delay(POLL_INTERVAL.toMillis().milliseconds)
        }

        logger.warn(tag) {
            "Termux is STILL RUNNING after ${STOP_WAIT.toSeconds()}s " +
                "($polls polls). The rebuild may race it."
        }
        return stop
    }

    override suspend fun pathExists(path: String): PathState {
        repeat(PATH_ATTEMPTS) { attempt ->
            val result = asTermux("sh -c 'test -e $path'", SHORT_TIMEOUT)

            when (result.exitCode) {
                0 -> {
                    logger.trace(tag) { "pathExists($path) = EXISTS" }
                    return PathState.EXISTS
                }
                1 -> {
                    logger.trace(tag) { "pathExists($path) = MISSING" }
                    return PathState.MISSING
                }
                else -> {
                    logger.warn(tag) {
                        "pathExists($path): ambiguous result on attempt " +
                                "${attempt + 1}/$PATH_ATTEMPTS - exit=${result.exitCode} " +
                                "timedOut=${result.timedOut}, retrying"
                    }
                    delay(RETRY_DELAY.toMillis().milliseconds)
                }
            }
        }

        logger.error(tag) {
            "pathExists($path) = UNKNOWN after $PATH_ATTEMPTS attempts. " +
                "Reporting this as unknown rather than missing."
        }
        return PathState.UNKNOWN
    }

    override suspend fun push(localPath: String, remotePath: String): Result<Unit> =
        runCatching {
            val file = java.io.File(localPath)
            require(file.isFile) { "not a file: $localPath" }

            val expected = file.length()
            val startedAt = System.currentTimeMillis()

            logger.debug(tag) { "push $localPath ($expected bytes) -> $remotePath" }

            executeRaw("rm -f $remotePath", SHORT_TIMEOUT)
            writeBytes(file, remotePath)

            val actual = executeRaw(
                "wc -c < $remotePath 2>/dev/null || echo 0",
                SHORT_TIMEOUT,
            ).stdout.trim().toLongOrNull() ?: 0L

            if (actual != expected) {
                logger.error(tag) {
                    "push SIZE MISMATCH for $remotePath: wrote $actual bytes, " +
                        "expected $expected. The transfer was truncated."
                }
                error("$remotePath is $actual bytes, expected $expected")
            }

            val elapsed = System.currentTimeMillis() - startedAt
            val kbps = if (elapsed > 0) expected * 1000 / elapsed / 1024 else 0
            logger.debug(tag) { "pushed $remotePath in ${elapsed}ms (~$kbps KB/s)" }
        }

    protected open suspend fun writeBytes(file: java.io.File, remotePath: String) {
        val encoded = android.util.Base64.encodeToString(
            file.readBytes(),
            android.util.Base64.NO_WRAP,
        )

        encoded.chunked(MAX_CHUNK_CHARS).forEachIndexed { index, chunk ->
            val operator = if (index == 0) ">" else ">>"
            val result = executeRaw(
                "echo -n $chunk | base64 -d $operator $remotePath",
                CHUNK_TIMEOUT,
            )
            if (!result.isSuccess) {
                error("chunk $index of $remotePath failed: ${result.stderr.take(200)}")
            }
        }
    }

    override suspend fun pull(remotePath: String): Result<ByteArray> = runCatching {
        val result = asTermux("sh -c 'base64 $remotePath'", PULL_TIMEOUT)
        if (!result.isSuccess) error("could not read $remotePath")
        android.util.Base64.decode(result.stdout, android.util.Base64.DEFAULT)
    }

    override fun close() = Unit

    protected companion object {
        val STOP_TIMEOUT: Duration = Duration.ofSeconds(30)
        val STOP_WAIT: Duration = Duration.ofSeconds(20)
        val POLL_INTERVAL: Duration = Duration.ofMillis(500)
        val SHORT_TIMEOUT: Duration = Duration.ofSeconds(30)
        val RETRY_DELAY: Duration = Duration.ofSeconds(1)
        val CHUNK_TIMEOUT: Duration = Duration.ofMinutes(2)
        val PULL_TIMEOUT: Duration = Duration.ofSeconds(60)

        const val PATH_ATTEMPTS = 3

        const val MAX_CHUNK_CHARS = 48 * 1024
    }
}
