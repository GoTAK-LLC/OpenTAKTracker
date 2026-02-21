# Implementation Roadmap

## Overview

The build is organized into 6 phases, each delivering a testable increment. Phases are ordered by dependency — each builds on the previous.

---

## Phase 1: Project Scaffold + Core Data Layer
**Goal:** Buildable Android project with DI, settings persistence, and logging.

### Tasks
1. **Android project setup**
   - Create project with Kotlin, Compose, Material 3
   - Configure `build.gradle.kts`: min SDK 26, target SDK 35, Compose BOM
   - Add Hilt dependency injection
   - Configure `AndroidManifest.xml` with all required permissions

2. **SettingsRepository**
   - Jetpack DataStore (Preferences) wrapper
   - All settings fields: callsign, team, role, server URL/port, broadcast interval, stale time, dynamic mode, UDP settings, map type, coordinate format, speed unit
   - Default value generation (callsign from device ID)
   - Flow-based reactive reads

3. **LogRepository**
   - In-memory ring buffer (200 entries)
   - Thread-safe `LogEntry` data class (timestamp, level, tag, message)
   - `StateFlow<List<LogEntry>>` for UI observation
   - Convenience methods: `info()`, `warn()`, `error()`

4. **Constants**
   - Default ports (8089, 8446, 8443, 6969)
   - Default multicast address (239.2.3.1)
   - CoT defaults (type, how, team, role)
   - Platform identifier string

### Deliverable
Compiles and runs. Settings can be written/read. Logs can be appended and observed.

### Verification
- Unit tests for SettingsRepository read/write
- Unit tests for LogRepository ring buffer behavior

---

## Phase 2: Location Engine + CoT Builder
**Goal:** Acquire GPS location and generate valid CoT XML.

### Tasks
1. **LocationManagerWrapper**
   - FusedLocationProviderClient setup
   - `PRIORITY_HIGH_ACCURACY` request
   - Configurable interval and displacement
   - `StateFlow<LocationData>` output (lat, lon, alt, speed, bearing, accuracy, timestamp)
   - Heading from `SensorManager` (magnetic + true north)
   - Orientation correction for landscape
   - Stale fix detection

2. **CotBuilder**
   - `buildPLI(location, settings)` → CoT XML string
   - `buildEmergency(location, type, cancel)` → emergency CoT XML
   - UID generation from `ANDROID_ID`
   - ISO 8601 UTC timestamp formatting
   - Battery level reading
   - Device model / OS / app version metadata

3. **CoordinateConverter**
   - Decimal degrees to DMS
   - Decimal degrees to MGRS (using mil-java or manual implementation)
   - Speed unit conversions (m/s, km/h, fps, mph)

4. **Permission handling**
   - Location permission request flow
   - Background location permission (two-step)
   - Notification permission (Android 13+)

### Deliverable
App displays current location. CoT XML can be generated and logged.

### Verification
- Unit tests for CotBuilder: valid XML structure, correct fields
- Unit tests for CoordinateConverter: DMS, MGRS, speed conversions
- Manual test: launch app, grant permissions, see location displayed
- Validate generated CoT XML against TAK server expectations

---

## Phase 3: Transport Layer (TCP + UDP)
**Goal:** Send CoT to TAK server over TCP/TLS and UDP multicast.

### Tasks
1. **CertificateStore**
   - Android Keystore wrapper
   - Store/retrieve client certificate and private key
   - Store/retrieve CA trust chain
   - Delete certificates (for disconnect)
   - Certificate info display (subject, issuer, expiry, fingerprint)
   - Expiry checking

2. **TakTcpClient**
   - SSLContext setup with KeyManager (client cert) and TrustManager (CA chain)
   - Persistent SSLSocket connection
   - `connect(host, port)` → establish TLS
   - `send(cotXml)` → write UTF-8 bytes to output stream
   - `disconnect()` → clean socket close
   - Connection state reporting
   - Error detection and classification

3. **TakUdpBroadcaster**
   - MulticastSocket setup
   - `connect(address, port)` → join multicast group
   - `send(cotXml)` → send DatagramPacket
   - `disconnect()` → leave group, close socket
   - MulticastLock acquisition

4. **ConnectionManager**
   - Orchestrate TCP + UDP lifecycle
   - Connection state machine (DISCONNECTED → CONNECTING → CONNECTED → etc.)
   - Exponential backoff reconnection
   - `ConnectivityManager.NetworkCallback` for network changes
   - `StateFlow<ConnectionState>` for UI
   - Auto-connect logic

### Deliverable
Can connect to a TAK server (given pre-configured certs) and send CoT. UDP broadcasts on local network.

### Verification
- Integration test: connect to test TAK server, verify CoT received
- Test reconnection: disconnect network, re-enable, verify auto-reconnect
- Test UDP: verify packets on multicast group with Wireshark/tcpdump
- Test state machine transitions with unit tests

---

## Phase 4: QR Enrollment
**Goal:** Scan QR code, perform CSR enrollment, receive and store certificates.

### Tasks
1. **QRCodeParser**
   - iTAK format parser (comma-separated)
   - ATAK JSON format parser
   - Input validation
   - `EnrollmentParameters` data class

2. **CSREnrollmentManager**
   - Fetch CA config: `GET /Marti/api/tls/config`
   - Parse XML response for O, OU
   - Generate RSA 2048 keypair in Android Keystore
   - Build PKCS#10 CSR (using Bouncy Castle or Android built-in)
   - Submit CSR: `POST /Marti/api/tls/signClient/v2`
   - Parse JSON response (signedCert, CA chain)
   - Store via CertificateStore
   - Status flow: NOT_STARTED → CONNECTING → CONFIGURING → ENROLLING → SUCCEEDED/FAILED

3. **QR Scanner UI**
   - CameraX preview
   - ML Kit BarcodeScanning (or ZXing)
   - Viewfinder overlay
   - Camera permission handling
   - Haptic feedback on scan

4. **Enrollment UI**
   - Connection options screen
   - Certificate enrollment form (host, username, password, ports)
   - QR scan integration (auto-populate form)
   - Enrollment status display
   - Auto-submit when ATAK format provides credentials

### Deliverable
Full enrollment flow: scan QR → enroll → auto-connect → transmitting.

### Verification
- Unit tests for QRCodeParser: both formats, invalid inputs
- Integration test: full enrollment against test TAK server
- Test: scan iTAK QR → manual credential entry → enrollment
- Test: scan ATAK QR → auto-enrollment
- Test: invalid QR → error message
- Verify cert stored in Keystore and connection works

---

## Phase 5: Foreground Service + Background Tracking
**Goal:** App continues transmitting when backgrounded.

### Tasks
1. **TrackingForegroundService**
   - `startForeground()` with notification
   - `FOREGROUND_SERVICE_LOCATION` type
   - `START_STICKY` for restart resilience
   - Notification channel setup
   - Notification with callsign, status, last TX time
   - "Stop" action in notification
   - Binder for ViewModel connection

2. **TrackerEngine**
   - Coroutine-based timer at configurable interval
   - Dynamic mode: check distance/speed thresholds
   - Failsafe timer for dynamic mode
   - Emergency mode: override to 3s interval
   - Dispatch CoT to ConnectionManager
   - Update notification on each transmit

3. **Service ↔ ViewModel Binding**
   - TrackerViewModel binds to service
   - Observes StateFlows from service components
   - Start/stop tracking actions
   - Emergency toggle action

### Deliverable
Full background tracking. App can be backgrounded and continues transmitting with visible notification.

### Verification
- Test: start tracking, background app, verify CoT continues being sent
- Test: stop from notification action
- Test: kill app, verify service restarts (START_STICKY)
- Test: dynamic mode — verify no transmit when stationary
- Test: emergency mode — verify 3s interval

---

## Phase 6: Full UI Polish + Emergency
**Goal:** Complete all UI screens, emergency beacon, and polish.

### Tasks
1. **Main Screen**
   - Callsign display
   - Location panel with tap-to-cycle (DMS/MGRS/Decimal)
   - Heading/Compass/Speed panels with tap-to-toggle
   - Map view with follow mode + tap to re-center
   - Server status badge
   - Toolbar with emergency, settings, logs buttons

2. **Settings Screen**
   - User information (callsign, team, role dropdowns)
   - Server information display (URL, status, cert info)
   - Connection options (navigate to enrollment)
   - Tracking options (interval, stale time, dynamic mode, UDP)
   - Display options (map type, screen on)
   - About section

3. **Emergency Screen**
   - Dual-confirm toggle (activate + confirm)
   - Alert type picker
   - Emergency activation → high-rate transmit
   - Cancel flow
   - Red warning indicator in main screen toolbar

4. **Log Viewer Screen**
   - Scrollable log list
   - Color-coded by level
   - Auto-scroll with pause on manual scroll
   - Timestamp + tag + message format

5. **Certificate Expiry Warnings**
   - Check on launch and periodically
   - Yellow warning in settings when < 30 days
   - Red error when expired

6. **Edge Cases & Polish**
   - No-GPS state handling
   - No-network state handling
   - Screen rotation
   - Dark theme consistency
   - Error dialogs and user feedback

### Deliverable
Feature-complete MVP ready for field testing.

### Verification
- Full end-to-end test: install → permissions → scan QR → enroll → transmit → background → emergency → cancel
- Verify on TAK server: PLI visible with correct callsign, team, position
- Test all coordinate formats display correctly
- Test all speed units display correctly
- Test map pan/zoom does not auto-reset
- Test emergency alert visible on TAK server
- Test reconnection after network drop
- Test certificate expiry warning (with near-expiry test cert)

---

## Dependency Graph

```
Phase 1: Scaffold ─────────────────────────────────────┐
    │                                                   │
    ▼                                                   │
Phase 2: Location + CoT ──────────────────────┐        │
    │                                          │        │
    ▼                                          ▼        ▼
Phase 3: Transport ◄──────────────── Phase 4: Enrollment
    │                                          │
    ▼                                          │
Phase 5: Foreground Service ◄──────────────────┘
    │
    ▼
Phase 6: UI Polish + Emergency
```

Phase 3 and Phase 4 can be developed **in parallel** — they share Phase 1+2 as prerequisites but are independent of each other. Phase 5 depends on both.

---

## Key Dependencies (Libraries)

| Library | Purpose | Phase |
|---------|---------|-------|
| Hilt | Dependency injection | 1 |
| DataStore | Settings persistence | 1 |
| Play Services Location | FusedLocationProvider | 2 |
| Google Maps Compose | Map display | 6 |
| CameraX | QR scanner camera | 4 |
| ML Kit Barcode | QR code detection | 4 |
| Bouncy Castle (optional) | CSR generation | 4 |
| Material 3 | UI components | 1+ |

---

## Testing Strategy

### Unit Tests (All Phases)
- `QRCodeParser` — both formats, edge cases
- `CotBuilder` — valid XML output, all field types
- `CoordinateConverter` — DMS, MGRS, speed conversions
- `ReconnectStrategy` — backoff timing
- `LogRepository` — ring buffer, thread safety
- `SettingsRepository` — read/write/defaults

### Integration Tests (Phases 3-5)
- TCP connection to test TAK server
- Full enrollment flow against test TAK server
- Background service lifecycle
- Network change handling

### Manual Testing (Phase 6)
- End-to-end field test with real TAK server
- Multiple device types (different Android versions)
- Network transition scenarios (WiFi ↔ cellular)
- Long-duration background tracking (hours)
- Battery consumption profiling
