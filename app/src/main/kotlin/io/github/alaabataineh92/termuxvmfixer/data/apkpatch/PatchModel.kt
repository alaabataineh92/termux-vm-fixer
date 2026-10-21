package io.github.alaabataineh92.termuxvmfixer.data.apkpatch

import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.payload.PatchScripts
import java.io.File

sealed interface PatchSource {
    data class Installed(val apk: File, val versionName: String?) : PatchSource
    data class Picked(val apk: File, val displayName: String?) : PatchSource

    val file: File
        get() = when (this) {
            is Installed -> apk
            is Picked -> apk
        }

    val label: String
        get() = when (this) {
            is Installed -> "installed Termux ${versionName ?: ""}".trim()
            is Picked -> displayName ?: apk.name
        }
}

data class PatchRequest(
    val source: PatchSource,
    val output: File,
    val payload: PatchPayload,

    val patchVersion: String = DEFAULT_PATCH_VERSION,
) {
    companion object {

        const val DEFAULT_PATCH_VERSION = "1.0.0"
    }
}

enum class PatchStep {
    READ_APK,
    EXTRACT_BOOTSTRAP,
    REWRITE_SYMLINKS,
    INJECT_PAYLOAD,
    REWRITE_SECOND_STAGE,
    REBUILD_ZIP,
    VERIFY,
    REPACK_APK,
    SIGN,
    DONE,
}

enum class PatchStepState { PENDING, RUNNING, DONE, FAILED, SKIPPED }

data class PatchEvent(
    val step: PatchStep,
    val state: PatchStepState,
    val detail: String? = null,
)

enum class VerifyCheckKind {

    ELF_HEADER,

    ELF_SIZE_UNCHANGED,

    PROGRAM_HEADERS_IN_RANGE,

    SECTION_TABLE_IN_RANGE,

    DYNAMIC_SECTION,

    DYNAMIC_TERMINATES,

    SECTION_BIASES,

    BOOTSTRAP_READABLE,

    INJECTED_FILES_PRESENT,

    SYMLINKS_ABSOLUTE,
}

data class VerifyCheck(
    val kind: VerifyCheckKind,
    val passed: Boolean,
    val detail: String,
)

data class PatchReport(
    val sourceLabel: String,
    val outputBytes: Long,
    val bootstrapBytes: Int,
    val symlinksRewritten: Int,
    val symlinksAlreadyAbsolute: Int,
    val entryCount: Int,
    val checks: List<VerifyCheck>,
) {
    val allChecksPassed: Boolean get() = checks.all { it.passed }
}

sealed interface PatchResult {

    data class Success(
        val apk: File,
        val report: PatchReport,
    ) : PatchResult

    data class Failed(
        val problems: List<String>,
        val cause: Throwable? = null,
    ) : PatchResult

    data object Cancelled : PatchResult
}

interface PatchPayload {
    fun library(): ByteArray

    fun scripts(): PatchScripts

    val version: String
}
