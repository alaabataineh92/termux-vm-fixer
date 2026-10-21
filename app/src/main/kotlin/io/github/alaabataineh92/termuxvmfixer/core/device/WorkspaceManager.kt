package io.github.alaabataineh92.termuxvmfixer.core.device

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    logSink: LogSink = NoOpLogSink,
) {

    private val logger = logSink.logger("Workspace")

    val requiredBytes: Long = REQUIRED_BYTES

    suspend fun create(): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, WORKSPACE_NAME)

        if (dir.exists()) {
            logger.debug(TAG) { "clearing the previous workspace at ${dir.absolutePath}" }
            dir.deleteRecursively()
        }
        if (!dir.mkdirs()) {
            throw DeviceError.Precondition("cannot create the workspace at ${dir.absolutePath}")
        }

        val available = dir.usableSpace
        logger.info(TAG) {
            "workspace ready at ${dir.absolutePath}: " +
                "${available / 1024 / 1024} MB free, " +
                "${requiredBytes / 1024 / 1024} MB needed"
        }

        if (available < requiredBytes) {
            logger.error(TAG) {
                "NOT ENOUGH STORAGE: ${available / 1024 / 1024} MB free but " +
                    "${requiredBytes / 1024 / 1024} MB is needed. The workspace " +
                    "must hold the APK, the unpacked bootstrap and the archive."
            }
            throw DeviceError.InsufficientStorage(
                neededBytes = requiredBytes,
                availableBytes = available,
            )
        }

        dir
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        File(context.filesDir, WORKSPACE_NAME).deleteRecursively()
        Unit
    }

    fun availableBytes(): Long = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val storage = context.getSystemService(StorageManager::class.java)
            storage.getAllocatableBytes(StorageManager.UUID_DEFAULT)
        } else {
            File(context.filesDir, WORKSPACE_NAME).let { dir ->
                (if (dir.exists()) dir else context.filesDir).usableSpace
            }
        }
    }.getOrDefault(0L)

    private companion object {
        const val TAG = "Workspace"
        const val WORKSPACE_NAME = "fix-work"

        const val REQUIRED_BYTES = 140L * 1024 * 1024
    }
}
