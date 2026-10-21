package io.github.alaabataineh92.termuxvmfixer.data.fix.symlink

import io.github.alaabataineh92.termuxvmfixer.core.common.shell.ShellQuote
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths

class SymlinkScriptGenerator {

    data class Symlink(val target: String, val linkPath: String)

    data class Generated(
        val script: String,
        val count: Int,
    )

    fun generate(manifest: String, prefix: String = TermuxPaths.PREFIX): Generated {
        val links = parse(manifest)
        val lines = mutableListOf("#!/system/bin/sh", "")

        val madeDirs = mutableSetOf<String>()

        for ((target1, linkPath) in links) {
            val linkDir = linkPath.substringBeforeLast('/', missingDelimiterValue = "")
            if (linkDir.isNotEmpty() && madeDirs.add(linkDir)) {
                lines += "mkdir -p ${ShellQuote.single("$prefix/$linkDir")}"
            }

            val target = ShellQuote.resolveSymlinkTarget(
                target = target1,
                linkPath = "$prefix/$linkPath",
            )

            lines += "ln -sfn ${ShellQuote.single(target)} ${ShellQuote.single("$prefix/$linkPath")}"
        }

        lines += "echo SYMLINKS=${links.size}"

        return Generated(script = lines.joinToString("\n") + "\n", count = links.size)
    }

    fun parse(manifest: String): List<Symlink> = manifest
        .lineSequence()
        .map { it.trimEnd('\r', '\n') }
        .filter { TermuxPaths.SYMLINK_SEPARATOR in it }
        .mapNotNull { line ->
            val index = line.indexOf(TermuxPaths.SYMLINK_SEPARATOR)
            val target = line.substring(0, index)
            var linkPath = line.substring(index + 1).trim()
            if (linkPath.startsWith("./")) linkPath = linkPath.removePrefix("./")
            if (linkPath.isEmpty()) null else Symlink(target, linkPath)
        }
        .toList()
}
