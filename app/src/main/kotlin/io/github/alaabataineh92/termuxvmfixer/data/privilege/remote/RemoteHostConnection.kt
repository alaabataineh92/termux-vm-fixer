package io.github.alaabataineh92.termuxvmfixer.data.privilege.remote

import android.content.Context
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbEndpoint
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbIdentity
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.DadbDeviceSession
import dadb.Dadb
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteHostConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logSink: LogSink = NoOpLogSink,
) {
    private val logger = logSink.logger("RemoteConn")

    @Volatile
    private var session: DeviceSession? = null

    private val _connected = MutableStateFlow(false)

    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    var address: String? = null
        private set

    fun current(): DeviceSession? = session

    val isConnected: Boolean get() = session != null

    suspend fun connect(host: String, port: Int): Result<DeviceSession> =
        withContext(Dispatchers.IO) {
            val display = "$host:$port"
            logger.info(TAG) { "connecting to $display" }

            close()

            runCatching {
                val identity = AdbIdentity(context)
                if (!identity.exists()) {
                    logger.info(TAG) { "no ADB key yet - generating one" }
                    identity.generate()
                }

                val keyPair = dadb.AdbKeyPair.read(
                    identity.privateKeyFile(),
                    identity.publicKeyFile(),
                )

                val endpoint = AdbEndpoint(
                    host = host,
                    port = port,
                    source = AdbEndpoint.Source.MANUAL,
                )

                val dadb = Dadb.create(
                    host,
                    port,
                    keyPair,
                    CONNECT_TIMEOUT_MILLIS,
                    SOCKET_TIMEOUT_MILLIS,
                )

                val newSession = DadbDeviceSession(dadb, endpoint, logSink)

                val probe = newSession.shell("echo ok", HANDSHAKE_TIMEOUT)
                if (!probe.isSuccess) {
                    throw IllegalStateException(
                        "the VM accepted the connection but could not run a " +
                            "command: " +
                            probe.stderr.take(160).ifBlank { "no output" },
                    )
                }

                session = newSession
                address = display
                _connected.value = true
                logger.info(TAG) { "connected to $display" }
                newSession
            }.onFailure { e ->
                _connected.value = false
                logger.error(TAG, e) { "could not connect to $display" }
            }
        }

    fun close() {
        _connected.value = false

        val existing = session ?: return
        runCatching { existing.close() }
        session = null
        address = null
        logger.info(TAG) { "disconnected" }
    }

    private companion object {
        const val TAG = "RemoteConn"

        const val CONNECT_TIMEOUT_MILLIS = 60_000

        const val SOCKET_TIMEOUT_MILLIS = 360_000

        val HANDSHAKE_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
