package io.github.alaabataineh92.termuxvmfixer.data.fix.bootstrap

object BootstrapArchive {

    private val LOCAL_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val CENTRAL_HEADER = byteArrayOf(0x50, 0x4B, 0x01, 0x02)
    private val EOCD = byteArrayOf(0x50, 0x4B, 0x05, 0x06)

    private const val EOCD_MIN_SIZE = 22

    private const val MAX_COMMENT = 0xFFFF

    data class Slot(val start: Int, val end: Int) {
        val size: Long get() = (end - start).toLong()
    }

    fun find(library: ByteArray): Slot? {
        if (!isElf(library)) return null

        val start = indexOf(library, LOCAL_HEADER, 0)
        if (start < 0) return null

        var searchEnd = library.size

        while (true) {
            val eocd = lastIndexOf(library, EOCD, searchEnd)
            if (eocd < 0) return null

            val commentLength = readShort(library, eocd + 20)
            val centralSize = readInt(library, eocd + 12)
            val centralOffset = readInt(library, eocd + 16)
            val end = eocd + EOCD_MIN_SIZE + commentLength

            val centralAt = start.toLong() + centralOffset.toLong()

            if (end <= library.size &&
                centralSize > 0 &&
                centralAt >= 0 &&
                centralAt + CENTRAL_HEADER.size <= library.size &&
                matches(library, centralAt.toInt(), CENTRAL_HEADER)
            ) {
                return Slot(start, end)
            }

            searchEnd = eocd
        }
    }

    fun extract(library: ByteArray, destination: java.io.File): java.io.File {
        val slot = find(library)
            ?: error("the bootstrap library has no embedded archive")

        destination.parentFile?.mkdirs()

        destination.outputStream().use { output ->
            output.write(library, slot.start, slot.size.toInt())
        }

        return destination
    }

    private fun isElf(data: ByteArray): Boolean =
        data.size >= 4 &&
            data[0] == 0x7F.toByte() &&
            data[1] == 'E'.code.toByte() &&
            data[2] == 'L'.code.toByte() &&
            data[3] == 'F'.code.toByte()

    fun readModes(archive: java.io.File): Map<String, Int> {
        if (!archive.isFile) return emptyMap()

        java.io.RandomAccessFile(archive, "r").use { raf ->
            val length = raf.length()
            if (length < EOCD_MIN_SIZE) return emptyMap()

            val tailSize = minOf(length, (MAX_COMMENT + EOCD_MIN_SIZE).toLong()).toInt()
            val tail = ByteArray(tailSize)
            raf.seek(length - tailSize)
            raf.readFully(tail)

            var eocd = -1
            for (i in tail.size - EOCD_MIN_SIZE downTo 0) {
                if (matches(tail, i, EOCD)) {
                    eocd = i
                    break
                }
            }
            if (eocd < 0) return emptyMap()

            val count = readShort(tail, eocd + 10) and 0xFFFF
            val centralSize = readInt(tail, eocd + 12)
            val centralOffset = readInt(tail, eocd + 16)

            if (centralSize <= 0 || centralOffset < 0) return emptyMap()
            if (centralOffset.toLong() + centralSize.toLong() > length) {
                return emptyMap()
            }

            val central = ByteArray(centralSize)
            raf.seek(centralOffset.toLong())
            raf.readFully(central)

            val modes = HashMap<String, Int>(count * 2)
            var at = 0

            repeat(count) {
                if (at + CENTRAL_HEADER.size > central.size) return modes
                if (!matches(central, at, CENTRAL_HEADER)) return modes

                val nameLength = readShort(central, at + 28) and 0xFFFF
                val extraLength = readShort(central, at + 30) and 0xFFFF
                val commentLength = readShort(central, at + 32) and 0xFFFF
                val external = readInt(central, at + 38)
                val name = String(central, at + 46, nameLength, Charsets.UTF_8)

                val mode = (external ushr 16) and 0xFFFF
                if (mode != 0) modes[name] = mode

                at += 46 + nameLength + extraLength + commentLength
            }

            return modes
        }
    }

    private fun matches(data: ByteArray, at: Int, needle: ByteArray): Boolean {
        if (at < 0 || at > data.size - needle.size) return false

        for (i in needle.indices) {
            if (data[at + i] != needle[i]) return false
        }
        return true
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun lastIndexOf(haystack: ByteArray, needle: ByteArray, before: Int): Int {
        for (i in (before - needle.size) downTo 0) {
            if (matches(haystack, i, needle)) return i
        }
        return -1
    }

    private fun readShort(data: ByteArray, at: Int): Int {
        if (at < 0 || at + 2 > data.size) return 0

        return (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)
    }

    private fun readInt(data: ByteArray, at: Int): Int {
        if (at < 0 || at + 4 > data.size) return 0

        return (data[at].toInt() and 0xFF) or
            ((data[at + 1].toInt() and 0xFF) shl 8) or
            ((data[at + 2].toInt() and 0xFF) shl 16) or
            ((data[at + 3].toInt() and 0xFF) shl 24)
    }
}
