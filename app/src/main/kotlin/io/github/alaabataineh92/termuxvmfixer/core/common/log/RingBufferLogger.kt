package io.github.alaabataineh92.termuxvmfixer.core.common.log

import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class RingBufferLogger(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val minLevel: LogLevel = LogLevel.TRACE,
) : Logger {

    data class Entry(
        val sequence: Long,
        val timestampMillis: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
    ) {
        fun format(): String = buildString {
            append(timestamp())
            append(' ')
            append(level.name.padEnd(5))
            append(' ')
            append(tag)
            append(": ")
            append(message)

            throwable?.let { append("\n    ").append(it.describe()) }
        }

        private fun timestamp(): String {
            val total = timestampMillis
            val ms = total % 1000
            val s = (total / 1000) % 60
            val m = (total / 60_000) % 60
            val h = (total / 3_600_000) % 24
            return "%02d:%02d:%02d.%03d".format(h, m, s, ms)
        }
    }

    private val lock = ReentrantReadWriteLock()
    private val buffer = ArrayDeque<Entry>(capacity)
    private var sequence = 0L
    private var dropped = 0

    override fun isEnabled(level: LogLevel): Boolean = level.isEnabledAt(minLevel)

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!isEnabled(level)) return

        lock.write {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
                dropped++
            }
            buffer.addLast(
                Entry(
                    sequence = sequence++,
                    timestampMillis = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message,
                    throwable = throwable,
                ),
            )
        }
    }

    fun entries(): List<Entry> = lock.read { buffer.toList() }

    fun droppedCount(): Int = lock.read { dropped }

    fun export(): String = lock.read {
        buildString {
            if (dropped > 0) {
                append("(")
                append(dropped)
                append(" earlier lines were dropped)\n")
            }
            buffer.forEach { append(it.format()).append('\n') }
        }
    }

    fun clear() = lock.write {
        buffer.clear()
        dropped = 0
        sequence = 0
    }

    companion object {
        const val DEFAULT_CAPACITY = 4000
    }
}
