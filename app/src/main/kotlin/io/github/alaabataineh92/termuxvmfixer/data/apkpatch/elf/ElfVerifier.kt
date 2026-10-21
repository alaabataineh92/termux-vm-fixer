package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.elf

import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.VerifyCheck
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.VerifyCheckKind
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfVerifier {

    fun verify(library: ByteArray, expectedSize: Int): List<VerifyCheck> {
        val checks = ArrayList<VerifyCheck>()

        checks += checkElfHeader(library)
        if (checks.last().passed.not()) return checks

        val header = Header.parse(library)
        val segments = header.segments(library)

        checks += VerifyCheck(
            kind = VerifyCheckKind.ELF_SIZE_UNCHANGED,
            passed = library.size == expectedSize,
            detail = "${library.size} bytes (expected $expectedSize)",
        )

        checks += checkSegmentsInRange(library, segments)
        checks += checkSectionTableInRange(library, header)
        checks += checkDynamic(header, segments)
        checks += checkSectionBiases(library, header, segments)

        return checks
    }

    private fun checkElfHeader(library: ByteArray): VerifyCheck {
        if (library.size < 64) {
            return VerifyCheck(
                VerifyCheckKind.ELF_HEADER,
                false,
                "the file is only ${library.size} bytes",
            )
        }

        val magic = library[0] == 0x7F.toByte() &&
            library[1] == 'E'.code.toByte() &&
            library[2] == 'L'.code.toByte() &&
            library[3] == 'F'.code.toByte()

        if (!magic) {
            return VerifyCheck(
                VerifyCheckKind.ELF_HEADER,
                false,
                "bad ELF magic: " + library.take(4).joinToString("") {
                    "%02x".format(it.toInt() and 0xFF)
                },
            )
        }

        val is64 = library[4].toInt() == 2
        val isLittleEndian = library[5].toInt() == 1
        val machine = (library[18].toInt() and 0xFF) or ((library[19].toInt() and 0xFF) shl 8)

        val ok = is64 && isLittleEndian && machine == 0xB7
        return VerifyCheck(
            kind = VerifyCheckKind.ELF_HEADER,
            passed = ok,
            detail = buildString {
                append(if (is64) "ELF64" else "ELF32")
                append(", ")
                append(if (isLittleEndian) "little-endian" else "big-endian")
                append(", machine 0x")
                append("%x".format(machine))
                append(if (machine == 0xB7) " (aarch64)" else " (NOT aarch64)")
            },
        )
    }

    private fun checkSegmentsInRange(library: ByteArray, segments: List<Segment>): VerifyCheck {
        val bad = segments.filter { it.fileSize > 0 && it.offset + it.fileSize > library.size }
        return VerifyCheck(
            kind = VerifyCheckKind.PROGRAM_HEADERS_IN_RANGE,
            passed = bad.isEmpty(),
            detail = if (bad.isEmpty()) {
                "${segments.size} segments, all within the file"
            } else {
                bad.joinToString { "type 0x${it.type.toString(16)} ends past the file" }
            },
        )
    }

    private fun checkSectionTableInRange(library: ByteArray, header: Header): VerifyCheck {
        val inRange = header.sectionOffset < library.size
        return VerifyCheck(
            kind = VerifyCheckKind.SECTION_TABLE_IN_RANGE,
            passed = inRange,
            detail = "e_shoff = ${header.sectionOffset}, file is ${library.size} bytes",
        )
    }

    private fun checkDynamic(header: Header, segments: List<Segment>): VerifyCheck {
        val dynamic = segments.firstOrNull { it.type == PT_DYNAMIC }
            ?: return VerifyCheck(
                VerifyCheckKind.DYNAMIC_SECTION,
                false,
                "no PT_DYNAMIC segment",
            )

        if (dynamic.offset + dynamic.fileSize > header.buffer.capacity()) {
            return VerifyCheck(
                VerifyCheckKind.DYNAMIC_SECTION,
                false,
                "PT_DYNAMIC runs past the file",
            )
        }

        var entries = 0
        var terminated = false
        var at = dynamic.offset.toInt()
        val dynamicEnd = (dynamic.offset + dynamic.fileSize).toInt()
        while (at + 16 <= dynamicEnd) {
            val tag = header.buffer.getLong(at)
            entries++
            if (tag == 0L) {
                terminated = true
                break
            }
            at += 16
        }

        return VerifyCheck(
            kind = VerifyCheckKind.DYNAMIC_TERMINATES,
            passed = terminated,
            detail = "$entries entries" + if (terminated) ", ends with DT_NULL" else ", NO DT_NULL",
        )
    }

    private fun checkSectionBiases(
        library: ByteArray,
        header: Header,
        segments: List<Segment>,
    ): VerifyCheck {
        val loads = segments.filter { it.type == PT_LOAD }
        var checked = 0
        var bad = 0
        val problems = ArrayList<String>()

        val shCount = header.sectionCount
        for (i in 0 until shCount) {
            val at = (header.sectionOffset + i.toLong() * header.sectionEntrySize).toInt()
            if (at + 64 > library.size) break

            val addr = header.buffer.getLong(at + 16)
            val offset = header.buffer.getLong(at + 24)
            val size = header.buffer.getLong(at + 32)
            if (size == 0L || addr == 0L || offset == 0L) continue

            if (offset + size > library.size) {
                bad++
                problems += "section $i runs past the file"
                continue
            }

            val owner = loads.firstOrNull { offset >= it.offset && offset < it.offset + it.fileSize }
                ?: continue

            checked++
            val expected = owner.vaddr - owner.offset
            if (addr - offset != expected) {
                bad++
                problems += "section $i bias ${addr - offset} != segment bias $expected"
            }
        }

        return VerifyCheck(
            kind = VerifyCheckKind.SECTION_BIASES,
            passed = bad == 0,
            detail = if (bad == 0) {
                "$checked sections checked"
            } else {
                problems.take(3).joinToString("; ")
            },
        )
    }

    data class Segment(
        val type: Int,
        val offset: Long,
        val vaddr: Long,
        val fileSize: Long,
    )

    class Header private constructor(
        val buffer: ByteBuffer,
        val programOffset: Long,
        val programEntrySize: Int,
        val programCount: Int,
        val sectionOffset: Long,
        val sectionEntrySize: Int,
        val sectionCount: Int,
    ) {
        fun segments(library: ByteArray): List<Segment> {
            val out = ArrayList<Segment>(programCount)
            for (i in 0 until programCount) {
                val at = (programOffset + i * programEntrySize).toInt()
                if (at + 56 > library.size) break
                out += Segment(
                    type = buffer.getInt(at),
                    offset = buffer.getLong(at + 8),
                    vaddr = buffer.getLong(at + 16),
                    fileSize = buffer.getLong(at + 32),
                )
            }
            return out
        }

        companion object {
            fun parse(library: ByteArray): Header {
                val buffer = ByteBuffer.wrap(library).order(ByteOrder.LITTLE_ENDIAN)
                return Header(
                    buffer = buffer,
                    programOffset = buffer.getLong(32),
                    programEntrySize = buffer.getShort(54).toInt() and 0xFFFF,
                    programCount = buffer.getShort(56).toInt() and 0xFFFF,
                    sectionOffset = buffer.getLong(40),
                    sectionEntrySize = buffer.getShort(58).toInt() and 0xFFFF,
                    sectionCount = buffer.getShort(60).toInt() and 0xFFFF,
                )
            }
        }
    }

    private companion object {
        const val PT_LOAD = 1
        const val PT_DYNAMIC = 2
    }
}
