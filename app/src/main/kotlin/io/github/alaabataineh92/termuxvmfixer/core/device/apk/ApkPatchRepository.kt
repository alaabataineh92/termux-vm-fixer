package io.github.alaabataineh92.termuxvmfixer.core.device.apk

import android.content.Context
import android.net.Uri
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.device.LibraryAsset
import io.github.alaabataineh92.termuxvmfixer.core.device.RemoteTermuxApk
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceError
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchEvent
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchRequest
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchResult
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchSource
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.engine.SelfWiringApkPatcher
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.payload.BundledPatchPayload
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.payload.PatchScripts
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.ApkSigSigner
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.SigningIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkPatchRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val installer: ApkInstaller,
    private val keyStore: SigningKeyStore,
    private val privateStorage: PrivateApkStorage,
    downloadsStorage: DownloadsApkStorage,
    safStorage: SafApkStorage,
    private val library: LibraryAsset,
    logSink: LogSink = NoOpLogSink,
) {

    private val logger = logSink.logger(TAG)

    val storages: List<ApkStorage> = listOf(privateStorage, downloadsStorage, safStorage)

    fun storage(id: String): ApkStorage =
        storages.firstOrNull { it.id == id } ?: privateStorage

    fun installedSource(): PatchSource? {
        val path = installer.installedApkPath() ?: return null
        val file = File(path)
        if (!file.isFile || !file.canRead()) return null
        return PatchSource.Installed(file, installer.installedVersion())
    }

    suspend fun pullRemoteApk(
        session: io.github.alaabataineh92.termuxvmfixer.core.model.DeviceSession,
        fastPuller: ((String, File) -> Boolean)?,
        fileName: String,
    ): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "remote-apk").apply { mkdirs() }
            RemoteTermuxApk(session, fastPuller).pullTo(File(dir, fileName))
        }

    suspend fun importPicked(uri: Uri, displayName: String?): PatchSource.Picked =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "apk-import").apply { mkdirs() }
            val name = displayName?.takeIf { it.isNotBlank() } ?: "picked.apk"
            val target = File(dir, name.replace('/', '_'))

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw DeviceError.Precondition("cannot read the picked file")

            logger.info(TAG) { "imported $name (${target.length()} bytes)" }
            PatchSource.Picked(target, name)
        }

    suspend fun patch(
        source: PatchSource,
        storageId: String,
        backUp: Boolean,
        identity: SigningIdentity?,
        onEvent: (PatchEvent) -> Unit,
    ): PatchOutcome = withContext(Dispatchers.IO) {
        val storage = storage(storageId)

        val backup = if (backUp) {
            runCatching {
                storage.write(backupName(source), source.file.readBytes())
            }.onFailure {
                logger.error(TAG, it) { "the backup failed; refusing to patch without one" }
                return@withContext PatchOutcome.Failed(
                    reason = PatchFailure.BACKUP_FAILED,
                    detail = it.message,
                )
            }.getOrThrow()
        } else {
            null
        }

        val work = File(context.cacheDir, "apk-patch").apply {
            deleteRecursively()
            mkdirs()
        }
        val output = File(work, patchedName(source))

        val signingIdentity = identity ?: keyStore.defaultIdentity()
        val payload = BundledPatchPayload(
            libraryBytes = library.file().readBytes(),
            patchScripts = readPatchScripts(),
        )

        val result = SelfWiringApkPatcher(ApkSigSigner()).patch(
            request = PatchRequest(
                source = source,
                output = output,
                payload = payload,
            ),
            identity = signingIdentity,
            onEvent = onEvent,
        )

        when (result) {
            is PatchResult.Success -> {
                val stored = runCatching {
                    storage.write(patchedName(source), output.readBytes())
                }.getOrElse {
                    logger.error(TAG, it) { "the patched APK could not be stored" }
                    return@withContext PatchOutcome.Failed(
                        reason = PatchFailure.NOT_SAVED,
                        detail = it.message,
                    )
                }

                runCatching { work.deleteRecursively() }

                PatchOutcome.Success(
                    stored = stored,
                    backup = backup,
                    report = result.report,

                    signingIdentity = signingIdentity,
                )
            }

            is PatchResult.Failed -> PatchOutcome.Failed(
                reason = PatchFailure.PATCH_FAILED,

                detail = result.problems.joinToString("; "),
            )

            PatchResult.Cancelled -> PatchOutcome.Failed(PatchFailure.CANCELLED)
        }
    }

    suspend fun backups(storageId: String): List<StoredApk> =
        storage(storageId).list().filter { it.name.contains("backup", ignoreCase = true) }

    suspend fun patchedApks(storageId: String): List<VersionedApk> =
        withContext(Dispatchers.IO) {
            storage(storageId).list()
                .filterNot { it.name.contains("backup", ignoreCase = true) }
                .map { apk ->
                    VersionedApk(
                        apk = apk,
                        version = versionOf(apk) ?: versionFromName(apk.name),
                    )
                }
        }

    suspend fun backupApks(storageId: String): List<VersionedApk> =
        withContext(Dispatchers.IO) {
            storage(storageId).list()
                .filter { it.name.contains("backup", ignoreCase = true) }
                .map { apk ->
                    VersionedApk(
                        apk = apk,
                        version = versionOf(apk) ?: versionFromName(apk.name),
                    )
                }
        }

    private fun versionFromName(name: String): String? =
        PATCHED_NAME.find(name)?.groupValues?.getOrNull(1)

    private suspend fun versionOf(apk: StoredApk): String? {
        val bytes = runCatching { storage(apk.storageId).read(apk) }.getOrNull() ?: return null

        val dir = File(context.cacheDir, "apk-version").apply { mkdirs() }
        val file = File(dir, apk.name)
        file.writeBytes(bytes)

        return runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.versionName
        }.getOrNull()
    }

    suspend fun deleteBackup(storageId: String, apk: StoredApk): Boolean =
        storage(storageId).delete(apk)

    suspend fun install(storageId: String, apk: StoredApk): ApkInstaller.Result {
        val file = materialise(storageId, apk)
        return installer.install(file)
    }

    suspend fun materialise(storageId: String, apk: StoredApk): File =
        withContext(Dispatchers.IO) {
            val bytes = storage(storageId).read(apk)
            val dir = File(context.cacheDir, "apk-install").apply { mkdirs() }
            File(dir, apk.name).apply { writeBytes(bytes) }
        }

    suspend fun deleteResult(storageId: String, apk: StoredApk): Boolean =
        storage(storageId).delete(apk)

    suspend fun shareApk(storageId: String, apk: StoredApk) {
        val file = materialise(storageId, apk)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, null).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun defaultIdentity(): SigningIdentity = keyStore.defaultIdentity()

    suspend fun userIdentity(
        uri: Uri,
        alias: String,
        storePassword: String,
        keyPassword: String?,
    ): SigningIdentity = keyStore.userIdentity(uri, alias, storePassword, keyPassword)

    fun isTermuxInstalled(): Boolean = installer.isTermuxInstalled()

    fun installedVersion(): String? = installer.installedVersion()

    fun installedSignerDigest(packageName: String): String? =
        installer.installedSignerDigest(packageName)

    fun canInstall(): Boolean = installer.canInstall()

    fun openInstallPermissionSettings() = installer.openInstallPermissionSettings()

    fun requestUninstall() = installer.requestUninstall()

    private fun readAsset(name: String): ByteArray =
        context.assets.open(name).use { it.readBytes() }

    private fun readPatchScripts(): PatchScripts = PatchScripts(
        touchShim = readScript(PatchScripts.TOUCH),
        cpShim = readScript(PatchScripts.CP),
        tarShim = readScript(PatchScripts.TAR),
        pathHook = readScript(PatchScripts.PATH_HOOK),
        vmfixHook = readScript(PatchScripts.VMFIX_HOOK),
        aptConf = readScript(PatchScripts.APT_CONF),
        loginHookAddition = readScript(PatchScripts.LOGIN_HOOK_ADDITION),
        repairHook = readScript(PatchScripts.REPAIR_HOOK),
        secondStageLauncher = readScript(PatchScripts.SECOND_STAGE_LAUNCHER),
    )

    private fun readScript(name: String): String =
        context.assets.open(name)
            .bufferedReader()
            .use { it.readText() }
            .replace("\r\n", "\n")
            .replace("\r", "\n")

    private fun backupName(source: PatchSource): String {
        val version = (source as? PatchSource.Installed)?.versionName
            ?: (source as? PatchSource.Picked)?.apk?.nameWithoutExtension
            ?: "termux"
        return "termux-$version-backup-${timestamp()}.apk"
    }

    private fun patchedName(source: PatchSource): String {
        val version = (source as? PatchSource.Installed)?.versionName ?: "custom"
        return "termux-$version-patched-${timestamp()}.apk"
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    sealed interface PatchOutcome {
        data class Success(
            val stored: StoredApk,
            val backup: StoredApk?,
            val report: io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchReport,

            val signingIdentity: SigningIdentity,
        ) : PatchOutcome

        data class Failed(
            val reason: PatchFailure,
            val detail: String? = null,
        ) : PatchOutcome
    }
    enum class PatchFailure {

        BACKUP_FAILED,

        NOT_SAVED,

        CANCELLED,

        PATCH_FAILED,
    }

    private companion object {
        const val TAG = "ApkPatch"

        val PATCHED_NAME = Regex("""^termux-(.+)-(patched|backup)-\d{8}-\d{6}\.apk$""")
    }
}
