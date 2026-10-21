package io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceProperties @Inject constructor(
    private val reader: PropertyReader,
) {
    fun adbTcpPort(): Int? {
        val raw = reader.read(PROP_SERVICE_ADB_TCP_PORT)
            ?: reader.read(PROP_PERSIST_ADB_TCP_PORT)
            ?: return null

        return parsePort(raw)
    }

    companion object {
        const val PROP_SERVICE_ADB_TCP_PORT = "service.adb.tcp.port"
        const val PROP_PERSIST_ADB_TCP_PORT = "persist.adb.tcp.port"

        fun parsePort(raw: String): Int? {
            val trimmed = raw.trim()
            val value = trimmed.toIntOrNull() ?: return null
            return value.takeIf { it in 1..65535 }
        }
    }
}
