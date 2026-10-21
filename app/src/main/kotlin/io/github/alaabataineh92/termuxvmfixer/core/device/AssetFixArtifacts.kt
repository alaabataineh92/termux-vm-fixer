package io.github.alaabataineh92.termuxvmfixer.core.device

import android.content.Context
import io.github.alaabataineh92.termuxvmfixer.data.fix.content.FixArtifacts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetFixArtifacts @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val library: LibraryAsset,
) : FixArtifacts {

    override fun shim(name: String): String {
        requireValidShimName(name)
        return readAsset("shims/$name")
    }

    override fun script(name: String): String = readAsset("scripts/$name")

    override fun library(): File = library.file()

    override fun supportsCurrentAbi(): Boolean = library.abi != null

    private fun readAsset(name: String): String =
        context.assets.open(name)
            .bufferedReader()
            .use { it.readText() }
            .normaliseLineEndings()

    private fun String.normaliseLineEndings(): String =
        replace("\r\n", "\n").replace("\r", "\n")
}
