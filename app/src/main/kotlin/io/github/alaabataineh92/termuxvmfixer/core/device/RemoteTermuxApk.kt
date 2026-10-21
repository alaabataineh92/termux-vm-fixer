package io.github.alaabataineh92.termuxvmfixer.core.device

import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceError
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import java.io.File
import java.time.Duration

class RemoteTermuxApk(
    private val session: DeviceSession,
    private val fastPuller: ((remotePath: String, destination: File) -> Boolean)? = null,
    logSink: LogSink = NoOpLogSink,
) {
    private val logger = logSink.logger("RemoteApk")

    suspend fun pullTo(destination: File): File {
        val path = remoteApkPath()
            ?: throw DeviceError.Precondition(
                "Termux is not installed on the remote device, or its APK " +
                    "cannot be found",
            )

        logger.info(TAG) { "pulling $path -> ${destination.absolutePath}" }

        destination.parentFile?.mkdirs()

        if (fastPuller != null) {
            val startedAt = System.currentTimeMillis()
            if (fastPuller(path, destination)) {
                val bytes = destination.length()
                val elapsed = System.currentTimeMillis() - startedAt
                val kbps = if (elapsed > 0) bytes * 1000 / elapsed / 1024 else 0
                logger.info(TAG) {
                    "pulled $bytes bytes in ${elapsed}ms (~$kbps KB/s) over the " +
                        "binary sync protocol"
                }
                return destination
            }
        }

        logger.info(TAG) { "binary pull unavailable, falling back to the shell" }

        val size = remoteSize(path)
            ?: throw DeviceError.Precondition("cannot size $path on the remote device")

        val aligned = size - (size % BLOCK_BYTES)

        var written = 0L
        destination.outputStream().buffered().use { out ->
            var offset = 0L
            while (offset < aligned) {
                val length = minOf(CHUNK_BYTES, aligned - offset)
                val slice = readSlice(path, offset, length)
                    ?: throw DeviceError.Precondition(
                        "the remote device stopped responding while reading $path " +
                            "at byte $offset",
                    )

                if (slice.isEmpty()) {
                    throw DeviceError.Precondition(
                        "the remote read returned no data at byte $offset of $path",
                    )
                }

                out.write(slice)
                written += slice.size
                offset += slice.size

                logger.info(TAG) {
                    "pulled ${written * 100 / size}% ($written of $size bytes)"
                }
            }

            val remainder = size - aligned
            if (remainder > 0) {
                val tailCommand = "tail -c $remainder ${quote(path)} | base64"
                val tail = session.asTermux(tailCommand, SLICE_TIMEOUT)
                if (!tail.isSuccess) {
                    throw DeviceError.Precondition(
                        "could not read the last $remainder bytes of $path",
                    )
                }
                val cleaned = tail.stdout.filterNot {
                    it == '\n' || it == '\r' || it == ' '
                }
                val bytes = android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
                out.write(bytes)
                written += bytes.size
                logger.info(TAG) { "pulled the final $remainder bytes" }
            }
        }

        if (written != size) {
            destination.delete()
            throw DeviceError.Precondition(
                "pulled $written bytes of $path, expected $size",
            )
        }

        logger.info(TAG) { "pulled $written bytes to ${destination.absolutePath}" }
        return destination
    }

    private suspend fun remoteApkPath(): String? {
        val result = session.shell("pm path ${TermuxPaths.PACKAGE}", SHORT_TIMEOUT)
        if (!result.isSuccess) return null

        return result.stdout.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("package:") }
            ?.removePrefix("package:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private suspend fun remoteSize(path: String): Long? {

        val result = session.asTermux(
            "stat -c %s ${quote(path)}",
            SHORT_TIMEOUT,
        )
        if (!result.isSuccess) return null
        return result.stdout.trim().toLongOrNull()
    }

    private suspend fun readSlice(path: String, offset: Long, length: Long): ByteArray? {
        require(offset % BLOCK_BYTES == 0L) { "offset $offset is not block aligned" }
        require(length % BLOCK_BYTES == 0L) { "length $length is not block aligned" }

        val skipBlocks = offset / BLOCK_BYTES
        val countBlocks = length / BLOCK_BYTES

        val command = "dd if=${quote(path)} bs=$BLOCK_BYTES skip=$skipBlocks " +
            "count=$countBlocks 2>/dev/null | base64"

        val result = session.asTermux(command, SLICE_TIMEOUT)
        if (!result.isSuccess) return null

        val cleaned = result.stdout.filterNot { it == '\n' || it == '\r' || it == ' ' }
        if (cleaned.isEmpty()) return ByteArray(0)

        return runCatching {
            android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
        }.getOrNull()
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val TAG = "RemoteApk"

        const val BLOCK_BYTES = 512L * 1024

        const val CHUNK_BYTES = 2L * 1024 * 1024

        val SHORT_TIMEOUT: Duration = Duration.ofSeconds(30)
        val SLICE_TIMEOUT: Duration = Duration.ofMinutes(2)
    }
}
