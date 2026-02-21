package com.opentak.tracker.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

// Location data from FusedLocationProvider
data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val accuracy: Float = 9999999f,
    val magneticHeading: Float = 0f,
    val trueHeading: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val isValid: Boolean = false
)

// Connection state machine
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SENDING,
    FAILED,
    RECONNECTING
}

// Enrollment status
enum class EnrollmentStatus {
    NOT_STARTED,
    CONNECTING,
    CONFIGURING,
    ENROLLING,
    SUCCEEDED,
    FAILED,
    UNTRUSTED
}

// QR parsing result
data class EnrollmentParameters(
    val hostName: String = "",
    val serverURL: String = "",
    val serverPort: String = "",
    val protocol: String = "ssl",
    val username: String = "",
    val password: String = "",
    val csrPort: String = "",
    val secureApiPort: String = "",
    val callsign: String = "",
    val team: String = "",
    val role: String = "",
    val shouldAutoSubmit: Boolean = false,
    val isValid: Boolean = true,
    val errorMessage: String = ""
)

// Per-server configuration
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val address: String = "",
    val port: Int = 8089,
    val protocol: String = "ssl",
    val enabled: Boolean = true,
    val csrPort: Int = 8446,
    val secureApiPort: Int = 8443,
    val trustAllCerts: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("address", address)
        put("port", port)
        put("protocol", protocol)
        put("enabled", enabled)
        put("csrPort", csrPort)
        put("secureApiPort", secureApiPort)
        put("trustAllCerts", trustAllCerts)
    }

    companion object {
        fun fromJson(json: JSONObject): ServerConfig = ServerConfig(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", ""),
            address = json.optString("address", ""),
            port = json.optInt("port", 8089),
            protocol = json.optString("protocol", "ssl"),
            enabled = json.optBoolean("enabled", true),
            csrPort = json.optInt("csrPort", 8446),
            secureApiPort = json.optInt("secureApiPort", 8443),
            trustAllCerts = json.optBoolean("trustAllCerts", false)
        )

        fun listToJson(configs: List<ServerConfig>): String {
            val array = JSONArray()
            configs.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(jsonString: String): List<ServerConfig> {
            if (jsonString.isBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonString)
                (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

// Log entry
data class LogEntry(
    val timestamp: Instant = Instant.now(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "",
    val message: String = ""
)

enum class LogLevel { INFO, WARN, ERROR }

// Team colors matching TAK ecosystem
enum class TeamColor(val displayName: String) {
    Cyan("Cyan"),
    White("White"),
    Yellow("Yellow"),
    Orange("Orange"),
    Magenta("Magenta"),
    Red("Red"),
    Maroon("Maroon"),
    Purple("Purple"),
    DarkBlue("Dark Blue"),
    Blue("Blue"),
    Teal("Teal"),
    Green("Green"),
    DarkGreen("Dark Green"),
    Brown("Brown")
}

// Team roles matching TAK ecosystem
enum class TeamRole(val displayName: String) {
    TeamMember("Team Member"),
    TeamLead("Team Lead"),
    HQ("HQ"),
    Sniper("Sniper"),
    Medic("Medic"),
    ForwardObserver("Forward Observer"),
    RTO("RTO"),
    K9("K9")
}

// Emergency types matching TAK ecosystem
enum class EmergencyType(val displayName: String, val cotType: String) {
    NineOneOne("911 Alert", "b-a-o-tbl"),
    RingTheBell("Ring the Bell", "b-a-o-can"),
    InContact("In Contact", "b-a-o-pan"),
    Cancel("Cancel", "")
}

// Coordinate display format
enum class CoordinateFormat { DMS, MGRS, DECIMAL }

// Speed display unit
enum class SpeedUnit(val label: String) {
    MPS("m/s"),
    KPH("km/h"),
    FPS("fps"),
    MPH("mph")
}

// Direction unit
enum class DirectionUnit(val label: String) {
    TN("TN"),
    MN("MN")
}

// Certificate info for display
data class CertificateInfo(
    val subject: String = "",
    val issuer: String = "",
    val expiresAt: Instant? = null,
    val fingerprint: String = "",
    val isExpiringSoon: Boolean = false,
    val isExpired: Boolean = false
)
