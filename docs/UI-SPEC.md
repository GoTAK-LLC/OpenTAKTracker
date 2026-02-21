# UI Specification

## Overview

OpenTAK Tracker uses **Jetpack Compose** with **Material 3** and a dark theme optimized for outdoor/field use. The app has a single-activity architecture with composable navigation.

---

## Navigation Structure

```
┌─────────────────────┐
│   MainScreen        │ ◄── Default / Home
│   (Status + Map)    │
└─────┬───────────────┘
      │
      ├── SettingsSheet (full screen cover)
      │   ├── User Information
      │   ├── Server Information
      │   ├── Connection Options
      │   │   └── Enrollment Screen
      │   │       └── QR Scanner
      │   ├── Tracking Options
      │   └── About
      │
      ├── EmergencySheet (full screen cover)
      │
      └── LogViewerSheet (full screen cover)
```

All secondary screens are **full-screen modal sheets** dismissed via "Dismiss" / "Close" button — matching the iOS reference behavior.

---

## Color Scheme (Dark Theme)

| Element | Color | Hex |
|---------|-------|-----|
| Background | Dark Gray | `#1C1C1E` |
| Surface | Medium Gray | `#2C2C2E` |
| Data panels | Black | `#000000` |
| Panel border | Blue | `#007AFF` |
| Primary text | White | `#FFFFFF` |
| Secondary text | Light Gray | `#8E8E93` |
| Connected | Green | `#34C759` |
| Disconnected/Error | Red | `#FF3B30` |
| Warning | Yellow | `#FFD60A` |
| Reconnecting | Orange | `#FF9500` |

---

## Main Screen

### Wireframe

```
┌──────────────────────────────────────┐
│ ▲ OpenTAK Tracker      ⚠  ⚙  📋    │  ← Toolbar
├──────────────────────────────────────┤
│                                      │
│          TRACKER-A1B2                │  ← Callsign (bold)
│                                      │
│ ┌──────────────────────────────────┐ │
│ │ Location (DMS)                   │ │  ← Tap to cycle format
│ │                                  │ │
│ │ N  38° 53' 22.110"              │ │
│ │ W  077° 02' 11.580"             │ │
│ └──────────────────────────────────┘ │
│                                      │
│ ┌──────────┐┌──────────┐┌──────────┐ │
│ │ Heading  ││ Compass  ││  Speed   │ │
│ │  (°TN)   ││  (°MN)   ││  (m/s)  │ │
│ │          ││          ││         │ │
│ │  270°    ││  265°    ││   3     │ │
│ └──────────┘└──────────┘└──────────┘ │
│  ← Tap to toggle TN/MN  Tap→units  │
│                                      │
│ ┌──────────────────────────────────┐ │
│ │                                  │ │
│ │           MAP VIEW               │ │
│ │        (user location            │ │
│ │         with heading)            │ │
│ │                                  │ │
│ │    Tap to re-center / follow     │ │
│ │                                  │ │
│ └──────────────────────────────────┘ │
│                                      │
│                  ┌──────────────────┐ │
│                  │Server: Connected │ │  ← Status badge
│                  └──────────────────┘ │
└──────────────────────────────────────┘
```

### Toolbar Items

| Position | Icon | Action |
|----------|------|--------|
| Left | App title "OpenTAK Tracker" | — |
| Right 1 | `⚠` Warning triangle | Open Emergency sheet |
| Right 2 | `⚙` Gear | Open Settings sheet |
| Right 3 | `📋` List | Open Log Viewer sheet |

Emergency icon turns **red** when alert is active.

### Data Panels

#### Location Panel
- Shows current coordinates
- **Tap to cycle**: DMS → MGRS → Decimal → DMS
- DMS format: Two lines (lat + lon) with direction prefix
- MGRS format: Single line (e.g., `18S UJ 23371 06519`)
- Decimal format: Two lines (Lat + Lon to 4 decimal places)
- Black background, blue border, white text, size 30sp

#### Heading / Compass / Speed Panels
Three equal-width panels in a row:

| Panel | Label | Subtitle | Value | Tap Action |
|-------|-------|----------|-------|------------|
| Heading | "Heading" | (°TN) or (°MN) | `270°` | Toggle TN ↔ MN |
| Compass | "Compass" | (°TN) or (°MN) | `265°` | Toggle TN ↔ MN |
| Speed | "Speed" | (m/s) etc | `3` | Cycle m/s → km/h → fps → mph |

- Black background, blue border, white text
- Value in size 30sp

#### Map Panel
- Fills remaining vertical space below data panels
- Shows user location with heading indicator
- User tracking mode: Follow with heading (default)
- **Tap map** to re-center and resume follow mode
- User can pan/zoom — does **not** auto-reset
- Map type respects settings (standard/satellite/hybrid)
- Hide map if vertical space < 150dp

#### Server Status Badge
- Positioned bottom-right, overlaid on content
- Green text "Server: Connected" when connected
- Red text "Server: {status}" when disconnected
- Black rounded rectangle background
- Font size 15sp

---

## Settings Screen

### Wireframe

```
┌──────────────────────────────────────┐
│ ← Settings                  Dismiss  │
├──────────────────────────────────────┤
│                                      │
│ USER INFORMATION                     │
│ ┌──────────────────────────────────┐ │
│ │ Callsign        [TRACKER-A1B2 ] │ │
│ │ Team            [Cyan        ▼] │ │
│ │ Role            [Team Member ▼] │ │
│ └──────────────────────────────────┘ │
│                                      │
│ SERVER INFORMATION                   │
│ ┌──────────────────────────────────┐ │
│ │ Server: tak.example.com:8089     │ │
│ │ Status: Connected                │ │
│ │ Certificate: Valid (expires      │ │
│ │   2025-03-15)                    │ │
│ ├──────────────────────────────────┤ │
│ │ ▶ Connect to a TAK Server       │ │
│ │ ✕ Delete Current Connection      │ │
│ └──────────────────────────────────┘ │
│                                      │
│ TRACKING OPTIONS                     │
│ ┌──────────────────────────────────┐ │
│ │ Broadcast Interval  [10s     ▼] │ │
│ │ Stale Time          [5 min   ▼] │ │
│ │ Dynamic Mode        [OFF      ] │ │
│ │ UDP Broadcast       [ON       ] │ │
│ │ UDP Address         [239.2.3.1] │ │
│ │ UDP Port            [6969     ] │ │
│ └──────────────────────────────────┘ │
│                                      │
│ DISPLAY                              │
│ ┌──────────────────────────────────┐ │
│ │ Map Type            [Standard▼] │ │
│ │ Keep Screen On      [ON       ] │ │
│ └──────────────────────────────────┘ │
│                                      │
│ ABOUT                                │
│ ┌──────────────────────────────────┐ │
│ │ Version: 1.0.0                   │ │
│ │ Device UID: OpenTAK-a1b2c3d4    │ │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

### Settings Fields

| Field | Type | Default | Persistence |
|-------|------|---------|-------------|
| Callsign | Text input | `TRACKER-{UUID_PREFIX}` | DataStore |
| Team | Dropdown (14 colors) | Cyan | DataStore |
| Role | Dropdown (8 roles) | Team Member | DataStore |
| Broadcast Interval | Slider/Dropdown (1-120s) | 10s | DataStore |
| Stale Time | Slider/Dropdown (1-60min) | 5 min | DataStore |
| Dynamic Mode | Toggle | OFF | DataStore |
| UDP Broadcast | Toggle | ON | DataStore |
| UDP Address | Text input | 239.2.3.1 | DataStore |
| UDP Port | Number input | 6969 | DataStore |
| Map Type | Dropdown | Standard | DataStore |
| Keep Screen On | Toggle | ON | DataStore |

---

## Enrollment Screen

### Wireframe (Connection Options)

```
┌──────────────────────────────────────┐
│ ← Connect to TAK Server             │
├──────────────────────────────────────┤
│                                      │
│    Choose a connection method:       │
│                                      │
│    ┌────────────────────────────┐    │
│    │  Certificate Enrollment    │    │
│    └────────────────────────────┘    │
│    ┌────────────────────────────┐    │
│    │  Scan QR Code              │    │
│    └────────────────────────────┘    │
│                                      │
└──────────────────────────────────────┘
```

### Wireframe (Certificate Enrollment Form)

```
┌──────────────────────────────────────┐
│ ← Certificate Enrollment            │
├──────────────────────────────────────┤
│                                      │
│ SERVER OPTIONS                       │
│ ┌──────────────────────────────────┐ │
│ │ Host Name    [tak.example.com  ] │ │
│ │ Username     [jexample         ] │ │
│ │ Password     [👁 ••••••••••••  ] │ │
│ └──────────────────────────────────┘ │
│                                      │
│ ADVANCED OPTIONS                     │
│ ┌──────────────────────────────────┐ │
│ │ Port              [8089        ] │ │
│ │ Cert Enroll Port  [8446        ] │ │
│ │ Secure API Port   [8443        ] │ │
│ └──────────────────────────────────┘ │
│                                      │
│     [Scan QR]  [Start Enrollment]    │
│                                      │
│     Status: Not Started              │
│     For Server: tak.example.com      │
│                                      │
└──────────────────────────────────────┘
```

### QR Scanner

```
┌──────────────────────────────────────┐
│                            Cancel    │
├──────────────────────────────────────┤
│                                      │
│                                      │
│        ┌──────────────────┐          │
│        │                  │          │
│        │   VIEWFINDER     │          │
│        │                  │          │
│        │   ┌──┐    ┌──┐  │          │
│        │   │  │    │  │  │          │
│        │   └──┘    └──┘  │          │
│        │   ┌──┐    ┌──┐  │          │
│        │   │  │    │  │  │          │
│        │   └──┘    └──┘  │          │
│        │                  │          │
│        └──────────────────┘          │
│                                      │
│   Point camera at TAK QR code        │
│                                      │
└──────────────────────────────────────┘
```

- Camera preview with viewfinder overlay
- Haptic vibration on successful scan
- Auto-dismiss on scan success

---

## Emergency Screen

### Wireframe

```
┌──────────────────────────────────────┐
│                                      │
│        Emergency Beacon              │
│                                      │
│  Turn on both switches to initiate   │
│         emergency beacon             │
│                                      │
│  Activate Alert                      │
│  ┌─────────────┬─────────────┐       │
│  │     Off     │     On      │       │  ← Segmented control
│  └─────────────┴─────────────┘       │     Red bg when ON
│                                      │
│  Confirm Alert                       │
│  ┌─────────────┬─────────────┐       │
│  │     Off     │     On      │       │
│  └─────────────┴─────────────┘       │
│                                      │
│  Alert Type                          │
│  ┌──────────────────────────┐        │
│  │  911 Alert            ▼  │        │  ← Dropdown
│  └──────────────────────────┘        │
│                                      │
│        [Cancel]    [OK]              │
│                                      │
│  OK disabled until both switches ON  │
│                                      │
└──────────────────────────────────────┘
```

### Emergency Alert Types

| Type | Display |
|------|---------|
| 911 | "911 Alert" |
| Ring the Bell | "Ring the Bell" |
| In Contact | "In Contact" |
| Cancel | "Cancel" (only shown when alert is active) |

### Behavior
- **Activate** + **Confirm** must both be ON to enable OK button (dual-confirm safety)
- When existing alert is active, opening this screen defaults to Cancel mode
- OK button triggers or cancels the emergency beacon
- Cancel button dismisses without action
- Emergency forces 3-second transmit interval

---

## Log Viewer Screen

### Wireframe

```
┌──────────────────────────────────────┐
│ ← Logs                     Dismiss   │
├──────────────────────────────────────┤
│                                      │
│ 14:30:00.123 INFO  Connection        │
│   Connecting to tak.example.com:8089 │
│                                      │
│ 14:30:00.456 INFO  TCP               │
│   TLS handshake started              │
│                                      │
│ 14:30:01.789 INFO  TCP               │
│   TLS handshake complete (TLSv1.2)   │
│                                      │
│ 14:30:01.801 INFO  Connection        │
│   Connected to tak.example.com:8089  │
│                                      │
│ 14:30:11.500 INFO  TCP               │
│   CoT sent (742 bytes)              │
│                                      │
│ 14:30:21.501 WARN  TCP               │  ← Yellow for WARN
│   Send timeout, retrying             │
│                                      │
│ 14:30:22.100 ERROR TCP               │  ← Red for ERROR
│   Connection lost: SocketException   │
│                                      │
│ 14:30:22.105 INFO  Connection        │
│   Reconnecting in 1000ms            │
│                                      │
│                         ▼ auto-scroll │
└──────────────────────────────────────┘
```

### Log Entry Format
```
{HH:mm:ss.SSS} {LEVEL} {TAG}
  {MESSAGE}
```

### Behavior
- Shows last 200 entries
- Auto-scrolls to bottom
- Manual scroll pauses auto-scroll
- Scroll to bottom resumes auto-scroll
- Color coded: INFO=white, WARN=yellow, ERROR=red

---

## Notification

### Persistent Foreground Service Notification

```
┌──────────────────────────────────────┐
│ 📍 OpenTAK Tracker                  │
│ TRACKER-A1B2 | Connected | 14:30:00 │
│                          [Stop]      │
└──────────────────────────────────────┘
```

| Field | Content |
|-------|---------|
| Title | "OpenTAK Tracker" |
| Body | "{Callsign} | {Status} | {Last TX time}" |
| Action | "Stop" button to stop tracking |
| Channel | "tracking_channel" with LOW importance |
| Priority | Ongoing (non-dismissable) |

---

## Permission Flow

### First Launch Sequence

```
1. App opens → Check location permission
   └─ Not granted → Show rationale dialog
      └─ Request ACCESS_FINE_LOCATION
         └─ Granted → Request ACCESS_BACKGROUND_LOCATION
            └─ Granted → Check notification permission
               └─ Android 13+ → Request POST_NOTIFICATIONS
                  └─ Ready to use

2. QR Scan → Check camera permission
   └─ Not granted → Request CAMERA
      └─ Granted → Open scanner
```

### Permission Denied Handling
- Show explanation of why each permission is needed
- Provide button to open app Settings if permanently denied
- App degrades gracefully:
  - No location → Show "Location Required" message, no tracking
  - No background location → Warn that tracking stops when backgrounded
  - No camera → Disable QR scanner, allow manual entry
  - No notifications → Warn that service notification won't show

---

## Responsive Layout

- **Portrait**: Full layout as wireframed above
- **Landscape**: Map expands to fill more space, data panels compress
- Minimum supported width: 320dp
- Map hidden if available height < 150dp
