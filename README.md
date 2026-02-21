# OpenTAK Tracker for Android

OpenTAK Tracker is a lightweight Android application that broadcasts your location as Position/Location Information (PLI) to one or more TAK servers. It is designed to work within the TAK ecosystem alongside ATAK, iTAK, and TAK Server, providing a simple, dedicated tracking solution without the complexity of a full EUD client.

## Features

- **Multi-Server Support** -- Connect to multiple TAK servers simultaneously and manage each connection independently.
- **QR Code Enrollment** -- Scan a QR code to configure a server connection in seconds. Supports multiple QR and URI formats used across the TAK ecosystem.
- **Deep Link Enrollment** -- Tap an `opentaktracker://` link on your Android device to open the app and begin enrollment automatically.
- **Emergency Alerts** -- Send TAK-standard emergency alerts (911, Ring the Bell, In Contact) to all connected servers.
- **Hardware SOS** -- Trigger a 911 emergency alert by pressing the volume key 5 times rapidly, even with the screen off. Useful for hands-free or covert distress signaling.
- **ATAK Auto-Pause** -- Automatically pause PLI transmission when ATAK is detected in the foreground, preventing duplicate position reports on the TAK server. Transmission resumes when ATAK is backgrounded or closed.
- **Team Color and Role** -- Set your callsign, team color, and team role to appear correctly on other TAK clients.
- **Kiosk Lock** -- Lock the app into the foreground to prevent accidental navigation away, useful for dedicated tracking devices.
- **Dark Map** -- Toggle a dark-themed map for low-light environments.
- **Start on Boot** -- Optionally launch and begin tracking automatically when the device powers on.
- **Coordinate Formats** -- Display your position in Decimal Degrees, Degrees/Minutes/Seconds (DMS), or MGRS.

## Getting Started

1. Install OpenTAK Tracker on your Android device.
2. Grant location permissions when prompted. The app requires location access to broadcast your position.
3. Add a server by scanning a QR code, tapping an enrollment link, or entering server details manually.
4. Once enrolled and connected, the app will begin sending your location to the configured server at a regular interval.

## Server Enrollment

The fastest way to add a server is to scan a QR code or tap an enrollment link. OpenTAK Tracker supports its own URI scheme that follows the same structure as ATAK's `tak://` enrollment scheme.

### The opentaktracker:// URI Scheme

Enrollment URIs use the following format:

```
opentaktracker://enroll?host=SERVER&username=USER&token=TOKEN&callsign=CALLSIGN&team=Cyan&role=Team%20Member
```

A password-based variant is also supported:

```
opentaktracker://enroll?host=SERVER&username=USER&password=PASS
```

If `callsign`, `team`, or `role` parameters are included, the app will apply them automatically after a successful enrollment.

When a user taps a link with the `opentaktracker://` scheme on an Android device that has the app installed, the app will open and begin the enrollment process automatically. If both a username and credential are present in the URI, enrollment will auto-submit without further input.

If only the `host` parameter is provided, the app will pre-fill the server address and prompt the user to enter credentials manually.

#### Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `host` | Yes | The TAK server hostname or IP address. |
| `username` | No | The username for certificate enrollment. |
| `token` | No | A token or password used for authentication. |
| `password` | No | Alternative to `token`; used for password-based authentication. |
| `port` | No | The streaming port (defaults to the standard TAK streaming port). |
| `csrPort` | No | The certificate signing request port, if different from the default. |
| `secureApiPort` | No | The secure API port, if different from the default. |
| `callsign` | No | Sets the device callsign after enrollment. |
| `team` | No | Sets the team color (e.g., `Cyan`, `Red`, `Blue`). |
| `role` | No | Sets the team role (e.g., `Team Member`, `Team Lead`, `HQ`). |

### QR Codes

These same URIs can be encoded into QR codes. When scanned from within the app, the QR code is parsed and enrollment begins. Server administrators can generate QR codes containing `opentaktracker://` URIs to simplify device onboarding.

## Supported QR and URI Formats

OpenTAK Tracker recognizes several formats commonly used across the TAK ecosystem:

| Format | Example |
|--------|---------|
| **OpenTAK Tracker URI** | `opentaktracker://enroll?host=tak.example.com&username=user1&token=abc123&callsign=ALPHA1&team=Cyan&role=Team%20Member` |
| **ATAK tak:// URI** | `tak://com.atakmap.app/enroll?host=tak.example.com&username=user1&token=abc123` |
| **iTAK CSV** | `MyServer,tak.example.com,8089,ssl` |
| **ATAK JSON** | `{"serverCredentials":{"connectionString":"tak.example.com:8089:ssl"},"userCredentials":{"username":"user1","password":"pass"}}` |

All four formats are supported when scanning QR codes. The `opentaktracker://` and `tak://` formats are also supported as tappable deep links.

## Permissions

OpenTAK Tracker requests only the permissions it needs:

| Permission | Purpose |
|------------|---------|
| **Location** | Required to broadcast your position. Background location access keeps tracking active when the app is not in the foreground. |
| **Camera** | Used for scanning QR codes during server enrollment. |
| **Notifications** | Required on Android 13+ to display the persistent tracking notification. |
| **Usage Access** | Required only if the **Pause When ATAK Active** feature is enabled. Allows the app to detect when ATAK is in the foreground. This is a special permission granted in system Settings, not a runtime popup. |

## License

See the [LICENSE](LICENSE) file for details.
