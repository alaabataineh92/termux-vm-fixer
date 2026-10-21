package io.github.alaabataineh92.termuxvmfixer.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.ChipTone
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.DialogShell
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.StatusChip
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Alphas
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.AppMotion
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Dimens
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing

@Composable
fun TransportDialog(

    options: List<ActionOption>,
    selected: ActionKind?,
    onSelect: (ActionKind) -> Unit,

    onSetAdbAddress: (() -> Unit)?,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedOption = options.firstOrNull { it.kind == selected }

    DialogShell(
        title = stringResource(R.string.vmfix_action_dialog_title),
        subtitle = stringResource(R.string.vmfix_action_dialog_body),
        onDismiss = onDismiss,
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(options.size, key = { options[it].kind.name }) { index ->
                val option = options[index]
                TransportRow(
                    option = option,
                    selected = option.kind == selected,
                    onSelect = { onSelect(option.kind) },

                    onSetAdbAddress = if (option.kind == ActionKind.NON_ROOT) {
                        onSetAdbAddress
                    } else {
                        null
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.vmfix_common_cancel))
            }
            Button(
                onClick = onStart,

                enabled = selectedOption?.selectable == true,
            ) {
                Text(stringResource(R.string.vmfix_action_dialog_start))
            }
        }
    }
}

@Composable
private fun TransportRow(
    option: ActionOption,
    selected: Boolean,
    onSelect: () -> Unit,
    onSetAdbAddress: (() -> Unit)?,
) {
    val enabled = option.selectable
    val scheme = MaterialTheme.colorScheme

    val container by animateColorAsState(
        targetValue = when {
            selected -> scheme.primaryContainer
            else -> scheme.surface.copy(alpha = 0f)
        },
        animationSpec = AppMotion.effect(),
        label = "transport-row-container",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {

            Box(
                modifier = Modifier
                    .size(Dimens.iconContainer)
                    .background(
                        color = if (selected) {
                            scheme.onPrimaryContainer.copy(alpha = Alphas.SUBTLE)
                        } else {
                            scheme.surfaceContainerHigh
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = option.kind.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.icon),
                    tint = when {
                        selected -> scheme.onPrimaryContainer
                        enabled -> scheme.onSurface
                        else -> scheme.onSurfaceVariant
                    },
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.kind.title(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                )
                Text(
                    text = option.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        scheme.onPrimaryContainer.copy(alpha = Alphas.MUTED)
                    } else {
                        scheme.onSurfaceVariant
                    },
                )
            }

            SelectionIndicator(selected = selected, option = option)
        }

        val chips = buildList {
            if (option.recommended) {
                add(stringResource(R.string.vmfix_action_recommended) to ChipTone.Success)
            }
            option.kind.highlight()
                ?.takeIf { enabled }
                ?.let { add(it to if (option.kind == ActionKind.ROOT) ChipTone.Success else ChipTone.Info) }
        }

        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chips.forEach { (text, tone) ->
                    StatusChip(text = text, tone = tone, icon = null)
                }
            }
        }

        StateDetail(option.state)

        if (onSetAdbAddress != null) {
            InlineAction(
                label = stringResource(R.string.vmfix_home_set_address_manually),
                onClick = onSetAdbAddress,
            )
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, option: ActionOption) {
    when {
        selected -> Box(
            modifier = Modifier
                .size(Dimens.icon)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        }

        option.selectable -> Box(
            modifier = Modifier
                .size(Dimens.icon)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    CircleShape,
                ),
        )

        else -> option.state.StateChip()
    }
}

@Composable
private fun StateDetail(state: ActionState) {
    when (state) {
        is ActionState.Ready -> Unit

        is ActionState.NeedsPermission -> Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = state.reason.text(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = Alphas.MUTED,
                        ),
                    )
                    Text(

                        text = step.text(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is ActionState.Unavailable -> Text(
            text = state.reason.text(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        is ActionState.AlreadyDone -> Text(
            text = stringResource(R.string.vmfix_action_already_done_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionState.StateChip() {
    val tone = when (this) {
        is ActionState.Ready -> ChipTone.Success
        is ActionState.NeedsPermission -> ChipTone.Warning
        is ActionState.Unavailable -> ChipTone.Neutral
        is ActionState.AlreadyDone -> ChipTone.Success
    }

    StatusChip(
        text = chipText(),
        tone = tone,
        icon = when (this) {
            is ActionState.AlreadyDone -> Icons.Default.Check
            is ActionState.Unavailable -> Icons.Default.Remove
            else -> null
        },
    )
}

@Composable
fun InlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) { Text(label) }
}

private fun ActionKind.icon(): ImageVector = when (this) {
    ActionKind.ROOT -> Icons.Default.Lock
    ActionKind.NON_ROOT -> Icons.Default.Usb
    ActionKind.PATCH -> Icons.Default.Build
}
