package io.github.alaabataineh92.termuxvmfixer.feature.patcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.DialogShell
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageCard
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageSeverity
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.ScreenScaffold
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Dimens
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing

@Composable
fun PatcherScreen(

    remote: Boolean = false,
    onBack: () -> Unit,
    viewModel: PatcherViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var confirmDataLoss by remember { mutableStateOf(false) }
    var showInstallWarning by remember { mutableStateOf(false) }

    LaunchedEffect(state.result) {
        if (state.result != null) viewModel.refreshInstallPermission()
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

    if (state.showKeystoreDialog) {
        KeystoreDialog(
            onDismiss = viewModel::dismissKeystoreDialog,
            onUnlock = viewModel::unlockKeystore,
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
                onDelete = viewModel::deleteResult,
                onShare = viewModel::shareResult,
                onReinstallOnVm = viewModel::reinstallOnVm,
                onDismiss = viewModel::resetRun,
            )
        } else {
            PatchProgressDialog(
                state = state,
                onCancel = viewModel::cancel,
                onRetry = viewModel::resetRun,
                onDismiss = viewModel::resetRun,
            )
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.vmfix_patcher_title),
        onBack = onBack,
        backLabel = stringResource(R.string.vmfix_common_back),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    text = stringResource(R.string.vmfix_patcher_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!state.isRemote && !state.canInstallPackages) {
                item {
                    WarningCard(
                        title = stringResource(R.string.vmfix_patcher_warning_install_title),
                        body = stringResource(R.string.vmfix_patcher_warning_install_body),
                        actionLabel = stringResource(R.string.vmfix_patcher_open_settings),
                        onAction = viewModel::openInstallSettings,
                    )
                }
            }

            item {
                SectionCard(stringResource(R.string.vmfix_patcher_section_source)) {
                    if (state.showDeviceInstalledOption) {
                        SourceRow(
                            title = stringResource(
                                R.string.vmfix_patcher_source_device_installed,
                            ),
                            subtitle = state.deviceInstalledVersion?.let {
                                stringResource(R.string.vmfix_patcher_source_version, it)
                            } ?: stringResource(R.string.vmfix_patcher_source_on_this_device),
                            selected = state.sourceKind == SourceKind.DEVICE_INSTALLED,
                            enabled = state.deviceInstalledAvailable,
                            icon = Icons.Default.PhoneAndroid,
                            onClick = viewModel::selectDeviceInstalled,
                        )
                    }

                    if (state.showVmInstalledOption) {
                        SourceRow(
                            title = stringResource(R.string.vmfix_patcher_source_vm_installed),
                            subtitle = state.vmInstalledVersion?.let {
                                stringResource(R.string.vmfix_patcher_source_version_remote, it)
                            } ?: stringResource(R.string.vmfix_patcher_source_name_vm_suffix),
                            selected = state.sourceKind == SourceKind.VM_INSTALLED,
                            enabled = state.vmInstalledAvailable,
                            icon = Icons.Default.Computer,
                            onClick = viewModel::selectVmInstalled,
                        )
                    }

                    if (!state.showDeviceInstalledOption &&
                        !state.showVmInstalledOption
                    ) {
                        SourceRow(
                            title = stringResource(R.string.vmfix_patcher_source_installed),
                            subtitle = stringResource(
                                R.string.vmfix_patcher_source_not_installed,
                            ),
                            selected = false,
                            enabled = false,
                            icon = Icons.Default.PhoneAndroid,
                            onClick = {},
                        )
                    }

                    SourceRow(
                        title = stringResource(R.string.vmfix_patcher_source_pick),
                        subtitle = state.pickedName
                            ?: stringResource(R.string.vmfix_patcher_source_pick_hint),
                        selected = state.sourceKind == SourceKind.PICKED,
                        enabled = true,
                        icon = Icons.Default.FolderOpen,
                        onClick = { pickApk.launch(arrayOf(APK_MIME)) },
                    )
                }
            }

            item {
                SectionCard(stringResource(R.string.vmfix_patcher_section_options)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = state.backUp,
                            onCheckedChange = viewModel::setBackUp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.vmfix_patcher_backup_label),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.vmfix_patcher_backup_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    Text(
                        stringResource(R.string.vmfix_patcher_where_to_save),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    viewModel.storages.forEach { storage ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(
                                selected = state.storageId == storage.id,
                                onClick = { viewModel.setStorage(storage.id) },
                            )
                            Text(
                                storage.kind.label(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(stringResource(R.string.vmfix_patcher_section_signing)) {
                    SigningRow(
                        title = stringResource(R.string.vmfix_patcher_signing_termux_title),
                        subtitle = stringResource(
                            if (state.keepsInstalledSignature) {
                                R.string.vmfix_patcher_signing_termux_body_matches
                            } else {
                                R.string.vmfix_patcher_signing_termux_body
                            },
                        ),
                        selected = state.signingMode == SigningMode.TERMUX,
                        onClick = { viewModel.setSigningMode(SigningMode.TERMUX) },
                    )
                    SigningRow(
                        title = stringResource(R.string.vmfix_patcher_signing_generated_title),
                        subtitle = stringResource(R.string.vmfix_patcher_signing_generated_body),
                        selected = state.signingMode == SigningMode.GENERATED,
                        onClick = { viewModel.setSigningMode(SigningMode.GENERATED) },
                    )
                    SigningRow(
                        title = stringResource(R.string.vmfix_patcher_signing_own_title),
                        subtitle = stringResource(R.string.vmfix_patcher_signing_own_body),
                        selected = state.signingMode == SigningMode.OWN_KEYSTORE,
                        onClick = {
                            viewModel.setSigningMode(SigningMode.OWN_KEYSTORE)
                            pickKeystore.launch(arrayOf("*/*"))
                        },
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        if (state.willLoseTermuxData) {
                            confirmDataLoss = true
                        } else {
                            viewModel.patch()
                        }
                    },
                    enabled = state.canPatch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (state.result == null) {
                                R.string.vmfix_patcher_action_patch
                            } else {
                                R.string.vmfix_patcher_action_patch_again
                            },
                        ),
                    )
                }
            }

            if (state.backups.isNotEmpty()) {
                item {
                    SectionCard(stringResource(R.string.vmfix_patcher_section_backups)) {
                        Text(
                            text = stringResource(R.string.vmfix_patcher_backups_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.backups.forEach { backup ->
                            BackupRow(
                                name = backup.name,
                                size = backup.sizeLabel,
                                onRestore = { viewModel.installBackup(backup) },
                                onDelete = { viewModel.deleteBackup(backup) },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.SectionCard(title = title) {
        content()
    }
}

@Composable
private fun SourceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.padding(horizontal = 4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SigningRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackupRow(name: String, size: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Default.Restore,
                contentDescription = stringResource(R.string.vmfix_patcher_backup_restore),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.vmfix_patcher_backup_delete),
            )
        }
    }
}

@Composable
private fun WarningCard(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    detail: String? = null,
    severity: MessageSeverity = MessageSeverity.Error,
) {
    MessageCard(
        severity = severity,
        title = title,
        body = body,
        detail = detail,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
internal fun UninstallDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vmfix_patcher_uninstall_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.vmfix_patcher_uninstall_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.vmfix_patcher_uninstall_bullet_prefix),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.vmfix_patcher_uninstall_bullet_packages),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.vmfix_patcher_uninstall_bullet_home),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.vmfix_patcher_uninstall_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.vmfix_patcher_uninstall_confirm))
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
internal fun KeystoreDialog(onDismiss: () -> Unit, onUnlock: (alias: String, password: String) -> Unit) {
    var alias by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    DialogShell(
        title = stringResource(R.string.vmfix_patcher_keystore_title),
        subtitle = stringResource(R.string.vmfix_patcher_keystore_body),
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it },
            label = { Text(stringResource(R.string.vmfix_patcher_keystore_alias)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.vmfix_patcher_keystore_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.vmfix_common_cancel))
            }
            TextButton(onClick = { onUnlock(alias, password) }) {
                Text(stringResource(R.string.vmfix_patcher_keystore_unlock))
            }
        }
    }
}

private const val APK_MIME = "application/vnd.android.package-archive"
