package io.github.alaabataineh92.termuxvmfixer.core.device

import io.github.alaabataineh92.termuxvmfixer.core.model.CommandResult
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.Duration

class AdbVmIo(
    private val session: DeviceSession,
    private val timeout: Duration = READ_TIMEOUT,
) : VirtualMasterDetector.VmIo {

    override fun isDirectory(path: String): Boolean {
        val result = run("test -d ${quote(path)}")
        return result != null && result.isSuccess
    }

    override fun canList(path: String): Boolean {
        val result = run("ls ${quote(path)} >/dev/null 2>&1")
        return result != null && result.isSuccess
    }

    override fun ownerUid(path: String): Int? {
        val result = run("ls -nd ${quote(path)}") ?: return null
        if (!result.isSuccess) return null
        return result.stdout.trim().split(Regex("\\s+"))
            .getOrNull(2)
            ?.toIntOrNull()
    }

    override fun vmSlots(hostDir: String): List<String> {
        val result = run("ls ${quote("$hostDir/vm")} 2>/dev/null") ?: return emptyList()
        if (!result.isSuccess) return emptyList()
        return result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("vm") }
            .sortedBy { it.removePrefix("vm").toIntOrNull() ?: Int.MAX_VALUE }
            .toList()
    }

    override fun hasRuntimeDevices(hostDir: String, slot: String): Boolean {
        val result = run("ls ${quote("$hostDir/vm/$slot/dev")} 2>/dev/null") ?: return false
        if (!result.isSuccess) return false
        val names = result.stdout.lineSequence().map { it.trim() }.toSet()
        return "qemu_pipe" in names && ("tun" in names || "binder" in names)
    }

    override fun hasGuestRootfs(hostDir: String, slot: String): Boolean {
        val fs = "$hostDir/vm/$slot/fs"

        val result = run(
            "test -d ${quote("$fs/bin")} && test -d ${quote("$fs/system")} " +
                "&& test -d ${quote("$fs/data")}",
        )
        return result != null && result.isSuccess
    }

    override fun packageInstalled(packageName: String): Boolean? {
        val result = run("pm path $packageName") ?: return null
        return when {
            result.timedOut -> null

            !result.isDefinite() -> null
            result.isSuccess -> result.stdout.contains("package:")
            else -> false
        }
    }

    override fun mountEvidence(hostPackage: String): String? {
        val result = run("cat /proc/self/mountinfo 2>/dev/null") ?: return null
        if (!result.isSuccess) return null

        val line = result.stdout.lineSequence().firstOrNull {
            MOUNT_TAG.containsMatchIn(it) || it.contains(hostPackage)
        } ?: return null
        return MOUNT_TAG.find(line)?.value ?: hostPackage
    }

    override fun cgroupUid(): Int? {
        val result = run("cat /proc/self/cgroup 2>/dev/null") ?: return null
        if (!result.isSuccess) return null
        return CGROUP_UID.find(result.stdout)?.groupValues?.get(1)?.toIntOrNull()
    }

    override fun ourUid(): Int? {
        val result = run("id -u") ?: return null
        if (!result.isSuccess) return null
        return result.stdout.trim().toIntOrNull()
    }

    override fun vmEnvironment(): Map<String, String> {
        val result = run("env 2>/dev/null") ?: return emptyMap()
        if (!result.isSuccess) return emptyMap()

        return result.stdout.lineSequence()
            .mapNotNull { line ->
                val at = line.indexOf('=')
                if (at <= 0) return@mapNotNull null
                val name = line.substring(0, at)
                if (!name.startsWith("VM")) return@mapNotNull null
                name to line.substring(at + 1)
            }
            .toMap()
    }

    private fun run(command: String): CommandResult? = runCatching {
        runBlocking(Dispatchers.IO) { session.shell(command, timeout) }
    }.getOrNull()

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {

        val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

        val MOUNT_TAG = Regex("""vmdl\d+""")
        val CGROUP_UID = Regex("""uid[_-](\d+)""")
    }
}
