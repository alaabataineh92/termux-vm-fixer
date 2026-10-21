package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.engine

import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchPayload
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.SymlinkManifestRewriter
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.payload.PatchContract
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.payload.PatchScripts
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.zip.BootstrapContents
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.zip.BootstrapZipReader
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.zip.FixedSizeZipWriter
import java.io.File

class BootstrapPatcher(
    private val rewriter: SymlinkManifestRewriter = SymlinkManifestRewriter(),
) {
    data class Result(
        val contents: BootstrapContents,
        val symlinksRewritten: Int,
        val symlinksAlreadyAbsolute: Int,
        val entriesAdded: Int,
    )

    fun patch(
        original: BootstrapContents,
        payload: PatchPayload,
        patchVersion: String,
    ): Result {
        var contents = original
        val before = original.entries.size
        val scripts = payload.scripts()

        val manifest = contents.require(PatchContract.Paths.SYMLINKS).toString(Charsets.UTF_8)
        val rewritten = rewriter.rewrite(manifest)
        contents = contents.with(
            PatchContract.Paths.SYMLINKS,
            rewritten.text.toByteArray(Charsets.UTF_8),
        )

        contents = contents.with(
            PatchContract.Paths.LIBRARY,
            payload.library(),
            BootstrapContents.DEFAULT_FILE_MODE,
        )

        for ((name, body) in scripts.shims()) {
            contents = contents.with(
                "${PatchContract.Paths.SHIM_DIR}/$name",
                body.toByteArray(Charsets.UTF_8),
                BootstrapContents.EXECUTABLE_MODE,
            )
        }

        contents = contents.with(
            PatchContract.Paths.PATH_HOOK,
            scripts.pathHook.toByteArray(Charsets.UTF_8),
        )
        contents = contents.with(
            PatchContract.Paths.VMFIX_HOOK,
            scripts.vmfixHook.toByteArray(Charsets.UTF_8),
        )
        contents = contents.with(
            PatchContract.Paths.APT_CONF,
            scripts.aptConf.toByteArray(Charsets.UTF_8),
        )

        val login = contents.require(PatchContract.Paths.LOGIN_HOOK).toString(Charsets.UTF_8)
        if (PatchContract.MARKER !in login) {
            contents = contents.with(
                PatchContract.Paths.LOGIN_HOOK,
                (login + "\n\n" + scripts.loginHookAddition).toByteArray(Charsets.UTF_8),
            )
        }

        contents = wireFallbackHook(contents, patchVersion, scripts)

        contents = splitSecondStage(contents, scripts.secondStageLauncher)

        return Result(
            contents = contents,
            symlinksRewritten = rewritten.rewritten,
            symlinksAlreadyAbsolute = rewritten.alreadyAbsolute,
            entriesAdded = contents.entries.size - before,
        )
    }

    private fun wireFallbackHook(
        contents: BootstrapContents,
        patchVersion: String,
        scripts: PatchScripts,
    ): BootstrapContents {
        val current = contents.require(PatchContract.Paths.FALLBACK_HOOK)
            .toString(Charsets.UTF_8)

        if (PatchContract.MARKER in current) {
            return contents.with(
                PatchContract.Paths.FALLBACK_HOOK,
                current.toByteArray(Charsets.UTF_8),
            )
        }

        val body = dropFirstLines(current, 2)

        val wired = buildString {
            append("#!/data/data/com.termux/files/usr/bin/sh\n")

            append(PatchContract.versionMarker(patchVersion))
            append("# shellcheck shell=sh\n")
            append("\n")
            append(scripts.pathHook)
            append(scripts.vmfixHook)
            append("\n")

            append(scripts.repairHook.replace("__PREFIX__", PatchContract.PREFIX))
            append("\n")
            append(body)
        }

        return contents.with(
            PatchContract.Paths.FALLBACK_HOOK,
            wired.toByteArray(Charsets.UTF_8),
        )
    }

    private fun splitSecondStage(
        contents: BootstrapContents,
        secondStageLauncher: String,
    ): BootstrapContents {

        val current = contents.require(PatchContract.Paths.SECOND_STAGE).toString(Charsets.UTF_8)
        val absoluteLine = "ln -s \"${PatchContract.PREFIX}/${PatchContract.Paths.SECOND_STAGE}\""

        val original = when {
            LOCK_LINE in current -> current

            PatchContract.Paths.SECOND_STAGE_REAL in contents.entries -> {
                val real = contents.require(PatchContract.Paths.SECOND_STAGE_REAL)
                    .toString(Charsets.UTF_8)

                if (LOCK_LINE in real) {
                    real
                } else if (absoluteLine in real) {

                    real
                } else {
                    throw IllegalStateException(
                        "the second-stage real script has neither a relative nor an " +
                            "absolute lock line; this bootstrap is not one this " +
                            "patcher recognises",
                    )
                }
            }

            else -> throw IllegalStateException(
                "the second-stage script has no lock line and there is no " +
                    "termux-bootstrap-second-stage-real.sh to fall back to; this " +
                    "bootstrap is not one this patcher recognises",
            )
        }

        val relative = LOCK_LINE

        val real = if (relative in original) {
            original.replaceFirst(relative, absoluteLine)
        } else {
            original
        }

        require(relative !in real) { "the relative lock line survived the substitution" }
        require(absoluteLine in real) { "the second-stage lock line is not absolute" }

        var out = contents.with(
            PatchContract.Paths.SECOND_STAGE_REAL,
            real.toByteArray(Charsets.UTF_8),
            BootstrapContents.OWNER_EXEC_MODE,
        )

        out = out.with(
            PatchContract.Paths.SECOND_STAGE,
            secondStageLauncher.toByteArray(Charsets.UTF_8),
            BootstrapContents.OWNER_EXEC_MODE,
        )

        return out
    }

    private fun dropFirstLines(text: String, count: Int): String {
        val lines = text.split("\n").let {
            if (it.lastOrNull() == "") it.dropLast(1) else it
        }
        return lines.drop(count).joinToString("\n")
    }

    private companion object {

        const val LOCK_LINE = "ln -s \"termux-bootstrap-second-stage.sh\""
    }

    fun rebuildTo(output: File, contents: BootstrapContents, targetSize: Long): Long {
        val entries = contents.entries.map { (name, data) ->
            FixedSizeZipWriter.Entry(name, data, contents.modeOf(name))
        }
        return FixedSizeZipWriter().buildTo(output, entries, targetSize)
    }

    fun read(zip: ByteArray): BootstrapContents = BootstrapZipReader.read(zip)
}
