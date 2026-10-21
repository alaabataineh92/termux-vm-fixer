package io.github.alaabataineh92.termuxvmfixer.core.device.apk

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import androidx.core.net.toUri

enum class InstallFailure {
    ApkMissing,
    NotAllowed,
    NoSession,
    InstallFailed,
    StatusFailed,
}

@Singleton
class ApkInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    logSink: LogSink = NoOpLogSink,
) {

    private val logger = logSink.logger(TAG)

    sealed interface Result {
        data object Success : Result

        data class Failed(
            val reason: InstallFailure,
            val detail: String? = null,
        ) : Result

        data object AwaitingUser : Result
    }

    fun canInstall(): Boolean = runCatching {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)

    fun installedSignerDigest(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )

            val signing = info.signingInfo
            val certificate = if (signing != null && signing.hasMultipleSigners()) {
                signing.apkContentsSigners?.firstOrNull()
            } else {
                signing?.signingCertificateHistory?.firstOrNull()
            }

            certificate?.toByteArray()?.let { digest(it) }
        } else {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES,
            )

            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()?.let { digest(it) }
        }
    }.getOrNull()

    private fun digest(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(certificate)
            .joinToString("") { "%02x".format(it) }

    fun openInstallPermissionSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    suspend fun install(apk: File): Result = withContext(Dispatchers.IO) {
        if (!apk.isFile) {
            return@withContext Result.Failed(
                reason = InstallFailure.ApkMissing,
                detail = apk.absolutePath,
            )
        }
        if (!canInstall()) {
            return@withContext Result.Failed(InstallFailure.NotAllowed)
        }

        logger.info(TAG) { "installing ${apk.absolutePath} (${apk.length()} bytes)" }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {

            setAppPackageName(TermuxPaths.PACKAGE)
        }

        val sessionId = try {
            installer.createSession(params)
        } catch (e: Throwable) {
            logger.error(TAG, e) { "could not create an install session" }
            return@withContext Result.Failed(InstallFailure.NoSession, e.message)
        }

        var status: StatusReceiver? = null

        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(NAME, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val receiver = StatusReceiver()
                status = receiver
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(ACTION_STATUS).setPackage(context.packageName),
                    flags,
                )
                val filter = IntentFilter(ACTION_STATUS)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(
                        receiver, filter
                    )
                }

                session.commit(pending.intentSender)

                val result = receiver.await()
                logger.info(TAG) { "install finished: $result" }
                result
            }
        } catch (e: Throwable) {
            logger.error(TAG, e) { "install failed" }
            Result.Failed(InstallFailure.InstallFailed, e.message)
        } finally {
            status?.let { receiver ->
                runCatching { context.unregisterReceiver(receiver) }
            }
            runCatching { installer.abandonSession(sessionId) }
        }
    }

    fun isTermuxInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TermuxPaths.PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun installedVersion(): String? = runCatching {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(
            TermuxPaths.PACKAGE, 0
        ).versionName
    }.getOrNull()

    fun installedApkPath(): String? = runCatching {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(
            TermuxPaths.PACKAGE, 0
        ).applicationInfo?.sourceDir
    }.getOrNull()

    fun requestUninstall() {
        logger.info(TAG) { "requesting uninstall of ${TermuxPaths.PACKAGE}" }
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = "package:${TermuxPaths.PACKAGE}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private class StatusReceiver : BroadcastReceiver() {

        private var continuation: ((Result) -> Unit)? = null

        suspend fun await(): Result = suspendCancellableCoroutine { cont ->
            continuation = { result -> cont.resume(result) }
            cont.invokeOnCancellation { continuation = null }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) ?: -1
            val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

            val result = when (status) {
                PackageInstaller.STATUS_SUCCESS -> Result.Success

                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION") val confirm =
                        intent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context?.startActivity(confirm) }
                    }
                    return
                }

                else -> Result.Failed(
                    reason = InstallFailure.StatusFailed,
                    detail = message ?: status.toString(),
                )
            }

            continuation?.invoke(result)
            continuation = null
        }
    }

    private companion object {
        const val TAG = "ApkInstaller"
        const val ACTION_STATUS = "io.github.alaabataineh92.termuxvmfixer.INSTALL_STATUS"
        const val NAME = "termux-patched.apk"
    }
}
