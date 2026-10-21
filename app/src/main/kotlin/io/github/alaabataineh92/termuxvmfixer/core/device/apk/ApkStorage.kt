package io.github.alaabataineh92.termuxvmfixer.core.device.apk

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import androidx.core.content.edit

enum class ApkStorageKind {
    APP,
    DOWNLOADS,
    CHOSEN_FOLDER,
}

interface ApkStorage {
    val id: String
    val kind: ApkStorageKind
    suspend fun write(fileName: String, bytes: ByteArray): StoredApk
    suspend fun list(): List<StoredApk>
    suspend fun delete(apk: StoredApk): Boolean
    suspend fun read(apk: StoredApk): ByteArray
}

data class StoredApk(
    val storageId: String,
    val name: String,
    val sizeBytes: Long,
    val location: String,

    val reference: String,
) {
    val sizeLabel: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / 1024.0 / 1024.0)
            sizeBytes >= 1024 -> "%d KB".format(sizeBytes / 1024)
            else -> "$sizeBytes B"
        }
}

data class VersionedApk(
    val apk: StoredApk,
    val version: String?,
)

@Singleton
class PrivateApkStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    logSink: LogSink = NoOpLogSink,
) : ApkStorage {

    private val logger = logSink.logger(TAG)

    override val id: String = ID
    override val kind: ApkStorageKind = ApkStorageKind.APP

    private fun dir(kind: Kind): File = File(context.filesDir, kind.directory).apply { mkdirs() }

    override suspend fun write(fileName: String, bytes: ByteArray): StoredApk =
        withContext(Dispatchers.IO) {
            val kind = Kind.of(fileName)
            val file = File(dir(kind), fileName)

            val temp = File(file.parentFile, "${file.name}.part")
            temp.writeBytes(bytes)
            if (temp.length() != bytes.size.toLong()) {
                temp.delete()
                throw DeviceError.Precondition(
                    "writing $fileName produced ${temp.length()} bytes, expected ${bytes.size}",
                )
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }

            logger.info(TAG) { "wrote ${file.absolutePath} (${bytes.size} bytes)" }
            stored(file)
        }

    override suspend fun list(): List<StoredApk> = withContext(Dispatchers.IO) {
        Kind.entries.flatMap { kind ->
            dir(kind).listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(".apk") }
                .map { stored(it) }
        }.sortedByDescending { it.name }
    }

    override suspend fun delete(apk: StoredApk): Boolean = withContext(Dispatchers.IO) {
        val file = File(apk.reference)

        val inside = Kind.entries.any { file.parentFile?.canonicalFile == dir(it).canonicalFile }
        if (!inside) {
            logger.warn(TAG) { "refusing to delete ${file.absolutePath}: not in our storage" }
            return@withContext false
        }
        file.delete()
    }

    override suspend fun read(apk: StoredApk): ByteArray = withContext(Dispatchers.IO) {
        File(apk.reference).readBytes()
    }

    private fun stored(file: File) = StoredApk(
        storageId = ID,
        name = file.name,
        sizeBytes = file.length(),
        location = file.absolutePath,
        reference = file.absolutePath,
    )

    enum class Kind(val directory: String) {
        BACKUP("backups"),
        PATCHED("patched"),
        ;

        companion object {

            fun of(fileName: String): Kind =
                if (fileName.contains("backup", ignoreCase = true)) BACKUP else PATCHED
        }
    }

    private companion object {
        const val TAG = "ApkStorage"
        const val ID = "private"
    }
}

@Singleton
class DownloadsApkStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    logSink: LogSink = NoOpLogSink,
) : ApkStorage {

    private val logger = logSink.logger(TAG)

    override val id: String = ID
    override val kind: ApkStorageKind = ApkStorageKind.DOWNLOADS

    override suspend fun write(fileName: String, bytes: ByteArray): StoredApk =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(fileName, bytes)
            } else {
                writeLegacy(fileName, bytes)
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(fileName: String, bytes: ByteArray): StoredApk {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, APK_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw DeviceError.Precondition("the system refused to create $fileName in Downloads")

        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw DeviceError.Precondition("cannot open $fileName for writing")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            logger.info(TAG) { "wrote Downloads/$fileName (${bytes.size} bytes)" }
            return StoredApk(
                storageId = ID,
                name = fileName,
                sizeBytes = bytes.size.toLong(),
                location = "Downloads/$fileName",
                reference = uri.toString(),
            )
        } catch (e: Throwable) {

            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun writeLegacy(fileName: String, bytes: ByteArray): StoredApk {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(bytes)

        logger.info(TAG) { "wrote ${file.absolutePath} (${bytes.size} bytes)" }
        return StoredApk(
            storageId = ID,
            name = fileName,
            sizeBytes = bytes.size.toLong(),
            location = file.absolutePath,
            reference = file.absolutePath,
        )
    }

    override suspend fun list(): List<StoredApk> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listViaMediaStore()
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".apk") }
                .map {
                    StoredApk(
                        ID, it.name, it.length(), it.absolutePath, it.absolutePath,
                    )
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listViaMediaStore(): List<StoredApk> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
        )
        val out = ArrayList<StoredApk>()

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.MIME_TYPE}=?",
            arrayOf(APK_MIME),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1) ?: continue
                val size = cursor.getLong(2)
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, id,
                )
                out += StoredApk(ID, name, size, "Downloads/$name", uri.toString())
            }
        }
        return out
    }

    override suspend fun delete(apk: StoredApk): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(apk.reference.toUri(), null, null) > 0
            } else {
                File(apk.reference).delete()
            }
        }.getOrDefault(false)
    }

    override suspend fun read(apk: StoredApk): ByteArray = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.openInputStream(apk.reference.toUri())?.use { it.readBytes() }
                ?: throw DeviceError.Precondition("cannot read ${apk.name}")
        } else {
            File(apk.reference).readBytes()
        }
    }

    private companion object {
        const val TAG = "ApkDownloads"
        const val ID = "downloads"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}

@Singleton
class SafApkStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    logSink: LogSink = NoOpLogSink,
) : ApkStorage {

    private val logger = logSink.logger(TAG)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override val id: String = ID
    override val kind: ApkStorageKind = ApkStorageKind.CHOSEN_FOLDER

    var treeUri: Uri?
        get() = prefs.getString(KEY_TREE, null)?.let(Uri::parse)
        private set(value) {
            prefs.edit {
                if (value == null) remove(KEY_TREE) else putString(KEY_TREE, value.toString())
            }
        }

    override suspend fun write(fileName: String, bytes: ByteArray): StoredApk =
        withContext(Dispatchers.IO) {
            val tree = treeUri
                ?: throw DeviceError.Precondition("no folder has been chosen yet")
            val dir = DocumentFile.fromTreeUri(context, tree)
                ?: throw DeviceError.Precondition("the chosen folder is no longer reachable")

            dir.findFile(fileName)?.delete()

            val file = dir.createFile(APK_MIME, fileName)
                ?: throw DeviceError.Precondition("cannot create $fileName in the chosen folder")

            context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
                ?: throw DeviceError.Precondition("cannot open $fileName for writing")

            logger.info(TAG) { "wrote ${file.uri} (${bytes.size} bytes)" }
            StoredApk(ID, file.name ?: fileName, bytes.size.toLong(), file.uri.toString(), file.uri.toString())
        }

    override suspend fun list(): List<StoredApk> = withContext(Dispatchers.IO) {
        val tree = treeUri ?: return@withContext emptyList()
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return@withContext emptyList()

        dir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".apk") == true }
            .map {
                StoredApk(ID, it.name ?: "?", it.length(), it.uri.toString(), it.uri.toString())
            }
    }

    override suspend fun delete(apk: StoredApk): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            DocumentFile.fromSingleUri(context, apk.reference.toUri())?.delete() == true
        }.getOrDefault(false)
    }

    override suspend fun read(apk: StoredApk): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(apk.reference.toUri())?.use { it.readBytes() }
            ?: throw DeviceError.Precondition("cannot read ${apk.name}")
    }

    private companion object {
        const val TAG = "ApkSaf"
        const val ID = "saf"
        const val PREFS = "apk-storage"
        const val KEY_TREE = "tree-uri"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
