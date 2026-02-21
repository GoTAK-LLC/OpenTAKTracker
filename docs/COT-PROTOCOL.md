# CoT (Cursor on Target) Protocol Specification

## Overview

Cursor on Target (CoT) is an XML-based messaging protocol used by TAK ecosystem for exchanging situational awareness data. OpenTAK Tracker generates CoT events for Position Location Information (PLI) and emergency alerts.

---

## CoT Event Schema

### Root Element: `<event>`

```xml
<event version="2.0"
       uid="{UID}"
       type="{TYPE}"
       how="{HOW}"
       time="{TIME}"
       start="{START}"
       stale="{STALE}">
  <point lat="{LAT}" lon="{LON}" hae="{HAE}" ce="{CE}" le="{LE}" />
  <detail>
    <!-- detail sub-elements -->
  </detail>
</event>
```

| Attribute | Type | Description |
|-----------|------|-------------|
| `version` | String | Always `"2.0"` |
| `uid` | String | Unique identifier for this sender. Stable per device. |
| `type` | String | CoT type string (see Type System below) |
| `how` | String | How the data was generated (see How Codes below) |
| `time` | ISO 8601 UTC | When the event was generated |
| `start` | ISO 8601 UTC | When the event becomes valid (usually same as `time`) |
| `stale` | ISO 8601 UTC | When the event expires (`time` + stale duration) |

---

## UID Generation

The UID must be **stable per device** and unique across the TAK ecosystem.

### Format
```
OpenTAK-{ANDROID_ID}
```

### Implementation
```kotlin
val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
val uid = "OpenTAK-$androidId"
```

- `ANDROID_ID` is a 64-bit hex string unique to each device + app signing key combination
- Persists across app reinstalls on Android 8.0+ (same signing key)
- If unavailable, fall back to `UUID.randomUUID()` and persist in DataStore

---

## Type System

CoT types follow a dot-separated hierarchical scheme: `a-{affiliation}-{battle_dimension}-{function}`

### PLI Type
```
a-f-G-U-C
│ │ │ │ └─ Combat (function)
│ │ │ └─── Unit (entity)
│ │ └───── Ground (battle dimension)
│ └─────── Friendly (affiliation)
└───────── Atom (root)
```

For our tracker, the default PLI type is always `a-f-G-U-C` (Friendly Ground Unit Combat).

### Emergency Types

| Alert | CoT Type | Description |
|-------|----------|-------------|
| 911 | `b-a-o-tbl` | 911/Emergency |
| Ring the Bell | `b-a-o-can` | Ring the Bell |
| In Contact | `b-a-o-pan` | In Contact / Troops in Contact |
| Cancel | *(original type)* | Cancels active alert |

Emergency types use `b-a-o-*` prefix (Bits - Alert - Other).

---

## How Codes

| Code | Meaning |
|------|---------|
| `m-g` | Machine GPS — position from GPS sensor |
| `h-e` | Human Estimated — manually entered position |

OpenTAK Tracker always uses `m-g`.

---

## Time Format

All timestamps use ISO 8601 in UTC with millisecond precision:

```
2024-01-15T14:30:00.000Z
```

### Kotlin Implementation
```kotlin
val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    .withZone(ZoneOffset.UTC)

val now = Instant.now()
val time = formatter.format(now)
val stale = formatter.format(now.plusSeconds(staleTimeMinutes * 60))
```

---

## Point Element

```xml
<point lat="38.889500" lon="-77.036600" hae="45.3" ce="10.0" le="9999999" />
```

| Attribute | Type | Unit | Description |
|-----------|------|------|-------------|
| `lat` | Double | Decimal degrees | WGS-84 latitude |
| `lon` | Double | Decimal degrees | WGS-84 longitude |
| `hae` | Double | Meters | Height Above Ellipsoid (GPS altitude) |
| `ce` | Double | Meters | Circular Error (horizontal accuracy). Use GPS accuracy value. |
| `le` | Double | Meters | Linear Error (vertical accuracy). `9999999` if unknown. |

---

## Detail Sub-Elements

### `__group` — Team/Role
```xml
<__group name="Cyan" role="Team Member" />
```

| Field | Values |
|-------|--------|
| `name` | Cyan, White, Yellow, Orange, Magenta, Red, Maroon, Purple, Dark Blue, Blue, Teal, Green, Dark Green, Brown |
| `role` | Team Member, Team Lead, HQ, Sniper, Medic, Forward Observer, RTO, K9 |

### `contact` — Callsign
```xml
<contact callsign="TRACKER-A1B2" />
```

### `uid` — Droid Identifier
```xml
<uid Droid="TRACKER-A1B2" />
```
Matches callsign. Used by ATAK/TAK for display.

### `precisionlocation` — Source Info
```xml
<precisionlocation altsrc="GPS" geopointsrc="GPS" />
```

### `status` — Device Status
```xml
<status battery="85" />
```
Battery level as integer percentage (0-100).

### `track` — Movement
```xml
<track speed="3.5" course="270.0" />
```

| Field | Unit | Description |
|-------|------|-------------|
| `speed` | m/s | Speed from GPS. `0` if stationary. |
| `course` | Degrees | Bearing/heading from GPS (0-360, 0=North). |

### `takv` — Platform Info
```xml
<takv device="Pixel 8"
      platform="OpenTAK-Tracker-Android"
      os="Android 14"
      version="1.0.0" />
```

### `emergency` — Emergency Detail (only in emergency events)
```xml
<emergency type="911 Alert" cancel="false">TRACKER-A1B2</emergency>
```

| Attribute | Description |
|-----------|-------------|
| `type` | Human-readable emergency type |
| `cancel` | `"true"` when cancelling, `"false"` when activating |
| Text content | Callsign of the sender |

---

## Complete PLI Example

```xml
<event version="2.0"
       uid="OpenTAK-a1b2c3d4e5f6"
       type="a-f-G-U-C"
       how="m-g"
       time="2024-01-15T14:30:00.000Z"
       start="2024-01-15T14:30:00.000Z"
       stale="2024-01-15T14:35:00.000Z">
  <point lat="38.889500" lon="-77.036600" hae="45.3"
         ce="10.0" le="9999999" />
  <detail>
    <__group name="Cyan" role="Team Member" />
    <contact callsign="TRACKER-A1B2" />
    <uid Droid="TRACKER-A1B2" />
    <precisionlocation altsrc="GPS" geopointsrc="GPS" />
    <status battery="85" />
    <track speed="3.5" course="270.0" />
    <takv device="Pixel 8" platform="OpenTAK-Tracker-Android"
          os="Android 14" version="1.0.0" />
  </detail>
</event>
```

---

## Complete Emergency Example

```xml
<event version="2.0"
       uid="OpenTAK-a1b2c3d4e5f6"
       type="b-a-o-tbl"
       how="m-g"
       time="2024-01-15T14:30:00.000Z"
       start="2024-01-15T14:30:00.000Z"
       stale="2024-01-15T14:31:00.000Z">
  <point lat="38.889500" lon="-77.036600" hae="45.3"
         ce="10.0" le="9999999" />
  <detail>
    <contact callsign="TRACKER-A1B2" />
    <emergency type="911 Alert" cancel="false">TRACKER-A1B2</emergency>
  </detail>
</event>
```

---

## Emergency Cancel Example

```xml
<event version="2.0"
       uid="OpenTAK-a1b2c3d4e5f6"
       type="b-a-o-can"
       how="m-g"
       time="2024-01-15T14:35:00.000Z"
       start="2024-01-15T14:35:00.000Z"
       stale="2024-01-15T14:36:00.000Z">
  <point lat="38.889500" lon="-77.036600" hae="45.3"
         ce="10.0" le="9999999" />
  <detail>
    <contact callsign="TRACKER-A1B2" />
    <emergency type="Cancel" cancel="true">TRACKER-A1B2</emergency>
  </detail>
</event>
```

---

## XML Construction Notes

### No XML Declaration
CoT events are sent **without** the XML declaration (`<?xml version="1.0"?>`). The raw `<event>` element is the entire message.

### TCP Framing
When sending over TCP, CoT messages are sent as raw UTF-8 XML with **no framing** — each `<event>...</event>` is written directly to the socket. The TAK server parses the XML stream.

### UDP Messages
Same XML, sent as a single UDP datagram. Messages must fit within a single UDP packet (practical limit ~1400 bytes, typical PLI is ~600-800 bytes).

### Character Encoding
All CoT XML is UTF-8 encoded.

### Numeric Precision
- Latitude/Longitude: 6 decimal places minimum
- HAE/CE/LE: 1 decimal place
- Speed: 1 decimal place
- Course: 1 decimal place

---

## CotBuilder Implementation Guide

```kotlin
class CotBuilder(
    private val settings: SettingsRepository
) {
    fun buildPLI(location: LocationData): String {
        val now = Instant.now()
        val time = formatTime(now)
        val stale = formatTime(now.plusSeconds(settings.staleTimeMinutes * 60))

        return buildString {
            append("<event version=\"2.0\"")
            append(" uid=\"${settings.deviceUid}\"")
            append(" type=\"${settings.cotType}\"")
            append(" how=\"m-g\"")
            append(" time=\"$time\"")
            append(" start=\"$time\"")
            append(" stale=\"$stale\">")

            append("<point")
            append(" lat=\"${location.latitude}\"")
            append(" lon=\"${location.longitude}\"")
            append(" hae=\"${location.altitude}\"")
            append(" ce=\"${location.accuracy}\"")
            append(" le=\"9999999\" />")

            append("<detail>")
            append("<__group name=\"${settings.team}\" role=\"${settings.role}\" />")
            append("<contact callsign=\"${settings.callsign}\" />")
            append("<uid Droid=\"${settings.callsign}\" />")
            append("<precisionlocation altsrc=\"GPS\" geopointsrc=\"GPS\" />")
            append("<status battery=\"${getBatteryLevel()}\" />")
            append("<track speed=\"${location.speed}\" course=\"${location.bearing}\" />")
            append("<takv device=\"${Build.MODEL}\"")
            append(" platform=\"OpenTAK-Tracker-Android\"")
            append(" os=\"Android ${Build.VERSION.RELEASE}\"")
            append(" version=\"${BuildConfig.VERSION_NAME}\" />")
            append("</detail>")

            append("</event>")
        }
    }

    fun buildEmergency(location: LocationData, type: EmergencyType, cancel: Boolean): String {
        // Similar structure with emergency-specific type and detail
    }
}
```
