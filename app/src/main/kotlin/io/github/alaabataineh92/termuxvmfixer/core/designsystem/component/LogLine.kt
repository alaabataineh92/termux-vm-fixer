package io.github.alaabataineh92.termuxvmfixer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Alphas
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.LocalStatusPalette
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.MonoSmallTextStyle
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel

@Composable
fun LogLevel.color(): Color = when (this) {
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
    LogLevel.WARN -> LocalStatusPalette.current.warning.container

    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.TRACE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alphas.FAINT)
}

@Composable
fun LogLine(
    message: String,
    level: LogLevel,
    modifier: Modifier = Modifier,
    timestamp: String? = null,
    levelBadge: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (timestamp != null) {
            Text(
                text = timestamp,
                style = MonoSmallTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alphas.FAINT),
            )
        }
        if (levelBadge != null) {
            Text(
                text = levelBadge,
                style = MonoSmallTextStyle,
                color = level.color(),
            )
        }
        Text(
            text = message,
            style = MonoSmallTextStyle,
            color = level.color(),
            modifier = Modifier.weight(1f),
        )
    }
}
