package io.github.alaabataineh92.termuxvmfixer.core.model

interface DeviceSession : AutoCloseable {
    val method: PrivilegeMethod
    val capabilities: Set<Capability>
    suspend fun shell(command: String, timeout: java.time.Duration): CommandResult
    suspend fun asTermux(command: String, timeout: java.time.Duration): CommandResult
    suspend fun forceStop(): CommandResult
    suspend fun pathExists(path: String): PathState
    suspend fun push(localPath: String, remotePath: String): Result<Unit>
    suspend fun pull(remotePath: String): Result<ByteArray>
    override fun close()
}

interface SessionProvider {
    val method: PrivilegeMethod
    val capabilities: Set<Capability>
    val rank: Int
    suspend fun probe(): ProviderStatus
    suspend fun open(): Result<DeviceSession>
}
