package io.github.alaabataineh92.termuxvmfixer.core.device.apk

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.alaabataineh92.termuxvmfixer.core.common.log.LogSink
import io.github.alaabataineh92.termuxvmfixer.core.common.log.NoOpLogSink
import io.github.alaabataineh92.termuxvmfixer.core.model.DeviceError
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.SigningIdentity
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.SigningKeySource
import io.github.alaabataineh92.termuxvmfixer.data.apkpatch.sign.certificateDigest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

@Singleton
class SigningKeyStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    logSink: LogSink = NoOpLogSink,
) {

    private val logger = logSink.logger(TAG)

    private val keyDir: File get() = File(context.filesDir, "keys").apply { mkdirs() }
    private val defaultKeyFile: File get() = File(keyDir, DEFAULT_KEYSTORE)

    private val prefs: SharedPreferences by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            encryptedPrefs()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun encryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun defaultIdentity(): SigningIdentity = withContext(Dispatchers.IO) {
        ensureDefaultKey()
        load(defaultKeyFile, defaultPassword(), DEFAULT_ALIAS, SigningKeySource.GENERATED)
    }

    suspend fun termuxIdentity(): SigningIdentity = withContext(Dispatchers.IO) {
        termuxKey()
    }

    suspend fun termuxKeyDigest(): String = withContext(Dispatchers.IO) {
        certificateDigest(termuxKey().certificates.first())
    }

    private fun termuxKey(): SigningIdentity {
        val bytes = context.assets.open(TERMUX_KEYSTORE).use { it.readBytes() }

        val store = KeyStore.getInstance(JKS).apply {
            load(bytes.inputStream(), TERMUX_KEYSTORE_PASSWORD.toCharArray())
        }

        val alias = store.aliases().toList().firstOrNull { store.isKeyEntry(it) }
            ?: throw DeviceError.Precondition("the Termux keystore contains no key")

        val password = TERMUX_KEYSTORE_PASSWORD.toCharArray()

        val key = store.getKey(alias, password) as? java.security.PrivateKey
            ?: throw DeviceError.Precondition("the Termux key could not be read")

        val chain = store.getCertificateChain(alias)
            ?.map { it as X509Certificate }
            ?.takeIf { it.isNotEmpty() }
            ?: throw DeviceError.Precondition("the Termux key has no certificate chain")

        logger.info(TAG) { "using Termux's own key, alias '$alias'" }
        return SigningIdentity(SigningKeySource.TERMUX, alias, key, chain)
    }

    suspend fun userIdentity(
        uri: Uri,
        alias: String,
        storePassword: String,
        keyPassword: String? = null,
    ): SigningIdentity = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw DeviceError.Precondition("cannot read the keystore")

        val store = try {
            KeyStore.getInstance(PKCS12).apply { load(bytes.inputStream(), storePassword.toCharArray()) }
        } catch (_: Throwable) {
            try {
                KeyStore.getInstance(JKS).apply { load(bytes.inputStream(), storePassword.toCharArray()) }
            } catch (_: Throwable) {
                throw DeviceError.Precondition(
                    "cannot open the keystore - wrong password, or not a PKCS12/JKS file",
                )
            }
        }

        val actualAlias = alias.takeIf { it.isNotBlank() }
            ?: store.aliases().toList().firstOrNull { store.isKeyEntry(it) }
            ?: throw DeviceError.Precondition("the keystore contains no key")

        val password = (keyPassword ?: storePassword).toCharArray()
        val key = try {
            store.getKey(actualAlias, password) as? java.security.PrivateKey
        } catch (_: Throwable) {
            throw DeviceError.Precondition("cannot read the key '$actualAlias' - wrong password?")
        } ?: throw DeviceError.Precondition("'$actualAlias' is not a private key entry")

        val chain = store.getCertificateChain(actualAlias)
            ?.map { it as X509Certificate }
            ?.takeIf { it.isNotEmpty() }
            ?: throw DeviceError.Precondition("the key has no certificate chain")

        logger.info(TAG) { "loaded the user keystore, alias '$actualAlias'" }
        SigningIdentity(SigningKeySource.USER, actualAlias, key, chain)
    }

    private fun ensureDefaultKey() {
        if (defaultKeyFile.isFile) return

        logger.info(TAG) { "generating the default signing key" }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(KEY_BITS, SecureRandom())
        val pair = generator.generateKeyPair()

        val now = System.currentTimeMillis()
        val name = javax.security.auth.x500.X500Principal(
            "CN=Termux VM Fixer, O=termux-vm-fixer, C=US",
        )

        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(now),
            Date(now - 86_400_000L),
            Date(now + CERTIFICATE_DAYS * 86_400_000L),
            name,
            pair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .build(pair.private)
        val certificate = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .getCertificate(builder.build(signer))

        val password = randomPassword()
        val store = KeyStore.getInstance(PKCS12)
        store.load(null, null)
        store.setKeyEntry(DEFAULT_ALIAS, pair.private, password.toCharArray(), arrayOf(certificate))

        val temp = File(keyDir, "$DEFAULT_KEYSTORE.part")
        temp.outputStream().use { store.store(it, password.toCharArray()) }
        if (!temp.renameTo(defaultKeyFile)) {
            temp.copyTo(defaultKeyFile, overwrite = true)
            temp.delete()
        }

        prefs.edit { putString(KEY_PASSWORD, password) }
        logger.info(TAG) { "default key written to ${defaultKeyFile.absolutePath}" }
    }

    private fun defaultPassword(): String =
        prefs.getString(KEY_PASSWORD, null)
            ?: throw DeviceError.Precondition(
                "the default key exists but its password is missing - the app's " +
                    "preferences were cleared. Generate a new key.",
            )

    private fun randomPassword(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun load(
        file: File,
        password: String,
        alias: String,
        source: SigningKeySource,
    ): SigningIdentity {
        val store = KeyStore.getInstance(PKCS12)
        file.inputStream().use { store.load(it, password.toCharArray()) }

        val key = store.getKey(alias, password.toCharArray()) as? java.security.PrivateKey
            ?: throw DeviceError.Precondition("the default key could not be read")

        val chain = store.getCertificateChain(alias)
            ?.map { it as X509Certificate }
            ?.takeIf { it.isNotEmpty() }
            ?: throw DeviceError.Precondition("the default key has no certificate chain")

        return SigningIdentity(source, alias, key, chain)
    }

    private companion object {
        const val TAG = "SigningKeys"
        const val PREFS = "signing-keys"
        const val KEY_PASSWORD = "default-key-password"
        const val DEFAULT_KEYSTORE = "default.p12"
        const val DEFAULT_ALIAS = "termux-vm-fixer"
        const val PKCS12 = "PKCS12"
        const val JKS = "JKS"
        const val KEY_BITS = 2048
        const val CERTIFICATE_DAYS = 3650L

        const val TERMUX_KEYSTORE = "termux-testkey.jks"
        const val TERMUX_KEYSTORE_PASSWORD = "xrj45yWGLbsO7W0v"
    }
}
