# OpenTAK Tracker - Technical Product Requirements Document

**Version:** 1.0
**Platform:** Android (Kotlin)
**Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 (Android 15)

---

## 1. Product Objective

Deliver a lightweight, standalone Android TAK-compatible tracker that:

- Runs as a **background Foreground Service** with persistent notification
- Enrolls via **TAK-style QR code** (iTAK and ATAK formats)
- Authenticates using **client certificate** obtained during enrollment
- Transmits **PLI (Position Location Information)** as CoT XML automatically
- Supports **TCP (TLS + client cert)** and **UDP (multicast broadcast)** simultaneously
- Provides clear **operational UI** with live status, diagnostics, and map
- Requires **no manual cert import, no data package import** - QR enrollment only

> Reference implementation: iOS TAKTracker by Flight Tactics (see `RESEARCH/TAKTracker/`)

---

## 2. Core Architecture

### 2.1 Component Overview

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Jetpack Compose)            │
│  ┌──────────┐ ┌──────────┐ ┌─────────┐ ┌────────────┐  │
│  │MainScreen│ │Settings  │ │Emergency│ │ LogViewer  │  │
│  │(Status+  │ │(Callsign,│ │(Beacon  │ │(Connection │  │
│  │ Map)     │ │Team,QR)  │ │Toggle)  │ │ + Enroll)  │  │
│  └────┬─────┘ └────┬─────┘ └────┬────┘ └─────┬──────┘  │
│       │            │            │             │          │
├───────┼────────────┼────────────┼─────────────┼──────────┤
│       ▼            ▼            ▼             ▼          │
│              ViewModel / State Layer                     │
│  ┌─────────────────────────────────────────────────┐    │
│  │          TrackerViewModel (StateFlow)            │    │
│  └──────────────────────┬──────────────────────────┘    │
│                         │                                │
├─────────────────────────┼────────────────────────────────┤
│                   Service Layer                          │
│  ┌──────────────────────┴──────────────────────────┐    │
│  │         TrackingForegroundService               │    │
│  │  ┌─────────────┐  ┌──────────────┐              │    │
│  │  │TrackerEngine │  │ConnectionMgr │              │    │
│  │  │  (Timer +    │  │  (TCP+UDP    │              │    │
│  │  │   Dispatch)  │  │   Lifecycle) │              │    │
│  │  └──────┬───────┘  └──────┬───────┘              │    │
│  │         │                 │                       │    │
│  │  ┌──────▼───────┐  ┌─────▼────────┐              │    │
│  │  │LocationMgr   │  │ CotBuilder   │              │    │
│  │  │(FusedLoc)    │  │ (XML Gen)    │              │    │
│  │  └──────────────┘  └──────────────┘              │    │
│  └──────────────────────────────────────────────────┘    │
│                                                          │
├──────────────────────────────────────────────────────────┤
│                  Transport Layer                         │
│  ┌──────────────────┐  ┌──────────────────┐              │
│  │  TakTcpClient    │  │ TakUdpBroadcaster│              │
│  │  (TLS+ClientCert)│  │ (Multicast)      │              │
│  └──────────────────┘  └──────────────────┘              │
│                                                          │
├──────────────────────────────────────────────────────────┤
│                 Enrollment Layer                         │
│  ┌──────────────────┐  ┌──────────────────┐              │
│  │ QRCodeParser     │  │ CSREnrollment    │              │
│  │ (iTAK + ATAK)    │  │ Manager          │              │
│  └──────────────────┘  └──────────────────┘              │
│                                                          │
├──────────────────────────────────────────────────────────┤
│                   Data Layer                             │
│  ┌──────────────────┐  ┌──────────────────┐              │
│  │SettingsRepository│  │  LogRepository   │              │
│  │(DataStore/Prefs) │  │  (In-Memory Ring)│              │
│  └──────────────────┘  └──────────────────┘              │
│                                                          │
│  ┌──────────────────┐                                    │
│  │ CertificateStore │                                    │
│  │ (Android Keystore)│                                   │
│  └──────────────────┘                                    │
└──────────────────────────────────────────────────────────┘
```

### 2.2 Package Structure

```
com.opentak.tracker/
├── ui/                      # Jetpack Compose screens
│   ├── main/                # Main status screen + map
│   ├── settings/            # Settings, callsign, team, connection
│   ├── emergency/           # Emergency beacon UI
│   ├── enrollment/          # QR scanner + enrollment status
│   └── logs/                # Log viewer
├── viewmodel/               # ViewModels (TrackerViewModel, etc.)
├── service/                 # Foreground service + tracker engine
│   ├── TrackingForegroundService.kt
│   ├── TrackerEngine.kt
│   └── LocationManagerWrapper.kt
├── transport/               # Network clients
│   ├── TakTcpClient.kt
│   ├── TakUdpBroadcaster.kt
│   └── ConnectionManager.kt
├── cot/                     # CoT XML building
│   └── CotBuilder.kt
├── enrollment/              # QR parsing + CSR enrollment
│   ├── QRCodeParser.kt
│   └── CSREnrollmentManager.kt
├── security/                # Certificate management
│   └── CertificateStore.kt
├── data/                    # Settings + logs persistence
│   ├── SettingsRepository.kt
│   └── LogRepository.kt
└── util/                    # Converters, constants
    ├── CoordinateConverter.kt
    └── Constants.kt
```

---

## 3. Functional Requirements

### A. Background Tracking

#### A1. Foreground Service
| Req | Description |
|-----|-------------|
| A1.1 | Uses `FOREGROUND_SERVICE_LOCATION` type |
| A1.2 | Persistent notification showing transmit status, callsign, and server state |
| A1.3 | Start/Stop toggle from UI and notification action |
| A1.4 | Survives app backgrounding - continues transmitting |
| A1.5 | Auto-reconnects on network loss |
| A1.6 | Stops only when user explicitly disables |
| A1.7 | Restarts on device reboot if was previously active (optional, via `BOOT_COMPLETED`) |

#### A2. Location Engine
| Req | Description |
|-----|-------------|
| A2.1 | Uses `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY` |
| A2.2 | Configurable update interval (default: 10 seconds) |
| A2.3 | Configurable minimum displacement (default: 0 meters) |
| A2.4 | Detects stale fixes (> 2x interval with no update) |
| A2.5 | Exposes: lat, lon, HAE (altitude), speed, bearing, accuracy, timestamp |

#### A3. Dynamic Mode (Movement Gated)
| Req | Description |
|-----|-------------|
| A3.1 | Toggle ON/OFF in settings |
| A3.2 | Sends only if distance > threshold (default 10m) OR speed > threshold (default 1 m/s) |
| A3.3 | Failsafe: transmit every N seconds regardless (default: 60s) |
| A3.4 | All thresholds configurable |

---

### B. QR Enrollment (TAK-Compatible)

#### B1. QR Scanner
| Req | Description |
|-----|-------------|
| B1.1 | Camera-based QR scanner using CameraX + ML Kit / ZXing |
| B1.2 | Accepts **iTAK format**: `serverName,serverURL,serverPort,protocol` |
| B1.3 | Accepts **ATAK JSON format**: `{"serverCredentials":{"connectionString":"host:port:ssl"},"userCredentials":{"username":"...","password":"..."}}` |
| B1.4 | Graceful rejection with user message on invalid payload |

#### B2. Enrollment Flow
| Req | Description |
|-----|-------------|
| B2.1 | Parse QR payload to extract: server host, port, username, password |
| B2.2 | Fetch CA config: `GET https://{host}:{csrPort}/Marti/api/tls/config` |
| B2.3 | Parse XML response for O (Organization) and OU (OrganizationalUnit) |
| B2.4 | Generate RSA 2048-bit keypair |
| B2.5 | Build CSR with CN=username, O, OU from config |
| B2.6 | Submit CSR: `POST https://{host}:{csrPort}/Marti/api/tls/signClient/v2?clientUid={UID}&version={VERSION}` |
| B2.7 | Body = Base64-encoded DER CSR, Basic Auth header |
| B2.8 | Parse JSON response: extract `signedCert` + `ca0..caN` trust chain |
| B2.9 | Store client cert + private key in Android Keystore |
| B2.10 | Store CA trust chain for TLS verification |
| B2.11 | Auto-connect immediately after successful enrollment |
| B2.12 | If ATAK format with username+password, auto-submit enrollment |

#### B3. Certificate Storage
| Req | Description |
|-----|-------------|
| B3.1 | Private key stored in Android Keystore (hardware-backed when available) |
| B3.2 | Certificate chain stored securely |
| B3.3 | Bound to connection profile (labeled by server URL) |
| B3.4 | Display: Subject, Issuer, Expiration, Fingerprint |
| B3.5 | Warn if certificate expires within 30 days |

---

### C. Identity & Metadata

#### C1. Callsign
| Req | Description |
|-----|-------------|
| C1.1 | Editable text field |
| C1.2 | Persisted across app restarts |
| C1.3 | Default: `TRACKER-{first_block_of_device_UUID}` |

#### C2. Team / Role
| Req | Description |
|-----|-------------|
| C2.1 | Team color selectable: Cyan, White, Yellow, Orange, Magenta, Red, Maroon, Purple, Dark Blue, Blue, Teal, Green, Dark Green, Brown |
| C2.2 | Role selectable: Team Member, Team Lead, HQ, Sniper, Medic, Forward Observer, RTO, K9 |
| C2.3 | Mapped into CoT `__group` detail element |
| C2.4 | Persisted across restarts |

#### C3. Emergency Beacon
| Req | Description |
|-----|-------------|
| C3.1 | Toggle ON/OFF with dual-confirm (activate + confirm switches) |
| C3.2 | Alert types: 911, Ring the Bell, In Contact, Cancel |
| C3.3 | Changes CoT event type to emergency type |
| C3.4 | Forces high-frequency transmit (3 second interval) |
| C3.5 | Visual persistent indicator in toolbar (red warning icon) |
| C3.6 | Cancel sends cancellation CoT event |

---

### D. Transport

#### D1. TCP to TAK Server
| Req | Description |
|-----|-------------|
| D1.1 | TLS 1.2+ only |
| D1.2 | Client certificate authentication from Android Keystore |
| D1.3 | Server certificate verification against stored CA trust chain |
| D1.4 | Persistent socket connection |
| D1.5 | Auto-reconnect with exponential backoff (1s, 2s, 4s, 8s... max 60s) |
| D1.6 | Detect and report: TLS failure, auth failure, socket drop, network change |

#### D2. UDP Broadcast
| Req | Description |
|-----|-------------|
| D2.1 | Default multicast: `239.2.3.1:6969` |
| D2.2 | Configurable IP and port |
| D2.3 | Multicast or broadcast mode |
| D2.4 | Independent enable/disable toggle |
| D2.5 | Can run simultaneously with TCP |

#### D3. Connection State Machine
```
                    ┌──────────┐
                    │DISCONNECTED│
                    └─────┬────┘
                          │ connect()
                    ┌─────▼────┐
                    │CONNECTING │
                    └─────┬────┘
                     ┌────┴────┐
                     │         │
              ┌──────▼──┐  ┌──▼──────┐
              │CONNECTED │  │ FAILED  │
              └─────┬────┘  └────┬────┘
                    │            │ retry (backoff)
              ┌─────▼────┐      │
              │ SENDING  │◄─────┘
              └─────┬────┘
                    │ socket drop / network change
              ┌─────▼────────┐
              │ RECONNECTING │──► CONNECTING
              └──────────────┘
```

#### D4. Auto-Connect Logic
| Req | Description |
|-----|-------------|
| D4.1 | Connect after successful enrollment |
| D4.2 | Connect on app start if previously connected |
| D4.3 | Reconnect on network change (WiFi ↔ cellular) |
| D4.4 | Reconnect automatically on socket drop |

---

### E. UI & Operational Display

#### E1. Live Status Screen (Main)
Must display:
- Current coordinates (toggleable format)
- Heading / Bearing (toggleable TN/MN)
- Compass heading (toggleable TN/MN)
- Speed (toggleable units)
- Server connection status
- Last transmit time
- Last error (if any)
- Callsign (prominent)

#### E2. Coordinate Display
| Format | Example |
|--------|---------|
| DMS | N 38° 53' 22.110" / W 077° 02' 11.580" |
| MGRS | 18S UJ 23371 06519 |
| Decimal | 38.8895 / -77.0366 |

Tap coordinate area to cycle formats.

#### E3. Speed Units
Tap speed value to cycle: **m/s → km/h → fps → mph**

#### E4. Compass
- Sensor-based magnetic heading
- Correct heading in both portrait and landscape
- Display both True North and Magnetic North values
- Smooth filtered output

#### E5. Map
- Map view (Google Maps or OSMDroid)
- Show user location with heading indicator
- "Follow Me" toggle - re-centers on position
- User can pan/zoom freely without auto-reset
- Re-center only when follow mode re-enabled (tap to reset like iOS ref)
- Map type toggle: Standard, Satellite, Hybrid

---

### F. Logging & Diagnostics

#### F1. Connection Log Events
```
DNS_RESOLVING → CONNECTING → TLS_HANDSHAKE → AUTH_SUCCESS →
CONNECTED → SENDING → RECONNECTING → ERROR(reason)
```

#### F2. Enrollment Log Events
```
QR_PARSED → CONFIG_FETCHED → CSR_GENERATED → CSR_SUBMITTED →
CERT_RECEIVED → CERT_INSTALLED → ENROLLMENT_COMPLETE | ENROLLMENT_FAILED(reason)
```

#### F3. Log Viewer
| Req | Description |
|-----|-------------|
| F3.1 | Last 200 entries visible in scrollable list |
| F3.2 | Timestamped entries (HH:mm:ss.SSS) |
| F3.3 | Categorized: INFO (white), WARN (yellow), ERROR (red) |
| F3.4 | Auto-scroll to latest, with pause on manual scroll |

---

## 4. CoT Transmission Format

### 4.1 Standard PLI Event
```xml
<event version="2.0"
       uid="{DEVICE_UID}"
       type="a-f-G-U-C"
       how="m-g"
       time="{ISO8601_UTC}"
       start="{ISO8601_UTC}"
       stale="{ISO8601_UTC + staleMinutes}">
  <point lat="{LAT}" lon="{LON}" hae="{ALT}"
         ce="{ACCURACY}" le="9999999" />
  <detail>
    <__group name="{TEAM_COLOR}" role="{ROLE}" />
    <contact callsign="{CALLSIGN}" />
    <uid Droid="{CALLSIGN}" />
    <precisionlocation altsrc="GPS" geopointsrc="GPS" />
    <status battery="{BATTERY_PCT}" />
    <track speed="{SPEED_M_S}" course="{BEARING_DEG}" />
    <takv device="{DEVICE_MODEL}" platform="OpenTAK-Tracker-Android"
          os="{ANDROID_VERSION}" version="{APP_VERSION}" />
  </detail>
</event>
```

### 4.2 Emergency Event
```xml
<event version="2.0"
       uid="{DEVICE_UID}"
       type="b-a-o-tbl"
       how="m-g"
       time="{ISO8601_UTC}"
       start="{ISO8601_UTC}"
       stale="{ISO8601_UTC + 60}">
  <point lat="{LAT}" lon="{LON}" hae="{ALT}"
         ce="{ACCURACY}" le="9999999" />
  <detail>
    <contact callsign="{CALLSIGN}" />
    <emergency type="{EMERGENCY_TYPE}" cancel="false">{CALLSIGN}</emergency>
  </detail>
</event>
```

### 4.3 Key Fields
| Field | Description |
|-------|-------------|
| `uid` | Stable per device. Format: `OpenTAK-{UUID}`. Derived from `Settings.Secure.ANDROID_ID` |
| `type` | Normal PLI: `a-f-G-U-C`. Emergency: varies by alert type |
| `how` | `m-g` (machine GPS) |
| `time/start` | Current UTC time in ISO 8601 |
| `stale` | Current UTC + stale time (default 5 minutes) |
| `ce` | Circular error = GPS accuracy in meters |
| `le` | Linear error, `9999999` if unknown |
| `hae` | Height Above Ellipsoid from GPS altitude |

### 4.4 Emergency Types
| Type | CoT type string |
|------|----------------|
| 911 | `b-a-o-tbl` |
| Ring the Bell | `b-a-o-can` |
| In Contact | `b-a-o-pan` |
| Cancel | (original type with `cancel="true"`) |

### 4.5 Transmission Rules
| Setting | Default | Range |
|---------|---------|-------|
| Broadcast interval | 10 seconds | 1-120s |
| Stale time | 5 minutes | 1-60 min |
| Emergency interval | 3 seconds | fixed |

---

## 5. Default Constants

| Constant | Value | Notes |
|----------|-------|-------|
| TCP streaming port | 8089 | TAK Server default |
| CSR enrollment port | 8446 | TAK Server cert enrollment |
| Secure API port | 8443 | TAK Server HTTPS API |
| UDP broadcast IP | 239.2.3.1 | TAK multicast default |
| UDP broadcast port | 6969 | TAK default |
| Default CoT type | a-f-G-U-C | Friendly Ground Unit Combat |
| Default CoT how | m-g | Machine GPS |
| Default team | Cyan | TAK default team color |
| Default role | Team Member | TAK default role |

---

## 6. Android Compliance

| Requirement | Implementation |
|-------------|---------------|
| Foreground Service type | `android:foregroundServiceType="location"` |
| Notification channel | Required, created at app start |
| `POST_NOTIFICATIONS` | Runtime permission on Android 13+ |
| `FOREGROUND_SERVICE_LOCATION` | Manifest permission (Android 14+) |
| Background location | `ACCESS_BACKGROUND_LOCATION` with proper permission flow |
| Camera | `CAMERA` permission for QR scanner |

### Full Permission List
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## 7. Security Requirements

| Requirement | Detail |
|-------------|--------|
| Enrollment transport | HTTPS only |
| Server TLS verification | Validate against CA trust chain from enrollment |
| Private key storage | Android Keystore (never exported) |
| Credentials | No plaintext persistence - cleared after enrollment |
| Certificate auth | TLS client certificate from Keystore |
| Self-signed servers | Rejected by default; configurable trust-all option for testing |

---

## 8. Reliability Requirements

| Requirement | Detail |
|-------------|--------|
| Network change detection | `ConnectivityManager.NetworkCallback` |
| Reconnect strategy | Exponential backoff: 1s → 2s → 4s → ... → 60s max |
| Malformed enrollment | No crash, user-visible error message |
| Socket drop | Detected, auto-reconnect triggered |
| Service restart | Resilient via `START_STICKY` |
| Graceful shutdown | Flush pending, close sockets, stop location updates |

---

## 9. UX Flow Summary

```
Install → Open → Grant Permissions → Scan QR
    → Enrolled → Auto-Connected → Transmitting

App Backgrounded → Continues Transmitting (notification visible)

Emergency Toggle → Immediate High-Rate Transmit (3s)

Network Drops → Reconnect Automatically (backoff)

Certificate Expiring → Visible Warning in Settings

Map Pan/Zoom → Does Not Reset (tap to re-center)
```

---

## 10. Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Location | Google Play Services FusedLocationProvider |
| Map | Google Maps Compose (or OSMDroid for FOSS) |
| QR Scanner | CameraX + ML Kit Barcode |
| Networking | Raw SSLSocket / NIO for TCP; DatagramSocket for UDP |
| Cert/Crypto | Android Keystore API, java.security |
| Settings | Jetpack DataStore (Preferences) |
| Architecture | MVVM + Service |

---

## 11. Detailed Specification Documents

| Document | Description |
|----------|-------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Component design, data flow, module responsibilities |
| [docs/COT-PROTOCOL.md](docs/COT-PROTOCOL.md) | CoT XML format, PLI schema, UID generation, stale times |
| [docs/ENROLLMENT-FLOW.md](docs/ENROLLMENT-FLOW.md) | QR scan → CSR → cert install → auto-connect |
| [docs/TRANSPORT.md](docs/TRANSPORT.md) | TCP/TLS client, UDP broadcaster, reconnection state machine |
| [docs/UI-SPEC.md](docs/UI-SPEC.md) | Screen wireframes, navigation, coordinate modes |
| [docs/SECURITY.md](docs/SECURITY.md) | Android Keystore usage, TLS config, threat model |
| [docs/IMPLEMENTATION-ROADMAP.md](docs/IMPLEMENTATION-ROADMAP.md) | Phased milestones, build order, testing strategy |
