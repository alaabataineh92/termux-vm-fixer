package io.github.alaabataineh92.termuxvmfixer.feature.home

import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceProfile
import io.github.alaabataineh92.termuxvmfixer.core.model.PatchState
import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.ProviderStatus
import io.github.alaabataineh92.termuxvmfixer.core.model.RankedMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.Reason
import io.github.alaabataineh92.termuxvmfixer.core.model.RepairCapability
import io.github.alaabataineh92.termuxvmfixer.core.model.SetupStep
import io.github.alaabataineh92.termuxvmfixer.core.model.TransportNote

enum class ActionKind {
    ROOT,
    NON_ROOT,
    PATCH,
    ;

    val method: PrivilegeMethod?
        get() = when (this) {
            ROOT -> PrivilegeMethod.ROOT
            NON_ROOT -> PrivilegeMethod.DIRECT_ADB
            PATCH -> null
        }
}

sealed interface ActionState {

    data object Ready : ActionState

    data class NeedsPermission(
        val reason: Reason,
        val steps: List<SetupStep> = emptyList(),
    ) : ActionState

    data class Unavailable(val reason: Reason) : ActionState

    data object AlreadyDone : ActionState
}

data class ActionOption(
    val kind: ActionKind,
    val state: ActionState,

    val recommended: Boolean = false,

    val note: TransportNote? = null,
) {

    val selectable: Boolean get() = state is ActionState.Ready
}

fun buildOptions(
    profile: DeviceProfile,
    methods: List<RankedMethod>,

    connected: Boolean = true,
): List<ActionOption> {
    val capabilities = profile.capabilities()

    val options = listOf(
        rootOption(profile, methods, capabilities),
        nonRootOption(profile, methods, capabilities),
        patchOption(profile),
    )

    val gated = if (connected) {
        options
    } else {
        options.map { option ->
            if (option.selectable) {
                option.copy(
                    state = ActionState.NeedsPermission(
                        reason = Reason.Disconnected,
                        steps = listOf(SetupStep.ENTER_VM_ADDRESS),
                    ),
                )
            } else {
                option
            }
        }
    }

    val first = gated.indexOfFirst { it.selectable }

    return if (first < 0) {
        gated
    } else {
        gated.mapIndexed { index, option ->
            if (index == first) option.copy(recommended = true) else option
        }
    }
}

private fun rootOption(
    profile: DeviceProfile,
    methods: List<RankedMethod>,
    capabilities: Set<RepairCapability>,
): ActionOption {
    val status = methods.statusOf(PrivilegeMethod.ROOT)

    val state = when {
        RepairCapability.ROOT !in capabilities ->
            ActionState.Unavailable(profile.rootReason())

        status is ProviderStatus.Available -> ActionState.Ready

        status is ProviderStatus.NeedsSetup -> ActionState.NeedsPermission(
            reason = status.reason,
            steps = status.steps,
        )

        status is ProviderStatus.Unavailable -> ActionState.Unavailable(status.reason)

        else -> ActionState.NeedsPermission(
            reason = Reason.RootUnprobed,
            steps = listOf(SetupStep.CHECK_ROOT_AGAIN),
        )
    }

    return ActionOption(
        kind = ActionKind.ROOT,
        state = state,
        note = (status as? ProviderStatus.Available)?.note,
    )
}

private fun nonRootOption(
    profile: DeviceProfile,
    methods: List<RankedMethod>,
    capabilities: Set<RepairCapability>,
): ActionOption {
    val status = methods.statusOf(PrivilegeMethod.DIRECT_ADB)

    val state = when {

        RepairCapability.NON_ROOT !in capabilities ->
            ActionState.Unavailable(profile.nonRootReason())

        status is ProviderStatus.Available -> ActionState.Ready

        status is ProviderStatus.NeedsSetup -> ActionState.NeedsPermission(
            reason = status.reason,
            steps = status.steps,
        )

        status is ProviderStatus.Unavailable -> ActionState.Unavailable(status.reason)

        else -> ActionState.NeedsPermission(
            reason = Reason.AdbUnprobed,
            steps = listOf(SetupStep.CHECK_ADB_AGAIN),
        )
    }

    return ActionOption(
        kind = ActionKind.NON_ROOT,
        state = state,
        note = (status as? ProviderStatus.Available)?.note,
    )
}

private fun patchOption(profile: DeviceProfile): ActionOption {
    val state = when {
        !profile.canPatch -> ActionState.Unavailable(
            if (!profile.isVirtualMaster) {
                Reason.PatchNotAVm
            } else {
                Reason.PatchUnreadableTermux
            },
        )

        profile.termux.patch == PatchState.PATCHED -> ActionState.AlreadyDone

        else -> ActionState.Ready
    }

    return ActionOption(kind = ActionKind.PATCH, state = state)
}

private fun List<RankedMethod>.statusOf(method: PrivilegeMethod): ProviderStatus? =
    firstOrNull { it.method == method }?.status

private fun DeviceProfile.rootReason(): Reason = when {
    !termux.installed -> Reason.RootNoTermux

    hasRoot -> Reason.RootUnreachable

    else -> Reason.RootAbsent
}

private fun DeviceProfile.nonRootReason(): Reason = when {
    !termux.installed -> Reason.AdbNoTermux

    !termux.isDebuggable && hasRoot -> Reason.AdbNotDebuggableWithRoot

    !termux.isDebuggable -> Reason.AdbNotDebuggable

    else -> Reason.AdbUnreachable
}
