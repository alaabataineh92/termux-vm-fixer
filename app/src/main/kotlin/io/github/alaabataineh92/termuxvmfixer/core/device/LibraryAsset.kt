package io.github.alaabataineh92.termuxvmfixer.core.device

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryAsset @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val abis: List<String> by lazy {
        context.assets.list(FOLDER)?.toList().orEmpty()
    }

    val abi: String? by lazy {
        Build.SUPPORTED_ABIS.firstOrNull { it in abis }
    }

    val size: Long by lazy {
        val name = abi ?: return@lazy 0L

        context.assets.open(path(name)).use { input ->
            input.readBytes().size.toLong()
        }
    }

    fun file(): File {
        val out = File(context.cacheDir, NAME)
        val name = abi ?: return out

        if (out.isFile && out.length() == size) return out

        context.assets.open(path(name)).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }

        return out
    }

    private fun path(abi: String): String = "$FOLDER/$abi/$NAME"

    private companion object {
        const val FOLDER = "libvmfix"
        const val NAME = "libvmfix.so"
    }
}
