package io.github.alaabataineh92.termuxvmfixer.core.common.log

import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel

interface Logger {
    fun isEnabled(level: LogLevel): Boolean

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    fun logIf(level: LogLevel, tag: String, message: () -> String) {
        if (isEnabled(level)) log(level, tag, message())
    }

    fun trace(tag: String, message: () -> String) = logIf(LogLevel.TRACE, tag, message)
    fun debug(tag: String, message: () -> String) = logIf(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: () -> String) = logIf(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: () -> String) = logIf(LogLevel.WARN, tag, message)

    fun error(tag: String, throwable: Throwable? = null, message: () -> String) =
        log(LogLevel.ERROR, tag, message(), throwable)
}

object NoOpLogger : Logger {
    override fun isEnabled(level: LogLevel): Boolean = false
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
}

class TeeLogger(private val delegates: List<Logger>) : Logger {
    override fun isEnabled(level: LogLevel): Boolean =
        delegates.any { it.isEnabled(level) }

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        delegates.forEach { it.log(level, tag, message, throwable) }
    }
}
