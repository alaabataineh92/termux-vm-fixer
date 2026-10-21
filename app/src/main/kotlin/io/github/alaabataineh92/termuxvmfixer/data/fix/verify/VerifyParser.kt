package io.github.alaabataineh92.termuxvmfixer.data.fix.verify

import io.github.alaabataineh92.termuxvmfixer.core.model.AptUpdateOutcome
import io.github.alaabataineh92.termuxvmfixer.core.model.VerificationReport

class VerifyParser {

    fun parse(
        output: String,
        prefixDirs: Set<String>,
        shimsPresent: Set<String>,
        libraryPresent: Boolean,
    ): VerificationReport {
        val lines = output.lineSequence().map { it.trim() }.toList()

        return VerificationReport(
            loginHookLoaded = lines.any { it.startsWith(LOGIN_HOOK_LABEL) && it.endsWith("loaded") },
            aptUpdate = parseAptUpdate(lines),
            pendingUpgrades = intAfter(lines, PENDING_LABEL),
            brokenPackages = parseBrokenPackages(lines),
            brokenSymlinks = intAfter(lines, BROKEN_SYMLINKS_LABEL),
            libraryInstalled = libraryPresent,
            shimsInstalled = shimsPresent,
            prefixDirsPresent = prefixDirs,
        )
    }

    fun completed(output: String): Boolean =
        output.lineSequence().any { it.trim().startsWith(BROKEN_SYMLINKS_LABEL) }

    private fun parseAptUpdate(lines: List<String>): AptUpdateOutcome {
        val line = lines.firstOrNull { it.startsWith(APT_LABEL) }
            ?: return AptUpdateOutcome.Failed("the check did not reach the apt step")

        val verdict = line.removePrefix(APT_LABEL).trim()
        return when {
            verdict == "ok" -> AptUpdateOutcome.Ok
            verdict.startsWith("TIMED OUT") -> AptUpdateOutcome.TimedOut(APT_TIMEOUT_SECONDS)
            else -> AptUpdateOutcome.Failed(verdict.ifEmpty { "unknown failure" })
        }
    }

    private fun parseBrokenPackages(lines: List<String>): List<String> {
        val headerIndex = lines.indexOfFirst { it.startsWith(BROKEN_PACKAGES_LABEL) }
        if (headerIndex < 0) return emptyList()

        if (lines[headerIndex].removePrefix(BROKEN_PACKAGES_LABEL).trim() == "none") {
            return emptyList()
        }

        return lines.drop(headerIndex + 1)
            .takeWhile { line ->
                line.isNotEmpty() &&
                    !line.startsWith(BROKEN_SYMLINKS_LABEL) &&
                    !line.startsWith(PENDING_LABEL)
            }
            .filter { it.isNotEmpty() }
    }

    private fun intAfter(lines: List<String>, label: String): Int? =
        lines.firstOrNull { it.startsWith(label) }
            ?.removePrefix(label)
            ?.trim()
            ?.toIntOrNull()

    private companion object {
        const val LOGIN_HOOK_LABEL = "login hook:"
        const val APT_LABEL = "apt update:"
        const val PENDING_LABEL = "pending upgrades:"
        const val BROKEN_PACKAGES_LABEL = "broken packages:"
        const val BROKEN_SYMLINKS_LABEL = "broken symlinks:"

        const val APT_TIMEOUT_SECONDS = 300
    }
}
