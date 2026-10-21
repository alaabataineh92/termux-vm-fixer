package io.github.alaabataineh92.termuxvmfixer.feature.fix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alaabataineh92.termuxvmfixer.core.device.RemoteTermuxApk
import io.github.alaabataineh92.termuxvmfixer.core.device.TermuxApkProvider
import io.github.alaabataineh92.termuxvmfixer.core.device.WorkspaceManager
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceError
import io.github.alaabataineh92.termuxvmfixer.core.model.FixProgress
import io.github.alaabataineh92.termuxvmfixer.core.model.FixResult
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel
import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.StepReason
import io.github.alaabataineh92.termuxvmfixer.data.fix.engine.FixEngine
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.DadbDeviceSession
import io.github.alaabataineh92.termuxvmfixer.data.privilege.registry.PrivilegeRegistry
import io.github.alaabataineh92.termuxvmfixer.data.privilege.remote.RemoteHostConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FixViewModel @Inject constructor(
    private val engine: FixEngine,
    private val registry: PrivilegeRegistry,
    private val apkProvider: TermuxApkProvider,
    private val workspace: WorkspaceManager,
    private val remoteConnection: RemoteHostConnection,
) : ViewModel() {

    private val _state = MutableStateFlow(FixUiState())
    val state: StateFlow<FixUiState> = _state.asStateFlow()

    private var runJob: Job? = null

    fun start(method: PrivilegeMethod, remote: Boolean = false) {
        if (runJob?.isActive == true) return

        _state.value = FixUiState(isRunning = true, method = method)

        if (remote) {
            startRemote(method)
            return
        }

        engine.beginSession(method)

        runJob = viewModelScope.launch {

            val progressJob = launch {
                engine.progress.collect { progress ->
                    progress?.let { value -> _state.update { it.copy(progress = value) } }
                }
            }

            try {
                val provider = registry.provider(method)
                    ?: throw DeviceError.Precondition("unknown method: $method")

                val session = try {
                    provider.open().getOrThrow()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val reason = "${e::class.java.simpleName}: ${e.message ?: "no message"}"
                    engine.addLog("connection failed - $reason", LogLevel.ERROR)
                    engine.failSession(reason)
                    throw DeviceError.SessionUnavailable(reason)
                }

                engine.addLog("connected")
                engine.completeSession()

                session.use { open ->
                    val workDir = workspace.create()

                    val apk = apkProvider.copyTo(File(workDir, "termux.apk"))

                    val result = engine.run(open, apk, workDir)

                    _state.update { it.copy(isRunning = false, result = result) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(isRunning = false, result = FixResult.Cancelled) }
                throw e
            } catch (e: Throwable) {

                val detail = e.message ?: "the repair failed"
                engine.addLog("stopped: $detail", LogLevel.ERROR)

                _state.update {
                    it.copy(
                        isRunning = false,
                        result = FixResult.Incomplete(
                            problems = listOf(StepReason.TransportUnavailable(e.message)),
                            report = null,
                            lastError = detail,
                        ),
                    )
                }
            } finally {
                progressJob.cancel()

                runCatching { workspace.clear() }
            }
        }
    }

    private fun startRemote(method: PrivilegeMethod) {
        engine.beginSession(method)

        runJob = viewModelScope.launch {
            val progressJob = launch {
                engine.progress.collect { progress ->
                    progress?.let { value -> _state.update { it.copy(progress = value) } }
                }
            }

            try {
                val session = remoteConnection.current()
                    ?: throw DeviceError.SessionUnavailable(
                        "no VM is connected - connect to one first",
                    )

                engine.addLog(
                    "using the existing connection to " +
                        (remoteConnection.address ?: "the VM"),
                )
                engine.completeSession()

                val workDir = workspace.create()

                engine.addLog("reading Termux's APK from the VM")
                val apk = withContext(Dispatchers.IO) {
                    RemoteTermuxApk(
                        session = session,

                        fastPuller = (session as? DadbDeviceSession)?.let { dadb ->
                            { remotePath, destination ->
                                dadb.pullBinary(remotePath, destination)
                            }
                        },
                    ).pullTo(File(workDir, "termux.apk"))
                }
                engine.addLog("APK pulled (${apk.length()} bytes)")

                val result = engine.run(session, apk, workDir)

                _state.update { it.copy(isRunning = false, result = result) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(isRunning = false, result = FixResult.Cancelled) }
                throw e
            } catch (e: Throwable) {
                val detail = e.message ?: "the repair failed"
                engine.addLog("stopped: $detail", LogLevel.ERROR)
                _state.update {
                    it.copy(
                        isRunning = false,
                        result = FixResult.Incomplete(
                            problems = listOf(StepReason.TransportUnavailable(e.message)),
                            report = null,
                            lastError = detail,
                        ),
                    )
                }
            } finally {
                progressJob.cancel()
                runCatching { workspace.clear() }
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
        _state.update { it.copy(isRunning = false, result = FixResult.Cancelled) }
    }

    fun dismiss() {
        if (runJob?.isActive == true) return
        _state.value = FixUiState()
    }
}

data class FixUiState(
    val isRunning: Boolean = false,
    val method: PrivilegeMethod? = null,
    val progress: FixProgress? = null,
    val result: FixResult? = null,
)
