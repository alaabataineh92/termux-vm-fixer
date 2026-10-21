package io.github.alaabataineh92.termuxvmfixer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Alphas
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Dimens
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing

enum class MessageSeverity { Info, Success, Warning, Error }

@Composable
fun MessageCard(
    severity: MessageSeverity,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.LocalStatusPalette.current

    val (container: Color, onContainer: Color) = when (severity) {
        MessageSeverity.Info -> scheme.secondaryContainer to scheme.onSecondaryContainer
        MessageSeverity.Success -> palette.success.container to palette.success.onContainer
        MessageSeverity.Warning -> palette.warning.container to palette.warning.onContainer
        MessageSeverity.Error -> scheme.errorContainer to scheme.onErrorContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = severity.icon(),
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(Dimens.icon),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                )
                body?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = Alphas.MUTED),
                    )
                }
                detail?.let {
                    Text(
                        text = it,
                        style = io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.MonoSmallTextStyle,
                        color = onContainer.copy(alpha = Alphas.MUTED),
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 0.dp,
                            vertical = Spacing.xs,
                        ),
                    ) {
                        Text(text = actionLabel)
                    }
                }
            }
        }
    }
}

private fun MessageSeverity.icon(): ImageVector = when (this) {
    MessageSeverity.Info -> Icons.Default.Info
    MessageSeverity.Success -> Icons.Default.CheckCircle
    MessageSeverity.Warning -> Icons.Default.WarningAmber
    MessageSeverity.Error -> Icons.Default.ErrorOutline
}
