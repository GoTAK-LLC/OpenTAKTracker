# Security Specification

## Overview

OpenTAK Tracker handles sensitive cryptographic material (private keys, certificates) and transmits location data over the network. This document specifies the security model, threat mitigations, and implementation requirements.

---

## Certificate Lifecycle

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│ QR Scan     │────►│ CSR Enrollment│────►│ Certificate  │
│             │     │ (HTTPS)      │     │ Installed    │
└─────────────┘     └──────────────┘     └──────┬───────┘
                                                │
                    ┌──────────────┐             │
                    │ Certificate  │◄────────────┘
                    │ Active       │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ Expiry Check │
                    │ (< 30 days)  │
                    └──────┬───────┘
                      ┌────┴────┐
                      │         │
                  Valid      Expiring
                      │         │
                      │    ┌────▼────────┐
                      │    │ Show Warning │
                      │    │ in Settings  │
                      │    └─────────────┘
                      │
                ┌─────▼─────┐
                │ Expired / │
                │ Revoked   │
                └─────┬─────┘
                      │
                ┌─────▼─────────┐
                │ Connection    │
                │ Fails → User  │
                │ must re-enroll│
                └───────────────┘
```

### Certificate Storage

| Item | Storage Location | Access |
|------|-----------------|--------|
| Client private key | Android Keystore (hardware-backed) | Never exportable |
| Client certificate | Android Keystore | Read-only by app |
| CA trust chain | Android Keystore | Read-only by app |
| Server URL/Port | Jetpack DataStore (encrypted) | App only |

### Android Keystore Properties

```kotlin
KeyGenParameterSpec.Builder(alias, purposes)
    .setKeySize(2048)
    .setDigests(KeyProperties.DIGEST_SHA256)
    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
    // Key is bound to device - cannot be extracted
    // Hardware-backed on supported devices (StrongBox / TEE)
    .build()
```

Key properties:
- **Non-exportable**: Private key cannot be read out of Keystore
- **Hardware-backed**: Uses Trusted Execution Environment (TEE) or StrongBox when available
- **App-bound**: Only this app (same signing key) can access the key
- **Survives app update**: Key persists across updates with same signing key
- **Deleted on app uninstall**: Key is removed when app is uninstalled

### Certificate Expiry Monitoring

Check certificate expiry on:
1. App launch
2. Settings screen display
3. Every 24 hours while running

```kotlin
fun checkCertificateExpiry(cert: X509Certificate): CertStatus {
    val now = Date()
    val daysUntilExpiry = TimeUnit.MILLISECONDS.toDays(
        cert.notAfter.time - now.time
    )
    return when {
        daysUntilExpiry < 0 -> CertStatus.EXPIRED
        daysUntilExpiry < 30 -> CertStatus.EXPIRING_SOON
        else -> CertStatus.VALID
    }
}
```

Display in Settings:
- **Valid**: Green text, show expiry date
- **Expiring < 30 days**: Yellow warning with days remaining
- **Expired**: Red error, prompt re-enrollment

---

## TLS Configuration

### Client-to-Server (TCP Streaming)

| Property | Value |
|----------|-------|
| Protocol | TLS 1.2 minimum |
| Client auth | X.509 certificate from Keystore |
| Server verification | Against stored CA trust chain |
| Cipher suites | System defaults (strong ciphers) |
| Hostname verification | Disabled when using custom CA (matches iOS ref behavior) |

### Enrollment HTTPS

| Property | Value |
|----------|-------|
| Protocol | TLS 1.2 minimum |
| Authentication | HTTP Basic Auth (username:password) |
| Server verification | System trust store by default |
| Untrusted servers | Configurable opt-in for testing |

### Trust Manager Implementation

For the TCP streaming connection, we use a custom TrustManager that validates against the CA chain received during enrollment:

```kotlin
class TakTrustManager(private val trustedCerts: List<X509Certificate>) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // First try system trust store
        try {
            val systemTmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            )
            systemTmf.init(null as KeyStore?)
            val systemTm = systemTmf.trustManagers[0] as X509TrustManager
            systemTm.checkServerTrusted(chain, authType)
            return // Trusted by system
        } catch (e: CertificateException) {
            // Not trusted by system, check custom CA chain
        }

        // Check against enrollment CA chain
        val customTrustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        customTrustStore.load(null)
        trustedCerts.forEachIndexed { i, cert ->
            customTrustStore.setCertificateEntry("ca-$i", cert)
        }
        val customTmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        customTmf.init(customTrustStore)
        val customTm = customTmf.trustManagers[0] as X509TrustManager
        customTm.checkServerTrusted(chain, authType)
        // If this throws, connection is rejected
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
```

---

## Credential Handling

### Enrollment Credentials (Username/Password)

| Phase | Handling |
|-------|---------|
| QR scan | Parsed into memory-only variables |
| Enrollment request | Used for HTTP Basic Auth header |
| After enrollment | **Cleared from memory immediately** |
| Persistence | **Never written to disk** |

```kotlin
// After successful enrollment:
username = ""
password = ""
// Force garbage collection hint (best effort)
System.gc()
```

### Why No Credential Persistence
- Credentials are one-time enrollment tokens
- The client certificate replaces credentials for all future auth
- Storing credentials creates unnecessary attack surface
- Re-enrollment requires a new QR code (by design)

---

## Data Protection

### At Rest

| Data | Protection |
|------|-----------|
| Private key | Android Keystore (hardware-backed, non-exportable) |
| Certificates | Android Keystore |
| Settings (callsign, team, etc.) | Jetpack DataStore with EncryptedSharedPreferences optional |
| Logs | In-memory only (not persisted to disk) |
| Location data | Not persisted - only current position in memory |

### In Transit

| Channel | Protection |
|---------|-----------|
| TCP to TAK Server | TLS 1.2+ with mutual authentication |
| UDP broadcast | **None** (inherent to UDP multicast - plaintext) |
| Enrollment HTTPS | TLS with server verification |

> **Note:** UDP broadcast is inherently unencrypted. This is by design in the TAK ecosystem for local network SA. Sensitive operations use TCP/TLS.

---

## Threat Model

### Threats and Mitigations

| Threat | Impact | Mitigation |
|--------|--------|------------|
| Device theft → cert extraction | Impersonation | Android Keystore: key is hardware-bound, non-exportable |
| Network MITM on TCP | Location interception | TLS with mutual certificate auth |
| Network MITM on enrollment | Credential theft | HTTPS with server verification |
| Malicious QR code | Enrollment to rogue server | User intent required, visible server URL before enrollment |
| UDP eavesdropping | Location disclosure (local net) | Accepted risk per TAK design. TCP is the secure channel. |
| App data backup extraction | Settings leak | Exclude from backup via `android:allowBackup="false"` |
| Log exposure | Operational intel leak | Logs are in-memory only, no disk persistence |
| Rooted device | Full key access | Accept risk; Keystore provides best available protection |

### Out of Scope
- Protection against a fully compromised (rooted) device
- Encryption of UDP multicast (not supported by TAK protocol)
- Protection against physical access with device PIN/biometric bypass

---

## Android Manifest Security

```xml
<application
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

### Network Security Config

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <!-- Default: trust system CAs only -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- For TAK server connections, we use custom trust management
         in code (TakTrustManager) rather than config-level overrides -->
</network-security-config>
```

### Data Extraction Rules (Android 12+)

```xml
<!-- res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" />
    </device-transfer>
</data-extraction-rules>
```

---

## Self-Signed Server Handling

For testing environments, the user can enable trust of self-signed certificates:

| Setting | Default | Description |
|---------|---------|-------------|
| Trust all enrollment certs | OFF | Accept any server cert during enrollment |
| Trust all streaming certs | OFF | Accept any server cert for TCP streaming |

**When enabled:**
- Shows a persistent warning banner in Settings
- Log warning on every connection
- Both settings are independent

**Implementation:**
```kotlin
// Only used when user explicitly enables trust-all
class TrustAllManager : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // Accept all - INSECURE, testing only
    }
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
```

---

## Security Checklist

- [ ] Private key generated in Android Keystore with `PURPOSE_SIGN`
- [ ] Private key is non-exportable
- [ ] Enrollment credentials cleared from memory after use
- [ ] No credentials persisted to disk
- [ ] TLS 1.2+ enforced for all HTTPS/TCP connections
- [ ] Server certificate validated against CA trust chain
- [ ] `android:allowBackup="false"` set
- [ ] `cleartextTrafficPermitted="false"` in network security config
- [ ] Certificate expiry warnings implemented
- [ ] Self-signed trust requires explicit user opt-in
- [ ] Logs contain no credentials or private key material
- [ ] UDP broadcast documented as unencrypted
