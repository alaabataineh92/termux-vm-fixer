package io.github.alaabataineh92.termuxvmfixer.core.model

data class VerificationReport(
    val loginHookLoaded: Boolean,
    val aptUpdate: AptUpdateOutcome,
    val pendingUpgrades: Int?,
    val brokenPackages: List<String>,
    val brokenSymlinks: Int?,
    val libraryInstalled: Boolean,
    val shimsInstalled: Set<String>,
    val prefixDirsPresent: Set<String>,
) {
    val isSuccess: Boolean
        get() = loginHookLoaded &&
            aptUpdate is AptUpdateOutcome.Ok &&
            brokenPackages.isEmpty() &&
            brokenSymlinks == 0 &&
            libraryInstalled &&
            shimsInstalled.size == TermuxPaths.SHIMS.size &&
            prefixDirsPresent.containsAll(TermuxPaths.REQUIRED_DIRS)

    val problems: List<VerifyProblem>
        get() = buildList {
            if (brokenSymlinks == null) {
                add(VerifyProblem.CheckIncomplete)
            } else {
                if (!loginHookLoaded) {
                    add(VerifyProblem.LoginHookNotLoaded)
                }
                when (val apt = aptUpdate) {
                    is AptUpdateOutcome.Ok -> Unit
                    is AptUpdateOutcome.Failed -> add(VerifyProblem.AptFailed(apt.reason))
                    is AptUpdateOutcome.TimedOut -> add(VerifyProblem.AptTimedOut(apt.seconds))
                }
                if (brokenSymlinks > 0) {
                    add(VerifyProblem.BrokenSymlinks(brokenSymlinks))
                }
            }
            if (!libraryInstalled) add(VerifyProblem.LibraryMissing)
            TermuxPaths.SHIMS.forEach { shim ->
                if (shim !in shimsInstalled) add(VerifyProblem.ShimMissing(shim))
            }
            TermuxPaths.REQUIRED_DIRS.forEach { dir ->
                if (dir !in prefixDirsPresent) add(VerifyProblem.PrefixDirMissing(dir))
            }
            if (brokenPackages.isNotEmpty()) {
                add(VerifyProblem.PackagesUnconfigured(brokenPackages))
            }
        }
}

sealed interface AptUpdateOutcome {
    data object Ok : AptUpdateOutcome

    data class Failed(val reason: String) : AptUpdateOutcome

    data class TimedOut(val seconds: Int) : AptUpdateOutcome
}

sealed interface FixResult {
    data class Success(val report: VerificationReport) : FixResult

    data class Incomplete(
        val problems: List<StepReason>,
        val report: VerificationReport?,
        val lastError: String?,
    ) : FixResult

    data class TransportFailed(val error: DeviceError) : FixResult

    data object Cancelled : FixResult
}
