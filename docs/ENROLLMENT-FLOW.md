# Enrollment Flow Specification

## Overview

OpenTAK Tracker enrolls with a TAK server by scanning a QR code, performing a Certificate Signing Request (CSR) over HTTPS, and storing the resulting client certificate for TLS authentication.

**No manual cert import. No data package import. QR enrollment only.**

---

## QR Code Formats

### Format 1: iTAK QR Code
Simple comma-separated format used by iTAK:

```
{serverName},{serverURL},{serverPort},{protocol}
```

**Example:**
```
MyTAK,tak.example.com,8089,SSL
```

| Field | Description |
|-------|-------------|
| `serverName` | Human-readable server name (display only) |
| `serverURL` | Server hostname or IP |
| `serverPort` | Streaming port (typically 8089) |
| `protocol` | Protocol type (typically `SSL` or `ssl`) |

**Detection:** String splits into exactly 4 comma-separated parts.

### Format 2: ATAK Registration QR Code
JSON format used by ATAK server registration:

```json
{
  "passphrase": "false",
  "type": "registration",
  "serverCredentials": {
    "connectionString": "tak.example.com:8089:ssl"
  },
  "userCredentials": {
    "username": "jexample",
    "password": ".A9QvmRf?II81Y1#6b$nKdSF,",
    "registrationId": "92dc5931-e528-45e1-baee-64cc7cd7e053"
  }
}
```

| Field | Description |
|-------|-------------|
| `serverCredentials.connectionString` | `host:port:protocol` |
| `userCredentials.username` | Enrollment username |
| `userCredentials.password` | Enrollment password |
| `userCredentials.registrationId` | Optional registration ID |

**Detection:** String starts with `{` (JSON).

**Auto-submit:** If both username and password are present, enrollment starts automatically after QR scan.

---

## Enrollment Sequence

```
┌────────┐     ┌──────────┐     ┌───────────┐     ┌──────────┐
│  User  │     │  Parser  │     │ CSR Mgr   │     │TAK Server│
└───┬────┘     └────┬─────┘     └─────┬─────┘     └────┬─────┘
    │               │                 │                 │
    │ Scan QR       │                 │                 │
    │──────────────►│                 │                 │
    │               │                 │                 │
    │               │ parse()         │                 │
    │               │────────┐        │                 │
    │               │        │        │                 │
    │               │◄───────┘        │                 │
    │               │ EnrollmentParams│                 │
    │               │────────────────►│                 │
    │               │                 │                 │
    │               │                 │ GET /tls/config │
    │               │                 │────────────────►│
    │               │                 │                 │
    │               │                 │ XML (O, OU)     │
    │               │                 │◄────────────────│
    │               │                 │                 │
    │               │                 │ Generate RSA    │
    │               │                 │ keypair in      │
    │               │                 │ Keystore        │
    │               │                 │────────┐        │
    │               │                 │        │        │
    │               │                 │◄───────┘        │
    │               │                 │                 │
    │               │                 │ Build CSR       │
    │               │                 │ (CN,O,OU)       │
    │               │                 │────────┐        │
    │               │                 │        │        │
    │               │                 │◄───────┘        │
    │               │                 │                 │
    │               │                 │ POST signClient │
    │               │                 │ (Base64 DER)    │
    │               │                 │────────────────►│
    │               │                 │                 │
    │               │                 │ JSON response   │
    │               │                 │ {signedCert,    │
    │               │                 │  ca0, ca1...}   │
    │               │                 │◄────────────────│
    │               │                 │                 │
    │               │                 │ Store cert +    │
    │               │                 │ trust chain     │
    │               │                 │ in Keystore     │
    │               │                 │────────┐        │
    │               │                 │        │        │
    │               │                 │◄───────┘        │
    │               │                 │                 │
    │ Status: OK    │                 │ Auto-connect    │
    │◄──────────────│─────────────────│────────────────►│
    │               │                 │                 │
    │               │                 │ TLS + ClientCert│
    │               │                 │◄───────────────►│
    │               │                 │  Connected!     │
```

---

## Step-by-Step Implementation

### Step 1: Parse QR Code

```kotlin
data class EnrollmentParameters(
    val hostName: String = "",
    val serverURL: String = "",
    val serverPort: String = "",
    val protocol: String = "ssl",
    val username: String = "",
    val password: String = "",
    val shouldAutoSubmit: Boolean = false,
    val isValid: Boolean = true
)

object QRCodeParser {
    fun parse(scannedString: String): EnrollmentParameters {
        val trimmed = scannedString.trim()
        if (trimmed.isEmpty()) return EnrollmentParameters(isValid = false)

        return when {
            trimmed.split(",").size == 4 -> parseITAK(trimmed)
            trimmed.startsWith("{") -> parseATAK(trimmed)
            else -> EnrollmentParameters(isValid = false)
        }
    }

    private fun parseITAK(s: String): EnrollmentParameters {
        val parts = s.split(",")
        // Validate URL and port
        val port = parts[2].toIntOrNull() ?: return EnrollmentParameters(isValid = false)
        if (parts[1].isBlank()) return EnrollmentParameters(isValid = false)

        return EnrollmentParameters(
            hostName = parts[0],
            serverURL = parts[1],
            serverPort = parts[2],
            protocol = parts[3]
        )
    }

    private fun parseATAK(s: String): EnrollmentParameters {
        // Parse JSON, extract connectionString and userCredentials
        // Auto-submit if both username and password present
    }
}
```

### Step 2: Fetch CA Configuration

**Request:**
```
GET https://{serverURL}:{csrPort}/Marti/api/tls/config
Authorization: Basic {base64(username:password)}
Content-Type: text/plain; charset=utf-8
```

Default `csrPort` = `8446`

**Response:** XML
```xml
<certificateConfig>
  <nameEntries>
    <nameEntry name="O" value="TAK"/>
    <nameEntry name="OU" value="TAK-Server-CA"/>
  </nameEntries>
</certificateConfig>
```

Parse `O` (Organization) and `OU` (OrganizationalUnit) from `nameEntry` elements.

### Step 3: Generate RSA Keypair

Generate a 2048-bit RSA keypair in the Android Keystore:

```kotlin
val keyPairGenerator = KeyPairGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
)
val parameterSpec = KeyGenParameterSpec.Builder(
    "tak-client-key-${serverUrl}",
    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT
).apply {
    setKeySize(2048)
    setDigests(KeyProperties.DIGEST_SHA256)
    setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
}.build()

keyPairGenerator.initialize(parameterSpec)
val keyPair = keyPairGenerator.generateKeyPair()
```

### Step 4: Build Certificate Signing Request

Build a PKCS#10 CSR using Bouncy Castle (Android bundled or standalone):

```kotlin
val subject = X500Principal("CN=$username, O=$orgName, OU=$orgUnitName")
val csrBuilder = PKCS10CertificationRequestBuilder(
    JcaX500NameUtil.getX500Name(subject),
    keyPair.public
)
val signer = JcaContentSignerBuilder("SHA256WithRSA")
    .build(keyPair.private)
val csr = csrBuilder.build(signer)
val derEncoded = csr.encoded  // DER bytes
```

### Step 5: Submit CSR

**Request:**
```
POST https://{serverURL}:{csrPort}/Marti/api/tls/signClient/v2?clientUid={UID}&version={VERSION}
Authorization: Basic {base64(username:password)}
Content-Type: text/plain; charset=utf-8
Body: {base64(DER-encoded CSR)}
```

**Response:** JSON
```json
{
  "signedCert": "MIIDxTCCAq2gAw...",
  "ca0": "MIIFjTCCA3Wg...",
  "ca1": "MIIFkDCCA3ig..."
}
```

| Field | Description |
|-------|-------------|
| `signedCert` | Base64-encoded client certificate (PEM body without headers) |
| `ca0`, `ca1`, ... | CA chain certificates, Base64-encoded |

### Step 6: Store Certificates

```kotlin
// Decode and store client certificate
val certBytes = Base64.decode(signedCertString, Base64.DEFAULT)
val certFactory = CertificateFactory.getInstance("X.509")
val clientCert = certFactory.generateCertificate(ByteArrayInputStream(certBytes))

// Store in Android Keystore bound to the private key
val keyStore = KeyStore.getInstance("AndroidKeyStore")
keyStore.load(null)
keyStore.setCertificateEntry("tak-client-cert-$serverUrl", clientCert)

// Store CA trust chain
caCerts.forEachIndexed { index, caCertString ->
    val caBytes = Base64.decode(caCertString, Base64.DEFAULT)
    val caCert = certFactory.generateCertificate(ByteArrayInputStream(caBytes))
    keyStore.setCertificateEntry("tak-ca-$index-$serverUrl", caCert)
}
```

### Step 7: Auto-Connect

After successful certificate storage:
1. Save server URL and port to `SettingsRepository`
2. Clear username/password from memory (never persist)
3. Trigger `ConnectionManager.connect()` with TLS + client cert

---

## Enrollment Status States

```
NOT_STARTED → CONNECTING → CONFIGURING → ENROLLING → SUCCEEDED
                 │              │             │
                 └──────────────┴─────────────┴──► FAILED
                                                      │
                                              (with error reason)
```

| State | Description |
|-------|-------------|
| `NOT_STARTED` | Enrollment not initiated |
| `CONNECTING` | Reaching enrollment server |
| `CONFIGURING` | Fetching CA config (O, OU) |
| `ENROLLING` | CSR submitted, waiting for signed cert |
| `SUCCEEDED` | Certificate received and stored |
| `FAILED` | Error occurred (network, auth, parse, cert) |
| `UNTRUSTED` | Server SSL certificate not trusted |

---

## Error Handling

| Error | User Message | Recovery |
|-------|-------------|----------|
| Invalid QR format | "QR code does not contain valid connection info" | Retry scan |
| Network unreachable | "Cannot reach enrollment server" | Check network, retry |
| HTTP 401/403 | "Authentication failed - check credentials" | Re-scan QR with valid creds |
| HTTP 5xx | "Server error during enrollment" | Retry later |
| SSL error | "Server certificate not trusted" | Option to trust or re-configure |
| Malformed response | "Invalid response from server" | Contact server admin |
| Keystore error | "Cannot store certificate" | App restart, clear and retry |

---

## Security Considerations

1. **HTTPS only** for enrollment requests
2. **Basic Auth** credentials are used only during enrollment, then **cleared from memory**
3. Credentials are **never persisted** to disk
4. Private key **never leaves** Android Keystore
5. Server TLS certificate validation during enrollment: system trust store by default
6. Option to accept untrusted server certs for testing (user must explicitly enable)

---

## Ports Reference

| Port | Purpose | Default |
|------|---------|---------|
| CSR Port | Certificate enrollment API | 8446 |
| Streaming Port | TCP CoT streaming | 8089 |
| Secure API Port | HTTPS API | 8443 |
