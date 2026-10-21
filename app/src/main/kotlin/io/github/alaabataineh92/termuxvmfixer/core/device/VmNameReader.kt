package io.github.alaabataineh92.termuxvmfixer.core.device

import android.annotation.SuppressLint
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import java.io.File
import java.time.Duration

object VmNameReader {
    suspend fun read(
        session: DeviceSession,
        hostPackage: String = VirtualMasterDetector.HOST_PACKAGE,
        slot: String?,
    ): String? {
        val path = configPath(hostPackage, slot) ?: return null

        val result = runCatching {
            session.shell("cat ${quote(path)} 2>/dev/null", NAME_TIMEOUT)
        }.getOrNull() ?: return null

        if (!result.isSuccess) return null

        return parse(result.stdout)
    }

    fun readLocal(
        hostPackage: String = VirtualMasterDetector.HOST_PACKAGE,
        slot: String?,
    ): String? {
        val path = configPath(hostPackage, slot) ?: return null

        val text = runCatching { File(path).readText() }.getOrNull() ?: return null

        return parse(text)
    }

    fun fallback(slot: String?, port: Int? = null): String {
        val slotNumber = slot
            ?.removePrefix("vm")
            ?.toIntOrNull()

        return when {
            slotNumber != null -> "Virtual Master VM $slotNumber"
            port != null -> "Virtual Master VM on port $port"
            else -> "Virtual Master VM"
        }
    }

    @SuppressLint("SdCardPath")
    private fun configPath(hostPackage: String, slot: String?): String? {
        val slotNumber = slot
            ?.removePrefix("vm")
            ?.toIntOrNull()
            ?: return null

        return "/data/data/$hostPackage/shared_prefs/vm_config_$slotNumber.xml"
    }

    private fun parse(text: String): String? = VM_NAME.find(text)
        ?.groupValues
        ?.get(1)
        ?.let(::decodeXml)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun quote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun decodeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private val NAME_TIMEOUT: Duration = Duration.ofSeconds(3)

    private val VM_NAME = Regex("""<string name="name">(.*?)</string>""")
}
