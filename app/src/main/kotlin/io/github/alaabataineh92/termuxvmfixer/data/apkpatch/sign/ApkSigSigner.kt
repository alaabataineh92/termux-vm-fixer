package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign

import com.android.apksig.ApkSigner as ApksigSigner
import java.io.File

class ApkSigSigner : ApkSigner {

    override fun sign(
        input: File,
        output: File,
        identity: SigningIdentity,
    ): File {
        require(input.isFile) { "the APK to sign does not exist: $input" }
        output.parentFile?.mkdirs()

        val signerConfig = ApksigSigner.SignerConfig.Builder(
            identity.label,
            identity.privateKey,
            identity.certificates,
        ).build()

        val signer = ApksigSigner.Builder(listOf(signerConfig))
            .setInputApk(input)
            .setOutputApk(output)

            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)

            .setMinSdkVersion(MIN_SDK)
            .build()

        signer.sign()
        return output
    }

    private companion object {

        const val MIN_SDK = 24
    }
}
