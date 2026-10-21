package io.github.alaabataineh92.termuxvmfixer.core.model

enum class FixStep {
    CONNECT,
    OBTAIN_APK,
    BUILD_ARCHIVE,
    STOP_TERMUX,
    UPLOAD,
    REBUILD_PREFIX,
    INSTALL_LIBRARY,
    INSTALL_HOOKS,
    REPAIR_SYMLINKS,
    COMPLETE_PACKAGES,
    VERIFY,
}

sealed interface StepState {
    data object Pending : StepState
    data object Running : StepState

    data class Done(val note: String? = null) : StepState

    data class Failed(val reason: StepReason) : StepState

    data class Skipped(val reason: StepReason) : StepState
}

data class StepProgress(
    val step: FixStep,
    val state: StepState,
)

data class LogEntry(
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val timestampMillis: Long = System.currentTimeMillis(),
)

enum class LogLevel(val severity: Int) {
    TRACE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    ;

    fun isEnabledAt(threshold: LogLevel): Boolean = severity >= threshold.severity
}

data class FixProgress(
    val method: PrivilegeMethod,
    val steps: List<StepProgress>,
    val log: List<LogEntry> = emptyList(),
    val isRunning: Boolean = true,
    val isCancelled: Boolean = false,
) {
    val currentStep: FixStep?
        get() = steps.firstOrNull { it.state is StepState.Running }?.step

    val completedCount: Int
        get() = steps.count { it.state is StepState.Done || it.state is StepState.Skipped }

    val totalCount: Int get() = steps.size

    val smoothedFraction: Float
        get() {
            if (totalCount == 0) return 0f
            val running = steps.count { it.state is StepState.Running }
            return ((completedCount + running * 0.5f) / totalCount).coerceIn(0f, 1f)
        }
}
