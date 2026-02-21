package com.opentak.tracker.enrollment

import android.util.Base64
import com.opentak.tracker.data.EnrollmentParameters
import com.opentak.tracker.data.EnrollmentStatus
import com.opentak.tracker.data.LogRepository
import com.opentak.tracker.data.ServerConfig
import com.opentak.tracker.data.SettingsRepository
import com.opentak.tracker.security.CertificateStore
import com.opentak.tracker.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URL
import java.security.KeyPair
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*

@Singleton
class CSREnrollmentManager @Inject constructor(
    private val certStore: CertificateStore,
    private val settings: SettingsRepository,
    private val logRepository: LogRepository
) {
    private val _status = MutableStateFlow(EnrollmentStatus.NOT_STARTED)
    val status: StateFlow<EnrollmentStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastEnrolledServer = MutableStateFlow<ServerConfig?>(null)
    val lastEnrolledServer: StateFlow<ServerConfig?> = _lastEnrolledServer.asStateFlow()

    suspend fun beginEnrollment(params: EnrollmentParameters) {
        withContext(Dispatchers.IO) {
            try {
                _status.value = EnrollmentStatus.CONNECTING
                _statusMessage.value = "Connecting to enrollment server..."

                val serverUrl = params.serverURL
                val csrPort = params.csrPort.ifBlank { settings.csrPort.first() }

                // Step 1: Fetch CA Config
                _status.value = EnrollmentStatus.CONFIGURING
                _statusMessage.value = "Fetching CA configuration..."
                logRepository.info("Enrollment", "Fetching CA config from $serverUrl:$csrPort")

                val configUrl = "https://$serverUrl:$csrPort${Constants.CERT_CONFIG_PATH}"
                val authHeader = buildBasicAuthHeader(params.username, params.password)
                val configXml = httpGet(configUrl, authHeader)

                val caConfig = parseCAConfig(configXml)
                val orgName = caConfig["O"] ?: ""
                val orgUnitName = caConfig["OU"] ?: ""
                logRepository.info("Enrollment", "CA config: O=$orgName, OU=$orgUnitName")

                // Step 2: Generate keypair
                _statusMessage.value = "Generating keypair..."
                logRepository.info("Enrollment", "Generating RSA keypair")
                val keyPair = certStore.generateKeyPair(serverUrl)

                // Step 3: Build CSR
                _statusMessage.value = "Building certificate request..."
                val csrDer = buildCSR(keyPair, params.username, orgName, orgUnitName)
                logRepository.info("Enrollment", "CSR generated (${csrDer.size} bytes)")

                // Step 4: Submit CSR
                _status.value = EnrollmentStatus.ENROLLING
                _statusMessage.value = "Submitting certificate request..."

                val uid = settings.deviceUid
                val version = "1.0.0"
                val csrUrl = "https://$serverUrl:$csrPort${Constants.CSR_PATH}?clientUid=$uid&version=$version"
                val csrBody = Base64.encodeToString(csrDer, Base64.NO_WRAP)

                logRepository.info("Enrollment", "Submitting CSR to $serverUrl:$csrPort")
                val responseJson = httpPost(csrUrl, csrBody, authHeader)

                // Step 5: Parse and store certificates
                _statusMessage.value = "Installing certificates..."
                val json = JSONObject(responseJson)
                storeCertificates(json, serverUrl)

                // Step 6: Add to server list
                val secureApiPort = params.secureApiPort.ifBlank { settings.secureApiPort.first() }
                val newServer = ServerConfig(
                    name = serverUrl,
                    address = serverUrl,
                    port = params.serverPort.toIntOrNull() ?: 8089,
                    csrPort = csrPort.toIntOrNull() ?: 8446,
                    secureApiPort = secureApiPort.toIntOrNull() ?: 8443
                )
                settings.addServerConfig(newServer)
                _lastEnrolledServer.value = newServer

                // Also keep legacy keys for backward compat
                settings.setServerUrl(serverUrl)
                settings.setServerPort(params.serverPort)

                // Apply user settings from URI if provided
                if (params.callsign.isNotBlank()) settings.setCallsign(params.callsign)
                if (params.team.isNotBlank()) settings.setTeam(params.team)
                if (params.role.isNotBlank()) settings.setRole(params.role)

                _status.value = EnrollmentStatus.SUCCEEDED
                _statusMessage.value = "Enrollment complete!"
                logRepository.info("Enrollment", "Enrollment succeeded for $serverUrl")

            } catch (e: SSLException) {
                _status.value = EnrollmentStatus.UNTRUSTED
                _statusMessage.value = "Server SSL certificate not trusted"
                logRepository.error("Enrollment", "SSL error: ${e.message}")
            } catch (e: Exception) {
                _status.value = EnrollmentStatus.FAILED
                _statusMessage.value = "Enrollment failed: ${e.message}"
                logRepository.error("Enrollment", "Failed: ${e.javaClass.simpleName} - ${e.message}")
            }
        }
    }

    fun reset() {
        _status.value = EnrollmentStatus.NOT_STARTED
        _statusMessage.value = ""
        _lastEnrolledServer.value = null
    }

    private fun buildBasicAuthHeader(username: String, password: String): String {
        val credentials = "$username:$password"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    private fun buildCSR(keyPair: KeyPair, commonName: String, orgName: String, orgUnitName: String): ByteArray {
        val subject = X500Name("CN=$commonName, O=$orgName, OU=$orgUnitName")
        val csrBuilder = JcaPKCS10CertificationRequestBuilder(subject, keyPair.public)
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val csr = csrBuilder.build(signer)
        return csr.encoded
    }

    private fun parseCAConfig(xml: String): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "nameEntry") {
                val name = parser.getAttributeValue(null, "name")
                val value = parser.getAttributeValue(null, "value")
                if (name != null && value != null && !entries.containsKey(name)) {
                    entries[name] = value
                }
            }
            eventType = parser.next()
        }
        return entries
    }

    private fun storeCertificates(json: JSONObject, serverUrl: String) {
        // Collect CA trust chain first (needed for client cert chain)
        val caCertsDer = mutableListOf<ByteArray>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.startsWith("ca")) {
                val caCertB64 = json.getString(key)
                val caPem = "-----BEGIN CERTIFICATE-----\n$caCertB64\n-----END CERTIFICATE-----"
                caCertsDer.add(parsePemToDer(caPem))
            }
        }
        if (caCertsDer.isNotEmpty()) {
            certStore.storeTrustChain(caCertsDer, serverUrl)
            logRepository.info("Enrollment", "Trust chain installed (${caCertsDer.size} CA certs)")
        }

        // Store signed client cert with full chain on the PrivateKeyEntry
        val signedCertB64 = json.optString("signedCert", "")
        if (signedCertB64.isNotBlank()) {
            val certPem = "-----BEGIN CERTIFICATE-----\n$signedCertB64\n-----END CERTIFICATE-----"
            val certDer = parsePemToDer(certPem)
            certStore.storeClientCertificate(certDer, serverUrl, caCertsDer)
            logRepository.info("Enrollment", "Client certificate installed")
        }
    }

    private fun parsePemToDer(pem: String): ByteArray {
        val b64 = pem.lines()
            .filter { !it.startsWith("-----") }
            .joinToString("")
        return Base64.decode(b64, Base64.DEFAULT)
    }

    private fun httpGet(url: String, authHeader: String): String {
        val connection = createTrustAllConnection(url)
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", authHeader)
        connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw Exception("HTTP $responseCode from $url")
        }
        return connection.inputStream.bufferedReader().readText()
    }

    private fun httpPost(url: String, body: String, authHeader: String): String {
        val connection = createTrustAllConnection(url)
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", authHeader)
        connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000

        connection.outputStream.use { it.write(body.toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw Exception("HTTP $responseCode from $url")
        }
        return connection.inputStream.bufferedReader().readText()
    }

    /**
     * During enrollment, we must trust the server's cert even though we don't
     * have its CA yet (the CA is what we're fetching). This mirrors the iOS
     * reference behavior.
     */
    private fun createTrustAllConnection(url: String): java.net.HttpURLConnection {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>, authType: String) {}
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        })

        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(null, trustAll, java.security.SecureRandom())

        // Wrap socket factory to force TLS 1.2/1.3 protocols
        val baseFactory = sslContext.socketFactory
        val tlsFactory = object : SSLSocketFactory() {
            override fun getDefaultCipherSuites() = baseFactory.defaultCipherSuites
            override fun getSupportedCipherSuites() = baseFactory.supportedCipherSuites
            override fun createSocket() = (baseFactory.createSocket() as SSLSocket).apply { enableModernTls() }
            override fun createSocket(s: java.net.Socket, host: String, port: Int, autoClose: Boolean) =
                (baseFactory.createSocket(s, host, port, autoClose) as SSLSocket).apply { enableModernTls() }
            override fun createSocket(host: String, port: Int) =
                (baseFactory.createSocket(host, port) as SSLSocket).apply { enableModernTls() }
            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int) =
                (baseFactory.createSocket(host, port, localHost, localPort) as SSLSocket).apply { enableModernTls() }
            override fun createSocket(host: java.net.InetAddress, port: Int) =
                (baseFactory.createSocket(host, port) as SSLSocket).apply { enableModernTls() }
            override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int) =
                (baseFactory.createSocket(address, port, localAddress, localPort) as SSLSocket).apply { enableModernTls() }
        }

        val connection = URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
        connection.sslSocketFactory = tlsFactory
        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
        return connection
    }

    private fun SSLSocket.enableModernTls() {
        enabledProtocols = supportedProtocols.filter { it in listOf("TLSv1.2", "TLSv1.3") }.toTypedArray()
    }
}
