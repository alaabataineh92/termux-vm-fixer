package io.github.alaabataineh92.termuxvmfixer.data.fix.bootstrap

import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class BootstrapReader {
    data class Extracted(

        val directory: File,

        val symlinks: String,

        val fileCount: Int,
    )

    fun extract(apk: File, destination: File): Extracted {
        destination.deleteRecursively()
        if (!destination.mkdirs()) error("cannot create ${destination.absolutePath}")

        val bootstrap = File(destination, "bootstrap.zip")
        extractBootstrapEntry(apk, bootstrap)

        val tree = File(destination, "tree")
        if (!tree.mkdirs()) error("cannot create ${tree.absolutePath}")
        val fileCount = unzip(bootstrap, tree)

        val symlinksFile = File(tree, TermuxPaths.SYMLINKS_FILE)
        if (!symlinksFile.isFile) {
            error("bootstrap has no ${TermuxPaths.SYMLINKS_FILE}")
        }

        bootstrap.delete()

        return Extracted(
            directory = tree,
            symlinks = symlinksFile.readText(),
            fileCount = fileCount,
        )
    }

    private fun extractBootstrapEntry(apk: File, destination: File) {
        ZipFile(apk).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { it.name.endsWith(TermuxPaths.BOOTSTRAP_ENTRY_SUFFIX) }
                ?: error("no ${TermuxPaths.BOOTSTRAP_ENTRY_SUFFIX} in the Termux APK")

            val library = zip.getInputStream(entry).use { it.readBytes() }

            if (BootstrapArchive.find(library) != null) {
                BootstrapArchive.extract(library, destination)
                return
            }

            zip.getInputStream(entry).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun unzip(archive: File, destination: File): Int {
        var count = 0
        val root = destination.canonicalPath
        val modes = BootstrapArchive.readModes(archive)

        ZipFile(archive).use { zip ->
            for (entry in zip.entries()) {
                if (writeEntry(zip, entry, destination, root, modes)) count++
            }
        }
        return count
    }

    private fun writeEntry(
        zip: ZipFile,
        entry: ZipEntry,
        destination: File,
        root: String,
        modes: Map<String, Int>,
    ): Boolean {
        val name = entry.name.removePrefix("./")
        if (name.isEmpty()) return false

        val target = File(destination, name)

        if (!target.canonicalPath.startsWith(root)) return false

        if (entry.isDirectory) {
            target.mkdirs()
            return true
        }

        target.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        if (isExecutable(modes[entry.name])) {
            target.setExecutable(true, false)
        }
        return true
    }

    private fun isExecutable(mode: Int?): Boolean =
        mode != null && mode and UNIX_EXEC_BIT != 0

    private companion object {

        const val UNIX_EXEC_BIT = 0b001_000_000
    }
}
