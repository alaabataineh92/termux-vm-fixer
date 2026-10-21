package io.github.alaabataineh92.termuxvmfixer.core.model

enum class Target {
    LOCAL,
    REMOTE,
}

enum class RepairCapability {
    ROOT,
    NON_ROOT,
    PATCH,
}

data class DeviceProfile(
    val target: Target,
    val isVirtualMaster: Boolean,
    val termux: TermuxState,
    val hasRoot: Boolean,
    val hasShell: Boolean,
    val slot: String? = null,
    val vmName: String? = null,
    val address: String? = null,
    val adbTcpPort: Int? = null,
) {

    fun capabilities(): Set<RepairCapability> = when {
        !isVirtualMaster -> emptySet()
        !termux.installed -> setOf(RepairCapability.PATCH)
        termux.isDebuggable -> buildSet {
            add(RepairCapability.NON_ROOT)
            add(RepairCapability.PATCH)
            if (hasRoot) add(RepairCapability.ROOT)
        }

        hasRoot -> setOf(RepairCapability.ROOT, RepairCapability.PATCH)

        else -> setOf(RepairCapability.PATCH)
    }

    val canRepair: Boolean
        get() = RepairCapability.NON_ROOT in capabilities() ||
            RepairCapability.ROOT in capabilities()

    val canPatch: Boolean get() = RepairCapability.PATCH in capabilities()

    val blockingIssue: BlockingIssue?
        get() = when {
            !isVirtualMaster -> BlockingIssue.NotAVm(
                remote = target == Target.REMOTE,
                address = address,
            )

            !termux.installed -> BlockingIssue.TermuxAbsent
            !termux.isDebuggable && !hasRoot -> BlockingIssue.NoRouteToPrefix
            termux.patch == PatchState.PATCHED && !canRepair ->
                BlockingIssue.AlreadyFixed

            else -> null
        }

    companion object {

        fun unknown(target: Target, address: String? = null) = DeviceProfile(
            target = target,
            isVirtualMaster = false,
            termux = TermuxState(
                installed = false,
                versionName = null,
                versionCode = null,
                isDebuggable = false,
                prefixPresent = false,
                missingDirs = TermuxPaths.REQUIRED_DIRS,
            ),
            hasRoot = false,
            hasShell = false,
            address = address,
        )
    }
}
