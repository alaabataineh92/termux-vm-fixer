package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.payload

data class PatchScripts(
    val touchShim: String,
    val cpShim: String,
    val tarShim: String,
    val pathHook: String,
    val vmfixHook: String,
    val aptConf: String,
    val loginHookAddition: String,
    val repairHook: String,
    val secondStageLauncher: String,
) {
    fun shims(): Map<String, String> = mapOf(
        "touch" to touchShim,
        "cp" to cpShim,
        "tar" to tarShim,
    )

    companion object {
        const val ASSET_DIR = "scripts/patch"
        const val TOUCH = "$ASSET_DIR/touch"
        const val CP = "$ASSET_DIR/cp"
        const val TAR = "$ASSET_DIR/tar"
        const val PATH_HOOK = "$ASSET_DIR/path-hook.sh"
        const val VMFIX_HOOK = "$ASSET_DIR/vmfix-hook.sh"
        const val APT_CONF = "$ASSET_DIR/apt.conf"
        const val LOGIN_HOOK_ADDITION = "$ASSET_DIR/login-hook-addition.sh"
        const val REPAIR_HOOK = "$ASSET_DIR/repair-hook.sh"
        const val SECOND_STAGE_LAUNCHER = "$ASSET_DIR/second-stage-launcher.sh"
    }
}
