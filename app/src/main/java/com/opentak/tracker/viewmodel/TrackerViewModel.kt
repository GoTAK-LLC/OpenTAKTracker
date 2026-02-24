package com.opentak.tracker.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opentak.tracker.cot.CotBuilder
import com.opentak.tracker.data.*
import com.opentak.tracker.enrollment.CSREnrollmentManager
import com.opentak.tracker.enrollment.QRCodeParser
import com.opentak.tracker.security.CertificateStore
import com.opentak.tracker.service.LocationManagerWrapper
import com.opentak.tracker.service.TrackerEngine
import com.opentak.tracker.service.TrackingForegroundService
import com.opentak.tracker.transport.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val settings: SettingsRepository,
    val logRepository: LogRepository,
    val connectionManager: ConnectionManager,
    val locationManager: LocationManagerWrapper,
    val enrollmentManager: CSREnrollmentManager,
    val certStore: CertificateStore,
    private val trackerEngine: TrackerEngine,
    private val cotBuilder: CotBuilder
) : ViewModel() {

    // UI state
    val location: StateFlow<LocationData> = locationManager.location
    val connectionState: StateFlow<ConnectionState> = connectionManager.state
    val lastTransmitTime: StateFlow<Long> = connectionManager.lastTransmitTime
    val lastError: StateFlow<String?> = connectionManager.lastError
    val enrollmentStatus: StateFlow<EnrollmentStatus> = enrollmentManager.status
    val enrollmentMessage: StateFlow<String> = enrollmentManager.statusMessage
    val logs: StateFlow<List<LogEntry>> = logRepository.logs

    // Multi-server state
    val serverConfigs: StateFlow<List<ServerConfig>> = settings.serverConfigs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val serverStates: StateFlow<Map<String, ConnectionState>> = connectionManager.serverStates
    val connectionSummary: StateFlow<String> = connectionManager.connectionSummary

    // Display toggles
    private val _coordinateFormat = MutableStateFlow(CoordinateFormat.DMS)
    val coordinateFormat: StateFlow<CoordinateFormat> = _coordinateFormat.asStateFlow()

    private val _speedUnit = MutableStateFlow(SpeedUnit.MPS)
    val speedUnit: StateFlow<SpeedUnit> = _speedUnit.asStateFlow()

    private val _headingUnit = MutableStateFlow(DirectionUnit.TN)
    val headingUnit: StateFlow<DirectionUnit> = _headingUnit.asStateFlow()

    private val _compassUnit = MutableStateFlow(DirectionUnit.MN)
    val compassUnit: StateFlow<DirectionUnit> = _compassUnit.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    // Lock state
    val lockPin: StateFlow<String> = settings.lockPin
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val isLocked: StateFlow<Boolean> = settings.isLocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Load persisted display preferences
        viewModelScope.launch {
            settings.coordinateFormat.collect { _coordinateFormat.value = it }
        }
        viewModelScope.launch {
            settings.speedUnit.collect { _speedUnit.value = it }
        }
    }

    // --- Tracking Controls ---

    fun startTracking() {
        val intent = Intent(context, TrackingForegroundService::class.java)
        context.startForegroundService(intent)
        _isTracking.value = true
        viewModelScope.launch { settings.setTrackingEnabled(true) }
    }

    fun stopTracking() {
        val intent = Intent(context, TrackingForegroundService::class.java).apply {
            action = TrackingForegroundService.ACTION_STOP
        }
        context.startService(intent)
        _isTracking.value = false
        viewModelScope.launch { settings.setTrackingEnabled(false) }
    }

    // --- Display Toggles ---

    fun cycleCoordinateFormat() {
        val next = when (_coordinateFormat.value) {
            CoordinateFormat.DMS -> CoordinateFormat.MGRS
            CoordinateFormat.MGRS -> CoordinateFormat.DECIMAL
            CoordinateFormat.DECIMAL -> CoordinateFormat.DMS
        }
        _coordinateFormat.value = next
        viewModelScope.launch { settings.setCoordinateFormat(next) }
    }

    fun cycleSpeedUnit() {
        val order = SpeedUnit.entries
        val current = order.indexOf(_speedUnit.value)
        val next = order[(current + 1) % order.size]
        _speedUnit.value = next
        viewModelScope.launch { settings.setSpeedUnit(next) }
    }

    fun toggleHeadingUnit() {
        _headingUnit.value = if (_headingUnit.value == DirectionUnit.TN) DirectionUnit.MN else DirectionUnit.TN
    }

    fun toggleCompassUnit() {
        _compassUnit.value = if (_compassUnit.value == DirectionUnit.TN) DirectionUnit.MN else DirectionUnit.TN
    }

    // --- Emergency ---

    fun activateEmergency(type: EmergencyType) {
        viewModelScope.launch {
            val location = locationManager.location.value
            if (!location.isValid) {
                logRepository.warn("Emergency", "No valid location, cannot send alert")
                return@launch
            }
            val uid = settings.deviceUid
            val callsign = settings.callsign.first()
            val cotXml = cotBuilder.buildEmergency(location, uid, callsign, type, false)
            connectionManager.send(cotXml)
            logRepository.info("Emergency", "Emergency alert sent: ${type.displayName}")
        }
    }

    fun cancelEmergency() {
        viewModelScope.launch {
            val location = locationManager.location.value
            if (!location.isValid) return@launch
            val uid = settings.deviceUid
            val callsign = settings.callsign.first()
            val cotXml = cotBuilder.buildEmergency(location, uid, callsign, EmergencyType.NineOneOne, true)
            connectionManager.send(cotXml)
            logRepository.info("Emergency", "Emergency cancel sent")
        }
    }

    // --- Enrollment ---

    fun parseQRCode(scannedString: String): EnrollmentParameters {
        return QRCodeParser.parse(scannedString)
    }

    fun beginEnrollment(params: EnrollmentParameters) {
        viewModelScope.launch {
            enrollmentManager.beginEnrollment(params)
            // Auto-connect after successful enrollment
            if (enrollmentManager.status.value == EnrollmentStatus.SUCCEEDED) {
                startTracking()
            }
        }
    }

    fun resetEnrollment() {
        enrollmentManager.reset()
    }

    // --- Settings ---

    fun updateCallsign(value: String) = viewModelScope.launch { settings.setCallsign(value) }
    fun updateTeam(value: String) = viewModelScope.launch { settings.setTeam(value) }
    fun updateRole(value: String) = viewModelScope.launch { settings.setRole(value) }
    fun updateBroadcastInterval(value: Long) = viewModelScope.launch { settings.setBroadcastInterval(value) }
    fun updateStaleTime(value: Long) = viewModelScope.launch { settings.setStaleTimeMinutes(value) }
    fun updateDynamicMode(enabled: Boolean) = viewModelScope.launch { settings.setDynamicModeEnabled(enabled) }
    fun updateUdpEnabled(enabled: Boolean) = viewModelScope.launch { settings.setUdpEnabled(enabled) }
    fun updateUdpAddress(value: String) = viewModelScope.launch { settings.setUdpAddress(value) }
    fun updateUdpPort(value: String) = viewModelScope.launch { settings.setUdpPort(value) }
    fun updateKeepScreenOn(value: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(value) }
    fun updateTrustAllCerts(value: Boolean) = viewModelScope.launch { settings.setTrustAllCerts(value) }
    fun updateStartOnBoot(value: Boolean) = viewModelScope.launch { settings.setStartOnBoot(value) }
    fun updateHardwareSOSEnabled(enabled: Boolean) = viewModelScope.launch { settings.setHardwareSOSEnabled(enabled) }
    fun updateAtakPauseEnabled(enabled: Boolean) = viewModelScope.launch { settings.setAtakPauseEnabled(enabled) }

    val isExternallyPaused: Boolean get() = trackerEngine.isExternallyPaused

    // --- Lock ---
    fun setLockPin(pin: String) = viewModelScope.launch { settings.setLockPin(pin) }
    fun lock() = viewModelScope.launch { settings.setIsLocked(true) }
    fun unlock(pin: String): Boolean {
        return if (pin == lockPin.value) {
            viewModelScope.launch { settings.setIsLocked(false) }
            true
        } else false
    }

    fun getCertificateInfo(serverUrl: String? = null): CertificateInfo? {
        val url = serverUrl ?: runCatching {
            kotlinx.coroutines.runBlocking { settings.serverUrl.first() }
        }.getOrNull() ?: return null
        if (url.isBlank()) return null
        return certStore.getClientCertificateInfo(url)
    }

    fun clearConnection() {
        viewModelScope.launch {
            val serverUrl = settings.serverUrl.first()
            if (serverUrl.isNotBlank()) {
                certStore.clearCertificates(serverUrl)
            }
            connectionManager.disconnect()
            settings.clearConnection()
            stopTracking()
        }
    }

    // --- Server Management ---

    fun removeServer(serverId: String) {
        viewModelScope.launch {
            val configs = settings.serverConfigs.first()
            val config = configs.find { it.id == serverId } ?: return@launch
            // Disconnect and remove client
            connectionManager.disconnectServer(serverId)
            // Clear certificates for this server
            certStore.clearCertificates(config.address)
            // Remove from persisted list
            settings.removeServerConfig(serverId)
        }
    }

    fun toggleServer(serverId: String) {
        viewModelScope.launch {
            settings.toggleServerEnabled(serverId)
            val configs = settings.serverConfigs.first()
            val config = configs.find { it.id == serverId } ?: return@launch
            if (!config.enabled) {
                // Was enabled, now disabled — disconnect
                connectionManager.disconnectServer(serverId)
            } else {
                // Was disabled, now enabled — connect if tracking
                if (_isTracking.value) {
                    connectionManager.connectServer(config)
                }
            }
        }
    }
}
