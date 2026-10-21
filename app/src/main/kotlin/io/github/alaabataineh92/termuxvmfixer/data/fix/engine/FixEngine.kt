package io.github.alaabataineh92.termuxvmfixer.data.fix.engine

import io.github.alaabataineh92.termuxvmfixer.core.common.log.Logger
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogger
import io.github.alaabataineh92.termuxvmfixer.core.model.Capability
import io.github.alaabataineh92.termuxvmfixer.core.model.CommandResult
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceError
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession
import io.github.alaabataineh92.termuxvmfixer.core.model.FixProgress
import io.github.alaabataineh92.termuxvmfixer.core.model.FixResult
import io.github.alaabataineh92.termuxvmfixer.core.model.FixStep
import io.github.alaabataineh92.termuxvmfixer.core.model.LogEntry
import io.github.alaabataineh92.termuxvmfixer.core.model.LogLevel
import io.github.alaabataineh92.termuxvmfixer.core.model.PathState
import io.github.alaabataineh92.termuxvmfixer.core.model.StepProgress
import io.github.alaabataineh92.termuxvmfixer.core.model.StepReason
import io.github.alaabataineh92.termuxvmfixer.core.model.StepState
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import io.github.alaabataineh92.termuxvmfixer.data.fix.archive.GnuTarWriter
import io.github.alaabataineh92.termuxvmfixer.data.fix.archive.TarInspector
import io.github.alaabataineh92.termuxvmfixer.data.fix.bootstrap.BootstrapReader
import io.github.alaabataineh92.termuxvmfixer.data.fix.content.FixArtifacts
import io.github.alaabataineh92.termuxvmfixer.data.fix.symlink.SymlinkScriptGenerator
import io.github.alaabataineh92.termuxvmfixer.data.fix.verify.VerifyParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration

class FixEngine(
    private val bootstrapReader: BootstrapReader,
    private val symlinkGenerator: SymlinkScriptGenerator,
    private val tarWriter: GnuTarWriter,
    private val artifacts: FixArtifacts,
    private val verifyParser: VerifyParser,
    private val logger: Logger = NoOpLogger,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _progress = MutableStateFlow<FixProgress?>(null)

    val progress: Flow<FixProgress?> = _progress.asStateFlow()

    fun beginSession(method: io.github.alaabataineh92.termuxvmfixer.core.model.PrivilegeMethod) {
        _progress.value = FixProgress(
            method = method,
            steps = FixStep.entries.map { StepProgress(it, StepState.Pending) },
        )
        markStep(FixStep.CONNECT, StepState.Running)
        log("connecting with ${method.name.lowercase().replace('_', ' ')}")
    }

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) = log(message, level)

    fun completeSession() {
        markStep(FixStep.CONNECT, StepState.Done())
    }

    fun failSession(detail: String?) {
        markStep(FixStep.CONNECT, StepState.Failed(StepReason.TransportUnavailable(detail)))
    }

    suspend fun run(
        session: DeviceSession,
        apk: File,
        workspace: File,
    ): FixResult = withContext(io) {
        val startedAt = System.currentTimeMillis()

        logger.info(TAG) {
            "run started: method=${session.method}, " +
                "capabilities=${session.capabilities.map { it.name }.sorted()}, " +
                "apk=${apk.name} (${apk.length()} bytes), " +
                "workspace=${workspace.absolutePath}"
        }

        if (_progress.value == null) {
            _progress.value = FixProgress(
                method = session.method,
                steps = FixStep.entries.map { StepProgress(it, StepState.Pending) },
            )
        }

        try {
            val bootstrap = prepareBootstrap(apk, workspace)
            val stageOneOk = runStageOne(session, workspace, bootstrap)

            if (!stageOneOk) {

                logger.error(TAG) {
                    "run aborted: the bootstrap rebuild did not finish, so " +
                        "stage 2 was not attempted. lastError=$lastError"
                }
                return@withContext FixResult.Incomplete(
                    problems = listOf(StepReason.BootstrapIncomplete),
                    report = null,
                    lastError = lastError,
                )
            }

            val report = runStageTwo(session, workspace)

            val elapsed = System.currentTimeMillis() - startedAt
            if (report.isSuccess) {
                logger.info(TAG) { "run succeeded in ${elapsed}ms: $report" }
                FixResult.Success(report)
            } else {
                logger.error(TAG) {
                    "run finished with problems in ${elapsed}ms: " +
                        "problems=${report.problems}, report=$report"
                }

                FixResult.Incomplete(
                    problems = report.problems.map { StepReason.Problem(it) },
                    report = report,
                    lastError = lastError,
                )
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            logger.warn(TAG) { "run cancelled by the user after ${System.currentTimeMillis() - startedAt}ms" }
            FixResult.Cancelled
        } catch (e: DeviceError) {
            logger.error(TAG, e) { "run failed: the transport is unusable" }
            failCurrentStep(e.message)
            FixResult.TransportFailed(e)
        } catch (e: Throwable) {
            logger.error(TAG, e) {
                "run failed with ${e::class.java.simpleName}: ${e.message}"
            }
            failCurrentStep(e.message)
            FixResult.Incomplete(
                problems = listOf(StepReason.RunFailed),
                report = null,
                lastError = e.message,
            )
        }
    }

    private data class PreparedBootstrap(val archive: File, val symlinkScript: File)

    private suspend fun prepareBootstrap(apk: File, workspace: File): PreparedBootstrap {

        val extracted = step(FixStep.OBTAIN_APK) {
            log("reading the bootstrap from ${apk.name}")

            val apkSize = apk.length()
            logger.debug(TAG) { "APK: ${apk.absolutePath} ($apkSize bytes)" }
            if (apkSize == 0L) {
                throw DeviceError.Precondition("the Termux APK is empty")
            }

            val startedAt = System.currentTimeMillis()
            val result = bootstrapReader.extract(apk, File(workspace, "bootstrap"))
            val elapsed = System.currentTimeMillis() - startedAt

            logger.info(TAG) {
                "bootstrap extracted in ${elapsed}ms: ${result.fileCount} files, " +
                    "symlink manifest ${result.symlinks.length} bytes, " +
                    "tree at ${result.directory.absolutePath}"
            }
            log("  ${result.fileCount} files")
            result
        }

        val prepared = step(FixStep.BUILD_ARCHIVE) {
            log("building the symlink list")

            val generated = symlinkGenerator.generate(extracted.symlinks)
            logger.info(TAG) {
                "generated ${generated.count} symlinks " +
                    "(${generated.script.length} bytes of script)"
            }
            logger.debug(TAG) {
                "symlink script preview:\n" +
                    generated.script.lineSequence().take(PREVIEW_LINES)
                        .joinToString("\n") { "    $it" }
            }
            log("  ${generated.count} symlinks")

            val symlinkFile = File(workspace, "make_symlinks.sh")
            symlinkFile.writeText(generated.script)

            log("packing (GNU tar format - older Android's tar cannot read PAX)")
            val sources = extracted.directory.listFiles()
                .orEmpty()
                .associate { it.name to it }
            logger.debug(TAG) {
                "packing ${sources.size} top-level entries: ${sources.keys.sorted()}"
            }

            val startedAt = System.currentTimeMillis()
            val archive = File(workspace, "bootstrap.tar")
            tarWriter.write(archive, sources)
            val elapsed = System.currentTimeMillis() - startedAt

            val profile = TarInspector.inspect(archive)
            logger.info(TAG) {
                "archive built in ${elapsed}ms: ${archive.length()} bytes, " +
                    "headerTypes=$profile"
            }
            if (profile.paxRecords > 0) {
                logger.error(TAG) {
                    "archive contains ${profile.paxRecords} PAX records. " +
                        "Android 11's toybox cannot read these and extraction " +
                        "will fail partway. This is a bug in GnuTarWriter."
                }
            }
            if (profile.longNameRecords == 0) {
                logger.warn(TAG) {
                    "archive has no GNU LongLink records, but the bootstrap " +
                        "contains paths over 100 bytes - long names may be truncated"
                }
            }

            log("  ${archive.length() / 1024 / 1024} MB")
            PreparedBootstrap(archive, symlinkFile)
        }

        return prepared
    }

    private suspend fun runStageOne(
        session: DeviceSession,
        workspace: File,
        bootstrap: PreparedBootstrap,
    ): Boolean {
        logger.info(TAG) { "=== Stage 1: rebuild the prefix ===" }
        requireCapability(session, Capability.RUN_AS_TERMUX, "rebuild the Termux prefix")

        step(FixStep.STOP_TERMUX) {
            log("stopping Termux")

            val startedAt = System.currentTimeMillis()
            val result = session.forceStop()
            val elapsed = System.currentTimeMillis() - startedAt

            logger.info(TAG) {
                "force-stop returned in ${elapsed}ms: exit=${result.exitCode}, " +
                    "stdout=${result.stdout.trim()}, stderr=${result.stderr.trim()}"
            }
            if (!result.isSuccess) warn("force-stop reported ${result.exitCode}")
        }

        val uploaded = step(FixStep.UPLOAD) {
            log("sending the archive and scripts (about 70 MB)")
            pushStageOneFiles(session, workspace, bootstrap)
        }

        if (!uploaded) {
            logger.error(TAG) { "stage 1 aborted: staging the files failed" }
            return false
        }

        val missing = step(FixStep.REBUILD_PREFIX) {
            log("rebuilding the prefix (this takes a few minutes)")

            logger.info(TAG) { "running repair.sh (timeout ${TIMEOUT_REBUILD.toMinutes()}m)" }
            val repair = exec(
                session = session,
                description = "repair.sh",
                asTermux = true,
                command = "/system/bin/sh ${TermuxPaths.WORK_DIR}/repair.sh",
                timeout = TIMEOUT_REBUILD,
            )

            exec(
                session = session,
                description = "cleanup staging",
                asTermux = false,
                command = "rm -rf ${TermuxPaths.WORK_DIR}",
                timeout = Duration.ofSeconds(30),
            )

            if (!repair.isSuccess) {
                warn("the rebuild exited with code ${repair.exitCode}")
            }

            val absent = missingPrefixDirs(session)
            logger.info(TAG) {
                "prefix check: missing=${absent.ifEmpty { listOf("none") }}"
            }
            absent
        }

        if (missing.isEmpty()) {
            logger.info(TAG) { "=== Stage 1 complete: the prefix is intact ===" }
            return true
        }

        warn("the prefix came back incomplete: ${missing.joinToString(", ")}")
        logger.error(TAG) {
            "=== Stage 1 FAILED: prefix is missing ${missing.joinToString(", ")}. " +
                "The rebuild output above should name the cause. ==="
        }

        markStep(
            FixStep.REBUILD_PREFIX,
            StepState.Failed(StepReason.PrefixIncomplete(missing)),
        )
        return false
    }

    private suspend fun pushStageOneFiles(
        session: DeviceSession,
        workspace: File,
        bootstrap: PreparedBootstrap,
    ): Boolean {
        exec(
            session = session,
            description = "prepare staging",
            asTermux = false,
            command = "rm -rf ${TermuxPaths.WORK_DIR} && mkdir -p ${TermuxPaths.WORK_DIR}",
            timeout = Duration.ofSeconds(30),
        )

        val repairScript = File(workspace, "repair.sh").apply {
            writeText(artifacts.script(FixArtifacts.REPAIR_SCRIPT))
        }

        val files = buildList {
            add(bootstrap.archive to "bootstrap.tar")
            add(bootstrap.symlinkScript to "make_symlinks.sh")
            add(repairScript to "repair.sh")
            add(artifacts.library() to "libvmfix.so")
            TermuxPaths.SHIMS.forEach { add(File(workspace, "shim-$it") to "shim-$it") }
        }

        TermuxPaths.SHIMS.forEach { name ->
            File(workspace, "shim-$name").writeText(artifacts.shim(name))
        }

        logger.info(TAG) { "pushing ${files.size} files to ${TermuxPaths.WORK_DIR}" }

        for ((local, remote) in files) {
            val size = local.length()
            val startedAt = System.currentTimeMillis()

            session.push(local.absolutePath, "${TermuxPaths.WORK_DIR}/$remote")
                .onFailure { error ->
                    logger.error(TAG, error) {
                        "push FAILED for $remote ($size bytes): ${error.message}"
                    }
                    warn("could not send $remote: ${error.message}")
                    return false
                }

            val elapsed = System.currentTimeMillis() - startedAt
            logger.debug(TAG) {
                "pushed $remote: $size bytes in ${elapsed}ms"
            }
        }

        logger.info(TAG) { "all staging files pushed" }
        return true
    }

    private suspend fun missingPrefixDirs(session: DeviceSession): List<String> =
        TermuxPaths.REQUIRED_DIRS.filter { dir ->
            val state = session.pathExists("${TermuxPaths.PREFIX}/$dir")

            logger.trace(TAG) { "prefix/$dir -> $state" }
            state == PathState.MISSING
        }

    private suspend fun runStageTwo(
        session: DeviceSession,
        workspace: File,
    ): io.github.alaabataineh92.termuxvmfixer.core.model.VerificationReport {
        logger.info(TAG) { "=== Stage 2: install the fix ===" }

        step(FixStep.INSTALL_LIBRARY) { log("installing the library and shims") }
        step(FixStep.INSTALL_HOOKS) { log("installing the hooks") }

        installStageTwoFiles(session, workspace)

        step(FixStep.REPAIR_SYMLINKS) {
            log("repairing symlinks")
            runScript(session, "relink.sh", TIMEOUT_SCRIPT)
        }

        if (Capability.ROOT in session.capabilities) {
            step(FixStep.COMPLETE_PACKAGES) {
                log("finishing half-installed packages")
                runScriptAsRoot(session, "repair-pkgs.sh")
            }
        } else {
            logger.info(TAG) {
                "skipping the package repair: ${session.method} has no root, " +
                    "so dpkg cannot unlink under / - packages stay marked iUR " +
                    "but their files are unpacked and the binaries run"
            }
            markStep(
                FixStep.COMPLETE_PACKAGES,
                StepState.Skipped(StepReason.NoRoot),
            )
        }

        return verify(session)
    }

    private suspend fun installStageTwoFiles(session: DeviceSession, workspace: File) {
        exec(
            session = session,
            description = "prepare staging (stage 2)",
            asTermux = false,
            command = "rm -rf ${TermuxPaths.WORK_DIR} && mkdir -p ${TermuxPaths.WORK_DIR}",
            timeout = Duration.ofSeconds(30),
        )

        val contents = linkedMapOf(
            "00-shims.sh" to artifacts.script(FixArtifacts.PATH_HOOK),
            "01-vmfix.sh" to artifacts.script(FixArtifacts.VMFIX_HOOK),
            "termux-login.sh" to artifacts.script(FixArtifacts.LOGIN_HOOK),
            "99-termux-vm-shims.conf" to artifacts.script(FixArtifacts.APT_CONF),
            "install.sh" to artifacts.script(FixArtifacts.INSTALL_SCRIPT),
            "relink.sh" to artifacts.script(FixArtifacts.RELINK_SCRIPT),
            "repair-pkgs.sh" to artifacts.script(FixArtifacts.REPAIR_PACKAGES_SCRIPT),
            "verify.sh" to artifacts.script(FixArtifacts.VERIFY_SCRIPT),
        )

        val files = contents.map { (name, text) ->
            File(workspace, name).apply { writeText(text) } to name
        } + TermuxPaths.SHIMS.map { name ->
            File(workspace, "shim-$name").apply { writeText(artifacts.shim(name)) } to "shim-$name"
        } + (artifacts.library() to "libvmfix.so")

        logger.info(TAG) { "pushing ${files.size} files for stage 2" }

        for ((local, remote) in files) {
            val size = local.length()
            session.push(local.absolutePath, "${TermuxPaths.WORK_DIR}/$remote")
                .onFailure { error ->

                    logger.error(TAG, error) {
                        "push failed for $remote ($size bytes): ${error.message}"
                    }
                    warn("could not send $remote: ${error.message}")
                }
        }

        runScript(session, "install.sh", TIMEOUT_SCRIPT)
    }

    private suspend fun runScript(
        session: DeviceSession,
        name: String,
        timeout: Duration,
    ): CommandResult = exec(
        session = session,
        description = name,
        asTermux = true,
        command = "/system/bin/sh ${TermuxPaths.WORK_DIR}/$name",
        timeout = timeout,
    )

    private suspend fun runScriptAsRoot(
        session: DeviceSession,
        name: String,
    ) {

        exec(
            session = session,
            description = "$name (root)",
            asTermux = false,
            command = "su -c '/system/bin/sh ${TermuxPaths.WORK_DIR}/$name'",
            timeout = TIMEOUT_PACKAGES,
        )
    }

    private suspend fun verify(
        session: DeviceSession,
    ): io.github.alaabataineh92.termuxvmfixer.core.model.VerificationReport {
        step(FixStep.VERIFY) { log("checking") }

        var output: String
        var completed = false
        var bestOutput = ""

        for (attempt in 0 until VERIFY_ATTEMPTS) {
            logger.debug(TAG) { "verify attempt ${attempt + 1} of $VERIFY_ATTEMPTS" }

            val result = exec(
                session = session,
                description = "verify.sh (attempt ${attempt + 1})",
                asTermux = true,
                command = "${TermuxPaths.BIN}/bash ${TermuxPaths.WORK_DIR}/verify.sh",
                timeout = TIMEOUT_VERIFY,
            )
            val attemptOutput = result.combined
            val isVerdict = verifyParser.completed(attemptOutput)

            bestOutput = attemptOutput

            if (isVerdict) {
                completed = true
                logger.info(TAG) {
                    "verify.sh ran to completion on attempt ${attempt + 1}"
                }
                break
            }

            if (attempt < VERIFY_ATTEMPTS - 1) {
                logger.warn(TAG) {
                    "verify.sh did not reach its last check (exit=${result.exitCode}, " +
                        "${attemptOutput.length} bytes of output). This VM kills " +
                        "processes with SIGSYS at random, so it is being retried."
                }
                warn("the check did not finish, running it again")
            }
        }

        output = bestOutput

        if (!completed) {
            logger.error(TAG) {
                "verify.sh did not complete after $VERIFY_ATTEMPTS attempts. " +
                    "The filesystem checks below still give a verdict."
            }
        }

        val dirs = TermuxPaths.REQUIRED_DIRS
            .filter { session.pathExists("${TermuxPaths.PREFIX}/$it") == PathState.EXISTS }
            .toSet()
        val shims = TermuxPaths.SHIMS
            .filter { session.pathExists("${TermuxPaths.SHIM_DIR}/$it") == PathState.EXISTS }
            .toSet()
        val library = session.pathExists(TermuxPaths.LIBRARY) == PathState.EXISTS

        logger.info(TAG) {
            "independent checks: prefixDirs=$dirs shims=$shims library=$library"
        }

        val report = verifyParser.parse(
            output = output,
            prefixDirs = dirs,
            shimsPresent = shims,
            libraryPresent = library,
        )

        exec(
            session = session,
            description = "remove verify.sh",
            asTermux = false,
            command = "rm -f ${TermuxPaths.WORK_DIR}/verify.sh",
            timeout = Duration.ofSeconds(30),
        )

        if (report.isSuccess) {
            logger.info(TAG) { "=== verification PASSED ===" }
            markStep(FixStep.VERIFY, StepState.Done())
        } else {
            logger.error(TAG) {
                "=== verification FAILED ===\n  " +
                    report.problems.joinToString("\n  ") { "$it" } +
                    "\n  raw report: $report"
            }
            markStep(
                FixStep.VERIFY,
                StepState.Failed(
                    report.problems.firstOrNull()
                        ?.let(StepReason::Problem)
                        ?: StepReason.RunFailed,
                ),
            )
        }
        return report
    }

    private var lastError: String? = null

    private suspend fun <T> step(step: FixStep, block: suspend () -> T): T {
        val startedAt = System.currentTimeMillis()
        logger.debug(TAG) { "step ${step.name} started" }
        markStep(step, StepState.Running)

        return try {
            block().also {
                val elapsed = System.currentTimeMillis() - startedAt
                logger.debug(TAG) { "step ${step.name} done in ${elapsed}ms" }
                markStep(step, StepState.Done())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.warn(TAG) { "step ${step.name} cancelled" }
            throw e
        } catch (e: Throwable) {
            val elapsed = System.currentTimeMillis() - startedAt
            lastError = e.message
            logger.error(TAG, e) {
                "step ${step.name} FAILED after ${elapsed}ms: ${e.message}"
            }
            markStep(step, StepState.Failed(StepReason.TransportUnavailable(e.message)))
            throw e
        }
    }

    private fun markStep(step: FixStep, state: StepState) {
        logger.trace(TAG) { "step ${step.name} -> ${state::class.simpleName}" }
        _progress.update { current ->
            current?.copy(
                steps = current.steps.map { if (it.step == step) it.copy(state = state) else it },
                isRunning = state !is StepState.Failed && current.isRunning,
            )
        }
    }

    private fun failCurrentStep(detail: String?) {
        _progress.update { current ->
            val running = current?.currentStep ?: return@update current
            current.copy(
                steps = current.steps.map {
                    if (it.step == running) {
                        it.copy(state = StepState.Failed(StepReason.TransportUnavailable(detail)))
                    } else {
                        it
                    }
                },
                isRunning = false,
            )
        }
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logger.log(level, TAG, message)
        _progress.update { current ->
            current?.copy(log = current.log + LogEntry(message, level))
        }
    }

    private fun warn(message: String) = log(message, LogLevel.WARN)

    private fun emit(result: CommandResult) {
        logger.debug(TAG) {
            "command result: exit=${result.exitCode} timedOut=${result.timedOut} " +
                "stdout=${result.stdout.length}B stderr=${result.stderr.length}B"
        }

        if (result.timedOut) {
            logger.warn(TAG) { "command timed out" }
        } else if (result.exitCode != 0) {
            logger.warn(TAG) { "command exited ${result.exitCode}" }
        }

        result.stdout.lineSequence().filter { it.isNotBlank() }.forEach { log(it) }
        result.stderr.lineSequence().filter { it.isNotBlank() }
            .forEach { log(it, LogLevel.WARN) }
    }

    private suspend fun exec(
        session: DeviceSession,
        description: String,
        asTermux: Boolean = true,
        command: String,
        timeout: Duration,
    ): CommandResult {
        val startedAt = System.currentTimeMillis()
        logger.debug(TAG) { "exec[$description] asTermux=$asTermux: $command" }

        val result = if (asTermux) {
            session.asTermux(command, timeout)
        } else {
            session.shell(command, timeout)
        }

        val elapsed = System.currentTimeMillis() - startedAt
        logger.debug(TAG) {
            "exec[$description] finished in ${elapsed}ms: " +
                "exit=${result.exitCode} timedOut=${result.timedOut}"
        }
        emit(result)
        return result
    }

    private fun requireCapability(session: DeviceSession, capability: Capability, action: String) {
        if (capability !in session.capabilities) {
            logger.error(TAG) {
                "capability check failed: ${session.method} lacks ${capability.name}, " +
                    "needed to $action"
            }
            throw DeviceError.Precondition("${session.method} cannot $action")
        }
        logger.trace(TAG) { "capability ok: ${capability.name} present for ${session.method}" }
    }

    private companion object {
        const val TAG = "Engine"

        const val PREVIEW_LINES = 12

        val TIMEOUT_REBUILD: Duration = Duration.ofMinutes(15)
        val TIMEOUT_SCRIPT: Duration = Duration.ofMinutes(5)
        val TIMEOUT_PACKAGES: Duration = Duration.ofMinutes(10)
        val TIMEOUT_VERIFY: Duration = Duration.ofMinutes(10)
        const val VERIFY_ATTEMPTS = 3
    }
}
