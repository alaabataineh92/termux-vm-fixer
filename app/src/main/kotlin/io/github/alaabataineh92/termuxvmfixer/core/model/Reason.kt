package io.github.alaabataineh92.termuxvmfixer.core.model

sealed interface BlockingIssue {
    data class NotAVm(val remote: Boolean, val address: String?) : BlockingIssue
    data object TermuxAbsent : BlockingIssue
    data object NoRouteToPrefix : BlockingIssue
    data object AlreadyFixed : BlockingIssue
}

sealed interface Reason {
    data object Disconnected : Reason
    data object RootUnprobed : Reason
    data object AdbUnprobed : Reason
    data object RootNoTermux : Reason
    data object RootUnreachable : Reason
    data object RootAbsent : Reason
    data object SuAbsent : Reason
    data object AdbNoTermux : Reason
    data object AdbNotDebuggableWithRoot : Reason
    data object AdbNotDebuggable : Reason
    data object AdbUnreachable : Reason
    data object PatchNotAVm : Reason
    data object PatchUnreadableTermux : Reason
    data object RootTimedOut : Reason
    data object RootRefused : Reason
    data class AdbWrongPort(val port: Int) : Reason
    data object AdbNothingListening : Reason
    data class ProbeFailed(val cause: String?) : Reason
}

enum class SetupStep {
    ENTER_VM_ADDRESS,
    CHECK_ROOT_AGAIN,
    CHECK_ADB_AGAIN,
    ENABLE_DEVELOPER_OPTIONS,
    RUN_ADB_TCPIP,
    RETURN_AND_CHECK,
    ALLOW_ROOT_ON_SCREEN,
    GRANT_ROOT_ACCESS,
}

sealed interface TransportNote {
    data class AdbFound(
        val address: String?,
        val configuredByUser: Boolean = false,
    ) : TransportNote

    data class SuInstalled(val remote: Boolean = false) : TransportNote
    data class SuAbsent(val remote: Boolean = false) : TransportNote

    data object AdbRemoteNotDebuggable : TransportNote

    data object AdbTransportUnavailable : TransportNote
}

sealed interface VerifyProblem {

    data object CheckIncomplete : VerifyProblem

    data object LoginHookNotLoaded : VerifyProblem
    data class AptFailed(val reason: String) : VerifyProblem
    data class AptTimedOut(val seconds: Int) : VerifyProblem
    data class BrokenSymlinks(val count: Int) : VerifyProblem

    data object LibraryMissing : VerifyProblem
    data class ShimMissing(val name: String) : VerifyProblem
    data class PrefixDirMissing(val dir: String) : VerifyProblem
    data class PackagesUnconfigured(val names: List<String>) : VerifyProblem
}

sealed interface StepReason {
    data class PrefixIncomplete(val missing: List<String>) : StepReason
    data object NoRoot : StepReason
    data object BootstrapIncomplete : StepReason
    data object RunFailed : StepReason
    data class TransportUnavailable(val detail: String?) : StepReason
    data class Problem(val problem: VerifyProblem) : StepReason
}
