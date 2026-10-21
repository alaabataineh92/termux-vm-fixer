package io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect

import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.Logger
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class AdbEndpoint(
    val host: String,
    val port: Int,
    val source: Source,
) {
    enum class Source {
        MANUAL,
        DEVICE_PROPERTY,
        DEFAULT,
    }

    val display: String get() = "$host:$port"
}

@Singleton
class AdbEndpointDiscovery @Inject constructor(
    private val deviceProperties: DeviceProperties,
    logSink: LogSink = NoOpLogSink,
) {

    private val logger: Logger = logSink.logger(TAG)

    fun candidates(manual: AdbEndpoint? = null): List<AdbEndpoint> {
        if (manual != null) return listOf(manual)

        val hosts = listOf(LOOPBACK) + localAddresses()
        val ports = candidatePorts()

        return hosts.flatMap { host ->
            ports.map { (port, source) -> AdbEndpoint(host, port, source) }
        }
    }

    fun isListening(endpoint: AdbEndpoint, timeoutMillis: Int = PROBE_TIMEOUT_MILLIS): Boolean {
        val startedAt = System.currentTimeMillis()

        val outcome = runCatching {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(endpoint.host, endpoint.port),
                    timeoutMillis,
                )
                true
            }
        }

        val elapsed = System.currentTimeMillis() - startedAt
        outcome.fold(
            onSuccess = { listening ->
                logger.debug(TAG) {
                    "probe ${endpoint.display} (${endpoint.source}): " +
                        "listening=$listening in ${elapsed}ms"
                }
            },
            onFailure = { error ->

                logger.debug(TAG) {
                    "probe ${endpoint.display} (${endpoint.source}): " +
                        "${error::class.java.simpleName} after ${elapsed}ms - " +
                        "${error.message}"
                }
            },
        )
        return outcome.getOrDefault(false)
    }

    fun findListening(manual: AdbEndpoint? = null): AdbEndpoint? {
        val candidates = candidates(manual)

        logger.info(TAG) {
            "searching for adbd among ${candidates.size} candidate(s): " +
                candidates.joinToString(", ") { "${it.display}(${it.source})" }
        }

        candidates.forEach { endpoint ->
            if (isListening(endpoint)) {
                logger.info(TAG) { "found adbd at ${endpoint.display} (${endpoint.source})" }
                return endpoint
            }
        }

        logger.warn(TAG) {
            "no adbd found. Every candidate refused the connection: " +
                candidates.joinToString(", ") { it.display } +
                ". The device's TCP ADB may not be enabled - `adb tcpip 5555` " +
                "from a computer turns it on."
        }
        return null
    }

    private fun candidatePorts(): List<Pair<Int, AdbEndpoint.Source>> {
        val configured = deviceProperties.adbTcpPort()

        return buildList {
            configured?.let { add(it to AdbEndpoint.Source.DEVICE_PROPERTY) }
            COMMON_PORTS.forEach { port ->
                if (port != configured) {
                    add(port to AdbEndpoint.Source.DEFAULT)
                }
            }
        }.distinctBy { (port, _) -> port }
    }

    private fun localAddresses(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.inetAddresses.toList() }
            .filterIsInstance<java.net.Inet4Address>()
            .mapNotNull { it.hostAddress }.toList()
    }.getOrDefault(emptyList())

    companion object {
        const val LOOPBACK = "127.0.0.1"

        private const val TAG = "Discovery"

        const val DEFAULT_PORT = 10000

        private const val VIRTUAL_MASTER_PORT = 10000

        private val COMMON_PORTS = listOf(DEFAULT_PORT, VIRTUAL_MASTER_PORT)

        private const val PROBE_TIMEOUT_MILLIS = 1200
    }
}
