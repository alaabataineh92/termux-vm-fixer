package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.payload

import android.annotation.SuppressLint

object PatchContract {

    @SuppressLint("SdCardPath")
    const val PREFIX = "/data/data/com.termux/files/usr"

    const val MARKER = "Added by the patched Termux bootstrap"

    fun versionMarker(version: String): String = "# patched-termux bootstrap $version\n"

    object Paths {
        const val SYMLINKS = "SYMLINKS.txt"
        const val LIBRARY = "lib/libvmfix.so"
        const val SHIM_DIR = "etc/termux-vm-shims"
        const val PATH_HOOK = "etc/profile.d/00-termux-vm-shims.sh"
        const val VMFIX_HOOK = "etc/profile.d/02-termux-vmfix.sh"
        const val APT_CONF = "etc/apt/apt.conf.d/99-termux-vm-shims"
        const val LOGIN_HOOK = "etc/termux-login.sh"
        const val FALLBACK_HOOK = "etc/profile.d/01-termux-bootstrap-second-stage-fallback.sh"

        const val SECOND_STAGE_DIR = "etc/termux/bootstrap"
        const val SECOND_STAGE = "$SECOND_STAGE_DIR/termux-bootstrap-second-stage.sh"
        const val SECOND_STAGE_REAL = "$SECOND_STAGE_DIR/termux-bootstrap-second-stage-real.sh"
    }
}
