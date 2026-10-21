package io.github.alaabataineh92.termuxvmfixer

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import io.github.alaabataineh92.termuxvmfixer.core.device.log.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogShare @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appLogger: AppLogger,
) {
    fun text(): String = buildString {
        appendLine(context.getString(R.string.vmfix_logshare_header))
        appendLine()
        appendLine(context.getString(R.string.vmfix_logshare_environment))
        appendLine(
            context.getString(R.string.vmfix_logshare_app, appVersion()),
        )
        appendLine(
            context.getString(
                R.string.vmfix_logshare_android,
                android.os.Build.VERSION.RELEASE,
                android.os.Build.VERSION.SDK_INT,
            ),
        )
        appendLine(
            context.getString(
                R.string.vmfix_logshare_device,
                android.os.Build.MANUFACTURER,
                android.os.Build.MODEL,
            ),
        )
        appendLine(
            context.getString(
                R.string.vmfix_logshare_abi,
                android.os.Build.SUPPORTED_ABIS.joinToString(", "),
            ),
        )
        appendLine(
            context.getString(R.string.vmfix_logshare_log_level, appLogger.level),
        )
        appendLine()
        appendLine(context.getString(R.string.vmfix_logshare_log))
        appendLine(appLogger.export())
    }

    private fun appVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        context.getString(
            R.string.vmfix_logshare_app_version,
            info.versionName,
            PackageInfoCompat.getLongVersionCode(info),
        )
    }.getOrDefault(context.getString(R.string.vmfix_logshare_unknown))

    companion object {
        fun text(context: Context): String {
            val app = context.applicationContext as TermuxVmFixerApp
            return app.logShare.text()
        }
    }
}
