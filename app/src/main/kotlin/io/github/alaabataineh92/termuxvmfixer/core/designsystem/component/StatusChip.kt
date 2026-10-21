package io.github.alaabataineh92.termuxvmfixer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.LocalStatusPalette
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.PillShape
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing

enum class ChipTone { Success, Warning, Error, Neutral, Info }

@Composable
fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = tone.defaultIcon(),
    leading: (@Composable () -> Unit)? = null,
) {
    val palette = LocalStatusPalette.current
    val scheme = MaterialTheme.colorScheme

    val (container: Color, onContainer: Color) = when (tone) {
        ChipTone.Success -> palette.success.container to palette.success.onContainer
        ChipTone.Warning -> palette.warning.container to palette.warning.onContainer
        ChipTone.Neutral -> palette.neutral.container to palette.neutral.onContainer
        ChipTone.Error -> scheme.error to scheme.onError
        ChipTone.Info -> scheme.primary to scheme.onPrimary
    }

    Row(
        modifier = modifier
            .background(container, PillShape)
            .padding(
                horizontal = Spacing.md,
                vertical = Spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        leading?.invoke()
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(ChipIconSize),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ChipIconSize = 14.dp

private fun ChipTone.defaultIcon(): ImageVector? = when (this) {
    ChipTone.Success -> Icons.Default.Check
    ChipTone.Warning -> Icons.Default.ErrorOutline
    ChipTone.Error -> Icons.Default.Close
    ChipTone.Neutral -> Icons.Default.Remove
    ChipTone.Info -> null
}
