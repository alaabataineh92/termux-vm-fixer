package io.github.alaabataineh92.termuxvmfixer.core.device.log

import android.content.Context
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel
import io.github.alaabataineh92.termuxvmfixer.core.common.log.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FileLogger(
    context: Context,
    private val minLevel: LogLevel = LogLevel.TRACE,
) : Logger {

    private val logFile = File(context.cacheDir, "vmfixer.log")
    private val previousFile = File(context.cacheDir, "vmfixer.previous.log")

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vmfixer-log").apply { isDaemon = true }
    }

    private val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var closed = false

    init {
        executor.execute {
            runCatching {
                if (logFile.exists()) {
                    previousFile.delete()
                    logFile.renameTo(previousFile)
                }
                logFile.parentFile?.mkdirs()
                logFile.writeText("# Termux VM Fixer log\n")
            }
        }
    }

    override fun isEnabled(level: LogLevel): Boolean = level.isEnabledAt(minLevel)

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!isEnabled(level) || closed) return

        val line = buildString {
            append(timestamp.format(Date()))
            append(' ')
            append(level.name.padEnd(5))
            append(' ')
            append(tag)
            append(": ")
            append(message)
            if (throwable != null) {
                append('\n')
                append(throwable.stackTraceToString())
            }
            append('\n')
        }

        executor.execute {
            runCatching { logFile.appendText(line) }
        }
    }

    fun close() {
        closed = true
        executor.shutdown()
        runCatching { executor.awaitTermination(2, TimeUnit.SECONDS) }
    }
}
