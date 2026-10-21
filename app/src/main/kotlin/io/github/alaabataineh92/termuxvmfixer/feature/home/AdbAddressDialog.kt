package io.github.alaabataineh92.termuxvmfixer.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.DialogShell
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.MonoTextStyle
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing

@Composable
fun AdbAddressDialog(
    initialHost: String,
    initialPort: String,
    onDismiss: () -> Unit,
    onConfirm: (host: String, port: Int) -> Unit,
    discoveryState: AdbDiscoveryState = AdbDiscoveryState.Idle,
    onAutoDiscover: (host: String) -> Unit = {},
    onShowDiscoveredVms: () -> Unit = {},
    forRemoteVm: Boolean = false,
) {
    var host by remember { mutableStateOf(initialHost) }
    var port by remember { mutableStateOf(initialPort) }
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(discoveryState) {
        val found = discoveryState as? AdbDiscoveryState.Found ?: return@LaunchedEffect
        if (found.vms.size != 1) return@LaunchedEffect

        val only = found.vms.first()
        host = only.host
        port = only.port.toString()
        showError = false
    }

    val parsedPort = port.trim().toIntOrNull()
    val hostValid = host.isNotBlank()
    val portValid = parsedPort != null && parsedPort in 1..65535

    DialogShell(
        title = stringResource(
            if (forRemoteVm) {
                R.string.vmfix_address_dialog_title_remote
            } else {
                R.string.vmfix_address_dialog_title_local
            },
        ),
        subtitle = stringResource(
            if (forRemoteVm) {
                R.string.vmfix_address_dialog_body_remote
            } else {
                R.string.vmfix_address_dialog_body_local
            },
        ),
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it; showError = false },
            label = { Text(stringResource(R.string.vmfix_address_dialog_label_host)) },
            singleLine = true,
            isError = showError && !hostValid,
            textStyle = MonoTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit); showError = false },
            label = { Text(stringResource(R.string.vmfix_address_dialog_label_port)) },
            singleLine = true,
            isError = showError && !portValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MonoTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )

        if (showError && !portValid) {
            Text(
                text = stringResource(R.string.vmfix_address_dialog_error_port),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = when (discoveryState) {
                    AdbDiscoveryState.Searching ->
                        stringResource(R.string.vmfix_home_adb_auto_discover_scanning)

                    is AdbDiscoveryState.Found -> pluralStringResource(
                        R.plurals.vmfix_home_adb_auto_discover_found,
                        discoveryState.vms.size,
                        discoveryState.vms.size,
                    )

                    AdbDiscoveryState.NotFound ->
                        stringResource(R.string.vmfix_home_adb_auto_discover_empty)

                    else -> stringResource(R.string.vmfix_home_adb_auto_discover_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (discoveryState is AdbDiscoveryState.NotFound) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            when (discoveryState) {
                is AdbDiscoveryState.Searching -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }

                is AdbDiscoveryState.Found -> {
                    TextButton(onClick = onShowDiscoveredVms) {
                        Text(stringResource(R.string.vmfix_home_adb_view_discovered))
                    }
                }

                else -> {
                    TextButton(onClick = { onAutoDiscover(host.trim()) }) {
                        Text(stringResource(R.string.vmfix_home_adb_auto_discover))
                    }
                }
            }
        }

        Text(
            text = stringResource(
                if (forRemoteVm) {
                    R.string.vmfix_address_dialog_tip_remote
                } else {
                    R.string.vmfix_address_dialog_tip_local
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.vmfix_common_cancel))
            }
            TextButton(
                enabled = discoveryState !is AdbDiscoveryState.Searching,
                onClick = {
                    if (hostValid && portValid) {
                        onConfirm(host.trim(), parsedPort)
                    } else {
                        showError = true
                    }
                },
            ) {
                Text(stringResource(R.string.vmfix_address_dialog_connect))
            }
        }
    }
}
