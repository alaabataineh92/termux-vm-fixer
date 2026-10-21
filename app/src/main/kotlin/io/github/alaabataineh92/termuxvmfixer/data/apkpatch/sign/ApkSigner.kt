package io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign

import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate

enum class SigningKeySource {
    TERMUX,
    GENERATED,
    USER,
}

data class SigningIdentity(
    val source: SigningKeySource,

    val alias: String? = null,
    val privateKey: java.security.PrivateKey,
    val certificates: List<X509Certificate>,
) {
    init {
        require(certificates.isNotEmpty()) { "a signing identity needs at least one certificate" }
    }

    val label: String
        get() = when (source) {
            SigningKeySource.TERMUX -> "Termux's own key"
            SigningKeySource.GENERATED -> "generated key"
            SigningKeySource.USER -> "keystore '$alias'"
        }
}

interface ApkSigner {

    fun sign(input: File, output: File, identity: SigningIdentity): File
}

fun certificateDigest(certificate: X509Certificate): String =
    MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { "%02x".format(it) }
