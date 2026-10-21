package io.github.alaabataineh92.termuxvmfixer.core.device

import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceProfile
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import io.github.alaabataineh92.termuxvmfixer.core.model.PatchState
import io.github.alaabataineh92.termuxvmfixer.core.model.PathState
import io.github.alaabataineh92.termuxvmfixer.core.model.Target
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxState
import java.time.Duration

class RemoteInspector(
    private val session: DeviceSession,
    private val address: String,
    private val librarySize: Long,
    logSink: LogSink = NoOpLogSink,
) {
    private val logger = logSink.logger("Remote")

    suspend fun inspect(): DeviceProfile {
        val isVm = VirtualMasterDetector(AdbVmIo(session)).detect()

        val termux = inspectTermux()
        val root = hasRoot()
        val vmName = if (isVm.insideVirtualMaster) {
            VmNameReader.read(
                session = session,
                hostPackage = isVm.hostPackage ?: VirtualMasterDetector.HOST_PACKAGE,
                slot = isVm.slot,
            )
        } else {
            null
        }

        logger.info(TAG) {
            "remote $address: vm=${isVm.insideVirtualMaster} slot=${isVm.slot} " +
                "name=$vmName termux=${termux.installed} " +
                "debuggable=${termux.isDebuggable} version=${termux.versionName} root=$root"
        }

        return DeviceProfile(
            target = Target.REMOTE,
            isVirtualMaster = isVm.insideVirtualMaster,
            termux = termux,
            hasRoot = root,
            hasShell = true,
            slot = isVm.slot,
            vmName = vmName,
            address = address,
        )
    }

    private suspend fun inspectTermux(): TermuxState {
        val installed = run("pm path ${TermuxPaths.PACKAGE}")
        val isInstalled = installed != null && installed.isSuccess &&
            installed.stdout.contains("package:")

        if (!isInstalled) {
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

        val version = run("dumpsys package ${TermuxPaths.PACKAGE}")
            ?.stdout
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("versionName=") }
            ?.removePrefix("versionName=")
            ?.trim()

        val runAs = run("run-as ${TermuxPaths.PACKAGE} id", RUN_AS_TIMEOUT)
        val debuggable = runAs != null && runAs.isSuccess && runAs.stdout.contains("uid=")

        if (!debuggable) {
            logger.info(TAG) {
                "run-as failed on the remote Termux: " +
                    (runAs?.stderr?.lineSequence()?.firstOrNull() ?: "no answer")
            }
        }

        return TermuxState(
            installed = true,
            versionName = version,
            versionCode = null,
            isDebuggable = debuggable,

            prefixPresent = true,
            missingDirs = emptyList(),
            patch = readPatchState(),
        )
    }

    private suspend fun readPatchState(): PatchState {
        val library = session.pathExists(TermuxPaths.LIBRARY)

        if (library == PathState.UNKNOWN) {
            logger.info(TAG) {
                "could not tell whether ${TermuxPaths.LIBRARY} exists on the " +
                    "remote; reporting the patch state as unknown"
            }
            return PatchState.UNKNOWN
        }

        if (library == PathState.MISSING) {
            logger.info(TAG) { "no library in the remote prefix; not patched" }
            return PatchState.NOT_PATCHED
        }

        val bytes = run("stat -c %s ${TermuxPaths.LIBRARY}")
            ?.stdout
            ?.trim()
            ?.toLongOrNull()

        return when (bytes) {
            null -> {
                logger.warn(TAG) { "the library exists but its size could not be read" }
                PatchState.UNKNOWN
            }

            librarySize -> {
                logger.info(TAG) { "the remote prefix carries the fix" }
                PatchState.PATCHED
            }

            else -> {
                logger.warn(TAG) {
                    "the remote library is $bytes bytes, expected " +
                        "$librarySize - treating it as not installed"
                }
                PatchState.NOT_PATCHED
            }
        }
    }

    private suspend fun hasRoot(): Boolean {
        val result = run(SU_BINARY_CHECK) ?: return false
        return result.isSuccess && result.stdout.isNotBlank()
    }

    private suspend fun run(
        command: String,
        timeout: Duration = DEFAULT_TIMEOUT,
    ): io.github.alaabataineh92.termuxvmfixer.core.model.CommandResult? =
        runCatching { session.shell(command, timeout) }.getOrNull()

    private companion object {
        const val TAG = "Remote"

        const val SU_BINARY_CHECK = "command -v su"

        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(20)

        val RUN_AS_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
