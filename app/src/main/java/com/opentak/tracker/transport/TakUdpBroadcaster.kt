package com.opentak.tracker.transport

import com.opentak.tracker.data.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TakUdpBroadcaster @Inject constructor(
    private val logRepository: LogRepository
) {
    private var socket: MulticastSocket? = null
    private var group: InetAddress? = null
    private var port: Int = 6969

    @Volatile
    var isConnected: Boolean = false
        private set

    suspend fun connect(address: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                this@TakUdpBroadcaster.port = port
                group = InetAddress.getByName(address)
                socket = MulticastSocket(port)
                isConnected = true
                logRepository.info("UDP", "Broadcasting to $address:$port")
                true
            } catch (e: Exception) {
                isConnected = false
                logRepository.error("UDP", "Setup failed: ${e.message}")
                false
            }
        }
    }

    suspend fun send(cotXml: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val s = socket ?: return@withContext false
                val g = group ?: return@withContext false
                val data = cotXml.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(data, data.size, g, port)
                s.send(packet)
                logRepository.info("UDP", "CoT broadcast (${data.size} bytes)")
                true
            } catch (e: Exception) {
                logRepository.error("UDP", "Broadcast failed: ${e.message}")
                false
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        group = null
        isConnected = false
        logRepository.info("UDP", "Disconnected")
    }
}
