package io.github.alaabataineh92.termuxvmfixer.feature.home

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.device.DeviceInspector
import io.github.alaabataineh92.termuxvmfixer.core.device.LibraryAsset
import io.github.alaabataineh92.termuxvmfixer.core.device.RemoteInspector
import io.github.alaabataineh92.termuxvmfixer.core.device.VmNameReader
import io.github.alaabataineh92.termuxvmfixer.core.device.WorkspaceManager
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.ApkInstaller
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.ApkPatchRepository
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.StoredApk
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceProfile
import io.github.alaabataineh92.termuxvmfixer.core.model.DiscoveredVm
import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.ProviderStatus
import io.github.alaabataineh92.termuxvmfixer.core.model.RankedMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.Reason
import io.github.alaabataineh92.termuxvmfixer.core.model.Target
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import io.github.alaabataineh92.termuxvmfixer.core.model.TransportNote
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbEndpoint
import io.github.alaabataineh92.termuxvmfixer.data.privilege.provider.DirectAdbSessionProvider
import io.github.alaabataineh92.termuxvmfixer.data.privilege.registry.PrivilegeRegistry
import io.github.alaabataineh92.termuxvmfixer.data.privilege.remote.RemoteHostConnection
import io.github.alaabataineh92.termuxvmfixer.data.privilege.remote.RemoteInstall
import io.github.alaabataineh92.termuxvmfixer.data.privilege.remote.RemoteVmDiscovery
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.time.Duration

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val inspector: DeviceInspector,
    private val registry: PrivilegeRegistry,
    private val workspace: WorkspaceManager,
    private val remote: RemoteHostConnection,
    private val vmDiscovery: RemoteVmDiscovery,
    private val apks: ApkPatchRepository,
    private val remoteInstall: RemoteInstall,
    private val library: LibraryAsset,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var localVmNameSlot: String? = null
    private var localVmName: String? = null

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.isRemote) {

            if (remote.isConnected) {
                viewModelScope.launch { inspectRemote() }
            } else {
                refreshLocal()
            }
            return
        }
        refreshLocal()
    }

    private fun refreshLocal() {
        _state.update {
            it.copy(isLoading = true, error = null, target = Target.LOCAL)
        }

        viewModelScope.launch {
            runCatching {

                val device = inspector.inspect()
                val profile = inspector.inspectProfile(device)
                    .withLocalVmName()
                val methods = registry.rank()

                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        target = Target.LOCAL,
                        profile = profile,
                        device = device,
                        localProfile = profile,
                        methods = methods,
                        selected = pickSelected(current.selected, profile, methods),
                        availableStorageBytes = workspace.availableBytes(),
                        requiredStorageBytes = workspace.requiredBytes,
                        remoteConnected = false,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = HomeError.ReadState(error.message),
                    )
                }
            }
        }
    }

    fun openAdbAddressDialog() {
        _state.update {
            it.copy(
                showAdbAddressDialog = true,
                showDiscoveredVmsDialog = false,
                addressDialogForRemote = true,
                adbDiscovery = AdbDiscoveryState.Idle,
            )
        }
    }

    fun openLocalAdbAddressDialog() {
        _state.update {
            it.copy(
                showAdbAddressDialog = true,
                showDiscoveredVmsDialog = false,
                addressDialogForRemote = false,
                adbDiscovery = AdbDiscoveryState.Idle,
            )
        }
    }

    fun dismissAdbAddressDialog() {
        _state.update {
            it.copy(
                showAdbAddressDialog = false,
                showDiscoveredVmsDialog = false,
                adbDiscovery = AdbDiscoveryState.Idle,
            )
        }
    }

    fun autoDiscoverAdb(hostHint: String) {
        if (_state.value.adbDiscovery is AdbDiscoveryState.Searching) return

        _state.update {
            it.copy(
                adbDiscovery = AdbDiscoveryState.Searching,
                showDiscoveredVmsDialog = true,
                remoteHost = hostHint.trim().ifBlank { "127.0.0.1" },
            )
        }

        viewModelScope.launch {
            val vms = runCatching {
                vmDiscovery.discover(hostHint)
            }.getOrDefault(emptyList())

            _state.update {
                it.copy(
                    adbDiscovery = if (vms.isEmpty()) {
                        AdbDiscoveryState.NotFound
                    } else {
                        AdbDiscoveryState.Found(vms)
                    },
                )
            }
        }
    }

    fun openDiscoveredVmsDialog() {
        if (_state.value.adbDiscovery !is AdbDiscoveryState.Found) return
        _state.update { it.copy(showDiscoveredVmsDialog = true) }
    }

    fun dismissDiscoveredVmsDialog() {
        _state.update { it.copy(showDiscoveredVmsDialog = false) }
    }

    fun selectDiscoveredVm(vm: DiscoveredVm) {
        val connectRemotely = _state.value.addressDialogForRemote

        _state.update { it.copy(showDiscoveredVmsDialog = false) }

        if (connectRemotely) {
            connectRemote(vm.host, vm.port)
        } else {
            setAdbAddress(vm.host, vm.port)
        }
    }

    fun connectRemote(host: String, port: Int) {
        _state.update { current ->
            current.copy(
                showAdbAddressDialog = false,
                showDiscoveredVmsDialog = false,
                adbDiscovery = AdbDiscoveryState.Idle,
                target = Target.REMOTE,
                localProfile = current.localProfile ?: current.profile?.takeIf { profile ->
                    profile.target == Target.LOCAL
                },
                remoteHost = host,
                remotePort = port.toString(),
                isConnecting = true,
                error = null,
            )
        }

        viewModelScope.launch {
            val result = remote.connect(host, port)

            result.onFailure { e ->
                _state.update {
                    it.copy(
                        isConnecting = false,
                        remoteConnected = false,
                        profile = null,
                        error = HomeError.Connect(host, port, e.message),
                    )
                }
            }

            if (result.isSuccess) {

                inspectRemote()
            }
        }
    }

    private suspend fun inspectRemote() {
        val session = remote.current()

        if (session == null) {
            _state.update {
                it.copy(
                    isConnecting = false,
                    remoteConnected = false,
                    profile = null,
                    error = HomeError.ConnectionClosed,
                )
            }
            return
        }

        val address = remote.address ?: "${_state.value.remoteHost}:${_state.value.remotePort}"

        runCatching {
            withContext(Dispatchers.IO) {
                RemoteInspector(session, address, library.size).inspect()
            }
        }.onSuccess { profile ->

            if (!profile.isVirtualMaster) {
                remote.close()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isConnecting = false,
                        remoteConnected = false,
                        target = Target.LOCAL,
                        profile = null,
                        error = HomeError.RemoteNotAVm(address),
                    )
                }
                refreshLocal()
                return
            }

            val methods = remoteMethods(profile)
            val localSnapshot = if (
                _state.value.device == null ||
                _state.value.localProfile == null
            ) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val device = _state.value.device ?: inspector.inspect()
                        val localProfile = _state.value.localProfile
                            ?: inspector.inspectProfile(device)
                        device to localProfile
                    }
                }.getOrNull()
            } else {
                null
            }

            _state.update { current ->
                current.copy(
                    isLoading = false,
                    isConnecting = false,
                    remoteConnected = true,
                    target = Target.REMOTE,
                    profile = profile,
                    device = localSnapshot?.first ?: current.device,
                    localProfile = localSnapshot?.second ?: current.localProfile,

                    methods = methods,
                    selected = pickSelected(current.selected, profile, methods),
                    availableStorageBytes = workspace.availableBytes(),
                    requiredStorageBytes = workspace.requiredBytes,
                )
            }
        }.onFailure { e ->
            _state.update {
                it.copy(
                    isLoading = false,
                    isConnecting = false,
                    remoteConnected = false,
                    profile = null,
                    error = HomeError.InspectVm(e.message),
                )
            }
        }
    }

    private fun remoteMethods(profile: DeviceProfile): List<RankedMethod> = buildList {
        add(
            RankedMethod(
                method = PrivilegeMethod.ROOT,
                status = if (profile.hasRoot) {
                    ProviderStatus.Available(note = TransportNote.SuInstalled(remote = true))
                } else {
                    ProviderStatus.Unavailable(Reason.SuAbsent)
                },
                rank = 100,
            ),
        )
        add(
            RankedMethod(
                method = PrivilegeMethod.DIRECT_ADB,
                status = if (profile.termux.isDebuggable) {
                    ProviderStatus.Available(
                        note = TransportNote.AdbFound(
                            address = profile.address,

                            configuredByUser = true,
                        ),
                    )
                } else {
                    ProviderStatus.Unavailable(Reason.AdbNotDebuggable)
                },
                rank = 95,
            ),
        )
    }.sorted()

    fun disconnectRemote() {
        remote.close()
        _state.update {
            it.copy(
                target = Target.LOCAL,
                remoteConnected = false,
                isConnecting = false,
                profile = null,
                methods = emptyList(),
                selected = null,
            )
        }
        refreshLocal()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun openPatchedApks() {
        _state.update { it.copy(showPatchedApksDialog = true) }
        loadPatchedApks()
    }

    fun dismissPatchedApks() {
        _state.update { it.copy(showPatchedApksDialog = false) }
    }

    fun openBackups() {
        _state.update { it.copy(showBackupsDialog = true) }
        loadBackups()
    }

    fun dismissBackups() {
        _state.update { it.copy(showBackupsDialog = false) }
    }

    private fun loadPatchedApks() {
        viewModelScope.launch {
            val patched = runCatching { apks.patchedApks(STORAGE) }.getOrDefault(emptyList())
            _state.update { it.copy(patchedApks = patched) }
        }
    }

    private fun loadBackups() {
        viewModelScope.launch {
            val backups = runCatching { apks.backupApks(STORAGE) }.getOrDefault(emptyList())
            _state.update { it.copy(backups = backups) }
        }
    }

    fun shareApk(apk: StoredApk) {
        viewModelScope.launch {
            runCatching { apks.shareApk(STORAGE, apk) }
        }
    }

    fun deleteApk(apk: StoredApk) {
        viewModelScope.launch {
            apks.deleteResult(STORAGE, apk)
            loadPatchedApks()
            loadBackups()
        }
    }

    fun requestInstall(apk: StoredApk) {
        _state.update {
            it.copy(
                pendingInstall = apk,
                showInstallConfirm = true,
            )
        }
    }

    fun dismissInstallConfirm() {
        _state.update {
            it.copy(
                showInstallConfirm = false,
                pendingInstall = null,
            )
        }
    }

    fun confirmInstall() {
        val apk = _state.value.pendingInstall ?: return

        _state.update {
            it.copy(
                showInstallConfirm = false,
                pendingInstall = null,
                isInstalling = true,
            )
        }

        viewModelScope.launch {
            val message = if (_state.value.isRemote && remote.isConnected) {
                installOnRemoteVm(apk)
            } else {
                installOnLocalVm(apk)
            }

            _state.update { it.copy(isInstalling = false) }
            toast(message)
            loadPatchedApks()
            loadBackups()
        }
    }

    private suspend fun installOnRemoteVm(apk: StoredApk): String {
        val session = remote.current()
            ?: return context.getString(R.string.vmfix_home_install_no_vm)

        val removed = runCatching {
            session.shell(
                "pm uninstall ${TermuxPaths.PACKAGE}",
                Duration.ofMinutes(2),
            )
        }.getOrNull()

        val output = removed?.combined?.trim().orEmpty()
        val gone = removed != null &&
            removed.isSuccess &&
            (output.contains("Success") || output.contains("not installed"))

        if (!gone) {
            return context.getString(
                R.string.vmfix_home_install_failed,
                output.take(160).ifBlank { null } ?: "",
            )
        }

        val file = runCatching { apks.materialise(STORAGE, apk) }.getOrNull()
            ?: return context.getString(R.string.vmfix_home_install_failed, "")

        return when (val outcome = remoteInstall.install(session, file)) {
            RemoteInstall.Result.Success ->
                context.getString(R.string.vmfix_home_install_done)

            is RemoteInstall.Result.Failed -> context.getString(
                R.string.vmfix_home_install_failed,
                outcome.detail.orEmpty(),
            )
        }
    }

    private suspend fun installOnLocalVm(apk: StoredApk): String {
        val session = registry.provider(PrivilegeMethod.ROOT)?.open()?.getOrNull()

        if (session != null) {
            runCatching {
                session.shell(
                    "pm uninstall ${TermuxPaths.PACKAGE}",
                    Duration.ofMinutes(2),
                )
            }
        }

        return when (val outcome = apks.install(STORAGE, apk)) {
            is ApkInstaller.Result.Success ->
                context.getString(R.string.vmfix_home_install_done)

            is ApkInstaller.Result.Failed -> context.getString(
                R.string.vmfix_home_install_failed,
                outcome.detail.orEmpty(),
            )

            ApkInstaller.Result.AwaitingUser ->
                context.getString(R.string.vmfix_home_install_waiting)
        }
    }

    private fun toast(message: String) {
        if (message.isBlank()) return
        runCatching {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun select(kind: ActionKind) {
        _state.update { it.copy(selected = kind) }
    }

    fun openActionDialog() {
        _state.update { it.copy(showActionDialog = true) }
    }

    fun dismissActionDialog() {
        _state.update { it.copy(showActionDialog = false) }
    }

    fun openPatchDialog() {
        _state.update { it.copy(showPatchDialog = true) }
    }

    fun dismissPatchDialog() {
        _state.update { it.copy(showPatchDialog = false) }
    }

    fun setAdbAddress(host: String, port: Int) {
        val provider = registry.provider(PrivilegeMethod.DIRECT_ADB)
            as? DirectAdbSessionProvider

        if (provider == null) {
            _state.update {
                it.copy(
                    showAdbAddressDialog = false,
                    showDiscoveredVmsDialog = false,
                    adbDiscovery = AdbDiscoveryState.Idle,
                    error = HomeError.AdbTransportUnavailable,
                )
            }
            return
        }

        provider.manualEndpoint = AdbEndpoint(
            host = host,
            port = port,
            source = AdbEndpoint.Source.MANUAL,
        )

        _state.update {
            it.copy(
                showAdbAddressDialog = false,
                showDiscoveredVmsDialog = false,
                adbDiscovery = AdbDiscoveryState.Idle,
            )
        }
        recheckMethods()
    }

    fun recheckMethods() {
        if (_state.value.isRemote) {
            viewModelScope.launch { inspectRemote() }
            return
        }

        viewModelScope.launch {
            val methods = runCatching { registry.rank() }.getOrDefault(emptyList())
            val profile = _state.value.profile

            _state.update { current ->
                current.copy(
                    methods = methods,
                    selected = if (profile != null) {
                        pickSelected(current.selected, profile, methods)
                    } else {
                        current.selected
                    },
                )
            }
        }
    }

    private suspend fun DeviceProfile.withLocalVmName(): DeviceProfile {
        if (!isVirtualMaster) return this

        val currentSlot = slot ?: return this

        if (localVmNameSlot == currentSlot) {
            val cached = localVmName ?: return this

            return copy(vmName = cached)
        }

        val name = withContext(Dispatchers.IO) {
            VmNameReader.readLocal(slot = currentSlot)
        } ?: return this

        localVmNameSlot = currentSlot
        localVmName = name

        return copy(vmName = name)
    }

    private fun pickSelected(
        current: ActionKind?,
        profile: DeviceProfile,
        methods: List<RankedMethod>,
    ): ActionKind? {
        val usable = buildOptions(profile, methods)
            .filter { it.selectable && it.kind != ActionKind.PATCH }
            .map { it.kind }

        return current?.takeIf { it in usable } ?: usable.firstOrNull()
    }

    private companion object {
        const val STORAGE = "private"
    }
}
