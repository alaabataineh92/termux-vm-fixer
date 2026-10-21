package io.github.alaabataineh92.termuxvmfixer.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.BottomActionBar
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.ChipTone
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageCard
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageSeverity
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.ScreenScaffold
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.StatusChip
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Alphas
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Dimens
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.LocalStatusPalette
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.MonoTextStyle
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing
import io.github.alaabataineh92.termuxvmfixer.core.model.PatchState
import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.ProviderStatus
import io.github.alaabataineh92.termuxvmfixer.core.model.VirtualMasterState
import io.github.alaabataineh92.termuxvmfixer.feature.fix.FixProgressDialog
import io.github.alaabataineh92.termuxvmfixer.feature.fix.FixViewModel
import io.github.alaabataineh92.termuxvmfixer.feature.patcher.PatchRunDialog

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenPatcher: (Boolean) -> Unit,
    onOpenLog: () -> Unit,
    fixViewModel: FixViewModel = hiltViewModel(),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fixState by fixViewModel.state.collectAsStateWithLifecycle()

    if (state.showAdbAddressDialog) {
        AdbAddressDialog(
            initialHost = state.remoteHost.ifBlank { "127.0.0.1" },
            initialPort = state.remotePort.ifBlank { "10000" },
            discoveryState = state.adbDiscovery,
            onAutoDiscover = viewModel::autoDiscoverAdb,
            onShowDiscoveredVms = viewModel::openDiscoveredVmsDialog,
            onDismiss = viewModel::dismissAdbAddressDialog,
            onConfirm = if (state.addressDialogForRemote) {
                viewModel::connectRemote
            } else {
                viewModel::setAdbAddress
            },
            forRemoteVm = state.addressDialogForRemote,
        )
    }

    if (state.showDiscoveredVmsDialog) {
        DiscoveredVmsDialog(
            discoveryState = state.adbDiscovery,
            onDismiss = viewModel::dismissDiscoveredVmsDialog,
            onScanAgain = {
                viewModel.autoDiscoverAdb(
                    state.remoteHost.ifBlank { "127.0.0.1" },
                )
            },
            onSelect = viewModel::selectDiscoveredVm,
        )
    }

    fixState.method?.let { method ->
        FixProgressDialog(
            progress = fixState.progress,
            result = fixState.result,
            methodLabel = methodLabel(fixState.method),
            onCancel = fixViewModel::cancel,
            onOpenLog = onOpenLog,
            onRetry = { fixViewModel.start(method, state.isRemote) },
            onDismiss = {
                fixViewModel.dismiss()
                viewModel.refresh()
            },
        )
    }

    if (state.showActionDialog) {
        TransportDialog(
            options = state.transportOptions,
            selected = state.selected,
            onSelect = viewModel::select,
            onSetAdbAddress = if (state.isRemote) {
                null
            } else {
                viewModel::openLocalAdbAddressDialog
            },
            onStart = {
                start(fixViewModel, state, viewModel)
            },
            onDismiss = viewModel::dismissActionDialog,
        )
    }

    if (state.showPatchDialog) {
        PatchRunDialog(
            remote = state.isRemote,
            onDismiss = {
                viewModel.dismissPatchDialog()
                viewModel.refresh()
            },
            onOpenFullPatcher = { remote ->
                viewModel.dismissPatchDialog()
                onOpenPatcher(remote)
            },
        )
    }

    if (state.showPatchedApksDialog) {
        ApkListDialog(
            title = stringResource(R.string.vmfix_home_patched_apks),
            emptyText = stringResource(R.string.vmfix_home_patched_apks_empty),
            apks = state.patchedApks,
            canInstall = state.canInstallOnVm,
            onShare = viewModel::shareApk,
            onDelete = viewModel::deleteApk,
            onInstall = viewModel::requestInstall,
            onDismiss = viewModel::dismissPatchedApks,
        )
    }

    if (state.showBackupsDialog) {
        ApkListDialog(
            title = stringResource(R.string.vmfix_home_backups),
            emptyText = stringResource(R.string.vmfix_home_backups_empty),
            apks = state.backups,
            canInstall = state.canInstallOnVm,
            onShare = viewModel::shareApk,
            onDelete = viewModel::deleteApk,
            onInstall = viewModel::requestInstall,
            onDismiss = viewModel::dismissBackups,
        )
    }

    if (state.showInstallConfirm) {
        InstallOnVmDialog(
            apkName = state.pendingInstall?.name.orEmpty(),
            installedVersion = vmTermuxVersion(state),
            onConfirm = viewModel::confirmInstall,
            onDismiss = viewModel::dismissInstallConfirm,
        )
    }

    (state.error as? HomeError.RemoteNotAVm)?.let { err ->        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.vmfix_home_error_not_a_vm_title)) },
            text = {
                Text(
                    stringResource(R.string.vmfix_home_error_not_a_vm_body, err.address),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.vmfix_common_close))
                }
            },
        )
    }

    HomeContent(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenSettings = onOpenSettings,
        onRepair = viewModel::openActionDialog,
        onPatch = viewModel::openPatchDialog,
        onConnect = viewModel::openAdbAddressDialog,
        onDisconnect = viewModel::disconnectRemote,
        onOpenPatchedApks = viewModel::openPatchedApks,
        onOpenBackups = viewModel::openBackups,
        onOpenLog = onOpenLog,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onRepair: () -> Unit,
    onPatch: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenPatchedApks: () -> Unit,
    onOpenBackups: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val busy = state.isLoading || state.isConnecting
    val showRepair = state.profile?.canRepair == true
    val showConnect = !showRepair && !state.remoteConnected

    ScreenScaffold(
        title = stringResource(R.string.vmfix_home_title),
        actions = {
            IconButton(onClick = onOpenPatchedApks) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = stringResource(
                        R.string.vmfix_home_patched_apks_button,
                    ),
                )
            }
            IconButton(onClick = onOpenBackups) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = stringResource(R.string.vmfix_home_backups_button),
                )
            }
            IconButton(onClick = onOpenLog) {
                Icon(
                    Icons.AutoMirrored.Filled.Article,
                    contentDescription = stringResource(R.string.vmfix_home_log_button),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.vmfix_home_settings),
                )
            }
        },
        bottomBar = {
            HomeActionBar(
                showConnect = showConnect,
                showRepair = showRepair,
                connecting = state.isConnecting,
                repairEnabled = !busy,
                patchEnabled = !busy,
                onConnect = onConnect,
                onRepair = onRepair,
                onPatch = onPatch,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = Dimens.gutter,
                    end = Dimens.gutter,
                    top = padding.calculateTopPadding() + Spacing.sm,
                    bottom = padding.calculateBottomPadding() + Spacing.sm,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraSmall),
                )
            }

            HomeHeroCard(state)

            state.error?.let { error ->
                val remoteRetry = state.isRemote && !state.remoteConnected
                ErrorCard(
                    error = error,
                    actionLabel = stringResource(
                        if (remoteRetry) {
                            R.string.vmfix_home_adb_connect_manual
                        } else {
                            R.string.vmfix_common_try_again
                        },
                    ),
                    onAction = if (remoteRetry) onConnect else onRefresh,
                )
            }

            state.storageWarningMb?.let { warning ->
                WarningCard(
                    pluralStringResource(
                        R.plurals.vmfix_home_warning_storage,
                        warning.availableMb,
                        warning.availableMb,
                        warning.requiredMb,
                    ),
                )
            }

            state.unsupportedAbi?.let { abi ->
                WarningCard(stringResource(R.string.vmfix_home_warning_abi, abi))
            }

            if (state.profile != null && !state.isConnecting) {
                DetailsGrid(
                    state = state,
                    onDisconnect = onDisconnect,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private data class HeroContent(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val tone: ChipTone,
)

@Composable
private fun homeHero(state: HomeUiState): HeroContent {
    val profile = state.profile

    return when {
        state.isConnecting -> HeroContent(
            title = stringResource(R.string.vmfix_home_state_connecting),
            body = stringResource(R.string.vmfix_home_state_connecting_body),
            icon = Icons.Default.Usb,
            tone = ChipTone.Info,
        )

        state.isRemote && !state.remoteConnected -> HeroContent(
            title = stringResource(R.string.vmfix_home_state_not_connected),
            body = stringResource(R.string.vmfix_home_adb_connection_body),
            icon = Icons.Default.Usb,
            tone = ChipTone.Neutral,
        )

        profile == null && state.isLoading -> HeroContent(
            title = stringResource(R.string.vmfix_home_state_checking),
            body = stringResource(R.string.vmfix_home_state_checking_body),
            icon = Icons.Default.Android,
            tone = ChipTone.Neutral,
        )

        profile == null -> HeroContent(
            title = stringResource(R.string.vmfix_home_state_unavailable),
            body = state.error?.text()
                ?: stringResource(R.string.vmfix_home_state_unavailable_body),
            icon = Icons.Default.WarningAmber,
            tone = ChipTone.Warning,
        )

        !profile.isVirtualMaster -> HeroContent(
            title = stringResource(R.string.vmfix_home_state_not_vm),
            body = profile.blockingIssue?.text()
                ?: stringResource(R.string.vmfix_blocking_not_a_vm_local),
            icon = Icons.Default.WarningAmber,
            tone = ChipTone.Warning,
        )

        profile.termux.patch == PatchState.PATCHED && !profile.canRepair -> HeroContent(
            title = stringResource(R.string.vmfix_home_state_already_fixed),
            body = stringResource(R.string.vmfix_blocking_already_fixed),
            icon = Icons.Default.CheckCircle,
            tone = ChipTone.Success,
        )

        profile.canRepair -> HeroContent(
            title = stringResource(R.string.vmfix_home_state_ready),
            body = summaryLine(state),
            icon = Icons.Default.VerifiedUser,
            tone = ChipTone.Success,
        )

        profile.canPatch -> HeroContent(
            title = stringResource(R.string.vmfix_home_state_patch),
            body = profile.blockingIssue?.text() ?: summaryLine(state),
            icon = Icons.Default.Build,
            tone = ChipTone.Info,
        )

        else -> HeroContent(
            title = stringResource(R.string.vmfix_home_state_attention),
            body = profile.blockingIssue?.text() ?: summaryLine(state),
            icon = Icons.Default.WarningAmber,
            tone = ChipTone.Warning,
        )
    }
}

@Composable
private fun HomeHeroCard(state: HomeUiState) {
    val hero = homeHero(state)
    val (orbContainer, orbContent) = heroOrbColors(hero.tone)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(orbContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = hero.icon,
                        contentDescription = null,
                        tint = orbContent,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = hero.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    EnvironmentChip(state)
                }
            }

            if (hero.body.isNotBlank()) {
                Text(
                    text = hero.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun heroOrbColors(tone: ChipTone): Pair<Color, Color> {
    val palette = LocalStatusPalette.current
    val scheme = MaterialTheme.colorScheme

    return when (tone) {
        ChipTone.Success -> palette.success.container.copy(alpha = Alphas.TINT) to
            palette.success.container

        ChipTone.Warning -> palette.warning.container.copy(alpha = Alphas.TINT) to
            palette.warning.container

        ChipTone.Error -> scheme.errorContainer to scheme.error
        ChipTone.Info -> scheme.primaryContainer to scheme.onPrimaryContainer
        ChipTone.Neutral -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
}

@Composable
private fun EnvironmentChip(state: HomeUiState) {
    val profile = state.profile

    val (text, tone) = when {
        state.isConnecting ->
            stringResource(R.string.vmfix_home_chip_connecting) to ChipTone.Info

        state.isRemote && !state.remoteConnected ->
            stringResource(R.string.vmfix_home_chip_not_connected) to ChipTone.Neutral

        profile == null && state.isLoading ->
            stringResource(R.string.vmfix_home_chip_checking) to ChipTone.Neutral

        profile == null ->
            stringResource(R.string.vmfix_action_state_unavailable) to ChipTone.Warning

        profile.isVirtualMaster -> {
            val base = stringResource(R.string.vmfix_home_chip_virtual_master)
            val label = profile.vmName ?: profile.slot

            val text = label?.let {
                stringResource(R.string.vmfix_home_chip_vm_slot, base, it)
            } ?: base
            text to ChipTone.Success
        }

        else -> stringResource(R.string.vmfix_home_chip_not_a_vm) to ChipTone.Warning
    }

    StatusChip(text = text, tone = tone, icon = null)
}

@Composable
private fun summaryLine(state: HomeUiState): String {
    val termux = state.profile?.termux
        ?: return stringResource(R.string.vmfix_home_summary_checking)

    return if (termux.installed) {
        ""
    } else {
        stringResource(R.string.vmfix_home_summary_no_termux)
    }
}

private data class DetailItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val tone: ChipTone = ChipTone.Neutral,
    val mono: Boolean = false,
)

private data class DualDetailItem(
    val icon: ImageVector,
    val label: String,
    val deviceValue: String,
    val vmValue: String,
    val deviceTone: ChipTone = ChipTone.Neutral,
    val vmTone: ChipTone = ChipTone.Neutral,
    val mono: Boolean = false,
)

@Composable
private fun DetailsGrid(
    state: HomeUiState,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile ?: return
    val showDevice = state.device != null || state.isRemote

    val showAdb = !state.isRunningOutsideVm() || state.remoteConnected

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (showDevice) {
            DeviceDetailsSection(state = state)
        }
        TermuxDetailsSection(state = state)
        if (profile.isVirtualMaster) {
            RootDetailsSection(state = state)
        }
        if (showAdb) {
            AdbDetailsSection(
                state = state,
                onDisconnect = onDisconnect,
            )
        }
    }
}

private fun HomeUiState.isRunningOutsideVm(): Boolean {
    val local = localProfile
    if (local != null) return !local.isVirtualMaster

    val currentDevice = device ?: return false
    return currentDevice.virtualMaster !is VirtualMasterState.Inside
}

private fun vmTermuxVersion(state: HomeUiState): String? =
    if (state.isRemote) {
        state.profile?.termux?.versionName
    } else {
        state.localProfile?.termux?.versionName
    }

@Composable
private fun DetailsSection(
    title: String,
    rows: List<DetailItem>,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    if (rows.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rows.forEach { CompactDetailRow(it) }
            }

            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onAction)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun DualDetailsSection(
    title: String,
    rows: List<DualDetailItem>,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    if (rows.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Spacer(modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.vmfix_home_column_device),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.1f),
                )
                Text(
                    text = stringResource(R.string.vmfix_home_column_vm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.1f),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rows.forEach { DualDetailRow(it) }
            }

            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onAction)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun DeviceDetailsSection(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile ?: return
    val device = state.device
    if (device == null && !state.isRemote) return

    val unknown = stringResource(R.string.vmfix_home_value_unknown)

    val rows = buildList {
        if (device != null) {
            add(
                DetailItem(
                    icon = Icons.Default.Android,
                    label = stringResource(R.string.vmfix_home_label_android),
                    value = stringResource(
                        R.string.vmfix_home_value_android,
                        device.androidRelease,
                        device.sdkInt,
                    ),
                ),
            )
            add(
                DetailItem(
                    icon = Icons.Default.Memory,
                    label = stringResource(R.string.vmfix_home_label_architecture),
                    value = device.abi,
                ),
            )
        }

        if (state.isRemote || profile.isVirtualMaster) {
            val vmName = profile.vmName
            val vmSlot = profile.slot
            val vmValue = when {
                vmName != null && vmSlot != null ->
                    stringResource(R.string.vmfix_home_value_name_slot, vmName, vmSlot)

                vmName != null -> vmName
                vmSlot != null ->
                    stringResource(R.string.vmfix_home_value_name_slot, unknown, vmSlot)

                else -> unknown
            }

            add(
                DetailItem(
                    icon = Icons.Default.Android,
                    label = stringResource(R.string.vmfix_home_label_vm_name),
                    value = vmValue,
                    tone = if (vmName != null) ChipTone.Success else ChipTone.Warning,
                ),
            )
        }
    }

    DetailsSection(
        title = stringResource(
            when {
                state.isRemote && device != null ->
                    R.string.vmfix_home_section_device_vm_details

                state.isRemote || profile.isVirtualMaster ->
                    R.string.vmfix_home_section_vm_details

                else -> R.string.vmfix_home_section_details
            },
        ),
        rows = rows,
        modifier = modifier,
    )
}

@Composable
private fun TermuxDetailsSection(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val vmTermux = state.profile?.termux ?: return
    val hostTermux = state.localProfile?.termux ?: state.device?.termux
    val unknown = stringResource(R.string.vmfix_home_value_unknown)
    val notInstalled = stringResource(R.string.vmfix_home_value_not_installed)
    val outsideVm = state.isRunningOutsideVm()

    if (state.isRemote && outsideVm && hostTermux != null) {
        val title = stringResource(R.string.vmfix_home_section_termux_details_both)
        val rows = buildList {
            add(
                DualDetailItem(
                    icon = Icons.Default.Android,
                    label = stringResource(R.string.vmfix_home_label_version),
                    deviceValue = if (hostTermux.installed) {
                        hostTermux.versionName ?: unknown
                    } else {
                        notInstalled
                    },
                    vmValue = if (vmTermux.installed) {
                        vmTermux.versionName ?: unknown
                    } else {
                        notInstalled
                    },
                    deviceTone = if (hostTermux.installed) {
                        ChipTone.Neutral
                    } else {
                        ChipTone.Warning
                    },
                    vmTone = if (vmTermux.installed) ChipTone.Neutral else ChipTone.Warning,
                ),
            )
            add(
                DualDetailItem(
                    icon = Icons.Default.BugReport,
                    label = stringResource(R.string.vmfix_home_label_debuggable),
                    deviceValue = if (hostTermux.installed) {
                        yesNo(hostTermux.isDebuggable)
                    } else {
                        notInstalled
                    },
                    vmValue = if (vmTermux.installed) {
                        yesNo(vmTermux.isDebuggable)
                    } else {
                        notInstalled
                    },
                    deviceTone = if (hostTermux.installed && hostTermux.isDebuggable) {
                        ChipTone.Success
                    } else {
                        ChipTone.Warning
                    },
                    vmTone = if (vmTermux.installed && vmTermux.isDebuggable) {
                        ChipTone.Success
                    } else {
                        ChipTone.Warning
                    },
                ),
            )
            add(
                DualDetailItem(
                    icon = Icons.Default.Folder,
                    label = stringResource(R.string.vmfix_home_label_prefix),
                    deviceValue = if (hostTermux.installed) {
                        prefixValue(hostTermux.prefixPresent)
                    } else {
                        notInstalled
                    },
                    vmValue = if (vmTermux.installed) {
                        prefixValue(vmTermux.prefixPresent)
                    } else {
                        notInstalled
                    },
                    deviceTone = if (hostTermux.installed && hostTermux.prefixPresent) {
                        ChipTone.Success
                    } else {
                        ChipTone.Warning
                    },
                    vmTone = if (vmTermux.installed && vmTermux.prefixPresent) {
                        ChipTone.Success
                    } else {
                        ChipTone.Warning
                    },
                ),
            )
        }

        DualDetailsSection(
            title = title,
            rows = rows,
            modifier = modifier,
        )
        return
    }

    val rows = buildList {
        add(
            DetailItem(
                icon = Icons.Default.Android,
                label = stringResource(R.string.vmfix_home_label_version),
                value = if (vmTermux.installed) {
                    vmTermux.versionName ?: unknown
                } else {
                    notInstalled
                },
                tone = if (vmTermux.installed) ChipTone.Neutral else ChipTone.Warning,
            ),
        )

        if (vmTermux.installed) {
            add(
                DetailItem(
                    icon = Icons.Default.BugReport,
                    label = stringResource(R.string.vmfix_home_label_debuggable),
                    value = yesNo(vmTermux.isDebuggable),
                    tone = if (vmTermux.isDebuggable) ChipTone.Success else ChipTone.Warning,
                ),
            )
            add(
                DetailItem(
                    icon = Icons.Default.Folder,
                    label = stringResource(R.string.vmfix_home_label_prefix),
                    value = prefixValue(vmTermux.prefixPresent),
                    tone = if (vmTermux.prefixPresent) ChipTone.Success else ChipTone.Warning,
                ),
            )
        }
    }

    DetailsSection(
        title = stringResource(R.string.vmfix_home_section_termux_details_vm),
        rows = rows,
        modifier = modifier,
    )
}

@Composable
private fun RootDetailsSection(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile ?: return
    if (!profile.isVirtualMaster) return

    val rows = buildList {
        add(
            DetailItem(
                icon = Icons.Default.Memory,
                label = stringResource(R.string.vmfix_home_label_root_binary),
                value = yesNo(profile.hasRoot),
                tone = if (profile.hasRoot) ChipTone.Success else ChipTone.Warning,
            ),
        )

        val rootStatus = state.methods
            .firstOrNull { it.method == PrivilegeMethod.ROOT }
            ?.status
        val (rootValue, rootTone) = rootStatus.toDetail()
        add(
            DetailItem(
                icon = Icons.Default.VerifiedUser,
                label = stringResource(R.string.vmfix_home_label_root_granted),
                value = rootValue,
                tone = rootTone,
            ),
        )
    }

    DetailsSection(
        title = stringResource(R.string.vmfix_home_section_root_details),
        rows = rows,
        modifier = modifier,
    )
}

@Composable
private fun AdbDetailsSection(
    state: HomeUiState,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile ?: return
    val unknown = stringResource(R.string.vmfix_home_value_unknown)
    val notAvailable = stringResource(R.string.vmfix_home_value_not_available)
    val title = stringResource(R.string.vmfix_home_section_adb_details)
    val outsideVm = state.isRunningOutsideVm()

    if (state.isRemote && outsideVm) {
        val vmAddress = profile.address ?: unknown

        val vmAdbRunning = state.remoteConnected && profile.hasShell
        val vmAdbValue = if (vmAdbRunning) {
            stringResource(R.string.vmfix_common_yes)
        } else {
            stringResource(R.string.vmfix_common_no)
        }
        val vmAdbTone = if (vmAdbRunning) ChipTone.Success else ChipTone.Warning

        val rows = listOf(
            DetailItem(
                icon = Icons.Default.Usb,
                label = stringResource(R.string.vmfix_home_label_adbd_status),
                value = vmAdbValue,
                tone = vmAdbTone,
            ),
            DetailItem(
                icon = Icons.Default.Usb,
                label = stringResource(R.string.vmfix_home_label_vm_address),
                value = vmAddress,
                tone = if (profile.address != null) ChipTone.Neutral else ChipTone.Warning,
                mono = true,
            ),
        )

        DetailsSection(
            title = title,
            rows = rows,
            modifier = modifier,
            actionLabel = stringResource(R.string.vmfix_home_disconnect),
            onAction = onDisconnect,
        )
        return
    }

    val adbStatus = state.methods
        .firstOrNull { it.method == PrivilegeMethod.DIRECT_ADB }
        ?.status
    val (adbValue, adbTone) = adbStatus.toDetail()

    val rows = buildList {
        add(
            DetailItem(
                icon = Icons.Default.Usb,
                label = stringResource(R.string.vmfix_home_label_adb_running),
                value = adbValue,
                tone = adbTone,
            ),
        )
        add(
            DetailItem(
                icon = Icons.Default.Usb,
                label = if (state.isRemote) {
                    stringResource(R.string.vmfix_home_label_address)
                } else {
                    stringResource(R.string.vmfix_home_label_adb_port)
                },
                value = if (state.isRemote) {
                    profile.address ?: unknown
                } else {
                    profile.adbTcpPort?.toString() ?: notAvailable
                },
                tone = if (
                    (state.isRemote && profile.address != null) ||
                    (!state.isRemote && profile.adbTcpPort != null)
                ) {
                    ChipTone.Neutral
                } else {
                    ChipTone.Warning
                },
                mono = true,
            ),
        )
    }

    DetailsSection(
        title = title,
        rows = rows,
        modifier = modifier,
        actionLabel = if (state.isRemote) {
            stringResource(R.string.vmfix_home_disconnect)
        } else {
            null
        },
        onAction = if (state.isRemote) onDisconnect else null,
    )
}

@Composable
private fun DualDetailRow(item: DualDetailItem) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = item.deviceValue,
            style = if (item.mono) MonoTextStyle else MaterialTheme.typography.labelSmall,
            color = detailValueColor(item.deviceTone),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f),
        )
        Text(
            text = item.vmValue,
            style = if (item.mono) MonoTextStyle else MaterialTheme.typography.labelSmall,
            color = detailValueColor(item.vmTone),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f),
        )
    }
}

@Composable
private fun detailValueColor(tone: ChipTone): Color {
    val palette = LocalStatusPalette.current
    val scheme = MaterialTheme.colorScheme

    return when (tone) {
        ChipTone.Success -> palette.success.container
        ChipTone.Warning -> palette.warning.container
        ChipTone.Error -> scheme.error
        ChipTone.Info -> scheme.primary
        ChipTone.Neutral -> scheme.onSurface
    }
}

@Composable
private fun CompactDetailRow(item: DetailItem) {
    val scheme = MaterialTheme.colorScheme

    val valueColor = detailValueColor(item.tone)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = item.value,
            style = if (item.mono) {
                MonoTextStyle
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f),
        )
    }
}

@Composable
private fun ProviderStatus?.toDetail(): Pair<String, ChipTone> = when (this) {
    is ProviderStatus.Available ->
        stringResource(R.string.vmfix_common_yes) to ChipTone.Success

    is ProviderStatus.NeedsSetup ->
        stringResource(R.string.vmfix_action_state_needs_setup) to ChipTone.Warning

    is ProviderStatus.Unavailable ->
        stringResource(R.string.vmfix_common_no) to ChipTone.Warning

    null -> stringResource(R.string.vmfix_home_value_unknown) to ChipTone.Neutral
}

@Composable
private fun prefixValue(present: Boolean): String = stringResource(
    if (present) {
        R.string.vmfix_home_value_present
    } else {
        R.string.vmfix_home_value_missing
    },
)

@Composable
private fun yesNo(value: Boolean): String = stringResource(
    if (value) R.string.vmfix_common_yes else R.string.vmfix_common_no,
)

@Composable
private fun PatchButtonContent() {
    Icon(Icons.Default.Build, contentDescription = null)
    Text(
        text = stringResource(R.string.vmfix_home_start_patch),
        modifier = Modifier.padding(start = Spacing.sm),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HomeActionBar(
    showConnect: Boolean,
    showRepair: Boolean,
    connecting: Boolean,
    repairEnabled: Boolean,
    patchEnabled: Boolean,
    onConnect: () -> Unit,
    onRepair: () -> Unit,
    onPatch: () -> Unit,
) {
    BottomActionBar {
        if (showConnect || showRepair) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showConnect) {
                    Button(
                        onClick = onConnect,
                        enabled = !connecting,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Dimens.buttonHeight),
                    ) {
                        if (connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimens.iconSmall),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.iconSmall),
                            )
                        }
                        Text(
                            text = stringResource(R.string.vmfix_home_adb_connect_manual),
                            modifier = Modifier.padding(start = Spacing.sm),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Button(
                        onClick = onRepair,
                        enabled = repairEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Dimens.buttonHeight),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(
                            text = stringResource(R.string.vmfix_home_start_repair),
                            modifier = Modifier.padding(start = Spacing.sm),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onPatch,
                    enabled = patchEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.buttonHeight),
                ) {
                    PatchButtonContent()
                }
            }
        } else {
            Button(
                onClick = onPatch,
                enabled = patchEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.buttonHeight),
            ) {
                PatchButtonContent()
            }
        }
    }
}

@Composable
private fun WarningCard(warning: String) {
    val palette = LocalStatusPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(palette.warning.container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = palette.warning.onContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = warning,
            style = MaterialTheme.typography.bodySmall,
            color = palette.warning.onContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorCard(
    error: HomeError,
    actionLabel: String,
    onAction: () -> Unit,
) {
    MessageCard(
        severity = MessageSeverity.Error,
        title = error.text(),
        detail = error.detail(),
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
private fun HomeError.text(): String = when (this) {
    is HomeError.ReadState -> stringResource(R.string.vmfix_home_error_read_state)

    is HomeError.Connect -> stringResource(
        R.string.vmfix_home_error_connect,
        host,
        port,
        detail ?: stringResource(R.string.vmfix_home_error_connect_fallback),
    )

    is HomeError.ConnectionClosed -> stringResource(R.string.vmfix_home_error_connection_closed)

    is HomeError.InspectVm -> stringResource(
        R.string.vmfix_home_error_inspect_vm,
        detail ?: stringResource(R.string.vmfix_home_error_inspect_fallback),
    )

    is HomeError.AdbTransportUnavailable ->
        stringResource(R.string.vmfix_note_adb_transport_unavailable)

    is HomeError.RemoteNotAVm -> stringResource(
        R.string.vmfix_home_error_not_a_vm_body,
        address,
    )
}

private fun HomeError.detail(): String? =
    (this as? HomeError.ReadState)?.detail

private fun start(
    fixViewModel: FixViewModel,
    state: HomeUiState,
    viewModel: HomeViewModel,
) {
    val method = state.selectedOption?.takeIf { it.selectable }?.kind?.method ?: return
    fixViewModel.start(method, state.isRemote)
    viewModel.dismissActionDialog()
}

@Composable
private fun methodLabel(method: PrivilegeMethod?): String = when (method) {
    null -> stringResource(R.string.vmfix_method_patcher)
    PrivilegeMethod.ROOT -> stringResource(R.string.vmfix_method_root)

    PrivilegeMethod.DIRECT_ADB -> stringResource(R.string.vmfix_method_adb)
}
