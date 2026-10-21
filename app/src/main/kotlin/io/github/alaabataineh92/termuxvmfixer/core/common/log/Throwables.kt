package io.github.alaabataineh92.termuxvmfixer.core.common.log

fun Throwable.describe(): String = buildString {
    var current: Throwable? = this@describe
    var depth = 0

    while (current != null) {
        if (depth > 0) append("\n    caused by: ")
        append(current::class.java.simpleName)
        current.message?.let { append(": ").append(it) }
        current = current.cause
        depth++

        if (depth > MAX_CAUSE_DEPTH) {
            append("\n    ... (cause chain truncated)")
            break
        }
    }
}

private const val MAX_CAUSE_DEPTH = 12
