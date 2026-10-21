package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.engine

import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchEvent
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchReport
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchRequest
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchResult
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchStep
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchStepState
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.VerifyCheck
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.VerifyCheckKind
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.elf.ElfVerifier
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.ApkSigner
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.SigningIdentity
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SelfWiringApkPatcher(
    private val signer: ApkSigner,
    private val verifier: ElfVerifier = ElfVerifier(),
    private val patcher: BootstrapPatcher = BootstrapPatcher(),
) {

    private data class Slot(val start: Int, val end: Int) {
        val size: Long get() = (end - start).toLong()
    }

    fun patch(
        request: PatchRequest,
        identity: SigningIdentity,
        onEvent: (PatchEvent) -> Unit = {},
    ): PatchResult {
        val source = request.source.file
        if (!source.isFile) {
            return PatchResult.Failed(listOf("the APK to patch does not exist: $source"))
        }

        val checks = ArrayList<VerifyCheck>()

        try {

            step(onEvent, PatchStep.READ_APK, PatchStepState.RUNNING, source.absolutePath)
            val entryName = findBootstrapEntry(source)
                ?: return fail(
                    onEvent,
                    PatchStep.READ_APK,
                    "the APK has no libtermux-bootstrap.so, so it is not a Termux build",
                )

            val originalLibrary = readEntry(source, entryName)
            step(
                onEvent, PatchStep.READ_APK, PatchStepState.DONE,
                "${originalLibrary.size} bytes, entry $entryName",
            )

            step(onEvent, PatchStep.EXTRACT_BOOTSTRAP, PatchStepState.RUNNING)
            val slot = findSlot(originalLibrary)
            val originalZip = originalLibrary.copyOfRange(slot.start, slot.end)
            val contents = patcher.read(originalZip)
            checks += VerifyCheck(
                kind = VerifyCheckKind.BOOTSTRAP_READABLE,
                passed = true,
                detail = "${contents.entries.size} entries",
            )
            step(
                onEvent, PatchStep.EXTRACT_BOOTSTRAP, PatchStepState.DONE,
                "${contents.entries.size} entries",
            )

            step(onEvent, PatchStep.REWRITE_SYMLINKS, PatchStepState.RUNNING)
            val patched = patcher.patch(contents, request.payload, request.patchVersion)
            step(
                onEvent, PatchStep.REWRITE_SYMLINKS, PatchStepState.DONE,
                "${patched.symlinksRewritten} rewritten, " +
                    "${patched.symlinksAlreadyAbsolute} already absolute",
            )
            step(
                onEvent, PatchStep.INJECT_PAYLOAD, PatchStepState.DONE,
                "${patched.entriesAdded} entries added",
            )
            step(onEvent, PatchStep.REWRITE_SECOND_STAGE, PatchStepState.DONE, "launcher in place")

            step(onEvent, PatchStep.REBUILD_ZIP, PatchStepState.RUNNING)
            val zipFile = File(request.output.parentFile, request.output.name + ".bootstrap.zip")
            try {
                val written = patcher.rebuildTo(zipFile, patched.contents, slot.size)
                step(onEvent, PatchStep.REBUILD_ZIP, PatchStepState.DONE, "$written bytes")

                step(onEvent, PatchStep.VERIFY, PatchStepState.RUNNING)
                val newLibrary = spliceLibrary(originalLibrary, slot, zipFile)
                checks += verifier.verify(newLibrary, originalLibrary.size)
                checks += verifyInjected(patched.contents)
                checks += verifySymlinksAbsolute(patched.contents)

                val failed = checks.filter { !it.passed }
                if (failed.isNotEmpty()) {

                    step(
                        onEvent, PatchStep.VERIFY, PatchStepState.FAILED,
                        failed.joinToString("; ") { it.kind.name },
                    )
                    return PatchResult.Failed(failed.map { "${it.kind}: ${it.detail}" })
                }
                step(onEvent, PatchStep.VERIFY, PatchStepState.DONE, "${checks.size} checks passed")

                step(onEvent, PatchStep.REPACK_APK, PatchStepState.RUNNING)
                val unsigned = File(request.output.parentFile, request.output.name + ".unsigned")
                repackApk(source, entryName, newLibrary, unsigned)
                step(onEvent, PatchStep.REPACK_APK, PatchStepState.DONE, unsigned.name)

                step(onEvent, PatchStep.SIGN, PatchStepState.RUNNING, identity.label)
                signer.sign(unsigned, request.output, identity)
                unsigned.delete()
                step(
                    onEvent, PatchStep.SIGN, PatchStepState.DONE,
                    "${request.output.length()} bytes",
                )

                step(onEvent, PatchStep.DONE, PatchStepState.DONE)

                return PatchResult.Success(
                    apk = request.output,
                    report = PatchReport(
                        sourceLabel = request.source.label,
                        outputBytes = request.output.length(),
                        bootstrapBytes = written.toInt(),
                        symlinksRewritten = patched.symlinksRewritten,
                        symlinksAlreadyAbsolute = patched.symlinksAlreadyAbsolute,
                        entryCount = patched.contents.entries.size,
                        checks = checks,
                    ),
                )
            } finally {

                runCatching { zipFile.delete() }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            return PatchResult.Failed(
                problems = listOf(e.message ?: e::class.java.simpleName),
                cause = e,
            )
        }
    }

    private fun spliceLibrary(original: ByteArray, slot: Slot, zipFile: File): ByteArray {
        require(zipFile.length() == slot.size) {
            "the zip must occupy exactly ${slot.size} bytes, not ${zipFile.length()}"
        }

        zipFile.inputStream().use { input ->
            var at = slot.start
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                System.arraycopy(buffer, 0, original, at, read)
                at += read
            }
            check(at == slot.end) {
                "the zip filled ${at - slot.start} bytes, expected ${slot.size}"
            }
        }

        return original
    }

    private fun findSlot(library: ByteArray): Slot {
        val start = indexOf(library, byteArrayOf(0x50, 0x4B, 0x03, 0x04), 0)
        require(start >= 0) { "the library has no embedded zip" }

        var searchEnd = library.size
        while (true) {
            val eocd = lastIndexOf(library, byteArrayOf(0x50, 0x4B, 0x05, 0x06), searchEnd)
            require(eocd >= 0) { "the embedded zip has no end-of-central-directory record" }

            val commentLength = readShort(library, eocd + 20)
            val centralSize = readInt(library, eocd + 12)
            val centralOffset = readInt(library, eocd + 16)
            val end = eocd + 22 + commentLength

            val cdAt = start + centralOffset
            if (end <= library.size &&
                cdAt + 4 <= library.size &&
                library[cdAt] == 0x50.toByte() &&
                library[cdAt + 1] == 0x4B.toByte() &&
                library[cdAt + 2] == 0x01.toByte() &&
                library[cdAt + 3] == 0x02.toByte() &&
                centralSize > 0
            ) {
                return Slot(start, end)
            }

            searchEnd = eocd
        }
    }

    private fun findBootstrapEntry(apk: File): String? = ZipFile(apk).use { zip ->
        zip.entries().asSequence()
            .map { it.name }
            .firstOrNull { it.endsWith(BOOTSTRAP_SUFFIX) }
    }

    private fun readEntry(apk: File, name: String): ByteArray = ZipFile(apk).use { zip ->
        zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }
    }

    private fun repackApk(
        source: File,
        entryName: String,
        newLibrary: ByteArray,
        output: File,
    ) {
        output.parentFile?.mkdirs()

        ZipFile(source).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) {
                        out.putNextEntry(entry)
                        out.closeEntry()
                        continue
                    }

                    val copy = ZipEntry(entry.name).apply {
                        method = entry.method
                        time = entry.time
                        comment = entry.comment
                        extra = entry.extra
                    }

                    if (entry.name == entryName) {
                        out.putNextEntry(copy)
                        out.write(newLibrary)
                        out.closeEntry()
                        continue
                    }

                    if (entry.method == ZipEntry.STORED) {

                        copy.size = entry.size
                        copy.compressedSize = entry.size
                        copy.crc = crcOf(zip, entry)
                    }

                    out.putNextEntry(copy)
                    zip.getInputStream(entry).use { input ->
                        input.copyTo(out, BUFFER_BYTES)
                    }
                    out.closeEntry()
                }
            }
        }
    }

    private fun crcOf(zip: ZipFile, entry: ZipEntry): Long {
        val crc = java.util.zip.CRC32()
        val buffer = ByteArray(BUFFER_BYTES)
        zip.getInputStream(entry).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }

    private fun verifyInjected(contents: io.github.alaabataineh92.termuxvmfixer.data.apkpatch.zip.BootstrapContents): List<VerifyCheck> {
        val required = listOf(
            "lib/libvmfix.so",
            "etc/termux-vm-shims/touch",
            "etc/termux-vm-shims/cp",
            "etc/termux-vm-shims/tar",
            "etc/profile.d/00-termux-vm-shims.sh",
            "etc/profile.d/02-termux-vmfix.sh",
            "etc/apt/apt.conf.d/99-termux-vm-shims",
            "etc/termux/bootstrap/termux-bootstrap-second-stage.sh",
            "etc/termux/bootstrap/termux-bootstrap-second-stage-real.sh",
        )

        val missing = required.filter { it !in contents.entries }
        return listOf(
            VerifyCheck(
                kind = VerifyCheckKind.INJECTED_FILES_PRESENT,
                passed = missing.isEmpty(),
                detail = if (missing.isEmpty()) "${required.size} files" else "missing: $missing",
            ),
        )
    }

    private fun verifySymlinksAbsolute(
        contents: io.github.alaabataineh92.termuxvmfixer.data.apkpatch.zip.BootstrapContents,
    ): List<VerifyCheck> {
        val manifest = contents.entries["SYMLINKS.txt"]?.toString(Charsets.UTF_8).orEmpty()
        val arrow = io.github.alaabataineh92.termuxvmfixer.data.apkpatch.SymlinkManifestRewriter.ARROW

        val links = manifest.lines().filter { arrow in it }
        val relative = links.filterNot { it.substringBefore(arrow).startsWith("/") }

        return listOf(
            VerifyCheck(
                kind = VerifyCheckKind.SYMLINKS_ABSOLUTE,
                passed = relative.isEmpty(),
                detail = "${links.size} links, ${relative.size} still relative",
            ),
        )
    }

    private fun step(
        onEvent: (PatchEvent) -> Unit,
        step: PatchStep,
        state: PatchStepState,
        detail: String? = null,
    ) {
        onEvent(PatchEvent(step, state, detail))
    }

    private fun fail(
        onEvent: (PatchEvent) -> Unit,
        step: PatchStep,
        message: String,
    ): PatchResult {
        onEvent(PatchEvent(step, PatchStepState.FAILED, message))
        return PatchResult.Failed(listOf(message))
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
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun readShort(data: ByteArray, at: Int): Int =
        (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)

    private fun readInt(data: ByteArray, at: Int): Int =
        (data[at].toInt() and 0xFF) or
            ((data[at + 1].toInt() and 0xFF) shl 8) or
            ((data[at + 2].toInt() and 0xFF) shl 16) or
            ((data[at + 3].toInt() and 0xFF) shl 24)

    private companion object {
        const val BOOTSTRAP_SUFFIX = "libtermux-bootstrap.so"

        const val BUFFER_BYTES = 64 * 1024
    }
}
