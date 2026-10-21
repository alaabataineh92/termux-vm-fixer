package io.github.alaabataineh92.termuxvmfixer.data.fix.archive

import java.io.File

object TarInspector {
    data class Profile(
        val files: Int,
        val directories: Int,
        val longNameRecords: Int,
        val paxRecords: Int,
        val globalPaxRecords: Int,
        val otherTypes: Map<Char, Int>,
        val sizeBytes: Long,
    ) {
        override fun toString(): String = buildString {
            append("files=").append(files)
            append(" dirs=").append(directories)
            append(" longNames=").append(longNameRecords)
            append(" pax=").append(paxRecords)
            if (globalPaxRecords > 0) append(" globalPax=").append(globalPaxRecords)
            if (otherTypes.isNotEmpty()) {
                append(" other=").append(
                    otherTypes.entries.joinToString(",") { "${it.key}:${it.value}" },
                )
            }
            append(" size=").append(sizeBytes).append('B')
        }
    }

    fun inspect(archive: File): Profile {
        if (!archive.isFile) {
            return Profile(0, 0, 0, 0, 0, emptyMap(), 0)
        }

        var files = 0
        var directories = 0
        var longNames = 0
        var pax = 0
        var globalPax = 0
        val others = mutableMapOf<Char, Int>()

        try {
            archive.inputStream().buffered(BUFFER).use { input ->
                val header = ByteArray(BLOCK)
                var zeroBlocks = 0

                while (true) {
                    if (!readFully(input, header)) break

                    if (header.all { it == 0.toByte() }) {
                        zeroBlocks++

                        if (zeroBlocks >= 2) break
                        continue
                    }
                    zeroBlocks = 0

                    val type = header[TYPE_OFFSET].toInt().toChar()
                    when (type) {
                        '0', '\u0000' -> files++
                        '5' -> directories++
                        'L' -> longNames++
                        'x' -> pax++
                        'g' -> globalPax++
                        else -> others[type] = (others[type] ?: 0) + 1
                    }

                    val size = parseOctal(header, SIZE_OFFSET, SIZE_LENGTH)
                    if (size < 0) break
                    if (size > 0) {
                        val padded = ((size + BLOCK - 1) / BLOCK) * BLOCK
                        if (!skipFully(input, padded.toLong())) break
                    }
                }
            }
        } catch (_: Exception) {

        }

        return Profile(
            files = files,
            directories = directories,
            longNameRecords = longNames,
            paxRecords = pax,
            globalPaxRecords = globalPax,
            otherTypes = others,
            sizeBytes = archive.length(),
        )
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun skipFully(input: java.io.InputStream, count: Long): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {

                if (input.read() < 0) return false
                remaining--
            } else {
                remaining -= skipped
            }
        }
        return true
    }

    private fun parseOctal(header: ByteArray, offset: Int, length: Int): Int {
        val text = header.copyOfRange(offset, offset + length)
            .toString(Charsets.US_ASCII)
            .trim('\u0000', ' ')
            .takeWhile { it in '0'..'7' }

        if (text.isEmpty()) return 0
        return text.toLongOrNull(8)?.takeIf { it <= Int.MAX_VALUE }?.toInt() ?: -1
    }

    private const val BLOCK = 512

    private const val TYPE_OFFSET = 156

    private const val SIZE_OFFSET = 124
    private const val SIZE_LENGTH = 12

    private const val BUFFER = 64 * 1024
}
