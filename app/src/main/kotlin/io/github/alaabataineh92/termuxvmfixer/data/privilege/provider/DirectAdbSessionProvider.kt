package io.github.alaabataineh92.termuxvmfixer.data.privilege.provider

import android.content.Context
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.Capability
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod
import io.github.alaabataineh92.termuxvmfixer.core.model.ProviderStatus
import io.github.alaabataineh92.termuxvmfixer.core.model.Reason
import io.github.alaabataineh92.termuxvmfixer.core.model.SessionProvider
import io.github.alaabataineh92.termuxvmfixer.core.model.SetupStep
import io.github.alaabataineh92.termuxvmfixer.core.model.TransportNote
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbIdentity
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbEndpoint
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbEndpointDiscovery
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.DadbDeviceSession
import dadb.AdbConnectException
import dadb.AdbException
import dadb.Dadb
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectAdbSessionProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val discovery: AdbEndpointDiscovery,
    private val logSink: LogSink = NoOpLogSink,
) : SessionProvider {

    private val logger = logSink.logger("AdbDirect")

    override val method: PrivilegeMethod = PrivilegeMethod.DIRECT_ADB

    override val capabilities: Set<Capability> = setOf(
        Capability.SHELL_UID,
        Capability.RUN_AS_TERMUX,
        Capability.FORCE_STOP,
        Capability.READ_OTHER_APKS,
    )

    override val rank: Int = 95

    @Volatile
    var manualEndpoint: AdbEndpoint? = null

    override suspend fun probe(): ProviderStatus = withContext(Dispatchers.IO) {
        val found = discovery.findListening(manualEndpoint)

        if (found == null) {
            val configured = discovery.candidates().firstOrNull()
            val hint = if (configured != null && configured.source == AdbEndpoint.Source.DEVICE_PROPERTY) {
                Reason.AdbWrongPort(configured.port)
            } else {
                Reason.AdbNothingListening
            }

            return@withContext ProviderStatus.NeedsSetup(
                reason = hint,
                steps = listOf(
                    SetupStep.ENABLE_DEVELOPER_OPTIONS,
                    SetupStep.RUN_ADB_TCPIP,
                    SetupStep.RETURN_AND_CHECK,
                ),
            )
        }

        ProviderStatus.Available(
            note = TransportNote.AdbFound(
                address = found.display,
                configuredByUser = found.source == AdbEndpoint.Source.MANUAL,
            ),
        )
    }

    override suspend fun open(): Result<DeviceSession> = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()

        logger.info(TAG) { "--- opening a direct ADB connection ---" }

        runCatching {
            val endpoint = discovery.findListening(manualEndpoint)
                ?: error(
                    "No adbd is listening. Run `adb tcpip 5555` from a computer, " +
                        "or set the address manually.",
                )
            logger.info(TAG) { "using ${endpoint.display} (${endpoint.source})" }

            val keyPair = loadOrCreateKeyPair()
            logger.info(TAG) { "key pair loaded (private + public)" }

            logger.debug(TAG) {
                "building the client for ${endpoint.host}:${endpoint.port} " +
                    "(connects on first use)"
            }

            val dadb = Dadb.create(
                endpoint.host,
                endpoint.port,
                keyPair,
                CONNECT_TIMEOUT_MILLIS,
                SOCKET_TIMEOUT_MILLIS,
            )

            val session = DadbDeviceSession(dadb, endpoint, logSink)

            val handshakeStarted = System.currentTimeMillis()
            logger.info(TAG) { "performing the handshake (first command)" }

            val probe = try {
                session.shell("echo ok", java.time.Duration.ofSeconds(30))
            } catch (e: AdbConnectException) {
                logger.error(TAG, e) {
                    "handshake FAILED after " +
                        "${System.currentTimeMillis() - handshakeStarted}ms. " +
                        "If the cause names a read timeout, the device never " +
                        "answered CNXN - which on this transport has meant the " +
                        "public key was missing or empty, so adbd ignored the " +
                        "authentication reply. Check that " +
                        "${AdbIdentity(context).publicKeyFile().absolutePath} exists."
                }
                runCatching { dadb.close() }
                throw e
            } catch (e: AdbException) {
                logger.error(TAG, e) {
                    "handshake FAILED after " +
                        "${System.currentTimeMillis() - handshakeStarted}ms: " +
                        "${e::class.java.simpleName}: ${e.message}"
                }
                runCatching { dadb.close() }
                throw e
            }

            logger.info(TAG) {
                "handshake complete in ${System.currentTimeMillis() - handshakeStarted}ms: " +
                    "exit=${probe.exitCode} stdout=${probe.stdout.trim().take(40)}"
            }

            if (!probe.isSuccess) {
                logger.error(TAG) {
                    "the handshake succeeded but a trivial command failed: " +
                        "exit=${probe.exitCode} stderr=${probe.stderr.take(200)}"
                }
                runCatching { dadb.close() }
                error("the session could not run a command: ${probe.stderr.take(120)}")
            }

            logger.info(TAG) {
                "--- direct ADB ready in ${System.currentTimeMillis() - startedAt}ms ---"
            }
            session as DeviceSession
        }.onFailure { error ->
            logger.error(TAG, error) {
                "--- direct ADB FAILED after " +
                    "${System.currentTimeMillis() - startedAt}ms: " +
                    "${error::class.java.simpleName}: ${error.message} ---"
            }
        }
    }

    private fun loadOrCreateKeyPair(): dadb.AdbKeyPair {
        val identity = AdbIdentity(context)

        if (!identity.exists()) {
            logger.info(TAG) { "no key pair yet - generating one (the device will prompt once)" }
            identity.generate()
        }

        val privateKey = identity.privateKeyFile()
        val publicKey = identity.publicKeyFile()

        logger.debug(TAG) {
            "reading the key pair: private=${privateKey.absolutePath} " +
                "(${privateKey.length()}B), public=${publicKey.absolutePath} " +
                "(${publicKey.length()}B)"
        }

        if (!publicKey.isFile || publicKey.length() == 0L) {

            logger.error(TAG) {
                "the public key file is missing or empty at ${publicKey.absolutePath}. " +
                    "Without it the handshake sends an empty key and the device " +
                    "never replies. Regenerating the identity."
            }
            identity.reset()
            identity.generate()
        }

        return dadb.AdbKeyPair.read(privateKey, publicKey)
    }

    private companion object {
        const val TAG = "AdbDirect"

        const val CONNECT_TIMEOUT_MILLIS = 60_000

        const val SOCKET_TIMEOUT_MILLIS = 360_000
    }
}
