package io.github.alaabataineh92.termuxvmfixer.feature.fix

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.model.FixStep
import io.github.alaabataineh92.termuxvmfixer.core.model.StepReason
import io.github.alaabataineh92.termuxvmfixer.core.model.VerifyProblem

@Composable
fun FixStep.title(): String = stringResource(
    when (this) {
        FixStep.CONNECT -> R.string.vmfix_fix_step_connect_title
        FixStep.OBTAIN_APK -> R.string.vmfix_fix_step_obtain_apk_title
        FixStep.BUILD_ARCHIVE -> R.string.vmfix_fix_step_build_archive_title
        FixStep.STOP_TERMUX -> R.string.vmfix_fix_step_stop_termux_title
        FixStep.UPLOAD -> R.string.vmfix_fix_step_upload_title
        FixStep.REBUILD_PREFIX -> R.string.vmfix_fix_step_rebuild_prefix_title
        FixStep.INSTALL_LIBRARY -> R.string.vmfix_fix_step_install_library_title
        FixStep.INSTALL_HOOKS -> R.string.vmfix_fix_step_install_hooks_title
        FixStep.REPAIR_SYMLINKS -> R.string.vmfix_fix_step_repair_symlinks_title
        FixStep.COMPLETE_PACKAGES -> R.string.vmfix_fix_step_complete_packages_title
        FixStep.VERIFY -> R.string.vmfix_fix_step_verify_title
    },
)

@Composable
fun FixStep.description(): String = stringResource(
    when (this) {
        FixStep.CONNECT -> R.string.vmfix_fix_step_connect_description
        FixStep.OBTAIN_APK -> R.string.vmfix_fix_step_obtain_apk_description
        FixStep.BUILD_ARCHIVE -> R.string.vmfix_fix_step_build_archive_description
        FixStep.STOP_TERMUX -> R.string.vmfix_fix_step_stop_termux_description
        FixStep.UPLOAD -> R.string.vmfix_fix_step_upload_description
        FixStep.REBUILD_PREFIX -> R.string.vmfix_fix_step_rebuild_prefix_description
        FixStep.INSTALL_LIBRARY -> R.string.vmfix_fix_step_install_library_description
        FixStep.INSTALL_HOOKS -> R.string.vmfix_fix_step_install_hooks_description
        FixStep.REPAIR_SYMLINKS -> R.string.vmfix_fix_step_repair_symlinks_description
        FixStep.COMPLETE_PACKAGES -> R.string.vmfix_fix_step_complete_packages_description
        FixStep.VERIFY -> R.string.vmfix_fix_step_verify_description
    },
)

@Composable
fun StepReason.text(): String = when (this) {

    is StepReason.PrefixIncomplete ->
        stringResource(R.string.vmfix_fix_error_prefix_incomplete, missing.joinToString(", "))

    StepReason.NoRoot -> stringResource(R.string.vmfix_fix_skipped_no_root)
    StepReason.BootstrapIncomplete -> stringResource(R.string.vmfix_fix_error_bootstrap_incomplete)
    StepReason.RunFailed -> stringResource(R.string.vmfix_fix_error_run_failed)
    is StepReason.TransportUnavailable ->
        stringResource(R.string.vmfix_fix_error_transport_unavailable)

    is StepReason.Problem -> problem.text()
}

@Composable
fun StepReason.detail(): String? = when (this) {

    is StepReason.TransportUnavailable ->
        this.detail?.let { stringResource(R.string.vmfix_fix_error_transport_detail, it) }

    else -> null
}

@Composable
fun VerifyProblem.text(): String = when (this) {
    VerifyProblem.CheckIncomplete -> stringResource(R.string.vmfix_verify_check_incomplete)
    VerifyProblem.LoginHookNotLoaded -> stringResource(R.string.vmfix_verify_login_hook)
    is VerifyProblem.AptFailed -> stringResource(R.string.vmfix_verify_apt_failed, reason)
    is VerifyProblem.AptTimedOut -> stringResource(R.string.vmfix_verify_apt_timed_out, seconds)

    is VerifyProblem.BrokenSymlinks ->
        pluralStringResource(R.plurals.vmfix_verify_broken_symlinks, count, count)

    VerifyProblem.LibraryMissing -> stringResource(R.string.vmfix_verify_library_missing)
    is VerifyProblem.ShimMissing -> stringResource(R.string.vmfix_verify_shim_missing, name)
    is VerifyProblem.PrefixDirMissing ->
        stringResource(R.string.vmfix_verify_prefix_dir_missing, dir)

    is VerifyProblem.PackagesUnconfigured ->
        stringResource(R.string.vmfix_verify_packages_unconfigured, names.joinToString(", "))
}
