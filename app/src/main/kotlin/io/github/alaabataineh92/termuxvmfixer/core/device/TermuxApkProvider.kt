package io.github.alaabataineh92.termuxvmfixer.core.device

import android.content.Context
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceError
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermuxApkProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    logSink: LogSink = NoOpLogSink,
) {
    private val logger = logSink.logger("Apk")

    suspend fun copyTo(destination: File): File = withContext(Dispatchers.IO) {
        val source = sourceDir()
            ?: throw DeviceError.Precondition(
                "Termux is not installed, so there is no bootstrap to rebuild from",
            )

        logger.info(TAG) { "copying the Termux APK from $source" }

        val sourceFile = File(source)
        if (!sourceFile.isFile) {
            throw DeviceError.Precondition("Termux's APK is missing at $source")
        }
        if (!sourceFile.canRead()) {
            throw DeviceError.Precondition(
                "Termux's APK at $source is not readable. This build of Termux " +
                    "may not allow other apps to read it, which the repair needs.",
            )
        }

        destination.parentFile?.mkdirs()

        val temp = File(destination.parentFile, "${destination.name}.part")
        sourceFile.inputStream().use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }

        val expected = sourceFile.length()
        val actual = temp.length()
        if (actual != expected) {
            temp.delete()
            throw DeviceError.Precondition(
                "copying the APK produced $actual bytes, expected $expected",
            )
        }

        logger.info(TAG) {
            "APK copied: ${destination.absolutePath} ($actual bytes)"
        }

        if (!temp.renameTo(destination)) {

            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }

        destination
    }

    private companion object {
        const val TAG = "Apk"
    }

    private fun sourceDir(): String? = runCatching {
        val info = context.packageManager.getPackageInfo(TermuxPaths.PACKAGE, 0)
        info.applicationInfo?.sourceDir
    }.getOrNull()
}
