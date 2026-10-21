package io.github.alaabataineh92.termuxvmfixer.core.model

data class DiscoveredVm(
    val name: String,
    val host: String,
    val port: Int,
    val slot: String? = null,
    val authorized: Boolean = true,
)
