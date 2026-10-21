package io.github.alaabataineh92.termuxvmfixer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.MonoTextStyle
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing

@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (trailing == null && needsStacking(label, value)) {
        StackedValue(
            label = label,
            value = value,
            modifier = modifier,
            mono = mono,
        )
        return
    }

    Row(
        modifier = modifier.padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        } else {
            Text(
                text = value,
                style = if (mono) MonoTextStyle else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun needsStacking(label: String, value: String): Boolean {
    if (value.isEmpty()) return false

    if (value.length > STACKING_VALUE_LENGTH) return true

    return label.length + value.length > STACKING_ROW_LENGTH
}

private const val STACKING_VALUE_LENGTH = 28

private const val STACKING_ROW_LENGTH = 42

@Composable
fun StackedValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
) {
    Column(modifier = modifier.padding(vertical = Spacing.sm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (mono) MonoTextStyle else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
