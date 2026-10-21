package io.github.alaabataineh92.termuxvmfixer.feature.patcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.DialogShell
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageCard
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageSeverity
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.SectionCard
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchStepState
import io.github.alaabataineh92.termuxvmfixer.feature.fix.MonitorStep
import io.github.alaabataineh92.termuxvmfixer.feature.fix.MonitorStepState
import io.github.alaabataineh92.termuxvmfixer.feature.fix.RunMonitorDialog
import io.github.alaabataineh92.termuxvmfixer.feature.fix.RunMonitorStatus

@Composable
fun PatchRunDialog(
    remote: Boolean,
    onDismiss: () -> Unit,
    onOpenFullPatcher: (Boolean) -> Unit,
    viewModel: PatcherViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val dismiss = {
        viewModel.resetRun()
        onDismiss()
    }

    LaunchedEffect(remote) {
        viewModel.bindTarget(remote)
    }

    val pickApk = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')
            viewModel.selectPicked(uri, name)
        }
    }

    val pickKeystore = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.setKeystoreFile(uri)
            viewModel.openKeystoreDialog()
        }
    }

    var confirmDataLoss by remember { mutableStateOf(false) }
    var showInstallWarning by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    LaunchedEffect(state.result) {
        if (state.result != null) viewModel.refreshInstallPermission()
    }

    if (state.showKeystoreDialog) {
        KeystoreDialog(
            onDismiss = viewModel::dismissKeystoreDialog,
            onUnlock = viewModel::unlockKeystore,
        )
    }

    if (state.showUninstallDialog) {
        UninstallDialog(
            onDismiss = viewModel::dismissUninstallDialog,
            onConfirm = viewModel::confirmUninstall,
        )
    }

    if (state.showVmReplaceConfirm) {
        VmReplaceDialog(
            version = state.vmReplaceVersion,
            onConfirm = viewModel::confirmVmReplace,
            onDismiss = viewModel::dismissVmReplaceConfirm,
        )
    }

    if (confirmDataLoss) {
        DataLossConfirmDialog(
            onDismiss = { confirmDataLoss = false },
            onConfirm = {
                confirmDataLoss = false
                viewModel.patch()
            },
        )
    }

    if (showInstallWarning) {
        InstallPermissionDialog(
            onDismiss = { showInstallWarning = false },
            onOpenSettings = {
                showInstallWarning = false
                viewModel.openInstallSettings()
            },
        )
    }

    if (state.hasRunToReport) {
        val result = state.result

        if (result != null) {
            PatchResultDialog(
                state = state,
                result = result,
                showInstall = state.canInstallPatchedApk,
                onInstall = {
                    if (!state.installOverAdb && !state.canInstallPackages) {
                        showInstallWarning = true
                    } else {
                        viewModel.installResult()
                    }
                },
                onDelete = {
                    viewModel.deleteResult()
                    dismiss()
                },
                onShare = viewModel::shareResult,
                onReinstallOnVm = viewModel::reinstallOnVm,
                onDismiss = dismiss,
            )
        } else {
            PatchProgressDialog(
                state = state,
                onCancel = viewModel::cancel,
                onRetry = viewModel::resetRun,
                onDismiss = dismiss,
            )
        }
        return
    }

    PatchSourceDialog(
        state = state,
        onPickApk = { pickApk.launch(arrayOf(APK_MIME)) },
        onSelectDeviceInstalled = viewModel::selectDeviceInstalled,
        onSelectVmInstalled = viewModel::selectVmInstalled,
        onPatch = {
            if (state.willLoseTermuxData) {
                confirmDataLoss = true
            } else {
                viewModel.patch()
            }
        },
        onMoreOptions = { showOptions = true },
        onDismissError = viewModel::dismissError,
        onDismiss = dismiss,
    )

    if (showOptions) {
        PatchOptionsDialog(
            state = state,
            storages = viewModel.storages,
            onBackUpChange = viewModel::setBackUp,
            onStorageChange = viewModel::setStorage,
            onSigningModeChange = viewModel::setSigningMode,
            onPickKeystore = { pickKeystore.launch(arrayOf("*/*")) },
            onPatch = {
                showOptions = false

                if (state.willLoseTermuxData) {
                    confirmDataLoss = true
                } else {
                    viewModel.patch()
                }
            },
            onOpenFullPatcher = {
                showOptions = false
                viewModel.resetRun()
                onOpenFullPatcher(state.isRemote)
            },
            onDismiss = { showOptions = false },
        )
    }
}

internal val PatcherUiState.hasRunToReport: Boolean
    get() = isRunning || result != null || (error != null && steps.isNotEmpty())

@Composable
private fun PatchSourceDialog(
    state: PatcherUiState,
    onPickApk: () -> Unit,
    onSelectDeviceInstalled: () -> Unit,
    onSelectVmInstalled: () -> Unit,
    onPatch: () -> Unit,
    onMoreOptions: () -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogShell(
        title = stringResource(R.string.vmfix_patcher_title),
        subtitle = stringResource(R.string.vmfix_patcher_intro),
        onDismiss = onDismiss,
    ) {
        SectionCard(title = stringResource(R.string.vmfix_patcher_section_source)) {
            if (state.showDeviceInstalledOption) {
                SourceOption(
                    title = stringResource(
                        R.string.vmfix_patcher_source_device_installed,
                    ),
                    subtitle = state.deviceInstalledVersion?.let {
                        stringResource(R.string.vmfix_patcher_source_version, it)
                    } ?: stringResource(R.string.vmfix_patcher_source_on_this_device),
                    selected = state.sourceKind == SourceKind.DEVICE_INSTALLED,
                    enabled = state.deviceInstalledAvailable,
                    onClick = onSelectDeviceInstalled,
                )
            }

            if (state.showVmInstalledOption) {
                SourceOption(
                    title = stringResource(R.string.vmfix_patcher_source_vm_installed),
                    subtitle = state.vmInstalledVersion?.let {
                        stringResource(R.string.vmfix_patcher_source_version_remote, it)
                    } ?: stringResource(R.string.vmfix_patcher_source_name_vm_suffix),
                    selected = state.sourceKind == SourceKind.VM_INSTALLED,
                    enabled = state.vmInstalledAvailable,
                    onClick = onSelectVmInstalled,
                )
            }

            if (!state.showDeviceInstalledOption && !state.showVmInstalledOption) {
                SourceOption(
                    title = stringResource(R.string.vmfix_patcher_source_installed),
                    subtitle = stringResource(R.string.vmfix_patcher_source_not_installed),
                    selected = false,
                    enabled = false,
                    onClick = {},
                )
            }

            SourceOption(
                title = stringResource(R.string.vmfix_patcher_source_pick),
                subtitle = state.pickedName
                    ?: stringResource(R.string.vmfix_patcher_source_pick_hint),
                selected = state.sourceKind == SourceKind.PICKED,
                enabled = true,

                onClick = onPickApk,
            )
        }

        state.error?.let { reason ->
            MessageCard(
                severity = MessageSeverity.Error,
                title = stringResource(R.string.vmfix_patcher_error_title),
                body = reason.text(state.errorDetail),
                detail = state.errorDetail
                    ?.takeIf { !reason.namesDetail }
                    ?.let { reason.detailText(it) },
                actionLabel = stringResource(R.string.vmfix_patcher_error_dismiss),
                onAction = onDismissError,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            TextButton(onClick = onMoreOptions, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.vmfix_patcher_quick_more))
            }

            Button(onClick = onPatch, enabled = state.canPatch) {
                Text(stringResource(R.string.vmfix_patcher_action_patch))
            }
        }
    }
}

@Composable
private fun SourceOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DataLossConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vmfix_patcher_data_loss_title)) },
        text = {
            Text(
                stringResource(R.string.vmfix_patcher_data_loss_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.vmfix_patcher_data_loss_proceed))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.vmfix_common_cancel))
            }
        },
    )
}

@Composable
internal fun PatchProgressDialog(
    state: PatcherUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val status = monitorStatus(state)
    val steps = state.orderedSteps.map { it.toMonitor() }

    RunMonitorDialog(
        title = stringResource(statusTitle(status)),
        methodLabel = stringResource(R.string.vmfix_method_patcher),
        status = status,
        steps = steps,
        currentStepIndex = state.orderedSteps
            .indexOfFirst { it.state == PatchStepState.RUNNING }
            .takeIf { it >= 0 },
        completedCount = state.orderedSteps.count {
            it.state == PatchStepState.DONE || it.state == PatchStepState.SKIPPED
        },
        totalCount = state.orderedSteps.size,

        smoothedFraction = state.smoothedFraction(),
        log = state.log,
        onClose = if (status == RunMonitorStatus.Running) onCancel else onDismiss,
        onCancel = onCancel,
        onDone = onDismiss,

        onRetry = onRetry,
        onOpenLog = null,
        resultVisible = state.error != null,
        resultContent = {
            state.error?.let { reason ->
                MessageCard(
                    severity = MessageSeverity.Error,
                    title = stringResource(R.string.vmfix_patcher_error_title),
                    body = reason.text(state.errorDetail),
                    detail = state.errorDetail
                        ?.takeIf { !reason.namesDetail }
                        ?.let { reason.detailText(it) },
                )
            }
        },
    )
}

private fun monitorStatus(state: PatcherUiState): RunMonitorStatus = when {
    state.isRunning -> RunMonitorStatus.Running

    state.result != null -> RunMonitorStatus.Success
    state.error != null -> RunMonitorStatus.Failed

    else -> RunMonitorStatus.Cancelled
}

private fun statusTitle(status: RunMonitorStatus): Int = when (status) {
    RunMonitorStatus.Running -> R.string.vmfix_patcher_title_running
    RunMonitorStatus.Success -> R.string.vmfix_patcher_outcome_done
    RunMonitorStatus.Failed -> R.string.vmfix_patcher_outcome_failed
    RunMonitorStatus.Cancelled -> R.string.vmfix_patcher_outcome_cancelled
}

@Composable
internal fun PatcherUiState.StepState.toMonitor(): MonitorStep = MonitorStep(
    title = step.title(),
    state = when (state) {
        PatchStepState.PENDING -> MonitorStepState.Pending
        PatchStepState.RUNNING -> MonitorStepState.Running
        PatchStepState.DONE -> MonitorStepState.Done
        PatchStepState.FAILED -> MonitorStepState.Failed
        PatchStepState.SKIPPED -> MonitorStepState.Skipped
    },
    detail = detail ?: when (state) {
        PatchStepState.DONE -> stringResource(R.string.vmfix_fix_note_complete)
        PatchStepState.PENDING -> stringResource(R.string.vmfix_fix_detail_waiting)
        PatchStepState.SKIPPED -> stringResource(R.string.vmfix_patcher_step_not_reached)

        else -> ""
    },
)

private fun PatcherUiState.smoothedFraction(): Float {
    val steps = orderedSteps
    if (steps.isEmpty()) return 0f
    val done = steps.count {
        it.state == PatchStepState.DONE || it.state == PatchStepState.SKIPPED
    }
    val running = steps.count { it.state == PatchStepState.RUNNING }
    return ((done + running * 0.5f) / steps.size).coerceIn(0f, 1f)
}

@Composable
internal fun InstallPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vmfix_patcher_warning_install_title)) },
        text = {
            Text(
                stringResource(R.string.vmfix_patcher_warning_install_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.vmfix_patcher_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.vmfix_common_cancel))
            }
        },
    )
}

private const val APK_MIME = "application/vnd.android.package-archive"
