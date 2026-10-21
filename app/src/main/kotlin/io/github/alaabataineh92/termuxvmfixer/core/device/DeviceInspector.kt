package io.github.alaabataineh92.termuxvmfixer.core.device

import android.content.Context
import android.os.Build
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceProfile
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceState
import io.github.alaabataineh92.termuxvmfixer.core.model.PatchState
import io.github.alaabataineh92.termuxvmfixer.core.model.Target
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxState
import io.github.alaabataineh92.termuxvmfixer.core.model.VirtualMasterState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInspector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val library: LibraryAsset,
    logSink: LogSink = NoOpLogSink,
) {
    private val logger = logSink.logger("Inspector")

    fun inspect(): DeviceState = DeviceState(
        androidRelease = Build.VERSION.RELEASE ?: "unknown",
        sdkInt = Build.VERSION.SDK_INT,
        abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
        toyboxVersion = readToyboxVersion(),
        termux = inspectTermux(),
        virtualMaster = inspectVirtualMaster(),
    ).also { state ->
        logger.info(TAG) {
            "device: android=${state.androidRelease} sdk=${state.sdkInt} " +
                "abi=${state.abi} toybox=${state.toyboxVersion} " +
                "supportedAbi=${state.isSupportedAbi}"
        }
        logger.info(TAG) {
            "termux: installed=${state.termux.installed} " +
                "version=${state.termux.versionName} " +
                "debuggable=${state.termux.isDebuggable} " +
                "fixable=${state.termux.isFixable}"
        }
        if (!state.termux.isDebuggable && state.termux.installed) {
            logger.error(TAG) {
                "Termux is installed but NOT debuggable, so run-as cannot " +
                    "reach its prefix. The repair cannot work with this build."
            }
        }
    }

    fun inspectProfile(state: DeviceState): DeviceProfile = DeviceProfile(
        target = Target.LOCAL,
        isVirtualMaster = state.virtualMaster is VirtualMasterState.Inside,
        termux = state.termux,
        hasRoot = hasLocalRoot(),

        hasShell = true,
        slot = (state.virtualMaster as? VirtualMasterState.Inside)?.slot,
        adbTcpPort = readAdbTcpPort(),
    )

    private fun readAdbTcpPort(): Int? =
        (readProp("service.adb.tcp.port") ?: readProp("persist.adb.tcp.port"))
            ?.toIntOrNull()
            ?.takeIf { it in 1..65535 }

    private fun readProp(name: String): String? = runCatching {
        val process = ProcessBuilder("/system/bin/getprop", name)
            .redirectErrorStream(true)
            .start()
        val value = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        value.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun hasLocalRoot(): Boolean = ROOT_BINARIES.any { File(it).exists() }

    private fun inspectVirtualMaster(): VirtualMasterState {

        val result = VirtualMasterDetector.forContext(context).detect()

        result.signals.forEach { signal ->
            logger.info(TAG) {
                "vm[${if (signal.present) "x" else " "}] " +
                    "+${signal.weight} ${signal.group.name.padEnd(11)} " +
                    "${signal.name}: ${signal.observed}"
            }
        }
        logger.info(TAG) { "score ${result.score}, confidence ${result.confidence}" }

        if (result.insideVirtualMaster) {
            logger.info(TAG) {
                "environment: inside Virtual Master " +
                    "(com.clone.android.dual.space)" +
                    (result.slot?.let { ", $it" } ?: "") +
                    if (result.forced) " [forced]" else ""
            }
            return VirtualMasterState.Inside(result.slot)
        }

        logger.info(TAG) { "environment: on the Android host" }
        return VirtualMasterState.NotVirtualMaster
    }

    private fun inspectTermux(): TermuxState {
        val info = runCatching {
            context.packageManager.getPackageInfo(TermuxPaths.PACKAGE, 0)
        }.getOrNull()

        if (info == null) {
            return TermuxState(
                installed = false,
                versionName = null,
                versionCode = null,
                isDebuggable = false,
                prefixPresent = false,
                missingDirs = TermuxPaths.REQUIRED_DIRS,

                patch = PatchState.NOT_PATCHED,
            )
        }

        val debuggable = (info.applicationInfo?.flags ?: 0) and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

        return TermuxState(
            installed = true,
            versionName = info.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            },
            isDebuggable = debuggable,

            prefixPresent = true,
            missingDirs = emptyList(),
            patch = readPatchState(),
        )
    }

    private fun readPatchState(): PatchState {

        val library = File(TermuxPaths.LIBRARY)
        val expected = this.library.size

        if (library.isFile && library.length() == expected) {
            logger.info(TAG) { "prefix carries the fix (${library.length()} bytes)" }
            return PatchState.PATCHED
        }

        if (!File(TermuxPaths.LIB_DIR).canRead()) {
            logger.info(TAG) {
                "cannot read ${TermuxPaths.LIB_DIR}, so whether the fix is " +
                    "installed is unknown"
            }
            return PatchState.UNKNOWN
        }

        if (library.exists()) {
            logger.warn(TAG) {
                "${TermuxPaths.LIBRARY} is ${library.length()} bytes, expected " +
                    "$expected - treating it as not installed"
            }
        } else {
            logger.info(TAG) { "no library in the prefix; Termux is not patched" }
        }

        return PatchState.NOT_PATCHED
    }

    private fun readToyboxVersion(): String? = runCatching {
        val process = ProcessBuilder("/system/bin/toybox", "--version")
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        text.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private companion object {
        const val TAG = "Inspector"

        val ROOT_BINARIES = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
        )
    }
}
