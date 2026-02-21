# OpenTAK Tracker - Android

## Project Overview
Lightweight standalone Android TAK-compatible tracker. Sends PLI (Position Location Information) as CoT XML to TAK servers.

## Architecture
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **Architecture:** MVVM + Foreground Service
- **Package:** `com.opentak.tracker`

## Key Directories
- `app/src/main/java/com/opentak/tracker/` — All source code
  - `ui/` — Compose screens (main, settings, emergency, enrollment, logs)
  - `viewmodel/` — TrackerViewModel
  - `service/` — ForegroundService, TrackerEngine, LocationManager
  - `transport/` — TCP client, UDP broadcaster, ConnectionManager
  - `enrollment/` — QR parser, CSR enrollment
  - `security/` — CertificateStore (Android Keystore)
  - `cot/` — CoT XML builder
  - `data/` — Models, SettingsRepository, LogRepository
  - `util/` — Constants, CoordinateConverter
  - `di/` — Hilt modules
- `docs/` — Technical specification documents
- `RESEARCH/` — iOS TAKTracker reference implementation (read-only reference)

## Build
```
./gradlew assembleDebug
```

## Key Dependencies
- Google Play Services Location (FusedLocationProvider)
- CameraX + ML Kit (QR scanning)
- Bouncy Castle (CSR generation)
- Jetpack DataStore (settings persistence)

## TAK Protocol Notes
- CoT XML sent over TCP (TLS + client cert) and UDP (multicast 239.2.3.1:6969)
- Enrollment: QR scan → CSR to /Marti/api/tls/signClient/v2 → receive signed cert
- Default ports: 8089 (streaming), 8446 (CSR), 8443 (API)
