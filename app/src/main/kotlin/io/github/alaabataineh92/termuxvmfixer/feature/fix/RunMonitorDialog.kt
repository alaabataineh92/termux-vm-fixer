package io.github.alaabataineh92.termuxvmfixer.feature.fix

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.common.putText
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Alphas
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Dimens
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.LocalStatusPalette
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.MonoSmallTextStyle
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.PillShape
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class RunMonitorStatus { Running, Success, Failed, Cancelled }

enum class MonitorStepState { Pending, Running, Done, Failed, Skipped, Abandoned }

data class MonitorStep(
    val title: String,
    val state: MonitorStepState,
    val detail: String? = null,
)

data class MonitorLogLine(
    val message: String,
    val level: LogLevel,
    val timestampMillis: Long,
)

@Composable
fun RunMonitorDialog(
    title: String,
    methodLabel: String,
    status: RunMonitorStatus,
    steps: List<MonitorStep>,
    currentStepIndex: Int?,
    completedCount: Int,
    totalCount: Int,
    smoothedFraction: Float,
    log: List<MonitorLogLine>,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onRetry: (() -> Unit)?,
    onOpenLog: (() -> Unit)?,
    resultVisible: Boolean = false,
    resultContent: @Composable ColumnScope.() -> Unit = {},
) {
    val stopped = status != RunMonitorStatus.Running

    val activeStep = steps.getOrNull(currentStepIndex ?: -1)?.title

    Dialog(
        onDismissRequest = { if (stopped) onDone() },
        properties = DialogProperties(

            dismissOnBackPress = stopped,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.lg)
                .fillMaxHeight(FULL_HEIGHT_FRACTION),

            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                Header(
                    title = title,
                    methodLabel = methodLabel,
                    status = status,
                    stopped = stopped,
                    onClose = onClose,
                )

                if (!stopped) PhaseChip(activeStep, currentStepIndex, totalCount)

                ProgressBlock(
                    completed = completedCount,
                    total = totalCount,
                    smoothedFraction = smoothedFraction,
                )

                Timeline(
                    steps = steps,
                    live = !stopped,
                    modifier = Modifier.weight(if (resultVisible) 0.3f else 1f),
                )

                Spacer(Modifier.height(14.dp))

                LogStream(
                    lines = log,
                    activeStep = activeStep,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                if (resultVisible) {
                    Column(
                        modifier = Modifier
                            .weight(0.7f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        resultContent()
                    }
                } else {
                    resultContent()
                }

                Actions(
                    stopped = stopped,
                    succeeded = status == RunMonitorStatus.Success,
                    onCancel = onCancel,
                    onDone = onDone,
                    onRetry = onRetry,
                    onOpenLog = onOpenLog,
                )
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    methodLabel: String,
    status: RunMonitorStatus,
    stopped: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusTile(status)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (stopped) {
                Text(
                    text = methodLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(
                    if (stopped) R.string.vmfix_fix_close_plain else R.string.vmfix_fix_close_stop,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusTile(status: RunMonitorStatus) {
    val palette = LocalStatusPalette.current

    val tint = when (status) {
        RunMonitorStatus.Success -> palette.success.container
        RunMonitorStatus.Failed -> MaterialTheme.colorScheme.error
        RunMonitorStatus.Cancelled -> palette.neutral.container
        RunMonitorStatus.Running -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .size(Dimens.statusTile)
            .background(tint.copy(alpha = Alphas.TINT), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            RunMonitorStatus.Success -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(29.dp),
            )

            RunMonitorStatus.Cancelled -> Icon(
                Icons.Default.Remove,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )

            RunMonitorStatus.Failed -> Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )

            RunMonitorStatus.Running -> CircularProgressIndicator(
                modifier = Modifier.size(27.dp),
                strokeWidth = 3.dp,
                color = tint,
            )
        }
    }
}

@Composable
private fun PhaseChip(activeStep: String?, currentStepIndex: Int?, totalCount: Int) {
    if (activeStep == null || currentStepIndex == null) return

    Row(
        modifier = Modifier.padding(start = 20.dp, top = 13.dp),
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, PillShape)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
            )
            Text(
                text = stringResource(
                    R.string.vmfix_fix_phase_chip,
                    currentStepIndex + 1,
                    totalCount,
                ),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.5.sp,
                    fontSize = 10.sp,
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Text(
        text = activeStep,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 20.dp, top = 7.dp),
    )
}

@Composable
private fun ProgressBlock(
    completed: Int,
    total: Int,
    smoothedFraction: Float,
) {
    val remaining = (total - completed).coerceAtLeast(0)

    val animated by animateFloatAsState(
        targetValue = smoothedFraction,
        animationSpec = tween(durationMillis = 450),
        label = "run-progress",
    )

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.vmfix_fix_overall_progress),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.vmfix_fix_progress_percent,
                    (smoothedFraction * 100).toInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = Alphas.TRACK),
        )

        Spacer(Modifier.height(9.dp))

        Text(
            text = buildString {
                append(stringResource(R.string.vmfix_fix_steps_complete, completed, total))
                if (remaining > 0) {
                    append(stringResource(R.string.vmfix_fix_steps_remaining, remaining))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Timeline(
    steps: List<MonitorStep>,

    live: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = 16.dp,
        bottom = 2.dp,
    ),
) {
    val listState = rememberLazyListState()

    val focusIndex = when {
        live -> steps.indexOfFirst { it.state == MonitorStepState.Running }
        else -> steps.indexOfFirst { it.state == MonitorStepState.Failed }
            .takeIf { it >= 0 }
            ?: steps.indexOfLast { it.state == MonitorStepState.Running }
    }

    LaunchedEffect(focusIndex, live) {
        if (focusIndex >= 0) {
            listState.animateScrollToItem(focusIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
    ) {
        items(
            count = steps.size,
            key = { steps[it].title },
        ) { index ->
            TimelineStep(
                number = index + 1,
                step = steps[index],
                isLast = index == steps.lastIndex,
                live = live,
            )
        }
    }
}

@Composable
private fun TimelineStep(
    number: Int,
    step: MonitorStep,
    isLast: Boolean,
    live: Boolean,
) {
    val palette = LocalStatusPalette.current

    val abandoned = step.state == MonitorStepState.Abandoned
    val active = step.state == MonitorStepState.Running && live

    val (marker, markerContent) = when {
        abandoned -> palette.warning.container to palette.warning.onContainer
        step.state == MonitorStepState.Done -> palette.success.container to palette.success.onContainer
        step.state == MonitorStepState.Running ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary

        step.state == MonitorStepState.Failed ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError

        step.state == MonitorStepState.Skipped -> palette.neutral.container to palette.neutral.onContainer
        else -> MaterialTheme.colorScheme.outlineVariant to MaterialTheme.colorScheme.onSurface
    }

    val titleColor = when {
        step.state == MonitorStepState.Failed -> MaterialTheme.colorScheme.error

        abandoned -> MaterialTheme.colorScheme.onSurfaceVariant
        step.state == MonitorStepState.Pending || step.state == MonitorStepState.Skipped ->
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StepMarker(
                number = number,
                state = step.state,
                abandoned = abandoned,
                tint = marker,
                onTint = markerContent,
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (active) 34.dp else 14.dp)
                        .background(
                            if (step.state == MonitorStepState.Done) {
                                palette.success.container.copy(alpha = Alphas.BORDER)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alphas.MUTED)
                            },
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp, bottom = if (isLast) 0.dp else 5.dp)
                .then(
                    if (active) Modifier.activeStepSurface() else Modifier.padding(bottom = 3.dp),
                ),
        ) {
            Row(

                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {

                Text(
                    text = stringResource(R.string.vmfix_fix_step_headline, number, step.title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    modifier = Modifier.weight(1f),
                )
                StatusWord(step.state, live)
            }

            Text(
                text = if (abandoned) {
                    stringResource(R.string.vmfix_fix_stopped_here)
                } else {
                    step.detail.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (step.state) {
                    MonitorStepState.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Modifier.activeStepSurface(): Modifier = this
    .background(
        MaterialTheme.colorScheme.primary.copy(alpha = Alphas.SUBTLE),
        MaterialTheme.shapes.medium,
    )
    .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = Alphas.BORDER),
        shape = MaterialTheme.shapes.medium,
    )
    .padding(horizontal = Spacing.md, vertical = Spacing.sm)

@Composable
private fun StatusWord(state: MonitorStepState, live: Boolean) {
    val palette = LocalStatusPalette.current

    val (word, color) = when {
        state == MonitorStepState.Abandoned || (state == MonitorStepState.Running && !live) ->
            R.string.vmfix_fix_status_stopped to palette.warning.container

        state == MonitorStepState.Done ->
            R.string.vmfix_fix_status_done to palette.success.container

        state == MonitorStepState.Running ->
            R.string.vmfix_fix_status_active to MaterialTheme.colorScheme.primary

        state == MonitorStepState.Failed ->
            R.string.vmfix_fix_status_failed to MaterialTheme.colorScheme.error

        state == MonitorStepState.Skipped ->
            R.string.vmfix_fix_status_skipped to palette.neutral.container

        else -> R.string.vmfix_fix_status_queued to MaterialTheme.colorScheme.outline
    }

    Text(
        text = stringResource(word),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

@Composable
private fun StepMarker(
    number: Int,
    state: MonitorStepState,
    abandoned: Boolean,
    tint: Color,
    onTint: Color,
) {
    val size = Dimens.marker

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        when {

            abandoned -> Box(
                modifier = Modifier
                    .size(size)
                    .background(tint.copy(alpha = Alphas.TRACK), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(11.dp)
                        .height(2.5.dp)
                        .background(tint, RoundedCornerShape(2.dp)),
                )
            }

            state == MonitorStepState.Running -> Box(
                modifier = Modifier
                    .size(size)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = Alphas.TINT),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size),
                    strokeWidth = 2.5.dp,
                    color = tint,
                )
            }

            state == MonitorStepState.Pending -> Box(
                modifier = Modifier
                    .size(size)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alphas.LINE),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.vmfix_fix_marker_number, number),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Box(
                modifier = Modifier
                    .size(size)
                    .background(tint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (state) {
                        MonitorStepState.Done -> Icons.Default.Check
                        MonitorStepState.Failed -> Icons.Default.Close
                        else -> Icons.Default.Remove
                    },
                    contentDescription = null,

                    tint = onTint,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

private data class StreamLine(val stamp: String, val message: String, val level: LogLevel)

@Composable
internal fun LogStream(
    lines: List<MonitorLogLine>,
    activeStep: String?,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val header = stringResource(R.string.vmfix_fix_stream_header)
    val stepSuffix = activeStep?.let { stringResource(R.string.vmfix_fix_stream_line, it) }.orEmpty()

    val stream = remember(lines) {
        val zone = ZoneId.systemDefault()
        lines.takeLast(LOG_TAIL_LINES).map {
            StreamLine(
                stamp = STAMP.format(Instant.ofEpochMilli(it.timestampMillis).atZone(zone)),
                message = it.message,
                level = it.level,
            )
        }
    }

    LaunchedEffect(stream.size) {
        if (stream.isNotEmpty()) listState.scrollToItem(stream.lastIndex)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(LocalStatusPalette.current.success.container, CircleShape),
            )
            Text(
                text = header + stepSuffix,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (stream.isNotEmpty()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.putText(lines.joinToString("\n") { it.message })
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.vmfix_fix_copy),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(STREAM_HEIGHT),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            if (stream.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.vmfix_fix_waiting_output),
                        style = MonoSmallTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alphas.FAINT),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm,
                    ),
                ) {
                    items(stream.size) { index ->
                        val line = stream[index]

                        io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.LogLine(
                            message = line.message,
                            level = line.level,
                            timestamp = line.stamp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Actions(
    stopped: Boolean,
    succeeded: Boolean,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onRetry: (() -> Unit)?,
    onOpenLog: (() -> Unit)?,
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOpenLog != null) {
            TextButton(onClick = onOpenLog) { Text(stringResource(R.string.vmfix_fix_action_full_log)) }
        }

        Spacer(Modifier.weight(1f))

        when {

            !stopped -> TextButton(onClick = onCancel) {
                Text(stringResource(R.string.vmfix_fix_action_stop))
            }

            succeeded || onRetry == null -> Button(onClick = onDone) {
                Text(stringResource(R.string.vmfix_common_done))
            }

            else -> {
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.vmfix_common_close))
                }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.vmfix_common_try_again))
                }
            }
        }
    }
}

private val STREAM_HEIGHT = Dimens.streamHeight

private const val FULL_HEIGHT_FRACTION = 0.92f

private const val LOG_TAIL_LINES = 120

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
