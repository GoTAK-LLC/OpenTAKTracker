package com.opentak.tracker.transport

import com.opentak.tracker.data.LogRepository
import com.opentak.tracker.security.CertificateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
class TakTcpClient(
    val serverId: String,
    private val certStore: CertificateStore,
    private val logRepository: LogRepository
) {
    private var socket: SSLSocket? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    suspend fun connect(host: String, port: Int, serverUrl: String, trustAll: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                logRepository.info("TCP", "Connecting to $host:$port")

                val keyManager = certStore.buildKeyManager(serverUrl)
                val trustManager = if (trustAll) {
                    logRepository.warn("TCP", "Trust-all mode enabled (INSECURE)")
                    TrustAllManager()
                } else {
                    certStore.buildTrustManager(serverUrl)
                }

                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(
                    if (keyManager != null) arrayOf(keyManager) else null,
                    arrayOf(trustManager),
                    null
                )

                logRepository.info("TCP", "TLS handshake starting")
                val sslSocket = sslContext.socketFactory.createSocket() as SSLSocket
                sslSocket.connect(InetSocketAddress(host, port), 10_000)
                sslSocket.startHandshake()

                socket = sslSocket
                isConnected = true
                logRepository.info("TCP", "Connected to $host:$port (${sslSocket.session.protocol})")
                true
            } catch (e: Exception) {
                isConnected = false
                logRepository.error("TCP", "Connection failed: ${e.javaClass.simpleName} - ${e.message}")
                false
            }
        }
    }

    suspend fun send(cotXml: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val s = socket ?: return@withContext false
                val data = cotXml.toByteArray(Charsets.UTF_8)
                s.outputStream.write(data)
                s.outputStream.flush()
                logRepository.info("TCP", "CoT sent (${data.size} bytes)")
                true
            } catch (e: IOException) {
                logRepository.error("TCP", "Send failed: ${e.message}")
                isConnected = false
                false
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        isConnected = false
        logRepository.info("TCP", "Disconnected")
    }
}

/** Trust-all manager for testing only */
private class TrustAllManager : javax.net.ssl.X509TrustManager {
    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>, authType: String) {}
    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
}
