package io.github.alaabataineh92.termuxvmfixer.feature.patcher

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.device.apk.ApkStorageKind
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchStep
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.VerifyCheckKind
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.SigningIdentity
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.SigningKeySource

@Composable
fun PatchStep.title(): String = stringResource(
    when (this) {
        PatchStep.READ_APK -> R.string.vmfix_patcher_step_read_apk
        PatchStep.EXTRACT_BOOTSTRAP -> R.string.vmfix_patcher_step_extract_bootstrap
        PatchStep.REWRITE_SYMLINKS -> R.string.vmfix_patcher_step_rewrite_symlinks
        PatchStep.INJECT_PAYLOAD -> R.string.vmfix_patcher_step_inject_payload
        PatchStep.REWRITE_SECOND_STAGE -> R.string.vmfix_patcher_step_rewrite_second_stage
        PatchStep.REBUILD_ZIP -> R.string.vmfix_patcher_step_rebuild_zip
        PatchStep.VERIFY -> R.string.vmfix_patcher_step_verify
        PatchStep.REPACK_APK -> R.string.vmfix_patcher_step_repack_apk
        PatchStep.SIGN -> R.string.vmfix_patcher_step_sign
        PatchStep.DONE -> R.string.vmfix_patcher_step_done
    },
)

@Composable
fun PatcherError.text(detail: String?): String = when (this) {
    PatcherError.APK_UNREADABLE -> stringResource(
        R.string.vmfix_patcher_error_apk_unreadable,
        detail.orEmpty(),
    )

    PatcherError.INSTALL_APK_MISSING -> stringResource(
        R.string.vmfix_install_error_no_apk,
        detail.orEmpty(),
    )

    PatcherError.INSTALL_STATUS -> stringResource(
        R.string.vmfix_install_error_status,
        detail.orEmpty(),
    )

    else -> stringResource(id())
}

@Composable
fun PatcherError.detailText(detail: String): String = when (this) {
    PatcherError.PATCH_BACKUP_FAILED,
    PatcherError.PATCH_NOT_SAVED,
    PatcherError.PATCH_CANCELLED,
    PatcherError.PATCH_FAILED,
    -> stringResource(R.string.vmfix_patch_error_detail, detail)

    PatcherError.APK_UNREADABLE,
    PatcherError.INSTALL_APK_MISSING,
    PatcherError.INSTALL_NOT_ALLOWED,
    PatcherError.INSTALL_NO_SESSION,
    PatcherError.INSTALL_FAILED,
    PatcherError.INSTALL_STATUS,
    PatcherError.INSTALL_NEEDS_UNINSTALL,
    PatcherError.REMOTE_STAGING_FAILED,
    PatcherError.REMOTE_COPY_FAILED,
    PatcherError.REMOTE_SIGNATURE_MISMATCH,
    PatcherError.REMOTE_VERSION_DOWNGRADE,
    PatcherError.REMOTE_INSTALL_FAILED,
    -> stringResource(R.string.vmfix_install_error_detail, detail)

    else -> detail
}

@StringRes
private fun PatcherError.id(): Int = when (this) {
    PatcherError.BLOCKED -> R.string.vmfix_patcher_error_blocked
    PatcherError.PICKED_UNREADABLE -> R.string.vmfix_patcher_error_picked_unreadable
    PatcherError.KEYSTORE_OPEN -> R.string.vmfix_patcher_error_keystore_open
    PatcherError.INSTALLED_UNREADABLE -> R.string.vmfix_patcher_error_installed_unreadable
    PatcherError.NO_KEYSTORE -> R.string.vmfix_patcher_error_no_keystore
    PatcherError.NO_APK -> R.string.vmfix_patcher_error_no_apk
    PatcherError.NO_VM -> R.string.vmfix_patcher_error_no_vm
    PatcherError.VM_GONE -> R.string.vmfix_patcher_error_vm_gone

    PatcherError.PATCH_BACKUP_FAILED -> R.string.vmfix_patch_error_backup
    PatcherError.PATCH_NOT_SAVED -> R.string.vmfix_patch_error_save
    PatcherError.PATCH_CANCELLED -> R.string.vmfix_patch_error_cancelled
    PatcherError.PATCH_FAILED -> R.string.vmfix_patch_error_failed

    PatcherError.INSTALL_NOT_ALLOWED -> R.string.vmfix_install_error_not_allowed
    PatcherError.INSTALL_NO_SESSION -> R.string.vmfix_install_error_no_session
    PatcherError.INSTALL_FAILED -> R.string.vmfix_install_error_failed
    PatcherError.INSTALL_NEEDS_UNINSTALL -> R.string.vmfix_install_error_needs_uninstall

    PatcherError.REMOTE_STAGING_FAILED -> R.string.vmfix_install_error_remote_staging
    PatcherError.REMOTE_COPY_FAILED -> R.string.vmfix_install_error_remote_copy
    PatcherError.REMOTE_SIGNATURE_MISMATCH -> R.string.vmfix_install_error_remote_signature
    PatcherError.REMOTE_VERSION_DOWNGRADE -> R.string.vmfix_install_error_remote_newer
    PatcherError.REMOTE_INSTALL_FAILED -> R.string.vmfix_install_error_remote_failed

    PatcherError.APK_UNREADABLE,
    PatcherError.INSTALL_APK_MISSING,
    PatcherError.INSTALL_STATUS,
    -> error("the sentence for $this names a value; use text(detail)")
}

fun SigningIdentity.asLabel(): SigningLabel = SigningLabel(source = source, alias = alias)

@Composable
fun SigningLabel.text(): String = when (source) {
    SigningKeySource.TERMUX -> stringResource(R.string.vmfix_keystore_termux_key)
    SigningKeySource.GENERATED -> stringResource(R.string.vmfix_keystore_generated_key)
    SigningKeySource.USER -> stringResource(R.string.vmfix_keystore_yours, alias.orEmpty())
}

@Composable
fun VerifyCheckKind.label(): String = stringResource(
    when (this) {
        VerifyCheckKind.ELF_HEADER -> R.string.vmfix_patcher_check_elf_header
        VerifyCheckKind.ELF_SIZE_UNCHANGED -> R.string.vmfix_patcher_check_elf_size
        VerifyCheckKind.PROGRAM_HEADERS_IN_RANGE -> R.string.vmfix_patcher_check_program_headers
        VerifyCheckKind.SECTION_TABLE_IN_RANGE -> R.string.vmfix_patcher_check_section_table
        VerifyCheckKind.DYNAMIC_SECTION -> R.string.vmfix_patcher_check_dynamic_section
        VerifyCheckKind.DYNAMIC_TERMINATES -> R.string.vmfix_patcher_check_dynamic_terminates
        VerifyCheckKind.SECTION_BIASES -> R.string.vmfix_patcher_check_section_biases
        VerifyCheckKind.BOOTSTRAP_READABLE -> R.string.vmfix_patcher_check_bootstrap_readable
        VerifyCheckKind.INJECTED_FILES_PRESENT -> R.string.vmfix_patcher_check_injected_files
        VerifyCheckKind.SYMLINKS_ABSOLUTE -> R.string.vmfix_patcher_check_symlinks_absolute
    },
)

@Composable
fun ApkStorageKind.label(): String = stringResource(
    when (this) {
        ApkStorageKind.APP -> R.string.vmfix_storage_app
        ApkStorageKind.DOWNLOADS -> R.string.vmfix_storage_downloads
        ApkStorageKind.CHOSEN_FOLDER -> R.string.vmfix_storage_chosen
    },
)
