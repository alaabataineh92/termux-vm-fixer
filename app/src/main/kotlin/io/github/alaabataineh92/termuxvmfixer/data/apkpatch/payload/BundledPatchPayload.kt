package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.payload

import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.PatchPayload

class BundledPatchPayload(
    private val libraryBytes: ByteArray,
    private val patchScripts: PatchScripts,
    override val version: String = DEFAULT_VERSION,
) : PatchPayload {

    override fun library(): ByteArray = libraryBytes

    override fun scripts(): PatchScripts = patchScripts

    companion object {
        const val DEFAULT_VERSION = "1.0.0"
    }
}
