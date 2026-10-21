package io.github.alaabataineh92.termuxvmfixer.data.apkpatch

import android.annotation.SuppressLint

class SymlinkManifestRewriter(
    private val prefix: String = DEFAULT_PREFIX,
) {

    data class Result(
        val text: String,
        val rewritten: Int,
        val alreadyAbsolute: Int,
        val skipped: Int,
    )

    fun rewrite(manifest: String): Result {
        var rewritten = 0
        var absolute = 0
        var skipped = 0

        val out = ArrayList<String>()

        val lines = manifest.split("\n").let { if (it.lastOrNull() == "") it.dropLast(1) else it }

        for (line in lines) {
            val cut = line.indexOf(ARROW)
            if (cut < 0) {
                out += line
                skipped++
                continue
            }

            val target = line.substring(0, cut)
            val linkPath = line.substring(cut + 1).trim()

            if (target.startsWith("/")) {
                out += line
                absolute++
                continue
            }

            out += joinTargetToLinkDirectory(target, linkPath) + ARROW + linkPath
            rewritten++
        }

        return Result(
            text = out.joinToString("\n") + "\n",
            rewritten = rewritten,
            alreadyAbsolute = absolute,
            skipped = skipped,
        )
    }

    private fun joinTargetToLinkDirectory(target: String, linkPath: String): String {
        val cleanLink = if (linkPath.startsWith("./")) linkPath.substring(2) else linkPath
        val linkDir = if ("/" in cleanLink) cleanLink.substringBeforeLast("/") else ""

        val joined = if (linkDir.isEmpty()) {
            "$prefix/$target"
        } else {
            "$prefix/$linkDir/$target"
        }

        return normalise(joined)
    }

    companion object {

        const val ARROW = '←'

        @SuppressLint("SdCardPath")
        const val DEFAULT_PREFIX = "/data/data/com.termux/files/usr"

        fun normalise(path: String): String {
            val parts = ArrayDeque<String>()

            for (part in path.split('/')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> parts.removeLastOrNull()
                    else -> parts.addLast(part)
                }
            }

            return "/" + parts.joinToString("/")
        }
    }
}
