package io.github.alaabataineh92.termuxvmfixer.data.fix.archive

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class GnuTarWriter {

    fun write(destination: File, sources: Map<String, File>) {
        val parent = destination.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            error("cannot create ${parent.absolutePath}")
        }

        BufferedOutputStream(FileOutputStream(destination), BUFFER_SIZE).use { fileOut ->
            TarArchiveOutputStream(fileOut).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_ERROR)
                tar.setAddPaxHeadersForNonAsciiNames(false)

                for ((name, file) in sources.toSortedMap()) {
                    addTree(tar, name, file)
                }
            }
        }
    }

    private fun addTree(tar: TarArchiveOutputStream, name: String, file: File) {
        addEntry(tar, name, file)

        if (!file.isDirectory) return

        val children = file.listFiles()?.sortedBy { it.name } ?: return
        for (child in children) {
            addTree(tar, "$name/${child.name}", child)
        }
    }

    private fun addEntry(tar: TarArchiveOutputStream, name: String, file: File) {
        val entry = TarArchiveEntry(file, name)

        entry.modTime = java.util.Date((file.lastModified() / 1000L) * 1000L)

        entry.setIds(0, 0)
        entry.setNames("", "")

        if (file.isDirectory && !entry.name.endsWith("/")) {
            entry.name = "${entry.name}/"
        }

        tar.putArchiveEntry(entry)
        if (file.isFile) {
            file.inputStream().use { input -> input.copyTo(tar, BUFFER_SIZE) }
        }
        tar.closeArchiveEntry()
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
