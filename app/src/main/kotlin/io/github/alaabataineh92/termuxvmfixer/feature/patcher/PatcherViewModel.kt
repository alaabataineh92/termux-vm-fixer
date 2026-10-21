package io.github.alaabataineh92.termuxvmfixer.feature.patcher

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.device.DeviceInspector
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.ApkInstaller
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.ApkPatchRepository
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.ApkStorage
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.SigningKeyStore
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.StoredApk
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel
import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import io.github.alaabataineh92.termuxvmfixer.core.model.VirtualMasterState
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchEvent
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchSource
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchStepState
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.SigningIdentity
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.certificateDigest
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.DadbDeviceSession
import io.github.alaabataineh92.termuxvmfixer.data.privilege.registry.PrivilegeRegistry
import io.github.alaabataineh92.termuxvmfixer.data.privilege.remote.RemoteHostConnection
import io.github.alaabataineh92.termuxvmfixer.data.privilege.remote.RemoteInstall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class PatcherViewModel @Inject constructor(
    private val repository: ApkPatchRepository,
    private val remoteConnection: RemoteHostConnection,
    private val remoteInstall: RemoteInstall,
    private val inspector: DeviceInspector,
    private val privilegeRegistry: PrivilegeRegistry,
    private val keyStore: SigningKeyStore,
    logSink: LogSink = NoOpLogSink,
) : ViewModel() {

    var isRemote: Boolean = false
        private set

    fun bindTarget(remote: Boolean) {
        isRemote = remote

        if (remote) {

            val session = remoteConnection.current()
            _state.update {
                it.copy(
                    isRemote = true,
                    remoteAddress = remoteConnection.address,
                    remoteConnected = session != null,
                    isLoading = session != null,
                )
            }

            viewModelScope.launch {
                val insideVm = withContext(Dispatchers.IO) {
                    runCatching {
                        inspector.inspect().virtualMaster is VirtualMasterState.Inside
                    }.getOrDefault(false)
                }

                _state.update { it.copy(isInsideVm = insideVm) }
            }

            loadVmSource(session)

            loadDeviceSource()
            return
        }

        _state.update {
            it.copy(
                isRemote = false,
                remoteConnected = false,
                remoteAddress = null,
                vmInstalledAvailable = false,
                vmInstalledVersion = null,
                vmTermuxInstalled = false,
            )
        }

        refresh()
    }

    private fun loadVmSource(session: DeviceSession?) {
        if (session == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    vmInstalledAvailable = false,
                    vmInstalledVersion = null,
                    vmTermuxInstalled = false,
                )
            }
            return
        }

        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                runCatching { remoteInstall.isTermuxInstalled(session) }.getOrDefault(false)
            }

            val version = if (installed) {
                withContext(Dispatchers.IO) {
                    runCatching { remoteInstall.termuxVersion(session) }.getOrNull()
                }
            } else {
                null
            }

            val signer = if (installed) {
                withContext(Dispatchers.IO) {
                    runCatching { remoteInstall.termuxSignerDigest(session) }.getOrNull()
                }
            } else {
                null
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    vmTermuxInstalled = installed,
                    vmInstalledAvailable = installed,
                    vmInstalledVersion = version,
                    installedSignerDigest = signer,
                )
            }
        }
    }

    private fun loadDeviceSource() {
        viewModelScope.launch {
            val available = withContext(Dispatchers.IO) {
                runCatching { repository.installedSource() != null }.getOrDefault(false)
            }
            val version = withContext(Dispatchers.IO) {
                runCatching { repository.installedVersion() }.getOrNull()
            }
            val signer = withContext(Dispatchers.IO) {
                runCatching {
                    repository.installedSignerDigest(TermuxPaths.PACKAGE)
                }.getOrNull()
            }

            _state.update { current ->
                current.copy(
                    deviceInstalledAvailable = available,
                    deviceInstalledVersion = version,
                    deviceTermuxInstalled = available,
                    installedSignerDigest = signer,
                    sourceKind = current.sourceKind.resolveDefault(current, available),
                )
            }
        }
    }

    private fun SourceKind.resolveDefault(
        current: PatcherUiState,
        deviceAvailable: Boolean,
    ): SourceKind = when (this) {
        SourceKind.PICKED -> this

        SourceKind.VM_INSTALLED ->
            if (current.vmInstalledAvailable) this else SourceKind.DEVICE_INSTALLED

        SourceKind.DEVICE_INSTALLED ->
            if (deviceAvailable) {
                this
            } else if (current.vmInstalledAvailable) {
                SourceKind.VM_INSTALLED
            } else {
                SourceKind.PICKED
            }
    }

    private val logger = logSink.logger(TAG)

    private val _state = MutableStateFlow(PatcherUiState())
    val state: StateFlow<PatcherUiState> = _state.asStateFlow()

    private var runJob: Job? = null

    private var pendingKeystore: Uri? = null
    private var userIdentity: SigningIdentity? = null

    private var pickedSource: PatchSource.Picked? = null

    val storages: List<ApkStorage> get() = repository.storages

    init {
        refresh()
        loadTermuxKeyDigest()

        viewModelScope.launch {
            remoteConnection.connected.collect { connected ->
                if (isRemote) {
                    loadVmSource(remoteConnection.current())
                } else if (!connected) {
                    _state.update {
                        it.copy(
                            remoteConnected = false,
                            vmInstalledAvailable = false,
                            vmInstalledVersion = null,
                            vmTermuxInstalled = false,
                        )
                    }
                }
            }
        }
    }

    private fun loadTermuxKeyDigest() {
        viewModelScope.launch {
            val digest = withContext(Dispatchers.IO) {
                runCatching { keyStore.termuxKeyDigest() }.getOrNull()
            }

            _state.update { it.copy(termuxKeyDigest = digest) }
        }
    }

    fun refresh() {
        _state.update {
            it.copy(
                isLoading = true,
                deviceInstalledAvailable = repository.installedSource() != null,
                deviceInstalledVersion = repository.installedVersion(),
                deviceTermuxInstalled = repository.isTermuxInstalled(),
                canInstallPackages = repository.canInstall(),
                storageId = it.storageId.ifBlank { "private" },
            )
        }
        viewModelScope.launch {
            val backups = runCatching { repository.backups(_state.value.storageId) }
                .getOrDefault(emptyList())

            val insideVm = withContext(Dispatchers.IO) {
                runCatching {
                    inspector.inspect().virtualMaster is VirtualMasterState.Inside
                }.getOrDefault(false)
            }

            _state.update {
                it.copy(isLoading = false, backups = backups, isInsideVm = insideVm)
            }
        }
    }

    fun refreshInstallPermission() {
        viewModelScope.launch {
            val allowed = withContext(Dispatchers.IO) {
                runCatching { repository.canInstall() }.getOrDefault(false)
            }

            _state.update { it.copy(canInstallPackages = allowed) }
        }
    }

    fun selectDeviceInstalled() {
        _state.update {
            it.copy(
                sourceKind = SourceKind.DEVICE_INSTALLED,
                error = null,
                errorDetail = null,
            )
        }
    }

    fun selectVmInstalled() {
        _state.update {
            it.copy(
                sourceKind = SourceKind.VM_INSTALLED,
                error = null,
                errorDetail = null,
            )
        }
    }

    fun selectPicked(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            try {
                val picked = repository.importPicked(uri, displayName)
                pickedSource = picked
                logger.info(TAG) { "picked ${picked.apk.name} (${picked.apk.length()} bytes)" }
                _state.update {
                    it.copy(
                        sourceKind = SourceKind.PICKED,
                        pickedName = picked.displayName ?: picked.apk.name,
                        pickedAvailable = true,
                        error = null,
                        errorDetail = null,
                    )
                }
            } catch (e: Throwable) {
                logger.error(TAG, e) { "the picked APK could not be imported" }
                _state.update {
                    it.copy(
                        sourceKind = SourceKind.PICKED,
                        error = PatcherError.PICKED_UNREADABLE,

                        errorDetail = e.message,
                    )
                }
            }
        }
    }

    fun setBackUp(enabled: Boolean) {
        _state.update { it.copy(backUp = enabled) }
    }

    fun setStorage(id: String) {
        _state.update { it.copy(storageId = id) }
        viewModelScope.launch {
            val backups = runCatching { repository.backups(id) }.getOrDefault(emptyList())
            _state.update { it.copy(backups = backups) }
        }
    }

    fun setSigningMode(mode: SigningMode) {
        _state.update { it.copy(signingMode = mode) }
    }

    fun openKeystoreDialog() {
        _state.update { it.copy(showKeystoreDialog = true) }
    }

    fun dismissKeystoreDialog() {
        _state.update { it.copy(showKeystoreDialog = false) }
    }

    fun setKeystoreFile(uri: Uri) {
        pendingKeystore = uri
    }

    fun unlockKeystore(alias: String, password: String) {
        val uri = pendingKeystore ?: return

        viewModelScope.launch {
            try {
                val identity = repository.userIdentity(uri, alias, password, null)
                userIdentity = identity
                _state.update {
                    it.copy(
                        signingMode = SigningMode.OWN_KEYSTORE,
                        showKeystoreDialog = false,
                        error = null,
                        errorDetail = null,
                        userKeyDigest = certificateDigest(identity.certificates.first()),
                    )
                }
                logger.info(TAG) { "keystore unlocked: ${identity.label}" }
            } catch (e: Throwable) {
                logger.error(TAG, e) { "the keystore could not be unlocked" }
                _state.update {
                    it.copy(
                        error = PatcherError.KEYSTORE_OPEN,
                        errorDetail = e.message,
                    )
                }
            }
        }
    }

    fun patch() {
        if (runJob?.isActive == true) return

        val current = _state.value

        _state.update {
            it.copy(
                isRunning = true,
                error = null,
                errorDetail = null,
                result = null,
                steps = emptyList(),
                log = emptyList(),
            )
        }

        runJob = viewModelScope.launch {
            try {
                val identity = when (current.signingMode) {
                    SigningMode.OWN_KEYSTORE -> userIdentity
                    SigningMode.TERMUX -> keyStore.termuxIdentity()
                    SigningMode.GENERATED -> repository.defaultIdentity()
                }

                if (identity == null) {
                    _state.update {
                        it.copy(
                            isRunning = false,
                            error = PatcherError.NO_KEYSTORE,
                            errorDetail = null,
                        )
                    }
                    return@launch
                }

                val actualSource = resolveSource(current)
                if (actualSource == null) {
                    _state.update {
                        it.copy(
                            isRunning = false,
                            error = if (current.sourceKind == SourceKind.PICKED) {
                                PatcherError.NO_APK
                            } else {
                                PatcherError.INSTALLED_UNREADABLE
                            },
                            errorDetail = null,
                        )
                    }
                    return@launch
                }

                val outcome = repository.patch(
                    source = actualSource,
                    storageId = current.storageId,
                    backUp = current.backUp,
                    identity = identity,
                    onEvent = ::onPatchEvent,
                )

                when (outcome) {
                    is ApkPatchRepository.PatchOutcome.Success -> {
                        logger.info(TAG) {
                            "patched to ${outcome.stored.location} (${outcome.stored.sizeLabel})"
                        }
                        _state.update {
                            it.copy(
                                isRunning = false,
                                result = PatcherUiState.PatchSuccess(
                                    apk = outcome.stored,
                                    backup = outcome.backup,
                                    report = outcome.report,
                                    signing = outcome.signingIdentity.asLabel(),
                                ),
                            )
                        }
                    }

                    is ApkPatchRepository.PatchOutcome.Failed -> {
                        logger.error(TAG) { "patch failed: ${outcome.reason}" }
                        _state.update {
                            it.copy(
                                isRunning = false,
                                error = outcome.reason.asPatcherError(),

                                errorDetail = outcome.detail,
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(isRunning = false) }
                throw e
            } catch (e: Throwable) {
                logger.error(TAG, e) { "the patch run failed" }
                _state.update {
                    it.copy(
                        isRunning = false,
                        error = PatcherError.PATCH_FAILED,
                        errorDetail = e.message,
                    )
                }
            }
        }
    }

    private suspend fun resolveSource(current: PatcherUiState): PatchSource? =
        when (current.sourceKind) {
            SourceKind.DEVICE_INSTALLED -> repository.installedSource()

            SourceKind.VM_INSTALLED -> pullVmApk()

            SourceKind.PICKED -> pickedSource
        }

    private suspend fun pullVmApk(): PatchSource.Picked? {
        val session = remoteConnection.current() ?: return null

        val file = withContext(Dispatchers.IO) {
            runCatching {
                repository.pullRemoteApk(
                    session = session,
                    fastPuller = (session as? DadbDeviceSession)?.let { dadb ->
                        { remotePath, destination ->
                            dadb.pullBinary(remotePath, destination)
                        }
                    },
                    fileName = "termux-from-vm.apk",
                )
            }.getOrNull()
        } ?: return null

        return PatchSource.Picked(file, "Termux on the VM")
    }

    private fun onPatchEvent(event: PatchEvent) {

        val line = io.github.alaabataineh92.termuxvmfixer.feature.fix.MonitorLogLine(
            message = event.step.name + ": " + event.state.name.lowercase() +
                (event.detail?.let { " - $it" } ?: ""),
            level = if (event.state == PatchStepState.FAILED) LogLevel.ERROR else LogLevel.INFO,
            timestampMillis = System.currentTimeMillis(),
        )

        _state.update { current ->
            val steps = current.steps.toMutableList()

            val index = steps.indexOfFirst { it.step == event.step }
            val entry = PatcherUiState.StepState(event.step, event.state, event.detail)
            if (index >= 0) steps[index] = entry else steps += entry

            current.copy(steps = steps, log = current.log + line)
        }

        if (event.state == PatchStepState.FAILED) {
            logger.error(TAG) { "${event.step.name} failed: ${event.detail}" }
        } else {
            logger.logIf(LogLevel.DEBUG, TAG) {
                "${event.step.name}: ${event.state.name}" +
                    (event.detail?.let { " - $it" } ?: "")
            }
        }
    }


    fun cancel() {
        runJob?.cancel()
        _state.update { it.copy(isRunning = false) }
    }

    fun installResult() {
        val current = _state.value
        val result = current.result ?: return

        if (current.installOverAdb) {
            if (current.vmTermuxInstalled && !current.keepsInstalledSignature) {
                askToReplace(current.vmInstalledVersion)
                return
            }

            installRemotely(result)
            return
        }

        if (current.deviceTermuxInstalled && !current.keepsInstalledSignature) {
            askToReplace(current.deviceInstalledVersion)
            return
        }

        installLocally(result)
    }

    private fun askToReplace(version: String?) {
        _state.update {
            it.copy(
                showVmReplaceConfirm = true,
                vmReplaceVersion = version,
            )
        }
    }

    fun dismissVmReplaceConfirm() {
        _state.update { it.copy(showVmReplaceConfirm = false) }
    }

    fun confirmVmReplace() {
        val current = _state.value
        val result = current.result ?: return

        _state.update { it.copy(showVmReplaceConfirm = false) }

        if (current.installOverAdb) {
            reinstallOnVm()
            return
        }

        replaceLocally(result)
    }

    private fun replaceLocally(result: PatcherUiState.PatchSuccess) {
        viewModelScope.launch {
            val session = privilegeRegistry.provider(PrivilegeMethod.ROOT)
                ?.open()
                ?.getOrNull()

            if (session == null) {
                logger.warn(TAG) { "no root: asking the system to uninstall Termux" }
                repository.requestUninstall()
                _state.update {
                    it.copy(
                        error = PatcherError.INSTALL_NEEDS_UNINSTALL,
                        errorDetail = null,
                    )
                }
                return@launch
            }

            if (!uninstallOnDevice(session)) return@launch

            installLocally(result)
        }
    }

    private suspend fun uninstallOnDevice(session: DeviceSession): Boolean {
        val removed = withContext(Dispatchers.IO) {
            runCatching {
                session.shell(
                    "pm uninstall ${TermuxPaths.PACKAGE}",
                    Duration.ofMinutes(2),
                )
            }.getOrNull()
        }

        val output = removed?.combined?.trim().orEmpty()

        val gone = removed != null &&
            removed.isSuccess &&
            (output.contains("Success") || output.contains("not installed"))

        if (!gone) {
            logger.error(TAG) { "the uninstall failed: $output" }
            _state.update {
                it.copy(
                    error = PatcherError.INSTALL_FAILED,
                    errorDetail = output.take(240).ifBlank { null },
                )
            }
        }

        return gone
    }

    private fun installLocally(result: PatcherUiState.PatchSuccess) {
        val current = _state.value

        _state.update {
            it.copy(
                error = null,
                errorDetail = null,
                notice = PatcherNotice.NONE,
                isInstalling = true,
            )
        }

        viewModelScope.launch {
            try {
                when (val outcome = repository.install(current.storageId, result.apk)) {
                    is ApkInstaller.Result.Success -> {
                        logger.info(TAG) { "installed ${result.apk.name}" }
                        _state.update {
                            it.copy(
                                error = null,
                                errorDetail = null,
                                notice = PatcherNotice.INSTALLED_ON_VM,
                            )
                        }
                        refresh()
                    }

                    is ApkInstaller.Result.Failed -> {
                        logger.error(TAG) { "install failed: ${outcome.reason}" }
                        _state.update {
                            it.copy(
                                error = outcome.reason.asPatcherError(),

                                errorDetail = outcome.detail,
                            )
                        }
                    }

                    ApkInstaller.Result.AwaitingUser ->
                        logger.info(TAG) { "waiting for the system to confirm the install" }
                }
            } finally {
                _state.update { it.copy(isInstalling = false) }
            }
        }
    }

    private fun installRemotely(result: PatcherUiState.PatchSuccess) {
        val session = remoteConnection.current()

        _state.update {
            it.copy(
                error = null,
                errorDetail = null,
                notice = PatcherNotice.NONE,
                isInstalling = true,
            )
        }

        if (session == null) {
            _state.update {
                it.copy(
                    error = PatcherError.VM_GONE,
                    errorDetail = null,
                    isInstalling = false,
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                val file = runCatching {
                    repository.materialise(_state.value.storageId, result.apk)
                }.getOrElse { e ->
                    _state.update {
                        it.copy(error = PatcherError.APK_UNREADABLE, errorDetail = e.message)
                    }
                    return@launch
                }

                when (val outcome = remoteInstall.install(session, file)) {
                    RemoteInstall.Result.Success -> {
                        logger.info(TAG) { "installed on the VM" }
                        _state.update {
                            it.copy(
                                error = null,
                                errorDetail = null,
                                notice = PatcherNotice.INSTALLED_ON_VM,
                            )
                        }
                    }

                    is RemoteInstall.Result.Failed -> {
                        logger.error(TAG) { "remote install failed: ${outcome.reason}" }

                        _state.update {
                            it.copy(
                                error = outcome.reason.asPatcherError(),
                                errorDetail = outcome.detail,
                            )
                        }
                    }
                }
            } finally {
                _state.update { it.copy(isInstalling = false) }
            }
        }
    }

    fun reinstallOnVm() {
        val current = _state.value
        val result = current.result ?: return

        _state.update {
            it.copy(
                error = null,
                errorDetail = null,
                notice = PatcherNotice.NONE,
                isInstalling = true,
            )
        }

        viewModelScope.launch {
            val session = remoteConnection.current()
            if (session == null) {
                _state.update {
                    it.copy(
                        error = PatcherError.VM_GONE,
                        errorDetail = null,
                        isInstalling = false,
                    )
                }
                return@launch
            }

            val removed = withContext(Dispatchers.IO) {
                runCatching {
                    session.shell(
                        "pm uninstall ${TermuxPaths.PACKAGE}",
                        Duration.ofMinutes(2),
                    )
                }.getOrNull()
            }

            val output = removed?.combined?.trim().orEmpty()

            val gone = removed != null &&
                removed.isSuccess &&
                (output.contains("Success") || output.contains("not installed"))

            if (!gone) {
                logger.error(TAG) { "the uninstall on the VM failed: $output" }
                _state.update {
                    it.copy(
                        error = PatcherError.REMOTE_INSTALL_FAILED,
                        errorDetail = output.take(240).ifBlank { null },
                        isInstalling = false,
                    )
                }
                return@launch
            }

            logger.info(TAG) { "removed the old Termux from the VM" }
            installRemotely(result)
        }
    }

    fun installBackup(backup: StoredApk) {
        viewModelScope.launch {
            when (val outcome = repository.install(_state.value.storageId, backup)) {
                is ApkInstaller.Result.Success -> {
                    logger.info(TAG) { "restored ${backup.name}" }
                    refresh()
                }

                is ApkInstaller.Result.Failed ->
                    _state.update {
                        it.copy(
                            error = outcome.reason.asPatcherError(),
                            errorDetail = outcome.detail,
                        )
                    }

                ApkInstaller.Result.AwaitingUser -> Unit
            }
        }
    }

    fun deleteBackup(backup: StoredApk) {
        viewModelScope.launch {
            repository.deleteBackup(_state.value.storageId, backup)
            refresh()
        }
    }

    fun dismissUninstallDialog() {
        _state.update { it.copy(showUninstallDialog = false) }
    }

    fun confirmUninstall() {
        _state.update { it.copy(showUninstallDialog = false) }
        logger.warn(TAG) { "uninstalling ${TermuxPaths.PACKAGE}" }
        repository.requestUninstall()
    }

    fun openInstallSettings() = repository.openInstallPermissionSettings()

    fun deleteResult() {
        val result = _state.value.result ?: return
        viewModelScope.launch {
            repository.deleteResult(_state.value.storageId, result.apk)
            resetRun()
        }
    }

    fun shareResult() {
        val result = _state.value.result ?: return
        viewModelScope.launch {
            runCatching {
                repository.shareApk(_state.value.storageId, result.apk)
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null, errorDetail = null) }
    }

    fun resetRun() {
        if (runJob?.isActive == true) return
        _state.update {
            it.copy(
                isRunning = false,
                steps = emptyList(),
                log = emptyList(),
                result = null,
                error = null,
                errorDetail = null,
                notice = PatcherNotice.NONE,
                isInstalling = false,
                showVmReplaceConfirm = false,
                vmReplaceVersion = null,
            )
        }
    }

    private companion object {
        const val TAG = "Patcher"
    }
}
