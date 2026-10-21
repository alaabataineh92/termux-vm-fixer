package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.zip

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

data class BootstrapContents(
    val entries: Map<String, ByteArray>,
    val modes: Map<String, Int>,
    val order: List<String>,
) {
    fun require(name: String): ByteArray =
        entries[name] ?: throw IllegalArgumentException("the bootstrap has no $name")

    fun modeOf(name: String): Int = modes[name] ?: DEFAULT_FILE_MODE

    fun with(name: String, data: ByteArray, mode: Int = modeOf(name)): BootstrapContents =
        copy(
            entries = entries + (name to data),
            modes = modes + (name to mode),
            order = if (name in entries) order else order + name,
        )

    companion object {

        const val DEFAULT_FILE_MODE = 0b1000000110100100
        const val EXECUTABLE_MODE = 0b1000000111101101
        const val OWNER_EXEC_MODE = 0b1000000111000000
        fun read(zipBytes: ByteArray): BootstrapContents = BootstrapZipReader.read(zipBytes)
    }
}

object BootstrapZipReader {

        internal fun read(zipBytes: ByteArray): BootstrapContents {
            val entries = LinkedHashMap<String, ByteArray>()
            val order = ArrayList<String>()

            ZipInputStream(ByteArrayInputStream(zipBytes)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.isDirectory) {

                        entries[entry.name] = ByteArray(0)
                    } else {
                        entries[entry.name] = input.readBytes()
                    }
                    order += entry.name
                }
            }

            return BootstrapContents(
                entries = entries,
                modes = readModes(zipBytes),
                order = order,
            )
        }

        fun readModes(zipBytes: ByteArray): Map<String, Int> {
            val buffer = java.nio.ByteBuffer.wrap(zipBytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)

            var eocd = zipBytes.size - 22
            while (eocd >= 0 && buffer.getInt(eocd) != EOCD_SIG) eocd--
            require(eocd >= 0) { "the bootstrap zip has no end-of-central-directory record" }

            val count = buffer.getShort(eocd + 10).toInt() and 0xFFFF
            var at = buffer.getInt(eocd + 16)

            val modes = HashMap<String, Int>(count * 2)
            repeat(count) {
                require(buffer.getInt(at) == CENTRAL_SIG) {
                    "bad central directory signature at $at"
                }
                val nameLen = buffer.getShort(at + 28).toInt() and 0xFFFF
                val extraLen = buffer.getShort(at + 30).toInt() and 0xFFFF
                val commentLen = buffer.getShort(at + 32).toInt() and 0xFFFF
                val external = buffer.getInt(at + 38)
                val name = String(zipBytes, at + 46, nameLen, Charsets.UTF_8)

                val mode = external ushr 16
                modes[name] = if (mode == 0) BootstrapContents.DEFAULT_FILE_MODE else mode

                at += 46 + nameLen + extraLen + commentLen
            }
            return modes
        }

        private const val EOCD_SIG = 0x06054b50
        private const val CENTRAL_SIG = 0x02014b50
}
