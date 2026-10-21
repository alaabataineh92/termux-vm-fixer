package io.github.alaabataineh92.termuxvmfixer.feature.patcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.SectionCard
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.ApkStorage

@Composable
internal fun PatchOptionsDialog(
    state: PatcherUiState,
    storages: List<ApkStorage>,
    onBackUpChange: (Boolean) -> Unit,
    onStorageChange: (String) -> Unit,
    onSigningModeChange: (SigningMode) -> Unit,
    onPickKeystore: () -> Unit,
    onPatch: () -> Unit,
    onOpenFullPatcher: () -> Unit,
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
                    text = stringResource(R.string.vmfix_patcher_options_title),
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = BODY_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SectionCard(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        title = stringResource(R.string.vmfix_patcher_options_backup_title),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = state.backUp,
                                onCheckedChange = onBackUpChange,
                            )

                            Text(
                                text = stringResource(R.string.vmfix_patcher_backup_label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Text(
                            text = stringResource(R.string.vmfix_patcher_backup_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(Spacing.md))

                    SectionCard(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        title = stringResource(R.string.vmfix_patcher_where_to_save),
                    ) {
                        storages.forEach { storage ->
                            StorageRow(
                                label = storage.kind.label(),
                                selected = state.storageId == storage.id,
                                onClick = { onStorageChange(storage.id) },
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))

                    SectionCard(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        title = stringResource(R.string.vmfix_patcher_options_signing_title),
                    ) {
                        OptionRow(
                            title = stringResource(
                                R.string.vmfix_patcher_signing_termux_title,
                            ),
                            body = stringResource(
                                if (state.keepsInstalledSignature) {
                                    R.string.vmfix_patcher_signing_termux_body_matches
                                } else {
                                    R.string.vmfix_patcher_signing_termux_body
                                },
                            ),
                            selected = state.signingMode == SigningMode.TERMUX,
                            onClick = { onSigningModeChange(SigningMode.TERMUX) },
                        )

                        OptionRow(
                            title = stringResource(
                                R.string.vmfix_patcher_signing_generated_title,
                            ),
                            body = stringResource(
                                R.string.vmfix_patcher_signing_generated_body,
                            ),
                            selected = state.signingMode == SigningMode.GENERATED,
                            onClick = { onSigningModeChange(SigningMode.GENERATED) },
                        )

                        OptionRow(
                            title = stringResource(
                                R.string.vmfix_patcher_signing_own_title,
                            ),
                            body = stringResource(
                                R.string.vmfix_patcher_signing_own_body,
                            ),
                            selected = state.signingMode == SigningMode.OWN_KEYSTORE,
                            onClick = {
                                onSigningModeChange(SigningMode.OWN_KEYSTORE)
                                onPickKeystore()
                            },
                        )
                    }

                    Spacer(Modifier.height(Spacing.md))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onOpenFullPatcher) {
                        Text(stringResource(R.string.vmfix_patcher_options_full))
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.vmfix_common_cancel))
                    }

                    Spacer(Modifier.width(Spacing.sm))

                    Button(onClick = onPatch, enabled = state.canPatch) {
                        Text(stringResource(R.string.vmfix_patcher_action_patch))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val BODY_MAX_HEIGHT = 520.dp
