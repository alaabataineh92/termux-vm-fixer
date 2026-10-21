package io.github.alaabataineh92.termuxvmfixer.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.SectionCard
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.StoredApk
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.VersionedApk

@Composable
internal fun ApkListDialog(
    title: String,
    emptyText: String,
    apks: List<VersionedApk>,
    canInstall: Boolean,
    onShare: (StoredApk) -> Unit,
    onDelete: (StoredApk) -> Unit,
    onInstall: (StoredApk) -> Unit,
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
                .padding(horizontal = Spacing.md, vertical = Spacing.lg),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = 12.dp,
                    ),
                )

                if (apks.isEmpty()) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 20.dp,
                        ),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = LIST_MAX_HEIGHT),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(apks, key = { it.apk.reference }) { entry ->
                            ApkRow(
                                entry = entry,
                                canInstall = canInstall,
                                onShare = { onShare(entry.apk) },
                                onDelete = { onDelete(entry.apk) },
                                onInstall = { onInstall(entry.apk) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.vmfix_common_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkRow(
    entry: VersionedApk,
    canInstall: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onInstall: () -> Unit,
) {
    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.apk.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                val version = entry.version

                Text(
                    text = if (version != null) {
                        stringResource(R.string.vmfix_patcher_source_version, version) +
                            " · " + entry.apk.sizeLabel
                    } else {
                        entry.apk.sizeLabel
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (canInstall) {
                IconButton(onClick = onInstall) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(
                            R.string.vmfix_home_apks_install,
                        ),
                    )
                }
            }

            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.vmfix_home_apks_share),
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.vmfix_home_apks_delete),
                )
            }
        }
    }
}

@Composable
internal fun InstallOnVmDialog(
    apkName: String,
    installedVersion: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vmfix_home_install_title)) },
        text = {
            Text(
                text = if (installedVersion != null) {
                    stringResource(
                        R.string.vmfix_home_install_body,
                        installedVersion,
                        apkName,
                    )
                } else {
                    stringResource(R.string.vmfix_home_install_body_unknown, apkName)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.vmfix_home_install_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.vmfix_common_cancel))
            }
        },
    )
}

private val LIST_MAX_HEIGHT = 460.dp
