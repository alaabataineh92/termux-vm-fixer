package io.github.alaabataineh92.termuxvmfixer.data.fix.content

import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import java.io.File

interface FixArtifacts {
    fun shim(name: String): String

    fun script(name: String): String

    fun library(): File

    fun supportsCurrentAbi(): Boolean

    fun requireValidShimName(name: String) {
        require(name in TermuxPaths.SHIMS) { "unknown shim: $name" }
    }

    companion object {
        const val REPAIR_SCRIPT = "fix/repair.sh"
        const val INSTALL_SCRIPT = "fix/install.sh"
        const val RELINK_SCRIPT = "fix/relink.sh"
        const val REPAIR_PACKAGES_SCRIPT = "fix/repair-pkgs.sh"
        const val VERIFY_SCRIPT = "fix/verify.sh"

        const val PATH_HOOK = "hooks/00-shims.sh"
        const val VMFIX_HOOK = "hooks/01-vmfix.sh"
        const val LOGIN_HOOK = "hooks/termux-login.sh"
        const val APT_CONF = "hooks/99-termux-vm-shims.conf"
    }
}
