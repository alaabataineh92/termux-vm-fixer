package io.github.alaabataineh92.termuxvmfixer.data.privilege.remote

import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteInstall @Inject constructor(
    logSink: LogSink,
) {
    private val logger = logSink.logger("RemoteInstall")

    sealed interface Result {
        data object Success : Result

        data class Failed(
            val reason: RemoteInstallFailure,
            val detail: String? = null,
        ) : Result
    }

    enum class RemoteInstallFailure {

        STAGING_FAILED,

        COPY_FAILED,

        SIGNATURE_MISMATCH,

        VERSION_DOWNGRADE,

        INSTALL_FAILED,
    }

    suspend fun install(session: DeviceSession, apk: File): Result =
        withContext(Dispatchers.IO) {
            val remotePath = "$STAGING/termux-patched.apk"

            logger.info(TAG) { "installing ${apk.name} (${apk.length()} bytes) on the VM" }

            val prepared = session.shell("mkdir -p $STAGING", SHORT_TIMEOUT)
            if (!prepared.isSuccess) {
                return@withContext Result.Failed(
                    reason = RemoteInstallFailure.STAGING_FAILED,
                    detail = prepared.stderr.take(160),
                )
            }

            val pushed = session.push(apk.absolutePath, remotePath)
            if (pushed.isFailure) {
                return@withContext Result.Failed(
                    reason = RemoteInstallFailure.COPY_FAILED,
                    detail = pushed.exceptionOrNull()?.message,
                )
            }

            session.shell("chmod 644 $remotePath", SHORT_TIMEOUT)

            logger.info(TAG) { "running pm install" }
            val install = session.shell(
                "pm install -r -d -g $remotePath",
                INSTALL_TIMEOUT,
            )

            session.shell("rm -f $remotePath", SHORT_TIMEOUT)

            val output = install.combined.trim()

            when {
                install.isSuccess && "Success" in output -> {
                    logger.info(TAG) { "installed successfully" }
                    Result.Success
                }

                "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in output ->
                    Result.Failed(RemoteInstallFailure.SIGNATURE_MISMATCH)

                "INSTALL_FAILED_VERSION_DOWNGRADE" in output ->
                    Result.Failed(RemoteInstallFailure.VERSION_DOWNGRADE)

                else -> Result.Failed(
                    reason = RemoteInstallFailure.INSTALL_FAILED,
                    detail = output.ifBlank { null }?.take(240),
                )
            }
        }

    suspend fun isTermuxInstalled(session: DeviceSession): Boolean {
        val result = session.shell("pm path $TERMUX", SHORT_TIMEOUT)
        return result.isSuccess && result.stdout.contains("package:")
    }

    suspend fun termuxVersion(session: DeviceSession): String? {
        val result = session.shell("dumpsys package $TERMUX", SHORT_TIMEOUT)
        if (!result.isSuccess) return null

        return VERSION.find(result.stdout)?.groupValues?.getOrNull(1)
    }

    suspend fun termuxSignerDigest(session: DeviceSession): String? {
        val result = session.shell("dumpsys package $TERMUX", SHORT_TIMEOUT)
        if (!result.isSuccess) return null

        return SIGNER_DIGEST.find(result.stdout)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
    }

    private companion object {
        const val TAG = "RemoteInstall"

        const val TERMUX = "com.termux"

        const val STAGING = "/data/local/tmp/termux-vm-fixer"

        val SHORT_TIMEOUT: Duration = Duration.ofSeconds(30)

        val INSTALL_TIMEOUT: Duration = Duration.ofMinutes(5)

        val VERSION = Regex("versionName=(\\S+)")

        val SIGNER_DIGEST = Regex("certificate SHA-256 digest:\\s*([0-9a-fA-F]+)")
    }
}
