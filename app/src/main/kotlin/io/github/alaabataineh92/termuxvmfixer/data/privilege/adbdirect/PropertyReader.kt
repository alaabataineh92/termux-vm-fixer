package io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect

import android.annotation.SuppressLint

interface PropertyReader {

    fun read(name: String): String?
}

class PlatformPropertyReader : PropertyReader {

    override fun read(name: String): String? =
        readViaReflection(name) ?: readViaGetprop(name)

    @SuppressLint("PrivateApi")
    private fun readViaReflection(name: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, name) as? String)?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun readViaGetprop(name: String): String? = runCatching {
        val process = ProcessBuilder("/system/bin/getprop", name)
            .redirectErrorStream(true)
            .start()

        val value = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()

        value.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
