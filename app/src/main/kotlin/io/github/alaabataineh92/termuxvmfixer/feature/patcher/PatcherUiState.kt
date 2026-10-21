package io.github.alaabataineh92.termuxvmfixer.feature.patcher

import io.github.alaabataineh92.termuxvmfixer.core.device.apk.ApkPatchRepository.PatchFailure
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.InstallFailure
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.StoredApk
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchReport
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchStep
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchStepState
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.SigningKeySource
import io.github.alaabataineh92.termuxvmfixer.data.privilege.remote.RemoteInstall
import io.github.alaabataineh92.termuxvmfixer.feature.fix.MonitorLogLine

enum class SourceKind {
    DEVICE_INSTALLED,
    VM_INSTALLED,
    PICKED,
}

enum class SigningMode {
    TERMUX,
    GENERATED,
    OWN_KEYSTORE,
}

enum class PatcherNotice {
    NONE,
    INSTALLED_ON_VM,
}

data class SigningLabel(
    val source: SigningKeySource,
    val alias: String? = null,
)

enum class PatcherError {
    BLOCKED,
    PICKED_UNREADABLE,
    KEYSTORE_OPEN,
    INSTALLED_UNREADABLE,
    NO_KEYSTORE,
    NO_APK,
    NO_VM,
    VM_GONE,
    APK_UNREADABLE,
    PATCH_BACKUP_FAILED,
    PATCH_NOT_SAVED,
    PATCH_CANCELLED,
    PATCH_FAILED,
    INSTALL_APK_MISSING,
    INSTALL_NOT_ALLOWED,
    INSTALL_NO_SESSION,
    INSTALL_FAILED,
    INSTALL_NEEDS_UNINSTALL,
    INSTALL_STATUS,
    REMOTE_STAGING_FAILED,
    REMOTE_COPY_FAILED,
    REMOTE_SIGNATURE_MISMATCH,
    REMOTE_VERSION_DOWNGRADE,
    REMOTE_INSTALL_FAILED,
    ;

    val namesDetail: Boolean
        get() = when (this) {
            APK_UNREADABLE, INSTALL_APK_MISSING, INSTALL_STATUS -> true
            else -> false
        }
}

fun PatchFailure.asPatcherError(): PatcherError = when (this) {
    PatchFailure.BACKUP_FAILED -> PatcherError.PATCH_BACKUP_FAILED
    PatchFailure.NOT_SAVED -> PatcherError.PATCH_NOT_SAVED
    PatchFailure.CANCELLED -> PatcherError.PATCH_CANCELLED
    PatchFailure.PATCH_FAILED -> PatcherError.PATCH_FAILED
}

fun InstallFailure.asPatcherError(): PatcherError = when (this) {
    InstallFailure.ApkMissing -> PatcherError.INSTALL_APK_MISSING
    InstallFailure.NotAllowed -> PatcherError.INSTALL_NOT_ALLOWED
    InstallFailure.NoSession -> PatcherError.INSTALL_NO_SESSION
    InstallFailure.InstallFailed -> PatcherError.INSTALL_FAILED
    InstallFailure.StatusFailed -> PatcherError.INSTALL_STATUS
}

fun RemoteInstall.RemoteInstallFailure.asPatcherError(): PatcherError = when (this) {
    RemoteInstall.RemoteInstallFailure.STAGING_FAILED ->
        PatcherError.REMOTE_STAGING_FAILED

    RemoteInstall.RemoteInstallFailure.COPY_FAILED -> PatcherError.REMOTE_COPY_FAILED
    RemoteInstall.RemoteInstallFailure.SIGNATURE_MISMATCH ->
        PatcherError.REMOTE_SIGNATURE_MISMATCH

    RemoteInstall.RemoteInstallFailure.VERSION_DOWNGRADE ->
        PatcherError.REMOTE_VERSION_DOWNGRADE

    RemoteInstall.RemoteInstallFailure.INSTALL_FAILED -> PatcherError.REMOTE_INSTALL_FAILED
}

data class PatcherUiState(
    val isLoading: Boolean = true,
    val sourceKind: SourceKind = SourceKind.DEVICE_INSTALLED,
    val deviceInstalledVersion: String? = null,
    val deviceInstalledAvailable: Boolean = false,
    val vmInstalledVersion: String? = null,
    val vmInstalledAvailable: Boolean = false,
    val pickedName: String? = null,
    val pickedAvailable: Boolean = false,
    val backUp: Boolean = true,
    val storageId: String = "private",
    val signingMode: SigningMode = SigningMode.TERMUX,
    val canInstallPackages: Boolean = false,
    val deviceTermuxInstalled: Boolean = false,
    val vmTermuxInstalled: Boolean = false,
    val isInsideVm: Boolean = false,
    val isRemote: Boolean = false,
    val remoteConnected: Boolean = false,
    val remoteAddress: String? = null,
    val isRunning: Boolean = false,
    val steps: List<StepState> = emptyList(),
    val log: List<MonitorLogLine> = emptyList(),
    val error: PatcherError? = null,
    val errorDetail: String? = null,
    val notice: PatcherNotice = PatcherNotice.NONE,
    val isInstalling: Boolean = false,
    val installedSignerDigest: String? = null,
    val termuxKeyDigest: String? = null,
    val userKeyDigest: String? = null,
    val result: PatchSuccess? = null,
    val showUninstallDialog: Boolean = false,
    val showKeystoreDialog: Boolean = false,
    val backups: List<StoredApk> = emptyList(),
    val showVmReplaceConfirm: Boolean = false,
    val vmReplaceVersion: String? = null,
) {
    data class StepState(
        val step: PatchStep,
        val state: PatchStepState,
        val detail: String? = null,
    )

    data class PatchSuccess(
        val apk: StoredApk,
        val backup: StoredApk?,
        val report: PatchReport,
        val signing: SigningLabel,
    )

    val orderedSteps: List<StepState>
        get() {
            if (steps.isNotEmpty()) return steps
            return PatchStep.entries
                .filter { it != PatchStep.DONE }
                .map { StepState(it, PatchStepState.PENDING) }
        }

    val canPatch: Boolean
        get() = !isRunning && when (sourceKind) {
            SourceKind.DEVICE_INSTALLED -> deviceInstalledAvailable
            SourceKind.VM_INSTALLED -> vmInstalledAvailable
            SourceKind.PICKED -> pickedAvailable
        }

    val offersBothInstalledSources: Boolean
        get() = !isInsideVm &&
                isRemote &&
                remoteConnected &&
                deviceInstalledAvailable &&
                vmInstalledAvailable

    val showDeviceInstalledOption: Boolean
        get() = deviceInstalledAvailable &&
                (!vmInstalledAvailable || offersBothInstalledSources)

    val showVmInstalledOption: Boolean
        get() = vmInstalledAvailable &&
                (!deviceInstalledAvailable || offersBothInstalledSources)
    val canInstallPatchedApk: Boolean
        get() = isInsideVm || (isRemote && remoteConnected)

    val installOverAdb: Boolean
        get() = !isInsideVm && isRemote && remoteConnected

    val willLoseTermuxData: Boolean
        get() = targetTermuxInstalled && when (signingMode) {
            SigningMode.TERMUX -> !keepsInstalledSignature
            SigningMode.GENERATED -> true
            SigningMode.OWN_KEYSTORE -> false
        }

    val keepsInstalledSignature: Boolean
        get() = when (signingMode) {
            SigningMode.TERMUX -> termuxKeyMatchesInstalled
            SigningMode.OWN_KEYSTORE -> userKeyMatchesInstalled
            SigningMode.GENERATED -> false
        }

    val termuxKeyMatchesInstalled: Boolean
        get() = termuxKeyDigest != null &&
                termuxKeyDigest == installedSignerDigest
    val userKeyMatchesInstalled: Boolean
        get() = userKeyDigest != null &&
                userKeyDigest == installedSignerDigest

    val targetTermuxInstalled: Boolean
        get() = when (sourceKind) {
            SourceKind.DEVICE_INSTALLED -> deviceTermuxInstalled
            SourceKind.VM_INSTALLED -> vmTermuxInstalled
            SourceKind.PICKED -> false
        }
}
