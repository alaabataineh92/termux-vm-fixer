package io.github.alaabataineh92.termuxvmfixer.feature.home

import io.github.alaabataineh92.termuxvmfixer.core.device.apk.StoredApk
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.VersionedApk
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceProfile
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceState
import io.github.alaabataineh92.termuxvmfixer.core.model.DiscoveredVm
import io.github.alaabataineh92.termuxvmfixer.core.model.RankedMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.Target
import io.github.alaabataineh92.termuxvmfixer.core.model.VirtualMasterState

data class HomeUiState(
    val isLoading: Boolean = true,

    val target: Target = Target.LOCAL,

    val profile: DeviceProfile? = null,

    val device: DeviceState? = null,
    
    val localProfile: DeviceProfile? = null,

    val methods: List<RankedMethod> = emptyList(),

    val selected: ActionKind? = null,

    val availableStorageBytes: Long = 0L,
    val requiredStorageBytes: Long = 0L,

    val error: HomeError? = null,

    val showAdbAddressDialog: Boolean = false,

    val showDiscoveredVmsDialog: Boolean = false,

    val adbDiscovery: AdbDiscoveryState = AdbDiscoveryState.Idle,

    val showActionDialog: Boolean = false,

    val showPatchDialog: Boolean = false,

    val addressDialogForRemote: Boolean = false,

    val remoteHost: String = "",
    val remotePort: String = "",

    val isConnecting: Boolean = false,

    val remoteConnected: Boolean = false,

    val showPatchedApksDialog: Boolean = false,

    val showBackupsDialog: Boolean = false,

    val patchedApks: List<VersionedApk> = emptyList(),

    val backups: List<VersionedApk> = emptyList(),

    val pendingInstall: StoredApk? = null,

    val showInstallConfirm: Boolean = false,

    val isInstalling: Boolean = false,
) {

    val isRemote: Boolean get() = target == Target.REMOTE

    val options: List<ActionOption>
        get() {
            val current = profile ?: return emptyList()
            return buildOptions(
                profile = current,
                methods = methods,

                connected = !isRemote || remoteConnected,
            )
        }

    val selectedOption: ActionOption?
        get() = options.firstOrNull { it.kind == selected }

    val transportOptions: List<ActionOption>
        get() = options.filter { it.kind != ActionKind.PATCH }

    val hasStorageForRun: Boolean
        get() = availableStorageBytes >= requiredStorageBytes

    val storageWarningMb: StorageWarning?
        get() = if (!hasStorageForRun && requiredStorageBytes > 0) {
            StorageWarning(
                availableMb = (availableStorageBytes / MEGABYTE).toInt(),
                requiredMb = (requiredStorageBytes / MEGABYTE).toInt(),
            )
        } else {
            null
        }

    val unsupportedAbi: String?
        get() = if (isRemote) null else device?.takeIf { !it.isSupportedAbi }?.abi

    val isInsideVm: Boolean
        get() = localProfile?.isVirtualMaster
            ?: (device?.virtualMaster is VirtualMasterState.Inside)

    val canInstallOnVm: Boolean
        get() = isInsideVm || (isRemote && remoteConnected)

    private companion object {
        const val MEGABYTE = 1024L * 1024L
    }
}

data class StorageWarning(val availableMb: Int, val requiredMb: Int)

sealed interface AdbDiscoveryState {

    data object Idle : AdbDiscoveryState

    data object Searching : AdbDiscoveryState

    data class Found(val vms: List<DiscoveredVm>) : AdbDiscoveryState

    data object NotFound : AdbDiscoveryState
}

sealed interface HomeError {
    data class ReadState(val detail: String?) : HomeError
    data class Connect(val host: String, val port: Int, val detail: String?) : HomeError

    data object ConnectionClosed : HomeError
    data class InspectVm(val detail: String?) : HomeError

    data object AdbTransportUnavailable : HomeError

    data class RemoteNotAVm(val address: String) : HomeError
}
