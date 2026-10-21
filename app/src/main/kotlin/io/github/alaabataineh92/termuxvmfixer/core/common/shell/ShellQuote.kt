package io.github.alaabataineh92.termuxvmfixer.core.common.shell

object ShellQuote {
    fun single(value: String): String {
        val escaped = value.replace("'", "'\\''")
        return "'$escaped'"
    }

    fun normalisePath(path: String): String {
        val parts = mutableListOf<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(part)
            }
        }
        return "/" + parts.joinToString("/")
    }

    fun resolveSymlinkTarget(target: String, linkPath: String): String {
        if (target.startsWith("/")) return target
        val linkDir = linkPath.substringBeforeLast('/', missingDelimiterValue = "")
        return normalisePath("$linkDir/$target")
    }
}
