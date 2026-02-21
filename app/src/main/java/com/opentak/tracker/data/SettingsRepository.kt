package com.opentak.tracker.data

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.opentak.tracker.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // Keys
    private object Keys {
        val CALLSIGN = stringPreferencesKey("callsign")
        val TEAM = stringPreferencesKey("team")
        val ROLE = stringPreferencesKey("role")
        val SERVER_URL = stringPreferencesKey("server_url")
        val SERVER_PORT = stringPreferencesKey("server_port")
        val CSR_PORT = stringPreferencesKey("csr_port")
        val SECURE_API_PORT = stringPreferencesKey("secure_api_port")
        val BROADCAST_INTERVAL = longPreferencesKey("broadcast_interval")
        val STALE_TIME_MINUTES = longPreferencesKey("stale_time_minutes")
        val DYNAMIC_MODE_ENABLED = booleanPreferencesKey("dynamic_mode_enabled")
        val DYNAMIC_DISTANCE_THRESHOLD = floatPreferencesKey("dynamic_distance_threshold")
        val DYNAMIC_SPEED_THRESHOLD = floatPreferencesKey("dynamic_speed_threshold")
        val DYNAMIC_FAILSAFE_SECONDS = longPreferencesKey("dynamic_failsafe_seconds")
        val UDP_ENABLED = booleanPreferencesKey("udp_enabled")
        val UDP_ADDRESS = stringPreferencesKey("udp_address")
        val UDP_PORT = stringPreferencesKey("udp_port")
        val COORDINATE_FORMAT = stringPreferencesKey("coordinate_format")
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val MAP_TYPE = intPreferencesKey("map_type")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val EMERGENCY_ACTIVE = booleanPreferencesKey("emergency_active")
        val EMERGENCY_TYPE = stringPreferencesKey("emergency_type")
        val TRUST_ALL_CERTS = booleanPreferencesKey("trust_all_certs")
        val SERVER_CONFIGS_JSON = stringPreferencesKey("server_configs_json")
        val LOCK_PIN = stringPreferencesKey("lock_pin")
        val IS_LOCKED = booleanPreferencesKey("is_locked")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val HARDWARE_SOS_ENABLED = booleanPreferencesKey("hardware_sos_enabled")
    }

    // Device UID - stable per device
    val deviceUid: String by lazy {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        "OpenTAK-$androidId"
    }

    // Default callsign from device ID
    private val defaultCallsign: String by lazy {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        "TRACKER-${androidId.take(8).uppercase()}"
    }

    // Flows for reactive reads
    val callsign: Flow<String> = dataStore.data.map { it[Keys.CALLSIGN] ?: defaultCallsign }
    val team: Flow<String> = dataStore.data.map { it[Keys.TEAM] ?: TeamColor.Cyan.displayName }
    val role: Flow<String> = dataStore.data.map { it[Keys.ROLE] ?: TeamRole.TeamMember.displayName }
    val serverUrl: Flow<String> = dataStore.data.map { it[Keys.SERVER_URL] ?: "" }
    val serverPort: Flow<String> = dataStore.data.map { it[Keys.SERVER_PORT] ?: Constants.DEFAULT_STREAMING_PORT }
    val csrPort: Flow<String> = dataStore.data.map { it[Keys.CSR_PORT] ?: Constants.DEFAULT_CSR_PORT }
    val secureApiPort: Flow<String> = dataStore.data.map { it[Keys.SECURE_API_PORT] ?: Constants.DEFAULT_SECURE_API_PORT }
    val broadcastInterval: Flow<Long> = dataStore.data.map { it[Keys.BROADCAST_INTERVAL] ?: Constants.DEFAULT_BROADCAST_INTERVAL_SECONDS }
    val staleTimeMinutes: Flow<Long> = dataStore.data.map { it[Keys.STALE_TIME_MINUTES] ?: Constants.DEFAULT_STALE_TIME_MINUTES }
    val dynamicModeEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.DYNAMIC_MODE_ENABLED] ?: false }
    val dynamicDistanceThreshold: Flow<Float> = dataStore.data.map { it[Keys.DYNAMIC_DISTANCE_THRESHOLD] ?: Constants.DEFAULT_DYNAMIC_DISTANCE_THRESHOLD_METERS }
    val dynamicSpeedThreshold: Flow<Float> = dataStore.data.map { it[Keys.DYNAMIC_SPEED_THRESHOLD] ?: Constants.DEFAULT_DYNAMIC_SPEED_THRESHOLD_MPS }
    val dynamicFailsafeSeconds: Flow<Long> = dataStore.data.map { it[Keys.DYNAMIC_FAILSAFE_SECONDS] ?: Constants.DEFAULT_DYNAMIC_FAILSAFE_SECONDS }
    val udpEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.UDP_ENABLED] ?: true }
    val udpAddress: Flow<String> = dataStore.data.map { it[Keys.UDP_ADDRESS] ?: Constants.DEFAULT_UDP_ADDRESS }
    val udpPort: Flow<String> = dataStore.data.map { it[Keys.UDP_PORT] ?: Constants.DEFAULT_UDP_PORT }
    val coordinateFormat: Flow<CoordinateFormat> = dataStore.data.map {
        try { CoordinateFormat.valueOf(it[Keys.COORDINATE_FORMAT] ?: "DMS") } catch (_: Exception) { CoordinateFormat.DMS }
    }
    val speedUnit: Flow<SpeedUnit> = dataStore.data.map {
        try { SpeedUnit.valueOf(it[Keys.SPEED_UNIT] ?: "MPS") } catch (_: Exception) { SpeedUnit.MPS }
    }
    val mapType: Flow<Int> = dataStore.data.map { it[Keys.MAP_TYPE] ?: 1 } // MAP_TYPE_NORMAL
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { it[Keys.KEEP_SCREEN_ON] ?: true }
    val trackingEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.TRACKING_ENABLED] ?: false }
    val emergencyActive: Flow<Boolean> = dataStore.data.map { it[Keys.EMERGENCY_ACTIVE] ?: false }
    val emergencyType: Flow<String> = dataStore.data.map { it[Keys.EMERGENCY_TYPE] ?: "" }
    val trustAllCerts: Flow<Boolean> = dataStore.data.map { it[Keys.TRUST_ALL_CERTS] ?: false }
    val lockPin: Flow<String> = dataStore.data.map { it[Keys.LOCK_PIN] ?: "" }
    val isLocked: Flow<Boolean> = dataStore.data.map { it[Keys.IS_LOCKED] ?: false }
    val startOnBoot: Flow<Boolean> = dataStore.data.map { it[Keys.START_ON_BOOT] ?: false }
    val hardwareSOSEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.HARDWARE_SOS_ENABLED] ?: false }

    // Server configs with migration from legacy single-server keys
    val serverConfigs: Flow<List<ServerConfig>> = dataStore.data.map { prefs ->
        val json = prefs[Keys.SERVER_CONFIGS_JSON]
        if (json != null) {
            ServerConfig.listFromJson(json)
        } else {
            // Migrate from legacy single-server settings
            val legacyUrl = prefs[Keys.SERVER_URL] ?: ""
            if (legacyUrl.isNotBlank()) {
                val legacyPort = prefs[Keys.SERVER_PORT] ?: Constants.DEFAULT_STREAMING_PORT
                val legacyCsrPort = prefs[Keys.CSR_PORT] ?: Constants.DEFAULT_CSR_PORT
                val legacySecureApiPort = prefs[Keys.SECURE_API_PORT] ?: Constants.DEFAULT_SECURE_API_PORT
                val legacyTrustAll = prefs[Keys.TRUST_ALL_CERTS] ?: false
                listOf(
                    ServerConfig(
                        name = legacyUrl,
                        address = legacyUrl,
                        port = legacyPort.toIntOrNull() ?: 8089,
                        csrPort = legacyCsrPort.toIntOrNull() ?: 8446,
                        secureApiPort = legacySecureApiPort.toIntOrNull() ?: 8443,
                        trustAllCerts = legacyTrustAll
                    )
                )
            } else {
                emptyList()
            }
        }
    }

    // Write operations
    suspend fun setCallsign(value: String) { dataStore.edit { it[Keys.CALLSIGN] = value } }
    suspend fun setTeam(value: String) { dataStore.edit { it[Keys.TEAM] = value } }
    suspend fun setRole(value: String) { dataStore.edit { it[Keys.ROLE] = value } }
    suspend fun setServerUrl(value: String) { dataStore.edit { it[Keys.SERVER_URL] = value } }
    suspend fun setServerPort(value: String) { dataStore.edit { it[Keys.SERVER_PORT] = value } }
    suspend fun setCsrPort(value: String) { dataStore.edit { it[Keys.CSR_PORT] = value } }
    suspend fun setSecureApiPort(value: String) { dataStore.edit { it[Keys.SECURE_API_PORT] = value } }
    suspend fun setBroadcastInterval(value: Long) { dataStore.edit { it[Keys.BROADCAST_INTERVAL] = value } }
    suspend fun setStaleTimeMinutes(value: Long) { dataStore.edit { it[Keys.STALE_TIME_MINUTES] = value } }
    suspend fun setDynamicModeEnabled(value: Boolean) { dataStore.edit { it[Keys.DYNAMIC_MODE_ENABLED] = value } }
    suspend fun setDynamicDistanceThreshold(value: Float) { dataStore.edit { it[Keys.DYNAMIC_DISTANCE_THRESHOLD] = value } }
    suspend fun setDynamicSpeedThreshold(value: Float) { dataStore.edit { it[Keys.DYNAMIC_SPEED_THRESHOLD] = value } }
    suspend fun setDynamicFailsafeSeconds(value: Long) { dataStore.edit { it[Keys.DYNAMIC_FAILSAFE_SECONDS] = value } }
    suspend fun setUdpEnabled(value: Boolean) { dataStore.edit { it[Keys.UDP_ENABLED] = value } }
    suspend fun setUdpAddress(value: String) { dataStore.edit { it[Keys.UDP_ADDRESS] = value } }
    suspend fun setUdpPort(value: String) { dataStore.edit { it[Keys.UDP_PORT] = value } }
    suspend fun setCoordinateFormat(value: CoordinateFormat) { dataStore.edit { it[Keys.COORDINATE_FORMAT] = value.name } }
    suspend fun setSpeedUnit(value: SpeedUnit) { dataStore.edit { it[Keys.SPEED_UNIT] = value.name } }
    suspend fun setMapType(value: Int) { dataStore.edit { it[Keys.MAP_TYPE] = value } }
    suspend fun setKeepScreenOn(value: Boolean) { dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value } }
    suspend fun setTrackingEnabled(value: Boolean) { dataStore.edit { it[Keys.TRACKING_ENABLED] = value } }
    suspend fun setEmergencyActive(value: Boolean) { dataStore.edit { it[Keys.EMERGENCY_ACTIVE] = value } }
    suspend fun setEmergencyType(value: String) { dataStore.edit { it[Keys.EMERGENCY_TYPE] = value } }
    suspend fun setTrustAllCerts(value: Boolean) { dataStore.edit { it[Keys.TRUST_ALL_CERTS] = value } }
    suspend fun setLockPin(value: String) { dataStore.edit { it[Keys.LOCK_PIN] = value } }
    suspend fun setIsLocked(value: Boolean) { dataStore.edit { it[Keys.IS_LOCKED] = value } }
    suspend fun setStartOnBoot(value: Boolean) { dataStore.edit { it[Keys.START_ON_BOOT] = value } }
    suspend fun setHardwareSOSEnabled(value: Boolean) { dataStore.edit { it[Keys.HARDWARE_SOS_ENABLED] = value } }

    suspend fun clearConnection() {
        dataStore.edit {
            it.remove(Keys.SERVER_URL)
            it.remove(Keys.SERVER_PORT)
        }
    }

    // --- Server Config CRUD ---

    suspend fun addServerConfig(config: ServerConfig) {
        val current = serverConfigs.first().toMutableList()
        current.add(config)
        saveServerConfigs(current)
    }

    suspend fun removeServerConfig(serverId: String) {
        val current = serverConfigs.first().toMutableList()
        current.removeAll { it.id == serverId }
        saveServerConfigs(current)
    }

    suspend fun updateServerConfig(config: ServerConfig) {
        val current = serverConfigs.first().toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            current[index] = config
            saveServerConfigs(current)
        }
    }

    suspend fun toggleServerEnabled(serverId: String) {
        val current = serverConfigs.first().toMutableList()
        val index = current.indexOfFirst { it.id == serverId }
        if (index >= 0) {
            current[index] = current[index].copy(enabled = !current[index].enabled)
            saveServerConfigs(current)
        }
    }

    private suspend fun saveServerConfigs(configs: List<ServerConfig>) {
        dataStore.edit {
            it[Keys.SERVER_CONFIGS_JSON] = ServerConfig.listToJson(configs)
        }
    }
}
