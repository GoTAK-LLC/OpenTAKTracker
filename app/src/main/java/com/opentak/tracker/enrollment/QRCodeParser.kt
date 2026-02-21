package com.opentak.tracker.enrollment

import android.net.Uri
import com.opentak.tracker.data.EnrollmentParameters
import com.opentak.tracker.util.Constants
import org.json.JSONObject

object QRCodeParser {

    fun parse(scannedString: String): EnrollmentParameters {
        val trimmed = scannedString.trim()
        if (trimmed.isEmpty()) {
            return EnrollmentParameters(isValid = false, errorMessage = "Empty QR code")
        }

        return when {
            // TAK URI: tak://com.atakmap.app/enroll?... or tak://com.atakmap.app/import?...
            trimmed.startsWith("tak://") -> parseTakUri(trimmed)
            // OpenTAK Tracker URI: opentaktracker://enroll?...
            trimmed.startsWith("opentaktracker://") -> parseTakUri(trimmed)
            // iTAK format: "serverName,serverURL,serverPort,protocol"
            trimmed.split(",").size == 4 -> parseITAK(trimmed)
            // ATAK JSON format
            trimmed.startsWith("{") -> parseATAK(trimmed)
            else -> EnrollmentParameters(isValid = false, errorMessage = "Unrecognized QR code format")
        }
    }

    private fun parseTakUri(raw: String): EnrollmentParameters {
        return try {
            val uri = Uri.parse(raw)
            val path = uri.path ?: ""
            val host = uri.host ?: ""
            // For tak://com.atakmap.app/enroll the action is in the path.
            // For opentaktracker://enroll?... the action is the host.
            val action = if (path.isNotBlank()) path else host

            when {
                action.contains("enroll") -> parseTakEnroll(uri)
                action.contains("import") -> parseTakImport(uri)
                else -> EnrollmentParameters(
                    isValid = false,
                    errorMessage = "Unknown URI action: $action"
                )
            }
        } catch (e: Exception) {
            EnrollmentParameters(isValid = false, errorMessage = "Invalid URI: ${e.message}")
        }
    }

    private fun parseTakEnroll(uri: Uri): EnrollmentParameters {
        val host = uri.getQueryParameter("host")
        if (host.isNullOrBlank()) {
            return EnrollmentParameters(isValid = false, errorMessage = "Missing 'host' parameter")
        }

        val username = uri.getQueryParameter("username") ?: ""
        // Support both "token" and "password" parameters
        val credential = uri.getQueryParameter("token")
            ?: uri.getQueryParameter("password")
            ?: ""

        val port = uri.getQueryParameter("port") ?: Constants.DEFAULT_STREAMING_PORT
        val csrPort = uri.getQueryParameter("csrPort") ?: ""
        val secureApiPort = uri.getQueryParameter("secureApiPort") ?: ""
        val callsign = uri.getQueryParameter("callsign") ?: ""
        val team = uri.getQueryParameter("team") ?: ""
        val role = uri.getQueryParameter("role") ?: ""

        if (username.isBlank() || credential.isBlank()) {
            // No credentials — just fill in the host, user enters creds manually
            return EnrollmentParameters(
                serverURL = host,
                serverPort = port,
                protocol = "ssl",
                csrPort = csrPort,
                secureApiPort = secureApiPort,
                callsign = callsign,
                team = team,
                role = role
            )
        }

        // Token/password serves as password for Basic Auth during enrollment
        return EnrollmentParameters(
            serverURL = host,
            serverPort = port,
            protocol = "ssl",
            username = username,
            password = credential,
            csrPort = csrPort,
            secureApiPort = secureApiPort,
            callsign = callsign,
            team = team,
            role = role,
            shouldAutoSubmit = true
        )
    }

    private fun parseTakImport(uri: Uri): EnrollmentParameters {
        // import URLs contain a direct download URL for a data package
        // We can't handle .p12 import directly, but extract the host
        val url = uri.getQueryParameter("url") ?: ""
        if (url.isBlank()) {
            return EnrollmentParameters(isValid = false, errorMessage = "Missing 'url' parameter in import URI")
        }

        // Try to extract the host from the download URL
        return try {
            val downloadUri = Uri.parse(url)
            val host = downloadUri.host ?: ""
            EnrollmentParameters(
                serverURL = host,
                serverPort = Constants.DEFAULT_STREAMING_PORT,
                protocol = "ssl"
            )
        } catch (_: Exception) {
            EnrollmentParameters(isValid = false, errorMessage = "Invalid download URL in import URI")
        }
    }

    private fun parseITAK(raw: String): EnrollmentParameters {
        val parts = raw.split(",")
        if (parts.size != 4) {
            return EnrollmentParameters(isValid = false, errorMessage = "Invalid iTAK QR format")
        }

        val serverName = parts[0].trim()
        val serverURL = parts[1].trim()
        val serverPort = parts[2].trim()
        val protocol = parts[3].trim()

        if (serverURL.isBlank()) {
            return EnrollmentParameters(isValid = false, errorMessage = "Server URL is empty")
        }
        if (serverPort.toIntOrNull() == null) {
            return EnrollmentParameters(isValid = false, errorMessage = "Invalid port number")
        }

        return EnrollmentParameters(
            hostName = serverName,
            serverURL = serverURL,
            serverPort = serverPort,
            protocol = protocol
        )
    }

    private fun parseATAK(raw: String): EnrollmentParameters {
        return try {
            val json = JSONObject(raw)

            val serverCreds = json.optJSONObject("serverCredentials")
                ?: return EnrollmentParameters(isValid = false, errorMessage = "Missing serverCredentials")

            val connectionString = serverCreds.optString("connectionString", "")
            if (connectionString.isBlank()) {
                return EnrollmentParameters(isValid = false, errorMessage = "Missing connectionString")
            }

            val connParts = connectionString.split(":")
            if (connParts.size < 2) {
                return EnrollmentParameters(isValid = false, errorMessage = "Invalid connectionString format")
            }

            val serverURL = connParts[0]
            val serverPort = connParts[1]

            if (serverURL.isBlank()) {
                return EnrollmentParameters(isValid = false, errorMessage = "Server URL is empty")
            }
            if (serverPort.toIntOrNull() == null) {
                return EnrollmentParameters(isValid = false, errorMessage = "Invalid port number")
            }

            val protocol = if (connParts.size > 2) connParts[2] else "ssl"

            // Optional user credentials
            val userCreds = json.optJSONObject("userCredentials")
            val username = userCreds?.optString("username", "") ?: ""
            val password = userCreds?.optString("password", "") ?: ""
            val shouldAutoSubmit = username.isNotBlank() && password.isNotBlank()

            EnrollmentParameters(
                serverURL = serverURL,
                serverPort = serverPort,
                protocol = protocol,
                username = username,
                password = password,
                shouldAutoSubmit = shouldAutoSubmit
            )
        } catch (e: Exception) {
            EnrollmentParameters(isValid = false, errorMessage = "Invalid JSON: ${e.message}")
        }
    }
}
