package io.github.alaabataineh92.termuxvmfixer.core.model

enum class PrivilegeMethod {
    DIRECT_ADB,
    ROOT,
}

sealed interface ProviderStatus {
    data class Available(val note: TransportNote? = null) : ProviderStatus

    data class NeedsSetup(
        val reason: Reason,
        val steps: List<SetupStep> = emptyList(),
    ) : ProviderStatus

    data class Unavailable(val reason: Reason) : ProviderStatus
}

data class RankedMethod(
    val method: PrivilegeMethod,
    val status: ProviderStatus,
    val rank: Int,
) : Comparable<RankedMethod> {
    override fun compareTo(other: RankedMethod): Int = other.rank.compareTo(rank)
}
