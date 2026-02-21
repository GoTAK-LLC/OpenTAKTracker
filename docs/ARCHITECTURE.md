# Architecture Specification

## Overview

OpenTAK Tracker follows **MVVM + Service** architecture with Jetpack Compose UI, Hilt DI, and a Foreground Service for background operation.

---

## Layer Diagram

```
┌─────────────────────────────────────────────────────┐
│                   PRESENTATION                       │
│                                                      │
│  Jetpack Compose Screens                             │
│  ┌────────┐ ┌────────┐ ┌──────────┐ ┌───────────┐  │
│  │ Main   │ │Settings│ │Emergency │ │Enrollment │  │
│  │Screen  │ │Screen  │ │ Screen   │ │  Screen   │  │
│  └───┬────┘ └───┬────┘ └────┬─────┘ └─────┬─────┘  │
│      └──────────┴───────────┴──────────────┘         │
│                        │                             │
│              ┌─────────▼──────────┐                  │
│              │  TrackerViewModel  │                  │
│              │  (Hilt ViewModel)  │                  │
│              └─────────┬──────────┘                  │
│                        │                             │
├────────────────────────┼─────────────────────────────┤
│                   DOMAIN / SERVICE                   │
│                        │                             │
│  ┌─────────────────────▼──────────────────────────┐  │
│  │      TrackingForegroundService                 │  │
│  │                                                │  │
│  │  ┌──────────────┐    ┌───────────────────┐     │  │
│  │  │TrackerEngine │    │ ConnectionManager │     │  │
│  │  │              │    │                   │     │  │
│  │  │ • Timer loop │    │ • TCP lifecycle   │     │  │
│  │  │ • Build CoT  │    │ • UDP lifecycle   │     │  │
│  │  │ • Dispatch   │    │ • State machine   │     │  │
│  │  │   to transpt │    │ • Auto-reconnect  │     │  │
│  │  └──────┬───────┘    └────────┬──────────┘     │  │
│  │         │                     │                │  │
│  │  ┌──────▼──────┐      ┌──────▼──────────┐     │  │
│  │  │LocationMgr  │      │  CotBuilder     │     │  │
│  │  │Wrapper      │      │                 │     │  │
│  │  │             │      │ • PLI XML       │     │  │
│  │  │ • Fused API │      │ • Emergency XML │     │  │
│  │  │ • Heading   │      │ • UID mgmt      │     │  │
│  │  │ • Filter    │      └─────────────────┘     │  │
│  │  └─────────────┘                               │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
├──────────────────────────────────────────────────────┤
│                    TRANSPORT                         │
│                                                      │
│  ┌──────────────────┐    ┌──────────────────────┐    │
│  │  TakTcpClient    │    │  TakUdpBroadcaster   │    │
│  │                  │    │                      │    │
│  │  • SSLSocket     │    │  • DatagramSocket    │    │
│  │  • Client cert   │    │  • Multicast group   │    │
│  │  • TLS verify    │    │  • Fire-and-forget   │    │
│  │  • Read thread   │    │                      │    │
│  └──────────────────┘    └──────────────────────┘    │
│                                                      │
├──────────────────────────────────────────────────────┤
│                    ENROLLMENT                        │
│                                                      │
│  ┌──────────────────┐    ┌──────────────────────┐    │
│  │  QRCodeParser    │    │CSREnrollmentManager  │    │
│  │                  │    │                      │    │
│  │  • iTAK format   │    │ • Fetch CA config    │    │
│  │  • ATAK JSON     │    │ • Generate keypair   │    │
│  │  • Validation    │    │ • Build & submit CSR │    │
│  └──────────────────┘    │ • Store cert chain   │    │
│                          └──────────────────────┘    │
│                                                      │
├──────────────────────────────────────────────────────┤
│                      DATA                            │
│                                                      │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ │
│  │ Settings     │ │ Certificate  │ │    Log       │ │
│  │ Repository   │ │ Store        │ │ Repository   │ │
│  │              │ │              │ │              │ │
│  │ DataStore    │ │ Android      │ │ Ring buffer  │ │
│  │ Preferences  │ │ Keystore     │ │ (200 entries)│ │
│  └──────────────┘ └──────────────┘ └──────────────┘ │
└──────────────────────────────────────────────────────┘
```

---

## Component Responsibilities

### UI Layer

#### TrackerViewModel
- Single ViewModel shared across screens
- Holds `StateFlow` for: location data, connection state, enrollment state, settings, logs
- Binds to `TrackingForegroundService` via bound service pattern
- Exposes actions: startTracking, stopTracking, startEnrollment, toggleEmergency

### Service Layer

#### TrackingForegroundService
- Android `Service` with `startForeground()`
- Owns the `TrackerEngine` and `ConnectionManager` lifecycle
- Creates and manages the persistent notification
- Returns `START_STICKY` from `onStartCommand` for restart resilience
- Binds to UI via `Binder` pattern for state observation

#### TrackerEngine
- Runs a coroutine-based timer at the configured broadcast interval
- On each tick:
  1. Gets latest location from `LocationManagerWrapper`
  2. Checks dynamic mode thresholds (if enabled)
  3. Builds CoT XML via `CotBuilder`
  4. Dispatches to `ConnectionManager` for TCP and/or UDP send
- Handles emergency mode: overrides interval to 3s, changes CoT type

#### LocationManagerWrapper
- Wraps `FusedLocationProviderClient`
- Requests location updates with configured interval/displacement
- Exposes `StateFlow<LocationData>` with lat, lon, alt, speed, bearing, accuracy, heading
- Handles device orientation changes for correct compass heading
- Manages `SensorManager` for magnetic heading

#### ConnectionManager
- Owns `TakTcpClient` and `TakUdpBroadcaster` instances
- Manages connection state machine (see Transport spec)
- Listens for `ConnectivityManager.NetworkCallback` for network changes
- Triggers reconnection on state transitions
- Exposes `StateFlow<ConnectionState>` for UI

### Transport Layer

#### TakTcpClient
- Runs on dedicated IO coroutine dispatcher
- Creates `SSLSocket` using `SSLContext` configured with:
  - `KeyManager` backed by Android Keystore (client cert)
  - `TrustManager` backed by stored CA chain
- Sends CoT XML as UTF-8 bytes
- Maintains persistent connection with read loop for server messages
- Reports connection state changes to `ConnectionManager`

#### TakUdpBroadcaster
- Creates `DatagramSocket`
- Joins multicast group (default `239.2.3.1`)
- Sends CoT XML as UDP datagrams
- No authentication, no acknowledgment (fire-and-forget)
- Independent lifecycle from TCP

### Enrollment Layer

#### QRCodeParser
- Parses two QR formats:
  - **iTAK**: comma-separated `name,url,port,protocol`
  - **ATAK**: JSON with `serverCredentials.connectionString` and optional `userCredentials`
- Returns `EnrollmentParameters` data class
- Validates URL and port values
- Reports parse errors

#### CSREnrollmentManager
- Orchestrates the full CSR enrollment flow
- Uses `HttpsURLConnection` with trust-all or system trust for enrollment HTTPS
- Generates RSA 2048-bit keypair in Android Keystore
- Builds X.509 CSR (DER encoded, Base64 for transport)
- Parses JSON response for signed cert + CA chain
- Stores everything via `CertificateStore`
- Emits status updates: Connecting → Configuring → Enrolling → Succeeded/Failed

### Data Layer

#### SettingsRepository
- Backed by Jetpack DataStore (Preferences)
- Stores: callsign, team, role, server URL/port, broadcast interval, stale time, dynamic mode settings, coordinate format preference, speed unit preference, map type
- Exposes `Flow` for reactive UI updates

#### CertificateStore
- Wraps Android Keystore API
- Stores client private key + certificate under server URL label
- Stores CA trust chain certificates
- Provides `KeyManager` and `TrustManager` for TLS
- Supports clear/delete for server disconnection

#### LogRepository
- In-memory ring buffer of 200 `LogEntry` objects
- Each entry: timestamp, level (INFO/WARN/ERROR), tag, message
- Exposed as `StateFlow<List<LogEntry>>` for UI
- Thread-safe via `ConcurrentLinkedDeque` or `synchronized`

---

## Data Flow: Location → Transmission

```
FusedLocationProvider
        │
        ▼
LocationManagerWrapper
        │ (StateFlow<LocationData>)
        ▼
TrackerEngine (timer tick)
        │
        ├── Check dynamic mode thresholds
        │
        ▼
CotBuilder.buildPLI(location, settings)
        │ (String: CoT XML)
        ▼
ConnectionManager.send(cotXml)
        │
        ├──────────────────┐
        ▼                  ▼
TakTcpClient.send()  TakUdpBroadcaster.send()
        │                  │
        ▼                  ▼
   TAK Server         Multicast Group
```

---

## Data Flow: QR Enrollment

```
Camera (CameraX + ML Kit)
        │ (scanned string)
        ▼
QRCodeParser.parse()
        │ (EnrollmentParameters)
        ▼
CSREnrollmentManager.beginEnrollment()
        │
        ├─ GET /Marti/api/tls/config (Basic Auth)
        │     → parse XML for O, OU
        │
        ├─ Generate RSA 2048 keypair (Android Keystore)
        │
        ├─ Build CSR (CN, O, OU)
        │
        ├─ POST /Marti/api/tls/signClient/v2 (Base64 DER body, Basic Auth)
        │     → parse JSON: signedCert, ca0..caN
        │
        ├─ CertificateStore.storeClientCert(signedCert, serverUrl)
        ├─ CertificateStore.storeTrustChain(caCerts)
        │
        ▼
ConnectionManager.connect(serverUrl, serverPort)
        │
        ▼
   TakTcpClient establishes TLS with client cert
```

---

## Dependency Injection (Hilt)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideSettingsRepository(@ApplicationContext ctx: Context): SettingsRepository

    @Provides @Singleton
    fun provideCertificateStore(@ApplicationContext ctx: Context): CertificateStore

    @Provides @Singleton
    fun provideLogRepository(): LogRepository

    @Provides @Singleton
    fun provideCotBuilder(settings: SettingsRepository): CotBuilder

    @Provides @Singleton
    fun provideQRCodeParser(): QRCodeParser
}
```

---

## Threading Model

| Component | Thread/Dispatcher |
|-----------|-------------------|
| UI / Compose | Main |
| TrackerEngine timer | `Dispatchers.Default` (coroutine) |
| TakTcpClient | `Dispatchers.IO` (dedicated coroutine) |
| TakUdpBroadcaster | `Dispatchers.IO` |
| CSREnrollmentManager | `Dispatchers.IO` |
| LocationManagerWrapper | Fused API callback (main), heading sensor (main) |
| SettingsRepository | DataStore's own IO dispatcher |
| LogRepository | Any (thread-safe) |

---

## Service Binding

```
Activity ──bind──► TrackingForegroundService
    │                      │
    │   TrackerBinder       │
    │   ┌──────────────┐   │
    │   │ getEngine()  │   │
    │   │ getConnMgr() │   │
    │   │ getLocMgr()  │   │
    │   └──────────────┘   │
    │                      │
    ▼                      │
TrackerViewModel ◄─────────┘
    observes StateFlows from service components
```

The ViewModel binds to the service on `init` and unbinds on `onCleared()`. All state flows from service components are collected in the ViewModel and exposed to Compose screens.
