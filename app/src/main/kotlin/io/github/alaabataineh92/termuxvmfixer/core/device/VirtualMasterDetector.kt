package io.github.alaabataineh92.termuxvmfixer.core.device

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import java.io.File

class VirtualMasterDetector(
    private val io: VmIo = FileVmIo(),

    private val hostPackage: String = HOST_PACKAGE,
) {
    enum class Confidence { NONE, WEAK, STRONG, CERTAIN }

    enum class Group(val cap: Int) {
        ENVIRONMENT(2),
        HOST_DIR(2),
        VM_TREE(3),
        SYSTEM(2),
    }

    enum class Override { AUTO, FORCE_INSIDE, FORCE_OUTSIDE }

    data class Signal(
        val name: String,
        val observed: String,
        val present: Boolean,
        val weight: Int,
        val group: Group,
    )

    data class Detection(
        val insideVirtualMaster: Boolean,
        val confidence: Confidence,
        val score: Int,
        val hostPackage: String?,

        val slot: String?,

        val forced: Boolean,
        val signals: List<Signal>,
    ) {

        val summary: String
            get() {
                val where = when {
                    insideVirtualMaster && slot != null ->
                        "inside Virtual Master ($hostPackage), $slot"

                    insideVirtualMaster -> "inside Virtual Master ($hostPackage)"
                    score > 0 -> "probably the Android host (some container-like behaviour)"
                    else -> "on the Android host"
                }
                return if (forced) "$where [forced by override]" else where
            }

        fun report(): String = buildString {
            val max = Group.entries.toTypedArray().sumOf { it.cap }
            appendLine("Virtual Master detection: $summary")
            appendLine("score $score/$max, confidence $confidence")
            for ((name, observed, present, weight, group) in signals) {
                val mark = if (present) "[x]" else "[ ]"
                appendLine("  $mark +$weight ${group.name.padEnd(11)} $name: $observed")
            }
        }.trimEnd()
    }

    @SuppressLint("SdCardPath")
    fun detect(override: Override = Override.AUTO): Detection {
        val hostDir = "/data/data/$hostPackage"
        val signals = ArrayList<Signal>()
        val ourUid = io.ourUid()
        val vmEnv = io.vmEnvironment()

        val pkgVar = vmEnv[ENV_PRIMARY]
        signals += Signal(
            name = "$ENV_PRIMARY names the host",
            observed = pkgVar ?: "unset",
            present = pkgVar == hostPackage,
            weight = 2,
            group = Group.ENVIRONMENT,
        )

        val pointing = ENV_SECONDARY.filter { vmEnv[it]?.contains(hostPackage) == true }
        signals += Signal(
            name = "VM engine variables point into the host",
            observed = pointing.joinToString().ifEmpty { "none" },
            present = pointing.isNotEmpty(),
            weight = 1,
            group = Group.ENVIRONMENT,
        )

        val hostDirVisible = io.isDirectory(hostDir)
        signals += Signal(
            name = "host data directory visible",
            observed = if (hostDirVisible) hostDir else "absent",
            present = hostDirVisible,

            weight = 0,
            group = Group.HOST_DIR,
        )

        val hostUid = if (hostDirVisible) io.ownerUid(hostDir) else null
        val owned = ourUid != null && hostUid != null && hostUid == ourUid
        signals += Signal(
            name = "host directory owned by this app's uid",
            observed = "host uid=$hostUid, ours=$ourUid",
            present = owned,
            weight = 2,
            group = Group.HOST_DIR,
        )

        val installed = io.packageInstalled(hostPackage)
        val contradiction = hostDirVisible && installed == false
        signals += Signal(
            name = "directory visible but package not installed",
            observed = when {
                !hostDirVisible -> "n/a (directory not visible)"
                installed == null -> "unknown (package check unavailable)"
                installed -> "installed"
                else -> "PackageManager has no $hostPackage"
            },
            present = contradiction,
            weight = 1,
            group = Group.HOST_DIR,
        )

        val slots = io.vmSlots(hostDir)
        val runtimeSlot = slots.firstOrNull { io.hasRuntimeDevices(hostDir, it) }
        signals += Signal(
            name = "runtime devices",
            observed = runtimeSlot?.let { "$hostDir/vm/$it/dev (qemu_pipe + tun/binder)" } ?: "none",
            present = runtimeSlot != null,
            weight = 2,
            group = Group.VM_TREE,
        )

        val rootfsSlot = slots.firstOrNull { io.hasGuestRootfs(hostDir, it) }
        signals += Signal(
            name = "guest root filesystem",
            observed = rootfsSlot?.let { "$hostDir/vm/$it/fs (bin, system, data)" } ?: "none",
            present = rootfsSlot != null,
            weight = 2,
            group = Group.VM_TREE,
        )

        val listable = PERMISSION_CANARIES.filter { io.canList(it) }
        signals += Signal(
            name = "permission canary readable",
            observed = listable.joinToString().ifEmpty { "denied, as expected" },
            present = listable.isNotEmpty(),

            weight = 1,
            group = Group.SYSTEM,
        )

        val controlListable = io.canList(MISSING_CONTROL)
        signals += Signal(
            name = "nonexistent control path listable",
            observed = if (controlListable) "yes (filesystem answers for missing paths)" else "no",
            present = controlListable,
            weight = 0,
            group = Group.SYSTEM,
        )

        val mount = io.mountEvidence(hostPackage)
        signals += Signal(
            name = "mount evidence",
            observed = mount ?: "none",
            present = mount != null,
            weight = 1,
            group = Group.SYSTEM,
        )

        val cgroupUid = io.cgroupUid()
        signals += Signal(
            name = "cgroup uid differs from process uid",
            observed = "cgroup=$cgroupUid, process=$ourUid",
            present = ourUid != null && cgroupUid != null && cgroupUid != ourUid,
            weight = 0,
            group = Group.SYSTEM,
        )

        val score = Group.entries.toTypedArray().sumOf { g ->
            minOf(g.cap, signals.filter { it.group == g && it.present }.sumOf { it.weight })
        }

        val inside = when (override) {
            Override.AUTO -> score >= INSIDE_THRESHOLD
            Override.FORCE_INSIDE -> true
            Override.FORCE_OUTSIDE -> false
        }

        val slot = if (!inside) null else slotFromEnv(vmEnv) ?: runtimeSlot ?: rootfsSlot

        return Detection(
            insideVirtualMaster = inside,
            confidence = confidenceFor(score),
            score = score,
            hostPackage = if (inside) hostPackage else null,
            slot = slot,
            forced = override != Override.AUTO,
            signals = signals,
        )
    }

    private fun confidenceFor(score: Int): Confidence = when {
        score >= CERTAIN_THRESHOLD -> Confidence.CERTAIN
        score >= INSIDE_THRESHOLD -> Confidence.STRONG
        score >= 1 -> Confidence.WEAK
        else -> Confidence.NONE
    }

    private fun slotFromEnv(env: Map<String, String>): String? =
        SLOT_ENV_KEYS.firstNotNullOfOrNull { key ->
            env[key]
                ?.takeIf { it.contains(hostPackage) }
                ?.let { SLOT_IN_PATH.find(it)?.groupValues?.get(1) }
        }

    interface VmIo {
        fun isDirectory(path: String): Boolean
        fun canList(path: String): Boolean
        fun ownerUid(path: String): Int?

        fun packageInstalled(packageName: String): Boolean?
        fun vmSlots(hostDir: String): List<String>
        fun hasRuntimeDevices(hostDir: String, slot: String): Boolean
        fun hasGuestRootfs(hostDir: String, slot: String): Boolean
        fun mountEvidence(hostPackage: String): String?
        fun cgroupUid(): Int?
        fun ourUid(): Int?

        fun vmEnvironment(): Map<String, String>
    }

    class FileVmIo(
        private val isPackageInstalled: ((String) -> Boolean?)? = null,
    ) : VmIo {

        override fun isDirectory(path: String): Boolean =
            runCatching { File(path).isDirectory }.getOrDefault(false)

        override fun canList(path: String): Boolean =
            runCatching { File(path).list() != null }.getOrDefault(false)

        override fun ownerUid(path: String): Int? =
            runCatching { android.system.Os.stat(path).st_uid }.getOrNull()

        override fun packageInstalled(packageName: String): Boolean? =
            runCatching { isPackageInstalled?.invoke(packageName) }.getOrNull()

        override fun vmSlots(hostDir: String): List<String> = runCatching {
            File(hostDir, "vm").listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("vm") }
                ?.map { it.name }
                ?.sortedBy { it.removePrefix("vm").toIntOrNull() ?: Int.MAX_VALUE }
                .orEmpty()
        }.getOrDefault(emptyList())

        override fun hasRuntimeDevices(hostDir: String, slot: String): Boolean = runCatching {
            val names = File("$hostDir/vm/$slot/dev").list()?.toSet().orEmpty()
            "qemu_pipe" in names && ("tun" in names || "binder" in names)
        }.getOrDefault(false)

        override fun hasGuestRootfs(hostDir: String, slot: String): Boolean = runCatching {
            val fs = File("$hostDir/vm/$slot/fs")
            fs.isDirectory &&
                File(fs, "bin").isDirectory &&
                File(fs, "system").isDirectory &&
                File(fs, "data").isDirectory
        }.getOrDefault(false)

        override fun mountEvidence(hostPackage: String): String? = runCatching {
            File("/proc/self/mountinfo").takeIf { it.isFile }?.useLines { lines ->
                lines.firstNotNullOfOrNull { line ->
                    MOUNT_TAG.find(line)?.value ?: if (line.contains(hostPackage)) hostPackage else null
                }
            }
        }.getOrNull()

        override fun cgroupUid(): Int? = runCatching {
            File("/proc/self/cgroup").takeIf { it.isFile }
                ?.readText()
                ?.let { CGROUP_UID.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        }.getOrNull()

        override fun ourUid(): Int? = runCatching { android.os.Process.myUid() }.getOrNull()

        override fun vmEnvironment(): Map<String, String> = runCatching {
            System.getenv().filterKeys { it.startsWith("VM") }
        }.getOrDefault(emptyMap())
    }

    companion object {
        const val HOST_PACKAGE = "com.clone.android.dual.space"

        const val ENV_PRIMARY = "VMPKG"
        val ENV_SECONDARY = listOf("VMKR64", "VMKR32", "VMSRCELF", "VMINTERPELF")

        private val SLOT_ENV_KEYS = listOf("VMSRCELF", "VMINTERPELF")

        @SuppressLint("SdCardPath")
        const val MISSING_CONTROL = "/data/data/io.github.alaabataineh92.termuxvmfixer.probe.does.not.exist"

        val PERMISSION_CANARIES = listOf("/data/adb")

        const val INSIDE_THRESHOLD = 4
        const val CERTAIN_THRESHOLD = 7

        private val MOUNT_TAG = Regex("""vmdl\d+""")
        private val CGROUP_UID = Regex("""uid[_-](\d+)""")
        private val SLOT_IN_PATH = Regex("""/vm/(vm\d+)(?:/|$)""")

        @Suppress("DEPRECATION")
        fun forContext(context: Context, hostPackage: String = HOST_PACKAGE): VirtualMasterDetector {
            val pm = context.applicationContext.packageManager
            val io = FileVmIo { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                } catch (_: SecurityException) {
                    null
                }
            }
            return VirtualMasterDetector(io, hostPackage)
        }
    }
}
