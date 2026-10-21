package io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect

import android.content.Context
import java.io.File

class AdbIdentity(private val context: Context) {

    private val keyFile: File
        get() = File(context.filesDir, PRIVATE_KEY_NAME)

    fun exists(): Boolean = keyFile.isFile && keyFile.length() > 0

    fun privateKeyFile(): File = keyFile

    fun generate(): File {
        keyFile.parentFile?.mkdirs()
        dadb.AdbKeyPair.generate(keyFile, publicKeyFile())
        return keyFile
    }

    fun publicKeyFile(): File = File(context.filesDir, PUBLIC_KEY_NAME)

    fun reset() {
        keyFile.delete()
        publicKeyFile().delete()
    }

    private companion object {
        const val PRIVATE_KEY_NAME = "adb-key"
        const val PUBLIC_KEY_NAME = "adb-key.pub"
    }
}
