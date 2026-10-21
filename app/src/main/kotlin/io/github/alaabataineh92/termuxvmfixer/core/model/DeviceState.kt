package io.github.alaabataineh92.termuxvmfixer.core.model

data class DeviceState(
    val androidRelease: String,
    val sdkInt: Int,
    val abi: String,
    val toyboxVersion: String?,
    val termux: TermuxState,

    val virtualMaster: VirtualMasterState = VirtualMasterState.NotVirtualMaster,
) {
    val isSupportedAbi: Boolean get() = abi == "arm64-v8a"
}

enum class PatchState {
    PATCHED,
    NOT_PATCHED,
    UNKNOWN,
}

data class TermuxState(
    val installed: Boolean,
    val versionName: String?,
    val versionCode: Long?,
    val isDebuggable: Boolean,
    val prefixPresent: Boolean,
    val missingDirs: List<String>,
    val patch: PatchState = PatchState.UNKNOWN,
) {
    val isFixable: Boolean get() = installed && isDebuggable
}

sealed interface VirtualMasterState {
    data object NotVirtualMaster : VirtualMasterState
    data class Inside(val slot: String?) : VirtualMasterState
}
