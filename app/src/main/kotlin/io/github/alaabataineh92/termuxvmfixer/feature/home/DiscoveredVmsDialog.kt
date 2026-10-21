package io.github.alaabataineh92.termuxvmfixer.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.DialogShell
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing
import io.github.alaabataineh92.termuxvmfixer.core.model.DiscoveredVm

@Composable
fun DiscoveredVmsDialog(
    discoveryState: AdbDiscoveryState,
    onDismiss: () -> Unit,
    onScanAgain: () -> Unit,
    onSelect: (DiscoveredVm) -> Unit,
) {
    DialogShell(
        title = stringResource(R.string.vmfix_discovered_dialog_title),
        subtitle = stringResource(R.string.vmfix_discovered_dialog_body),
        onDismiss = onDismiss,
    ) {
        when (discoveryState) {
            AdbDiscoveryState.Idle -> {
                Text(
                    text = stringResource(R.string.vmfix_home_adb_auto_discover_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AdbDiscoveryState.Searching -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.vmfix_home_adb_auto_discover_scanning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AdbDiscoveryState.NotFound -> {
                Text(
                    text = stringResource(R.string.vmfix_discovered_dialog_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is AdbDiscoveryState.Found -> {
                Text(
                    text = pluralStringResource(
                        R.plurals.vmfix_home_adb_auto_discover_found,
                        discoveryState.vms.size,
                        discoveryState.vms.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    discoveryState.vms.forEach { vm ->
                        DiscoveredVmRow(
                            vm = vm,
                            onClick = { onSelect(vm) },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                enabled = discoveryState !is AdbDiscoveryState.Searching,
                onClick = onScanAgain,
            ) {
                Text(stringResource(R.string.vmfix_discovered_dialog_scan_again))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.vmfix_common_close))
            }
        }
    }
}

@Composable
private fun DiscoveredVmRow(
    vm: DiscoveredVm,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.sm,
                vertical = Spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = if (vm.authorized) {
                    Icons.Default.Android
                } else {
                    Icons.Default.WarningAmber
                },
                contentDescription = null,
                tint = if (vm.authorized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(20.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = vm.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.vmfix_home_adb_discovered_vm_port,
                        vm.port,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!vm.authorized) {
                    Text(
                        text = stringResource(R.string.vmfix_discovered_dialog_auth_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
