# Transport Layer Specification

## Overview

OpenTAK Tracker sends CoT XML over two independent transport channels:
- **TCP** — Persistent TLS connection with client certificate auth to a TAK server
- **UDP** — Multicast/broadcast datagrams (no auth, fire-and-forget)

Both can run simultaneously.

---

## TCP Client (TakTcpClient)

### Connection Parameters

| Parameter | Source | Default |
|-----------|--------|---------|
| Host | Enrollment QR / Settings | *(from enrollment)* |
| Port | Enrollment QR / Settings | 8089 |
| TLS | Always | TLS 1.2+ |
| Client cert | Android Keystore | *(from enrollment)* |
| CA trust chain | CertificateStore | *(from enrollment)* |

### TLS Configuration

```kotlin
// Build SSLContext with client cert from Keystore
val keyStore = KeyStore.getInstance("AndroidKeyStore")
keyStore.load(null)

// KeyManager: presents client certificate
val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
kmf.init(keyStore, null)  // Android Keystore doesn't use password

// TrustManager: validates server cert against stored CA chain
val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
trustStore.load(null)
// Add stored CA certs to trust store
storedCaCerts.forEachIndexed { i, cert ->
    trustStore.setCertificateEntry("tak-ca-$i", cert)
}
val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
tmf.init(trustStore)

val sslContext = SSLContext.getInstance("TLSv1.2")
sslContext.init(kmf.keyManagers, tmf.trustManagers, null)

val socket = sslContext.socketFactory.createSocket(host, port) as SSLSocket
socket.startHandshake()
```

### Send Operation

CoT XML is sent as raw UTF-8 bytes directly to the socket output stream. No framing protocol — the TAK server parses the XML stream.

```kotlin
suspend fun send(cotXml: String) {
    withContext(Dispatchers.IO) {
        try {
            socket.outputStream.write(cotXml.toByteArray(Charsets.UTF_8))
            socket.outputStream.flush()
            logRepository.info("TCP", "CoT sent (${cotXml.length} bytes)")
        } catch (e: IOException) {
            logRepository.error("TCP", "Send failed: ${e.message}")
            onConnectionLost()
        }
    }
}
```

### Connection Lifecycle

```kotlin
class TakTcpClient(
    private val certStore: CertificateStore,
    private val logRepository: LogRepository
) {
    private var socket: SSLSocket? = null
    private var isConnected = false

    suspend fun connect(host: String, port: Int) { /* ... */ }
    suspend fun send(cotXml: String) { /* ... */ }
    fun disconnect() { /* ... */ }
    fun isConnected(): Boolean = isConnected
}
```

---

## UDP Broadcaster (TakUdpBroadcaster)

### Connection Parameters

| Parameter | Default | Configurable |
|-----------|---------|-------------|
| IP | 239.2.3.1 | Yes |
| Port | 6969 | Yes |
| Mode | Multicast | Multicast or Broadcast |

### Multicast Setup

```kotlin
val group = InetAddress.getByName("239.2.3.1")
val socket = MulticastSocket(6969)
socket.joinGroup(InetSocketAddress(group, 6969), NetworkInterface.getByName("wlan0"))
```

### Send Operation

```kotlin
suspend fun send(cotXml: String) {
    withContext(Dispatchers.IO) {
        try {
            val data = cotXml.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(data, data.size, group, port)
            socket.send(packet)
            logRepository.info("UDP", "CoT broadcast (${data.size} bytes)")
        } catch (e: IOException) {
            logRepository.error("UDP", "Broadcast failed: ${e.message}")
        }
    }
}
```

### Notes
- No authentication
- No delivery confirmation
- Messages must fit in single UDP packet (~1400 bytes practical limit)
- Typical PLI is 600-800 bytes — well within limits
- Requires `CHANGE_WIFI_MULTICAST_STATE` permission
- Acquire `WifiManager.MulticastLock` to receive multicast on WiFi

---

## Connection Manager

The `ConnectionManager` orchestrates both TCP and UDP transports and manages the connection state machine.

### State Machine

```
                        ┌─────────────┐
                        │ DISCONNECTED│◄────────────────────┐
                        └──────┬──────┘                     │
                               │                            │
                          connect()                    disconnect()
                               │                            │
                        ┌──────▼──────┐                     │
                   ┌───►│ CONNECTING  │                     │
                   │    └──────┬──────┘                     │
                   │      ┌────┴────┐                       │
                   │      │         │                       │
                   │  success    failure                    │
                   │      │         │                       │
                   │ ┌────▼────┐ ┌──▼───────┐              │
                   │ │CONNECTED│ │  FAILED   │──────────────┘
                   │ └────┬────┘ └──┬───────┘
                   │      │         │
                   │   send()    retry (backoff)
                   │      │         │
                   │ ┌────▼────┐    │
                   │ │ SENDING │    │
                   │ └────┬────┘    │
                   │      │         │
                   │  socket drop   │
                   │  / network     │
                   │  change        │
                   │      │         │
                   │ ┌────▼────────┐│
                   └─┤RECONNECTING ├┘
                     └─────────────┘
```

### States

| State | Description | UI Display |
|-------|-------------|------------|
| `DISCONNECTED` | No active connection | "Disconnected" (gray) |
| `CONNECTING` | TLS handshake in progress | "Connecting..." (yellow) |
| `CONNECTED` | Socket open, ready to send | "Connected" (green) |
| `SENDING` | Actively writing to socket | "Connected" (green) |
| `FAILED` | Connection attempt failed | "Failed" (red) |
| `RECONNECTING` | Waiting before retry | "Reconnecting..." (orange) |

### Exponential Backoff

```kotlin
class ReconnectStrategy {
    private var attempt = 0
    private val baseDelay = 1000L  // 1 second
    private val maxDelay = 60_000L // 60 seconds
    private val multiplier = 2.0

    fun nextDelay(): Long {
        val delay = (baseDelay * multiplier.pow(attempt)).toLong()
            .coerceAtMost(maxDelay)
        attempt++
        return delay
    }

    fun reset() { attempt = 0 }
}
```

Backoff sequence: 1s → 2s → 4s → 8s → 16s → 32s → 60s → 60s → ...

Reset to 0 on successful connection.

### Network Change Detection

```kotlin
val connectivityManager = getSystemService(ConnectivityManager::class.java)

val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        // Network available — trigger reconnect if disconnected
        if (state == DISCONNECTED || state == FAILED) {
            reconnect()
        }
    }

    override fun onLost(network: Network) {
        // Network lost — mark disconnected, start backoff
        onConnectionLost()
    }

    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
        // Network type changed (WiFi ↔ Cellular) — reconnect
        if (state == CONNECTED) {
            reconnect()
        }
    }
}

connectivityManager.registerDefaultNetworkCallback(networkCallback)
```

### Auto-Connect Rules

| Trigger | Action |
|---------|--------|
| Enrollment success | Connect immediately |
| App start (service start) | Connect if server URL configured and cert exists |
| Network becomes available | Reconnect if was previously connected |
| Network type changes | Reconnect (new socket on new network) |
| Socket error/drop | Reconnect with exponential backoff |
| Server URL changes | Cancel current, connect to new |

### Connection Manager Implementation

```kotlin
class ConnectionManager(
    private val certStore: CertificateStore,
    private val settings: SettingsRepository,
    private val logRepository: LogRepository,
    private val context: Context
) {
    private val tcpClient = TakTcpClient(certStore, logRepository)
    private val udpBroadcaster = TakUdpBroadcaster(logRepository)
    private val reconnectStrategy = ReconnectStrategy()

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    suspend fun connect() {
        val serverUrl = settings.serverUrl.first()
        val serverPort = settings.serverPort.first().toIntOrNull() ?: 8089

        if (serverUrl.isBlank()) {
            logRepository.warn("Connection", "No server configured")
            return
        }

        _state.value = ConnectionState.CONNECTING
        logRepository.info("Connection", "Connecting to $serverUrl:$serverPort")

        try {
            tcpClient.connect(serverUrl, serverPort)
            _state.value = ConnectionState.CONNECTED
            reconnectStrategy.reset()
            logRepository.info("Connection", "Connected to $serverUrl:$serverPort")
        } catch (e: Exception) {
            _state.value = ConnectionState.FAILED
            logRepository.error("Connection", "Failed: ${e.message}")
            scheduleReconnect()
        }

        // UDP is independent
        if (settings.udpEnabled.first()) {
            udpBroadcaster.connect(
                settings.udpAddress.first(),
                settings.udpPort.first().toIntOrNull() ?: 6969
            )
        }
    }

    suspend fun send(cotXml: String) {
        // Send to TCP if connected
        if (tcpClient.isConnected()) {
            tcpClient.send(cotXml)
        }

        // Send to UDP if enabled
        if (settings.udpEnabled.first()) {
            udpBroadcaster.send(cotXml)
        }
    }

    private fun scheduleReconnect() {
        _state.value = ConnectionState.RECONNECTING
        val delay = reconnectStrategy.nextDelay()
        logRepository.info("Connection", "Reconnecting in ${delay}ms")
        // Schedule reconnect via coroutine delay
    }
}
```

---

## Error Detection and Reporting

### TCP Errors

| Error | Detection | Log Level | Action |
|-------|-----------|-----------|--------|
| DNS resolution failure | `UnknownHostException` | ERROR | Retry with backoff |
| Connection refused | `ConnectException` | ERROR | Retry with backoff |
| TLS handshake failure | `SSLHandshakeException` | ERROR | Check cert, retry |
| Client cert rejected | `SSLException` (auth) | ERROR | Re-enrollment needed |
| Socket timeout | `SocketTimeoutException` | WARN | Retry with backoff |
| Connection reset | `IOException` on send | WARN | Reconnect immediately |
| Network unreachable | `NoRouteToHostException` | ERROR | Wait for network callback |

### UDP Errors

| Error | Detection | Log Level | Action |
|-------|-----------|-----------|--------|
| Network unreachable | `IOException` | WARN | Continue (best-effort) |
| Send failed | `IOException` | WARN | Continue (best-effort) |
| Multicast not supported | `SocketException` | ERROR | Disable UDP, notify user |

---

## Log Events

### Connection Log Sequence (Success)
```
[INFO] Connection  | DNS resolving tak.example.com
[INFO] Connection  | Connecting to tak.example.com:8089
[INFO] TCP         | TLS handshake started
[INFO] TCP         | Client certificate presented
[INFO] TCP         | Server certificate verified
[INFO] TCP         | TLS handshake complete (TLSv1.2)
[INFO] Connection  | Connected to tak.example.com:8089
[INFO] TCP         | CoT sent (742 bytes)
```

### Connection Log Sequence (Failure + Reconnect)
```
[INFO]  Connection  | Connecting to tak.example.com:8089
[ERROR] TCP         | TLS handshake failed: certificate rejected
[ERROR] Connection  | Failed: SSLHandshakeException
[INFO]  Connection  | Reconnecting in 1000ms
[INFO]  Connection  | Connecting to tak.example.com:8089
[INFO]  TCP         | TLS handshake complete
[INFO]  Connection  | Connected to tak.example.com:8089
```

---

## Performance Considerations

- TCP socket uses `Dispatchers.IO` to avoid blocking main thread
- UDP sends are non-blocking (fire-and-forget)
- Connection state is observed via `StateFlow` (no polling)
- Network callbacks are system-level (no polling)
- Socket read timeout: 0 (infinite) for persistent connection
- Socket connect timeout: 10 seconds
