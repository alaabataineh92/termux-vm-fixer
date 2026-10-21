package io.github.alaabataineh92.termuxvmfixer.core.device.log

import android.content.Context
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LevelFilteredLogger
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.Logger
import io.github.alaabataineh92.termuxvmfixer.core.common.log.RingBufferLogger
import io.github.alaabataineh92.termuxvmfixer.core.common.log.TeeLogger
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogger @Inject constructor(
    @ApplicationContext context: Context,
) : LogSink {

    val ring: RingBufferLogger = RingBufferLogger(
        capacity = RingBufferLogger.DEFAULT_CAPACITY,
        minLevel = LogLevel.TRACE,
    )

    private val file = FileLogger(context, minLevel = LogLevel.DEBUG)
    private val logcat = LogcatLogger(minLevel = LogLevel.TRACE)

    @Volatile
    override var level: LogLevel = LogLevel.DEBUG
        private set

    val logger: Logger = TeeLogger(listOf(logcat, file, ring))

    override fun logger(tag: String): Logger = LevelFilteredLogger(tag, level, logger)

    fun setLevel(newLevel: LogLevel) {
        if (newLevel == level) return
        val previous = level
        level = newLevel
        logger.info(TAG) { "log level changed: $previous -> $newLevel" }
    }

    fun export(): String = ring.export()

    fun droppedLines(): Int = ring.droppedCount()

    fun clearRing() = ring.clear()

    fun close() = file.close()

    companion object {
        const val TAG = "App"
    }
}
