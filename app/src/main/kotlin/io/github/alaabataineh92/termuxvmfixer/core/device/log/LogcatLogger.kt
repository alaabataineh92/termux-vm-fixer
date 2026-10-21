package io.github.alaabataineh92.termuxvmfixer.core.device.log

import android.util.Log
import io.github.alaabataineh92.termuxvmfixer.core.common.log.Logger
import io.github.alaabataineh92.termuxvmfixer.core.common.log.describe
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel

class LogcatLogger(
    private val minLevel: LogLevel = LogLevel.TRACE,
) : Logger {

    override fun isEnabled(level: LogLevel): Boolean = level.isEnabledAt(minLevel)

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!isEnabled(level)) return

        val logcatTag = prefixed(tag)
        val priority = level.toLogcatPriority()

        val full = if (throwable == null) {
            message
        } else {
            "$message\n    ${throwable.describe()}\n${Log.getStackTraceString(throwable)}"
        }

        var offset = 0
        var part = 1
        while (offset < full.length) {
            val end = minOf(offset + MAX_LINE, full.length)
            val prefix = if (part == 1) "" else "[part ${part}] "

            Log.println(priority, logcatTag, prefix + full.substring(offset, end))

            offset = end
            part++
        }
    }

    private fun prefixed(tag: String): String = "$TAG_PREFIX-$tag".take(MAX_TAG)

    private fun LogLevel.toLogcatPriority(): Int = when (this) {
        LogLevel.TRACE -> Log.VERBOSE
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
    }

    companion object {

        const val TAG_PREFIX = "VMFixer"

        private const val MAX_LINE = 3500

        private const val MAX_TAG = 23
    }
}
