package io.github.alaabataineh92.termuxvmfixer.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.common.putText
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.EmptyState
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.LogLine
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageCard
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.MessageSeverity
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.ScreenScaffold
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel
import kotlinx.coroutines.launch

@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    onShare: () -> Unit,
    viewModel: LogViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) {
            listState.animateScrollToItem(state.entries.lastIndex)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.vmfix_log_title),
        onBack = onBack,
        backLabel = stringResource(R.string.vmfix_common_back),
        actions = {
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.putText(viewModel.exportText())
                    }
                },
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.vmfix_log_copy),
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.vmfix_log_share),
                )
            }
            IconButton(onClick = viewModel::clear) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.vmfix_log_clear),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LevelSelector(
                current = state.threshold,
                onChange = viewModel::setThreshold,
            )

            if (state.droppedLines > 0) {
                DroppedNotice(state.droppedLines)
            }

            if (state.entries.isEmpty()) {
                EmptyLog()
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {

                itemsIndexed(
                    items = state.entries,
                    key = { index, entry -> "$index-${entry.timestampMillis}" },
                ) { _, entry ->

                    LogLine(
                        message = entry.message,
                        level = entry.level,
                        levelBadge = entry.level.name.take(1),
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelSelector(
    current: LogLevel,
    onChange: (LogLevel) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.vmfix_log_detail_level),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = current == level,
                    onClick = { onChange(level) },

                    label = { Text(level.name) },
                )
            }
        }
        Text(
            text = current.description(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun LogLevel.description(): String = stringResource(
    when (this) {
        LogLevel.TRACE -> R.string.vmfix_log_level_trace
        LogLevel.DEBUG -> R.string.vmfix_log_level_debug
        LogLevel.INFO -> R.string.vmfix_log_level_info
        LogLevel.WARN -> R.string.vmfix_log_level_warn
        LogLevel.ERROR -> R.string.vmfix_log_level_error
    },
)

@Composable
private fun DroppedNotice(count: Int) {
    MessageCard(
        severity = MessageSeverity.Warning,
        title = pluralStringResource(R.plurals.vmfix_log_dropped_lines, count, count),
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

@Composable
private fun EmptyLog() {
    EmptyState(
        icon = Icons.AutoMirrored.Filled.Article,
        title = stringResource(R.string.vmfix_log_empty_title),
        body = stringResource(R.string.vmfix_log_empty_body),
    )
}
