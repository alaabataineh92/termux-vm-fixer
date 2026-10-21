package io.github.alaabataineh92.termuxvmfixer.core.model

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
) {
    val isSuccess: Boolean get() = !timedOut && exitCode == 0

    val combined: String get() = stdout + stderr

    fun isDefinite(): Boolean = !timedOut && (exitCode == 0 || exitCode == 1)

    companion object {
        fun timeout(): CommandResult =
            CommandResult(exitCode = -1, stdout = "", stderr = "", timedOut = true)
    }
}

enum class PathState { EXISTS, MISSING, UNKNOWN }

sealed class DeviceError(override val message: String) : Exception(message) {

    class SessionUnavailable(message: String) : DeviceError(message)

    class Precondition(message: String) : DeviceError(message)

    class InsufficientStorage(val neededBytes: Long, val availableBytes: Long) :
        DeviceError("needs ${neededBytes / 1024 / 1024} MB, only ${availableBytes / 1024 / 1024} MB free")
}
