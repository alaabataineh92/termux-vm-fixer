package io.github.alaabataineh92.termuxvmfixer.data.privilege.session

import io.github.alaabataineh92.termuxvmfixer.core.model.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

object ProcessRunner {

    suspend fun run(
        command: List<String>,
        timeout: java.time.Duration,
        stdin: ByteArray? = null,
    ): CommandResult = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(command).start()
        } catch (e: Exception) {
            return@withContext CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "could not start process",
            )
        }

        try {

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val stdoutThread = drain(process.inputStream, stdout)
            val stderrThread = drain(process.errorStream, stderr)

            if (stdin != null) {
                process.outputStream.use { it.write(stdin) }
            } else {
                process.outputStream.close()
            }

            val finished = waitFor(process, timeout.toMillis())
            if (!finished) {
                kill(process)
                stdoutThread.join(GRACE_MILLIS)
                stderrThread.join(GRACE_MILLIS)
                return@withContext CommandResult.timeout()
            }

            stdoutThread.join(GRACE_MILLIS)
            stderrThread.join(GRACE_MILLIS)

            CommandResult(
                exitCode = process.exitValue(),
                stdout = stdout.toString(),
                stderr = stderr.toString(),
            )
        } finally {
            if (isAlive(process)) kill(process)
        }
    }

    private fun waitFor(process: Process, timeoutMillis: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (!isAlive(process)) return true
            Thread.sleep(POLL_MILLIS)
        }
        return !isAlive(process)
    }

    private fun isAlive(process: Process): Boolean =
        try {
            process.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }

    private fun kill(process: Process) {
        process.destroy()
    }

    private fun drain(
        stream: java.io.InputStream,
        into: StringBuilder,
    ): Thread = Thread {
        stream.bufferedReader().use { reader ->

            val buffer = CharArray(8192)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                synchronized(into) { into.appendRange(buffer, 0, read) }
            }
        }
    }.apply {
        isDaemon = true
        start()
    }

    fun findExecutable(candidates: List<String>): String? =
        candidates.firstOrNull { path ->
            val file = File(path)
            file.isFile && file.canExecute()
        }

    private const val GRACE_MILLIS = 1000L
    private const val POLL_MILLIS = 25L
}
