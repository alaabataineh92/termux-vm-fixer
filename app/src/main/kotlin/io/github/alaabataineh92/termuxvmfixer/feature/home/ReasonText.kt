package io.github.alaabataineh92.termuxvmfixer.feature.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.model.BlockingIssue
import io.github.alaabataineh92.termuxvmfixer.core.model.Reason
import io.github.alaabataineh92.termuxvmfixer.core.model.SetupStep
import io.github.alaabataineh92.termuxvmfixer.core.model.TransportNote

@Composable
fun Reason.text(): String = when (this) {

    is Reason.AdbWrongPort -> stringResource(R.string.vmfix_reason_adb_wrong_port, port)

    else -> stringResource(id())
}

@StringRes
private fun Reason.id(): Int = when (this) {
    is Reason.Disconnected -> R.string.vmfix_action_disconnected_reason

    is Reason.RootUnprobed -> R.string.vmfix_action_root_unprobed_reason
    is Reason.AdbUnprobed -> R.string.vmfix_action_adb_unprobed_reason

    is Reason.RootNoTermux -> R.string.vmfix_reason_root_no_termux
    is Reason.RootUnreachable -> R.string.vmfix_reason_root_unreachable
    is Reason.RootAbsent -> R.string.vmfix_reason_root_absent

    is Reason.AdbNoTermux -> R.string.vmfix_reason_adb_no_termux
    is Reason.AdbNotDebuggableWithRoot -> R.string.vmfix_reason_adb_not_debuggable_root
    is Reason.AdbNotDebuggable -> R.string.vmfix_reason_adb_not_debuggable
    is Reason.AdbUnreachable -> R.string.vmfix_reason_adb_unreachable

    is Reason.PatchNotAVm -> R.string.vmfix_reason_patch_not_a_vm
    is Reason.PatchUnreadableTermux -> R.string.vmfix_reason_patch_unreadable_termux

    is Reason.SuAbsent -> R.string.vmfix_reason_su_absent
    is Reason.RootTimedOut -> R.string.vmfix_reason_root_timed_out
    is Reason.RootRefused -> R.string.vmfix_reason_root_refused

    is Reason.AdbWrongPort -> R.string.vmfix_reason_adb_wrong_port
    is Reason.AdbNothingListening -> R.string.vmfix_reason_adb_nothing_listening

    is Reason.ProbeFailed -> R.string.vmfix_reason_probe_failed
}

@Composable
fun SetupStep.text(): String = stringResource(
    when (this) {
        SetupStep.ENTER_VM_ADDRESS -> R.string.vmfix_action_disconnected_step

        SetupStep.CHECK_ROOT_AGAIN -> R.string.vmfix_action_root_unprobed_step
        SetupStep.CHECK_ADB_AGAIN -> R.string.vmfix_action_adb_unprobed_step

        SetupStep.ENABLE_DEVELOPER_OPTIONS -> R.string.vmfix_reason_adb_step_developer
        SetupStep.RUN_ADB_TCPIP -> R.string.vmfix_reason_adb_step_tcpip
        SetupStep.RETURN_AND_CHECK -> R.string.vmfix_reason_adb_step_return

        SetupStep.ALLOW_ROOT_ON_SCREEN -> R.string.vmfix_reason_root_step_check_screen
        SetupStep.GRANT_ROOT_ACCESS -> R.string.vmfix_reason_root_step_grant
    },
)

@Composable
fun TransportNote?.text(): String? = when (this) {
    null -> null

    is TransportNote.AdbFound -> {
        val named = address ?: stringResource(R.string.vmfix_note_adb_the_vm)
        val base = stringResource(R.string.vmfix_note_adb_found, named)
        base
    }

    is TransportNote.SuInstalled -> stringResource(
        if (remote) R.string.vmfix_note_su_installed_remote else R.string.vmfix_note_su_installed_local,
    )

    is TransportNote.SuAbsent -> stringResource(
        if (remote) R.string.vmfix_note_su_absent_remote else R.string.vmfix_note_su_absent_local,
    )

    is TransportNote.AdbRemoteNotDebuggable ->
        stringResource(R.string.vmfix_note_adb_remote_not_debuggable)

    is TransportNote.AdbTransportUnavailable ->
        stringResource(R.string.vmfix_note_adb_transport_unavailable)
}

@Composable
fun ActionKind.title(): String = stringResource(
    when (this) {
        ActionKind.ROOT -> R.string.vmfix_action_root_title
        ActionKind.NON_ROOT -> R.string.vmfix_action_adb_title
        ActionKind.PATCH -> R.string.vmfix_action_patch_title
    },
)

@Composable
fun ActionOption.subtitle(): String = when {
    kind == ActionKind.NON_ROOT && note != null -> note.text().orEmpty()

    kind == ActionKind.NON_ROOT -> stringResource(R.string.vmfix_action_adb_subtitle)

    state is ActionState.AlreadyDone -> stringResource(R.string.vmfix_action_patch_subtitle_already_done)

    kind == ActionKind.ROOT -> stringResource(R.string.vmfix_action_root_subtitle)

    else -> stringResource(R.string.vmfix_action_patch_subtitle)
}

@Composable
fun ActionKind.highlight(): String? = when (this) {
    ActionKind.ROOT -> stringResource(R.string.vmfix_action_root_highlight)
    ActionKind.NON_ROOT -> null
    ActionKind.PATCH -> stringResource(R.string.vmfix_action_patch_highlight)
}

@Composable
fun ActionState.chipText(): String = stringResource(
    when (this) {
        is ActionState.Ready -> R.string.vmfix_action_state_ready
        is ActionState.NeedsPermission -> R.string.vmfix_action_state_needs_setup
        is ActionState.Unavailable -> R.string.vmfix_action_state_unavailable
        is ActionState.AlreadyDone -> R.string.vmfix_action_state_already_done
    },
)

@Composable
fun BlockingIssue.text(): String = when (this) {
    is BlockingIssue.NotAVm -> when {
        !remote -> stringResource(R.string.vmfix_blocking_not_a_vm_local)

        else -> {
            val addr = address
            if (addr != null) {
                stringResource(R.string.vmfix_blocking_not_a_vm_remote, addr)
            } else {
                stringResource(R.string.vmfix_blocking_not_a_vm_remote_unnamed)
            }
        }
    }

    is BlockingIssue.TermuxAbsent -> stringResource(R.string.vmfix_blocking_termux_absent)
    is BlockingIssue.NoRouteToPrefix -> stringResource(R.string.vmfix_blocking_no_route)
    is BlockingIssue.AlreadyFixed -> stringResource(R.string.vmfix_blocking_already_fixed)
}
