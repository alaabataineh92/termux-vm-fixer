package io.github.alaabataineh92.termuxvmfixer.core.common.log

import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel

interface LogSink {
    val level: LogLevel

    fun logger(tag: String): Logger
}

object NoOpLogSink : LogSink {
    override val level: LogLevel = LogLevel.ERROR
    override fun logger(tag: String): Logger = NoOpLogger
}

class LevelFilteredLogger(
    private val tag: String,
    private val threshold: LogLevel,
    private val delegate: Logger,
) : Logger {
    override fun isEnabled(level: LogLevel): Boolean =
        level.isEnabledAt(threshold) && delegate.isEnabled(level)

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!isEnabled(level)) return

        delegate.log(level, tag.ifEmpty { this.tag }, message, throwable)
    }
}
