package io.github.alaabataineh92.termuxvmfixer.feature.patcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.common.putText
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.ChipTone
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.LabeledValue
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageCard
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageSeverity
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.StatusChip
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Alphas
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Dimens
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.LocalStatusPalette
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.MonoTextStyle
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing
import io.github.alaabataineh92.termuxvmfixer.feature.fix.LogStream
import io.github.alaabataineh92.termuxvmfixer.feature.fix.Timeline
import kotlinx.coroutines.launch

@Composable
internal fun PatchResultDialog(
    state: PatcherUiState,
    result: PatcherUiState.PatchSuccess,
    showInstall: Boolean,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onReinstallOnVm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.lg)
                .fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ResultHeader(
                    result = result,
                    onClose = onDismiss,
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.notice != PatcherNotice.NONE) {
                        MessageCard(
                            severity = MessageSeverity.Success,
                            title = stringResource(
                                R.string.vmfix_patcher_notice_installed_vm,
                            ),
                        )

                        Spacer(Modifier.height(Spacing.md))
                    }

                    state.error?.let { reason ->
                        MessageCard(
                            severity = MessageSeverity.Error,
                            title = stringResource(R.string.vmfix_patcher_error_title),
                            body = reason.text(state.errorDetail),
                            detail = state.errorDetail
                                ?.takeIf { !reason.namesDetail }
                                ?.let { reason.detailText(it) },
                            actionLabel = if (reason == PatcherError.REMOTE_SIGNATURE_MISMATCH) {
                                stringResource(R.string.vmfix_patcher_action_reinstall_on_vm)
                            } else {
                                null
                            },
                            onAction = if (reason == PatcherError.REMOTE_SIGNATURE_MISMATCH) {
                                onReinstallOnVm
                            } else {
                                null
                            },
                        )

                        Spacer(Modifier.height(Spacing.md))
                    }

                    ResultFileCard(result)

                    Spacer(Modifier.height(Spacing.md))

                    ChecksCard(result)

                    Spacer(Modifier.height(Spacing.md))

                    RunDetailSection(state)
                }

                ResultActions(
                    showInstall = showInstall,
                    canInstall = state.installOverAdb || state.canInstallPackages,
                    installing = state.isInstalling,
                    onInstall = onInstall,
                    onShare = onShare,
                    onDelete = onDelete,
                    onDone = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ResultHeader(
    result: PatcherUiState.PatchSuccess,
    onClose: () -> Unit,
) {
    val palette = LocalStatusPalette.current
    val passed = result.report.allChecksPassed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.statusTile)
                .background(
                    if (passed) {
                        palette.success.container.copy(alpha = Alphas.TINT)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = Alphas.TINT)
                    },
                    MaterialTheme.shapes.medium,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (passed) Icons.Default.Check else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (passed) palette.success.container else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(29.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.vmfix_patcher_outcome_done),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = stringResource(R.string.vmfix_method_patcher),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.vmfix_fix_close_plain),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultFileCard(result: PatcherUiState.PatchSuccess) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = result.apk.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                StatusChip(
                    text = stringResource(
                        if (result.report.allChecksPassed) {
                            R.string.vmfix_patcher_result_verified
                        } else {
                            R.string.vmfix_patcher_result_check_failed
                        },
                    ),
                    tone = if (result.report.allChecksPassed) {
                        ChipTone.Success
                    } else {
                        ChipTone.Error
                    },
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            LabeledValue(
                stringResource(R.string.vmfix_patcher_result_label_size),
                result.apk.sizeLabel,
            )

            LabeledValue(
                stringResource(R.string.vmfix_patcher_result_label_signed_with),
                result.signing.text(),
                mono = false,
            )

            LabeledValue(
                stringResource(R.string.vmfix_patcher_result_label_symlinks),
                result.report.symlinksRewritten.toString(),
            )

            result.backup?.let {
                LabeledValue(
                    stringResource(R.string.vmfix_patcher_result_label_backup),
                    it.name,
                    mono = false,
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = stringResource(R.string.vmfix_patcher_result_label_saved_to),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.xs))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = Spacing.md,
                        end = Spacing.xs,
                        top = Spacing.sm,
                        bottom = Spacing.sm,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = result.apk.location,
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(onClick = {
                        scope.launch {
                            clipboard.putText(result.apk.location)
                        }
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(
                                R.string.vmfix_patcher_result_copy_path,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecksCard(result: PatcherUiState.PatchSuccess) {
    var expanded by remember { mutableStateOf(false) }

    val checks = result.report.checks
    val passed = checks.count { it.passed }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = if (result.report.allChecksPassed) {
                        Icons.Default.Check
                    } else {
                        Icons.Default.ErrorOutline
                    },
                    contentDescription = null,
                    tint = if (result.report.allChecksPassed) {
                        LocalStatusPalette.current.success.container
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(18.dp),
                )

                Text(
                    text = stringResource(
                        R.string.vmfix_patcher_result_checks_summary,
                        passed,
                        checks.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                if (checks.size > 1) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = stringResource(
                                if (expanded) {
                                    R.string.vmfix_patcher_result_checks_hide
                                } else {
                                    R.string.vmfix_patcher_result_checks_show
                                },
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = Alphas.LINE,
                        ),
                    )

                    Spacer(Modifier.height(Spacing.xs))

                    checks.forEach { check ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Icon(
                                imageVector = if (check.passed) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.ErrorOutline
                                },
                                contentDescription = null,
                                tint = if (check.passed) {
                                    LocalStatusPalette.current.success.container
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(14.dp),
                            )

                            Text(
                                text = stringResource(
                                    R.string.vmfix_patcher_result_check_row,
                                    check.kind.label(),
                                    check.detail,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunDetailSection(state: PatcherUiState) {
    var expanded by remember { mutableStateOf(false) }

    val steps = state.orderedSteps.map { it.toMonitor() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(Dimens.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.vmfix_patcher_result_steps_show),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = stringResource(
                            if (expanded) {
                                R.string.vmfix_patcher_result_steps_hide
                            } else {
                                R.string.vmfix_patcher_result_steps_show
                            },
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.sm))

                    Timeline(
                        steps = steps,
                        live = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 2.dp),
                    )

                    Spacer(Modifier.height(Spacing.md))

                    LogStream(
                        lines = state.log,
                        activeStep = null,
                    )
                }
            }
        }
    }
}

@Composable
internal fun VmReplaceDialog(
    version: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vmfix_patcher_replace_title)) },
        text = {
            Text(
                text = if (version != null) {
                    stringResource(R.string.vmfix_patcher_replace_body, version)
                } else {
                    stringResource(R.string.vmfix_patcher_replace_body_unknown)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.vmfix_patcher_replace_confirm))
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
private fun ResultActions(
    showInstall: Boolean,
    canInstall: Boolean,
    installing: Boolean,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alphas.LINE),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (showInstall) {
                    Button(
                        onClick = onInstall,
                        enabled = !installing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (installing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }

                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = stringResource(
                                R.string.vmfix_patcher_action_install_on_vm,
                            ),
                        )
                    }

                    if (!canInstall && !installing) {
                        Text(
                            text = stringResource(R.string.vmfix_patcher_result_install_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.vmfix_patcher_action_share))
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.vmfix_patcher_action_delete))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))

                    Button(onClick = onDone) {
                        Text(stringResource(R.string.vmfix_common_done))
                    }
                }
            }
        }
    }
}
