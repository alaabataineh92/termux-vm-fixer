package io.github.alaabataineh92.termuxvmfixer.data.privilege.remote

import android.content.Context
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.device.AdbVmIo
import io.github.alaabataineh92.termuxvmfixer.core.device.VirtualMasterDetector
import io.github.alaabataineh92.termuxvmfixer.core.device.VmNameReader
import io.github.alaabataineh92.termuxvmfixer.core.model.DiscoveredVm
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbEndpoint
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbEndpointDiscovery
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbHandshake
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbIdentity
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.DadbDeviceSession
import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbTimeoutException
import dadb.AdbKeyPair
import dadb.Dadb
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteVmDiscovery @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val endpointDiscovery: AdbEndpointDiscovery,
    private val logSink: LogSink = NoOpLogSink,
) {

    private val logger = logSink.logger("VmDiscovery")

    suspend fun discover(hostHint: String): List<DiscoveredVm> = withContext(Dispatchers.IO) {
        val host = normalizeHost(hostHint)
        val ports = candidatePorts()

        logger.info(TAG) {
            "scanning ${ports.size} local ADB port(s) on $host"
        }

        val openPorts = coroutineScope {
            ports.map { port ->
                async(Dispatchers.IO) {
                    val endpoint = AdbEndpoint(
                        host = host,
                        port = port,
                        source = AdbEndpoint.Source.MANUAL,
                    )
                    port.takeIf {
                        endpointDiscovery.isListening(endpoint, PORT_PROBE_TIMEOUT_MILLIS)
                    }
                }
            }.awaitAll().filterNotNull().sorted()
        }

        if (openPorts.isEmpty()) {
            logger.warn(TAG) { "no open local ADB port found on $host" }
            return@withContext emptyList()
        }

        logger.info(TAG) { "checking open port(s): ${openPorts.joinToString()}" }

        val keyPair = loadKeyPair()

        val vms = openPorts.mapNotNull { port ->
            probeVm(host, port, keyPair)
        }.distinctBy { vm -> vm.host to vm.port }

        logger.info(TAG) {
            "found ${vms.size} Virtual Master VM(s) on $host: " +
                vms.joinToString {
                    "${it.name}@${it.host}:${it.port}" +
                        if (it.authorized) "" else " (authorization needed)"
                }
        }

        vms.sortedBy { it.port }
    }

    private fun normalizeHost(hostHint: String): String {
        val trimmed = hostHint.trim()
        return when {
            trimmed.isEmpty() -> AdbEndpointDiscovery.LOOPBACK
            trimmed.equals("localhost", ignoreCase = true) -> AdbEndpointDiscovery.LOOPBACK
            trimmed == AdbEndpointDiscovery.LOOPBACK -> AdbEndpointDiscovery.LOOPBACK
            else -> trimmed
        }
    }

    @Synchronized
    private fun loadKeyPair(): AdbKeyPair {
        val identity = AdbIdentity(context)
        if (!identity.exists()) {
            logger.info(TAG) { "no ADB key yet - generating one for discovery" }
            identity.generate()
        }
        return AdbKeyPair.read(identity.privateKeyFile(), identity.publicKeyFile())
    }

    private suspend fun probeVm(
        host: String,
        port: Int,
        keyPair: AdbKeyPair,
    ): DiscoveredVm? {
        if (!AdbHandshake.speaksAdb(host, port, HANDSHAKE_PROBE_TIMEOUT_MILLIS)) {
            logger.debug(TAG) { "port $port is open but does not answer as ADB" }
            return null
        }

        var dadb: Dadb? = null
        var session: DadbDeviceSession? = null
        var wrapped = false

        return try {
            val newDadb = Dadb.create(
                host,
                port,
                keyPair,
                CONNECT_TIMEOUT_MILLIS,
                SOCKET_TIMEOUT_MILLIS,
            )
            dadb = newDadb

            val probe = try {
                newDadb.shell("echo ok")
            } catch (_: AdbAuthException) {
                logger.info(TAG) { "port $port requires ADB authorization" }
                return unauthorizedVm(host, port)
            } catch (_: AdbTimeoutException) {
                logger.info(TAG) {
                    "port $port did not finish ADB authorization in time - " +
                        "treating it as an unauthorized VM"
                }
                return unauthorizedVm(host, port)
            } catch (e: AdbConnectException) {
                logger.info(TAG) {
                    "port $port answered ADB but the authenticated handshake did not " +
                        "complete - treating it as an unauthorized VM: ${e.message}"
                }
                return unauthorizedVm(host, port)
            } catch (e: Exception) {
                logger.debug(TAG) {
                    "port $port ADB shell probe failed: ${e::class.java.simpleName}: ${e.message}"
                }
                return unauthorizedVm(host, port)
            }

            if (!probe.output.trim().equals("ok", ignoreCase = true)) {
                logger.debug(TAG) { "port $port accepted ADB but the shell probe failed" }
                return unauthorizedVm(host, port)
            }

            val endpoint = AdbEndpoint(
                host = host,
                port = port,
                source = AdbEndpoint.Source.MANUAL,
            )
            val newSession = DadbDeviceSession(newDadb, endpoint, logSink)
            session = newSession
            wrapped = true

            val detection = VirtualMasterDetector(
                AdbVmIo(newSession, DISCOVERY_COMMAND_TIMEOUT),
            ).detect()

            if (!detection.insideVirtualMaster) {
                logger.debug(TAG) { "port $port is ADB but not a Virtual Master VM" }
                return null
            }

            val name = VmNameReader.read(
                session = newSession,
                hostPackage = detection.hostPackage ?: VirtualMasterDetector.HOST_PACKAGE,
                slot = detection.slot,
            ) ?: VmNameReader.fallback(detection.slot, port)

            knownNames[key(host, port)] = name

            logger.info(TAG) {
                "confirmed $name at $host:$port (slot=${detection.slot})"
            }

            DiscoveredVm(
                name = name,
                host = host,
                port = port,
                slot = detection.slot,
                authorized = true,
            )
        } catch (e: Exception) {
            logger.debug(TAG) {
                "probe of $host:$port failed: ${e::class.java.simpleName}: ${e.message}"
            }
            unauthorizedVm(host, port)
        } finally {
            if (wrapped) {
                session?.close()
            } else {
                dadb?.close()
            }
        }
    }

    private fun unauthorizedVm(host: String, port: Int): DiscoveredVm? {
        if (!isVmPort(port)) return null

        val slot = slotForPort(port)
        val name = knownNames[key(host, port)] ?: VmNameReader.fallback(slot, port)

        logger.info(TAG) {
            "found unauthorized VM candidate $name at $host:$port (slot=$slot)"
        }

        return DiscoveredVm(
            name = name,
            host = host,
            port = port,
            slot = slot,
            authorized = false,
        )
    }

    private fun key(host: String, port: Int): String = "$host:$port"

    private fun slotForPort(port: Int): String? =
        if (isVmPort(port)) "vm${port - FIRST_VM_PORT}" else null

    private fun isVmPort(port: Int): Boolean = port in FIRST_VM_PORT..LAST_VM_PORT

    private fun candidatePorts(): List<Int> =
        ((FIRST_VM_PORT..LAST_VM_PORT).toList() + DEFAULT_ADB_PORT).distinct()

    private companion object {
        const val TAG = "VmDiscovery"

        const val FIRST_VM_PORT = 10_000
        const val LAST_VM_PORT = 10_099
        const val DEFAULT_ADB_PORT = 5_555

        const val PORT_PROBE_TIMEOUT_MILLIS = 500
        const val HANDSHAKE_PROBE_TIMEOUT_MILLIS = 1_500

        const val CONNECT_TIMEOUT_MILLIS = 3_000
        const val SOCKET_TIMEOUT_MILLIS = 3_000

        val DISCOVERY_COMMAND_TIMEOUT: Duration = Duration.ofSeconds(5)
    }

    private val knownNames = ConcurrentHashMap<String, String>()
}
