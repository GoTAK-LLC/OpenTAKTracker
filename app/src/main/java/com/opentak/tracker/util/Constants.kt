package com.opentak.tracker.util

object Constants {
    // Platform
    const val TAK_PLATFORM = "OpenTAK-Tracker-Android"

    // Default Ports
    const val DEFAULT_STREAMING_PORT = "8089"
    const val DEFAULT_CSR_PORT = "8446"
    const val DEFAULT_SECURE_API_PORT = "8443"
    const val DEFAULT_UDP_PORT = "6969"

    // Default UDP
    const val DEFAULT_UDP_ADDRESS = "239.2.3.1"

    // CoT Defaults
    const val DEFAULT_COT_TYPE = "a-f-G-U-C"
    const val DEFAULT_COT_HOW = "m-g"
    const val DEFAULT_STALE_TIME_MINUTES = 5L
    const val DEFAULT_BROADCAST_INTERVAL_SECONDS = 10L
    const val EMERGENCY_BROADCAST_INTERVAL_SECONDS = 3L

    // Dynamic Mode Defaults
    const val DEFAULT_DYNAMIC_DISTANCE_THRESHOLD_METERS = 10f
    const val DEFAULT_DYNAMIC_SPEED_THRESHOLD_MPS = 1f
    const val DEFAULT_DYNAMIC_FAILSAFE_SECONDS = 60L

    // TAK Server API Paths
    const val CERT_CONFIG_PATH = "/Marti/api/tls/config"
    const val CSR_PATH = "/Marti/api/tls/signClient/v2"

    // Reconnect
    const val RECONNECT_BASE_DELAY_MS = 1000L
    const val RECONNECT_MAX_DELAY_MS = 60_000L
    const val RECONNECT_MULTIPLIER = 2.0

    // Logging
    const val LOG_BUFFER_SIZE = 200

    // Notification
    const val TRACKING_NOTIFICATION_ID = 1
}
