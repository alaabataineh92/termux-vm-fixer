package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.zip

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

class FixedSizeZipWriter {
    data class Entry(
        val name: String,
        val data: ByteArray,
        val mode: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Entry

            if (mode != other.mode) return false
            if (name != other.name) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = mode
            result = 31 * result + name.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    fun buildTo(output: java.io.File, entries: List<Entry>, targetSize: Long): Long {
        require(entries.isNotEmpty()) { "a zip needs at least one entry" }

        val sorted = entries.sortedBy { it.name }
        val prepared = compressAll(sorted)

        val bareSize = prepared.sumOf { entry ->
            (LOCAL_HEADER + CENTRAL_HEADER + 2L * entry.nameBytes.size + entry.compressed.size)
        } + EOCD_SIZE

        require(bareSize <= targetSize) {
            "the entries need $bareSize bytes but only $targetSize are available"
        }

        output.parentFile?.mkdirs()
        java.io.FileOutputStream(output).buffered().use { out ->
            writeArchive(prepared, targetSize - bareSize, out)
        }

        check(output.length() == targetSize) {
            "wrote ${output.length()} bytes, expected $targetSize"
        }
        return output.length()
    }

    fun build(entries: List<Entry>, targetSize: Long): ByteArray {
        require(entries.isNotEmpty()) { "a zip needs at least one entry" }

        val sorted = entries.sortedBy { it.name }

        val prepared = compressAll(sorted)

        val bareSize = prepared.sumOf { entry ->
            (LOCAL_HEADER + CENTRAL_HEADER + 2L * entry.nameBytes.size + entry.compressed.size)
        } + EOCD_SIZE

        require(bareSize <= targetSize) {
            "the entries need $bareSize bytes but only $targetSize are available; " +
                "the patch does not fit in the space the ELF reserves for it"
        }

        val pad = targetSize - bareSize

        val padded = assemble(prepared, padTotal = pad)

        check(padded.size.toLong() == targetSize) {
            "the rebuilt zip is ${padded.size} bytes, expected $targetSize"
        }

        return padded
    }

    private data class Prepared(
        val name: String,
        val nameBytes: ByteArray,

        val size: Long,
        val compressed: ByteArray,
        val crc: Int,
        val mode: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Prepared

            if (size != other.size) return false
            if (crc != other.crc) return false
            if (mode != other.mode) return false
            if (name != other.name) return false
            if (!nameBytes.contentEquals(other.nameBytes)) return false
            if (!compressed.contentEquals(other.compressed)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = size.hashCode()
            result = 31 * result + crc
            result = 31 * result + mode
            result = 31 * result + name.hashCode()
            result = 31 * result + nameBytes.contentHashCode()
            result = 31 * result + compressed.contentHashCode()
            return result
        }
    }

    private fun compressAll(sorted: List<Entry>): List<Prepared> = sorted.map { entry ->
        Prepared(
            name = entry.name,
            nameBytes = entry.name.toByteArray(Charsets.UTF_8),
            size = entry.data.size.toLong(),
            compressed = deflate(entry.data),
            crc = crc32(entry.data),
            mode = entry.mode,
        )
    }

    private fun writeArchive(entries: List<Prepared>, padTotal: Long, out: java.io.OutputStream) {
        val shares = distribute(padTotal, entries.size)
        val central = ByteArrayOutputStream(entries.size * 96)
        var offset = 0L

        for ((index, entry) in entries.withIndex()) {
            val nameBytes = entry.nameBytes
            val compressed = entry.compressed
            val crc = entry.crc
            val localOffset = offset

            val header = ByteArrayOutputStream(LOCAL_HEADER + nameBytes.size)
            writeInt(header, LOCAL_SIG)
            writeShort(header, VERSION_NEEDED)
            writeShort(header, 0)
            writeShort(header, METHOD_DEFLATE)
            writeShort(header, 0)
            writeShort(header, DOS_DATE_1980)
            writeInt(header, crc)
            writeInt(header, compressed.size)
            writeInt(header, entry.size.toInt())
            writeShort(header, nameBytes.size)
            writeShort(header, 0)
            header.write(nameBytes)

            val headerBytes = header.toByteArray()
            out.write(headerBytes)
            out.write(compressed)
            offset += headerBytes.size.toLong() + compressed.size

            val field = shares[index]
            val extra = extraRecords(field - RECORD_HEADER)
            check(extra.size.toLong() == field) {
                "the extra field for ${entry.name} is ${extra.size} bytes, expected $field"
            }

            writeInt(central, CENTRAL_SIG)
            writeShort(central, VERSION_MADE_BY)
            writeShort(central, VERSION_NEEDED)
            writeShort(central, 0)
            writeShort(central, METHOD_DEFLATE)
            writeShort(central, 0)
            writeShort(central, DOS_DATE_1980)
            writeInt(central, crc)
            writeInt(central, compressed.size)
            writeInt(central, entry.size.toInt())
            writeShort(central, nameBytes.size)
            writeShort(central, extra.size)
            writeShort(central, 0)
            writeShort(central, 0)
            writeShort(central, 0)
            writeInt(central, entry.mode shl 16)
            writeInt(central, localOffset.toInt())
            central.write(nameBytes)
            central.write(extra)
        }

        val centralBytes = central.toByteArray()
        val cdOffset = offset
        out.write(centralBytes)

        val eocd = ByteArrayOutputStream(EOCD_SIZE)
        writeInt(eocd, EOCD_SIG)
        writeShort(eocd, 0)
        writeShort(eocd, 0)
        writeShort(eocd, entries.size)
        writeShort(eocd, entries.size)
        writeInt(eocd, centralBytes.size)
        writeInt(eocd, cdOffset.toInt())
        writeShort(eocd, 0)
        out.write(eocd.toByteArray())
    }

    private fun assemble(entries: List<Prepared>, padTotal: Long): ByteArray {
        val shares = distribute(padTotal, entries.size)

        val exact = entries.sumOf {
            (LOCAL_HEADER + CENTRAL_HEADER + 2L * it.nameBytes.size + it.compressed.size)
        } + EOCD_SIZE + padTotal
        val capacity = exact.coerceIn(1L shl 20, Int.MAX_VALUE - 8L).toInt()

        val body = ByteArrayOutputStream(capacity)
        val central = ByteArrayOutputStream(entries.size * 96)

        for ((index, entry) in entries.withIndex()) {
            val nameBytes = entry.nameBytes
            val compressed = entry.compressed
            val crc = entry.crc
            val localOffset = body.size()

            writeInt(body, LOCAL_SIG)
            writeShort(body, VERSION_NEEDED)
            writeShort(body, 0)
            writeShort(body, METHOD_DEFLATE)
            writeShort(body, 0)
            writeShort(body, DOS_DATE_1980)
            writeInt(body, crc)
            writeInt(body, compressed.size)
            writeInt(body, entry.size.toInt())
            writeShort(body, nameBytes.size)
            writeShort(body, 0)
            body.write(nameBytes)
            body.write(compressed)

            val field = shares[index]
            val extra = extraRecords(field - RECORD_HEADER)
            check(extra.size.toLong() == field) {
                "the extra field for ${entry.name} is ${extra.size} bytes, expected $field"
            }

            writeInt(central, CENTRAL_SIG)
            writeShort(central, VERSION_MADE_BY)
            writeShort(central, VERSION_NEEDED)
            writeShort(central, 0)
            writeShort(central, METHOD_DEFLATE)
            writeShort(central, 0)
            writeShort(central, DOS_DATE_1980)
            writeInt(central, crc)
            writeInt(central, compressed.size)
            writeInt(central, entry.size.toInt())
            writeShort(central, nameBytes.size)
            writeShort(central, extra.size)
            writeShort(central, 0)
            writeShort(central, 0)
            writeShort(central, 0)

            writeInt(central, entry.mode shl 16)
            writeInt(central, localOffset)
            central.write(nameBytes)
            central.write(extra)
        }

        val bodyBytes = body.toByteArray()
        val centralBytes = central.toByteArray()

        val out = ByteArrayOutputStream(bodyBytes.size + centralBytes.size + 22)
        out.write(bodyBytes)
        val cdOffset = out.size()
        out.write(centralBytes)

        writeInt(out, EOCD_SIG)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, entries.size)
        writeShort(out, entries.size)
        writeInt(out, centralBytes.size)
        writeInt(out, cdOffset)
        writeShort(out, 0)

        return out.toByteArray()
    }

    private fun distribute(padTotal: Long, entryCount: Int): LongArray {
        if (padTotal == 0L) return LongArray(entryCount)

        val shares = ArrayList<Long>()
        var remaining = padTotal

        while (remaining > 0) {
            require(shares.size < entryCount) {
                "padding of $padTotal bytes needs more than the $entryCount entries available"
            }

            var take = minOf(remaining, MAX_FIELD.toLong())

            val leftover = remaining - take
            if (leftover in 1 until MIN_FIELD) take = remaining

            require(take >= MIN_FIELD) { "padding of $padTotal is too small to place" }

            shares += take
            remaining -= take
        }

        return LongArray(entryCount).also { out ->
            for (i in shares.indices) out[i] = shares[i]
        }
    }

    private fun extraRecords(payloadLen: Long): ByteArray {
        if (payloadLen <= 0) return ByteArray(0)

        val out = ByteArrayOutputStream(payloadLen.toInt())
        var remaining = payloadLen

        while (remaining > 0) {
            val chunk = minOf(remaining, MAX_RECORD_PAYLOAD.toLong()).toInt()
            require(chunk >= MIN_RECORD_PAYLOAD) {
                "extra-field padding of $payloadLen is too small"
            }
            writeShort(out, PRIVATE_RECORD_ID)
            writeShort(out, chunk)
            out.write(ByteArray(chunk))
            remaining -= (RECORD_HEADER + chunk)
        }

        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {

        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setStrategy(Deflater.DEFAULT_STRATEGY)
        try {
            deflater.setInput(data)
            deflater.finish()

            val out = ByteArrayOutputStream(maxOf(64, data.size / 2))
            val buffer = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun crc32(data: ByteArray): Int {
        val crc = CRC32()
        crc.update(data)
        return crc.value.toInt()
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private companion object {
        const val LOCAL_SIG = 0x04034b50
        const val CENTRAL_SIG = 0x02014b50
        const val EOCD_SIG = 0x06054b50

        const val VERSION_NEEDED = 20
        const val VERSION_MADE_BY = 20
        const val METHOD_DEFLATE = 8

        const val LOCAL_HEADER = 30

        const val CENTRAL_HEADER = 46

        const val EOCD_SIZE = 22

        const val DOS_DATE_1980 = 0x0021

        const val RECORD_HEADER = 4

        const val MAX_FIELD = 0xFFFF

        const val MIN_FIELD = 5

        const val MAX_RECORD_PAYLOAD = 0xFFFF - RECORD_HEADER

        const val MIN_RECORD_PAYLOAD = 1

        const val PRIVATE_RECORD_ID = 0x9999
    }
}
