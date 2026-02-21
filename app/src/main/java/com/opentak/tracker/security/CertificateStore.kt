package com.opentak.tracker.security

import android.content.Context
import com.opentak.tracker.data.CertificateInfo
import com.opentak.tracker.data.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*

@Singleton
class CertificateStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: LogRepository
) {
    companion object {
        private const val PKCS12_PASSWORD = "atakatak" // Standard TAK keystore password
        private const val CLIENT_KEY_ALIAS = "client-key"
        private const val CA_ALIAS_PREFIX = "ca-"
    }

    private val certsDir: File by lazy {
        File(context.filesDir, "certs").also { it.mkdirs() }
    }

    private fun clientKeystoreFile(serverUrl: String): File =
        File(certsDir, "client-${serverUrl.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.p12")

    private fun trustKeystoreFile(serverUrl: String): File =
        File(certsDir, "trust-${serverUrl.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.p12")

    // In-memory cache of generated key pairs (before enrollment completes)
    private val pendingKeyPairs = mutableMapOf<String, java.security.KeyPair>()

    fun generateKeyPair(serverUrl: String): java.security.KeyPair {
        logRepository.info("CertStore", "Generating RSA 2048 keypair for $serverUrl")
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()
        pendingKeyPairs[serverUrl] = keyPair
        return keyPair
    }

    fun storeClientCertificate(certDer: ByteArray, serverUrl: String, caCertsDer: List<ByteArray> = emptyList()) {
        val certFactory = CertificateFactory.getInstance("X.509")
        val clientCert = certFactory.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate

        val keyPair = pendingKeyPairs.remove(serverUrl)
        if (keyPair == null) {
            logRepository.error("CertStore", "No pending key pair found for $serverUrl")
            return
        }

        // Build full chain: client cert + CA certs
        val chain = mutableListOf<java.security.cert.Certificate>(clientCert)
        caCertsDer.forEach { caDer ->
            chain.add(certFactory.generateCertificate(ByteArrayInputStream(caDer)))
        }

        // Create PKCS12 keystore with private key + cert chain
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(CLIENT_KEY_ALIAS, keyPair.private, PKCS12_PASSWORD.toCharArray(), chain.toTypedArray())

        val file = clientKeystoreFile(serverUrl)
        file.outputStream().use { ks.store(it, PKCS12_PASSWORD.toCharArray()) }
        logRepository.info("CertStore", "Client PKCS12 saved (${chain.size} certs): ${clientCert.subjectDN}")
    }

    fun storeTrustChain(caCertsDer: List<ByteArray>, serverUrl: String) {
        val certFactory = CertificateFactory.getInstance("X.509")
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)

        caCertsDer.forEachIndexed { index, der ->
            val cert = certFactory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
            ks.setCertificateEntry("$CA_ALIAS_PREFIX$index", cert)
            logRepository.info("CertStore", "CA cert stored [$index]: ${cert.subjectDN}")
        }

        val file = trustKeystoreFile(serverUrl)
        file.outputStream().use { ks.store(it, PKCS12_PASSWORD.toCharArray()) }
    }

    fun hasClientCertificate(serverUrl: String): Boolean {
        return clientKeystoreFile(serverUrl).exists()
    }

    fun getClientCertificateInfo(serverUrl: String): CertificateInfo? {
        val file = clientKeystoreFile(serverUrl)
        if (!file.exists()) return null

        return try {
            val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, PKCS12_PASSWORD.toCharArray()) }

            val cert = ks.getCertificate(CLIENT_KEY_ALIAS) as? X509Certificate ?: return null
            val now = java.util.Date()
            val daysUntilExpiry = TimeUnit.MILLISECONDS.toDays(cert.notAfter.time - now.time)

            val md = MessageDigest.getInstance("SHA-256")
            val fingerprint = md.digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }

            CertificateInfo(
                subject = cert.subjectDN.name,
                issuer = cert.issuerDN.name,
                expiresAt = cert.notAfter.toInstant(),
                fingerprint = fingerprint,
                isExpiringSoon = daysUntilExpiry in 0..30,
                isExpired = daysUntilExpiry < 0
            )
        } catch (e: Exception) {
            logRepository.error("CertStore", "Failed to read cert info: ${e.message}")
            null
        }
    }

    fun buildKeyManager(serverUrl: String): KeyManager? {
        val file = clientKeystoreFile(serverUrl)
        if (!file.exists()) {
            logRepository.warn("CertStore", "No client keystore for $serverUrl")
            return null
        }

        return try {
            val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, PKCS12_PASSWORD.toCharArray()) }

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, PKCS12_PASSWORD.toCharArray())
            kmf.keyManagers.firstOrNull()
        } catch (e: Exception) {
            logRepository.error("CertStore", "Failed to build KeyManager: ${e.message}")
            null
        }
    }

    fun buildTrustManager(serverUrl: String): X509TrustManager {
        val file = trustKeystoreFile(serverUrl)
        if (!file.exists()) {
            logRepository.warn("CertStore", "No trust keystore, using system trust")
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            return tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }

        return try {
            val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, PKCS12_PASSWORD.toCharArray()) }
            TakTrustManager(ks, logRepository)
        } catch (e: Exception) {
            logRepository.error("CertStore", "Failed to load trust store: ${e.message}")
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }
    }

    fun clearCertificates(serverUrl: String) {
        clientKeystoreFile(serverUrl).delete()
        trustKeystoreFile(serverUrl).delete()
        pendingKeyPairs.remove(serverUrl)
        logRepository.info("CertStore", "Certificates cleared for $serverUrl")
    }
}

/**
 * Custom TrustManager that validates against enrollment CA chain,
 * falling back to system trust store.
 */
class TakTrustManager(
    private val customTrustStore: KeyStore,
    private val logRepository: LogRepository
) : X509TrustManager {

    private val systemTm: X509TrustManager by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    private val customTm: X509TrustManager by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(customTrustStore)
        tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        try {
            systemTm.checkServerTrusted(chain, authType)
            logRepository.info("TLS", "Server trusted by system CA")
        } catch (systemEx: Exception) {
            try {
                customTm.checkServerTrusted(chain, authType)
                logRepository.info("TLS", "Server trusted by enrollment CA")
            } catch (customEx: Exception) {
                logRepository.error("TLS", "Server not trusted: ${customEx.message}")
                throw customEx
            }
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
