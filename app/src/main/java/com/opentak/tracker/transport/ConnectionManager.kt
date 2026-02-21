package com.opentak.tracker.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.opentak.tracker.data.ConnectionState
import com.opentak.tracker.data.LogRepository
import com.opentak.tracker.data.ServerConfig
import com.opentak.tracker.data.SettingsRepository
import com.opentak.tracker.security.CertificateStore
import com.opentak.tracker.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val certStore: CertificateStore,
    private val udpBroadcaster: TakUdpBroadcaster,
    private val settings: SettingsRepository,
    private val logRepository: LogRepository
) {
    // Per-server TCP clients
    private val tcpClients = ConcurrentHashMap<String, TakTcpClient>()

    // Per-server state
    private val _serverStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val serverStates: StateFlow<Map<String, ConnectionState>> = _serverStates.asStateFlow()

    // Aggregate state for backward compat (service notification, status badge)
    val state: StateFlow<ConnectionState> = _serverStates.map { states ->
        when {
            states.isEmpty() -> ConnectionState.DISCONNECTED
            states.values.any { it == ConnectionState.SENDING } -> ConnectionState.SENDING
            states.values.all { it == ConnectionState.CONNECTED } -> ConnectionState.CONNECTED
            states.values.any { it == ConnectionState.CONNECTED } -> ConnectionState.CONNECTED
            states.values.any { it == ConnectionState.CONNECTING } -> ConnectionState.CONNECTING
            states.values.any { it == ConnectionState.RECONNECTING } -> ConnectionState.RECONNECTING
            states.values.any { it == ConnectionState.FAILED } -> ConnectionState.FAILED
            else -> ConnectionState.DISCONNECTED
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, ConnectionState.DISCONNECTED)

    private val _lastTransmitTime = MutableStateFlow<Long>(0)
    val lastTransmitTime: StateFlow<Long> = _lastTransmitTime.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Connection summary: "0/1 connected" — total comes from configured servers, not active connections
    val connectionSummary: StateFlow<String> = combine(
        _serverStates,
        settings.serverConfigs
    ) { states, configs ->
        val connected = states.values.count { it == ConnectionState.CONNECTED || it == ConnectionState.SENDING }
        val total = configs.size
        "$connected/$total connected"
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, "0/0 connected")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reconnectJobs = ConcurrentHashMap<String, Job>()
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startNetworkMonitoring() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                logRepository.info("Network", "Network available")
                val currentState = state.value
                if (currentState == ConnectionState.DISCONNECTED || currentState == ConnectionState.FAILED) {
                    scope.launch { connectAll() }
                }
            }

            override fun onLost(network: Network) {
                logRepository.warn("Network", "Network lost")
                // Schedule reconnect for all connected servers
                tcpClients.forEach { (serverId, client) ->
                    if (client.isConnected) {
                        updateServerState(serverId, ConnectionState.RECONNECTING)
                        scheduleReconnect(serverId)
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val currentState = state.value
                if (currentState == ConnectionState.CONNECTED) {
                    logRepository.info("Network", "Network capabilities changed, reconnecting all")
                    scope.launch {
                        disconnectAll()
                        connectAll()
                    }
                }
            }
        }

        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    fun stopNetworkMonitoring() {
        networkCallback?.let {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
        networkCallback = null
    }

    suspend fun connectAll() {
        val configs = settings.serverConfigs.first()
        val enabledConfigs = configs.filter { it.enabled }

        if (enabledConfigs.isEmpty()) {
            logRepository.warn("Connection", "No enabled servers configured")
            return
        }

        _lastError.value = null

        // Connect each enabled server in parallel
        enabledConfigs.forEach { config ->
            scope.launch { connectServer(config) }
        }

        // UDP is independent
        if (settings.udpEnabled.first()) {
            val udpAddr = settings.udpAddress.first()
            val udpPort = settings.udpPort.first().toIntOrNull() ?: 6969
            udpBroadcaster.connect(udpAddr, udpPort)
        }
    }

    suspend fun connectServer(config: ServerConfig) {
        updateServerState(config.id, ConnectionState.CONNECTING)

        // Create or reuse client
        val client = tcpClients.getOrPut(config.id) {
            TakTcpClient(config.id, certStore, logRepository)
        }

        // Disconnect existing connection if any
        if (client.isConnected) {
            client.disconnect()
        }

        val success = client.connect(config.address, config.port, config.address, config.trustAllCerts)

        if (success) {
            updateServerState(config.id, ConnectionState.CONNECTED)
            reconnectAttempts[config.id] = 0
            reconnectJobs[config.id]?.cancel()
            logRepository.info("Connection", "Connected to ${config.name} (${config.address}:${config.port})")
        } else {
            updateServerState(config.id, ConnectionState.FAILED)
            _lastError.value = "Connection failed to ${config.address}:${config.port}"
            scheduleReconnect(config.id)
        }
    }

    fun disconnectServer(serverId: String) {
        reconnectJobs[serverId]?.cancel()
        reconnectJobs.remove(serverId)
        reconnectAttempts.remove(serverId)
        tcpClients[serverId]?.disconnect()
        tcpClients.remove(serverId)
        val states = _serverStates.value.toMutableMap()
        states.remove(serverId)
        _serverStates.value = states
    }

    suspend fun send(cotXml: String) {
        var anySent = false

        // Fan out to all connected TCP clients
        tcpClients.forEach { (serverId, client) ->
            if (client.isConnected) {
                val success = client.send(cotXml)
                if (success) {
                    anySent = true
                } else {
                    updateServerState(serverId, ConnectionState.RECONNECTING)
                    scheduleReconnect(serverId)
                }
            }
        }

        // UDP (independent, fire-and-forget)
        if (udpBroadcaster.isConnected) {
            udpBroadcaster.send(cotXml)
            anySent = true
        }

        if (anySent) {
            _lastTransmitTime.value = System.currentTimeMillis()
        }
    }

    fun disconnect() {
        disconnectAll()
        udpBroadcaster.disconnect()
        _lastError.value = null
    }

    private fun disconnectAll() {
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        reconnectAttempts.clear()
        tcpClients.values.forEach { it.disconnect() }
        tcpClients.clear()
        _serverStates.value = emptyMap()
    }

    private fun updateServerState(serverId: String, newState: ConnectionState) {
        val states = _serverStates.value.toMutableMap()
        states[serverId] = newState
        _serverStates.value = states
    }

    private fun scheduleReconnect(serverId: String) {
        reconnectJobs[serverId]?.cancel()
        val attempt = reconnectAttempts.getOrDefault(serverId, 0)
        val delay = calculateBackoffDelay(attempt)
        logRepository.info("Connection", "Reconnecting $serverId in ${delay}ms (attempt ${attempt + 1})")
        updateServerState(serverId, ConnectionState.RECONNECTING)

        reconnectJobs[serverId] = scope.launch {
            delay(delay)
            reconnectAttempts[serverId] = attempt + 1

            // Find current config for this server
            val configs = settings.serverConfigs.first()
            val config = configs.find { it.id == serverId }
            if (config != null && config.enabled) {
                connectServer(config)
            }
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        return (Constants.RECONNECT_BASE_DELAY_MS * Constants.RECONNECT_MULTIPLIER.pow(attempt.toDouble()))
            .toLong()
            .coerceAtMost(Constants.RECONNECT_MAX_DELAY_MS)
    }

    fun cleanup() {
        scope.cancel()
        stopNetworkMonitoring()
        disconnect()
    }
}
