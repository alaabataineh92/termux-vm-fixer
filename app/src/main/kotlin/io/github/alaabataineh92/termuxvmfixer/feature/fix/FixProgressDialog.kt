package io.github.alaabataineh92.termuxvmfixer.feature.fix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.LocalStatusPalette
import io.github.alaabataineh92.termuxvmfixer.core.model.AptUpdateOutcome
import io.github.alaabataineh92.termuxvmfixer.core.model.FixProgress
import io.github.alaabataineh92.termuxvmfixer.core.model.FixResult
import io.github.alaabataineh92.termuxvmfixer.core.model.StepReason
import io.github.alaabataineh92.termuxvmfixer.core.model.StepState
import io.github.alaabataineh92.termuxvmfixer.core.model.VerificationReport
import io.github.alaabataineh92.termuxvmfixer.core.model.VerifyProblem


@Composable
fun FixProgressDialog(
    progress: FixProgress?,
    result: FixResult?,
    methodLabel: String,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenLog: () -> Unit,
) {

    val status = monitorStatus(result, progress)

    RunMonitorDialog(
        title = title(result, progress),
        methodLabel = methodLabel,
        status = status,
        steps = progress?.steps?.map { it.toMonitor() }.orEmpty(),
        currentStepIndex = progress?.currentStepIndex(),
        completedCount = progress?.completedCount ?: 0,
        totalCount = progress?.totalCount ?: 0,
        smoothedFraction = progress?.smoothedFraction ?: 0f,
        log = progress?.log?.map {
            MonitorLogLine(
                message = it.message,
                level = it.level,
                timestampMillis = it.timestampMillis,
            )
        }.orEmpty(),
        onClose = {

            if (status == RunMonitorStatus.Running) {
                onCancel()
            } else {
                onDismiss()
            }
        },
        onCancel = onCancel,
        onDone = onDismiss,
        onRetry = onRetry,
        onOpenLog = onOpenLog,
    ) {
        ResultBlock(result)
    }
}

private fun monitorStatus(
    result: FixResult?,
    progress: FixProgress?,
): RunMonitorStatus = when (result) {
    is FixResult.Success -> RunMonitorStatus.Success
    is FixResult.Cancelled -> RunMonitorStatus.Cancelled
    is FixResult.Incomplete, is FixResult.TransportFailed -> RunMonitorStatus.Failed

    null -> if (progress?.isRunning == false) {
        RunMonitorStatus.Cancelled
    } else {
        RunMonitorStatus.Running
    }
}

@Composable
private fun title(result: FixResult?, progress: FixProgress?): String = stringResource(
    when (result) {
        is FixResult.Success -> R.string.vmfix_fix_outcome_repaired
        is FixResult.Incomplete -> R.string.vmfix_fix_outcome_incomplete
        is FixResult.TransportFailed -> R.string.vmfix_fix_outcome_transport_failed
        is FixResult.Cancelled -> R.string.vmfix_fix_outcome_cancelled
        null -> if (progress?.isRunning == true) {
            R.string.vmfix_fix_title_running
        } else {
            R.string.vmfix_fix_title_preparing
        }
    },
)

private fun FixProgress.currentStepIndex(): Int? =
    steps.indexOfFirst { it.state is StepState.Running }.takeIf { it >= 0 }

@Composable
private fun io.github.alaabataineh92.termuxvmfixer.core.model.StepProgress.toMonitor(): MonitorStep = MonitorStep(
    title = step.title(),
    state = when (state) {
        is StepState.Pending -> MonitorStepState.Pending
        is StepState.Running -> MonitorStepState.Running
        is StepState.Done -> MonitorStepState.Done
        is StepState.Failed -> MonitorStepState.Failed
        is StepState.Skipped -> MonitorStepState.Skipped
    },
    detail = when (val s = state) {
        is StepState.Failed -> s.reason.text()
        is StepState.Skipped -> s.reason.text()
        is StepState.Done -> s.note ?: stringResource(R.string.vmfix_fix_note_complete)
        is StepState.Running -> step.description()
        is StepState.Pending -> stringResource(R.string.vmfix_fix_detail_waiting)
    },
)

@Composable
private fun ResultBlock(result: FixResult?) {
    when (result) {
        is FixResult.Success -> VerificationSummary(result.report)

        is FixResult.Incomplete -> Problems(
            result.problems.ifEmpty { listOf(StepReason.RunFailed) },
        )

        is FixResult.TransportFailed -> Problems(
            listOf(StepReason.TransportUnavailable(result.error.message)),
        )

        is FixResult.Cancelled, null -> Unit
    }
}

@Composable
private fun VerificationSummary(report: VerificationReport) {
    val ok = LocalStatusPalette.current.success.container

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CheckRow(
            label = stringResource(R.string.vmfix_fix_label_login_hook),
            value = stringResource(
                if (report.loginHookLoaded) {
                    R.string.vmfix_fix_value_loaded
                } else {
                    R.string.vmfix_fix_value_not_loaded
                },
            ),
            good = report.loginHookLoaded,
            okColor = ok,
        )

        CheckRow(
            label = stringResource(R.string.vmfix_fix_label_apt_update),
            value = when (val apt = report.aptUpdate) {
                is AptUpdateOutcome.Ok -> stringResource(R.string.vmfix_fix_value_ok)

                is AptUpdateOutcome.Failed ->
                    VerifyProblem.AptFailed(apt.reason).text()

                is AptUpdateOutcome.TimedOut ->
                    stringResource(R.string.vmfix_fix_value_apt_timed_out, apt.seconds)
            },
            good = report.aptUpdate is AptUpdateOutcome.Ok,
            okColor = ok,
        )

        CheckRow(
            label = stringResource(R.string.vmfix_fix_label_broken_packages),
            value = report.brokenPackages.size.toString(),
            good = report.brokenPackages.isEmpty(),
            okColor = ok,
        )

        CheckRow(
            label = stringResource(R.string.vmfix_fix_label_broken_symlinks),
            value = report.brokenSymlinks?.toString()
                ?: stringResource(R.string.vmfix_fix_value_unknown),
            good = report.brokenSymlinks == 0,
            okColor = ok,
        )
    }
}

@Composable
private fun CheckRow(
    label: String,
    value: String,
    good: Boolean,
    okColor: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (good) Icons.Default.Check else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (good) okColor else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Problems(problems: List<StepReason>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        problems.forEach { problem ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp),
                )
                Column {
                    Text(
                        text = problem.text(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    problem.detail()?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
