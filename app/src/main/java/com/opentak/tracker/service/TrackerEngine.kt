package com.opentak.tracker.service

import android.location.Location
import com.opentak.tracker.cot.CotBuilder
import com.opentak.tracker.data.*
import com.opentak.tracker.transport.ConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerEngine @Inject constructor(
    private val locationManager: LocationManagerWrapper,
    private val connectionManager: ConnectionManager,
    private val cotBuilder: CotBuilder,
    private val settings: SettingsRepository,
    private val logRepository: LogRepository
) {
    private var timerJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastTransmitLocation: LocationData? = null
    private var lastTransmitTime: Long = 0

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var isExternallyPaused: Boolean = false

    fun start() {
        if (isRunning) return
        isRunning = true
        logRepository.info("Engine", "Tracker engine started")

        timerJob = scope.launch {
            while (isActive) {
                val interval = settings.broadcastInterval.first()
                delay(interval * 1000)
                transmit()
            }
        }
    }

    fun stop() {
        isRunning = false
        timerJob?.cancel()
        timerJob = null
        logRepository.info("Engine", "Tracker engine stopped")
    }

    private suspend fun transmit() {
        val location = locationManager.location.value
        if (!location.isValid) {
            logRepository.warn("Engine", "No valid location, skipping transmit")
            return
        }

        // ATAK pause check
        if (isExternallyPaused) {
            logRepository.info("Engine", "Paused (ATAK active), skipping transmit")
            return
        }

        // Dynamic mode check
        if (settings.dynamicModeEnabled.first()) {
            if (!shouldTransmit(location)) {
                return
            }
        }

        val uid = settings.deviceUid
        val callsign = settings.callsign.first()
        val team = settings.team.first()
        val role = settings.role.first()
        val staleMinutes = settings.staleTimeMinutes.first()

        val cotXml = cotBuilder.buildPLI(location, uid, callsign, team, role, staleMinutes)

        connectionManager.send(cotXml)
        lastTransmitLocation = location
        lastTransmitTime = System.currentTimeMillis()
    }

    private suspend fun shouldTransmit(location: LocationData): Boolean {
        val now = System.currentTimeMillis()
        val failsafe = settings.dynamicFailsafeSeconds.first() * 1000

        // Always transmit if failsafe time exceeded
        if (now - lastTransmitTime > failsafe) return true

        val last = lastTransmitLocation ?: return true

        // Check distance threshold
        val distThreshold = settings.dynamicDistanceThreshold.first()
        val results = FloatArray(1)
        Location.distanceBetween(
            last.latitude, last.longitude,
            location.latitude, location.longitude,
            results
        )
        if (results[0] > distThreshold) return true

        // Check speed threshold
        val speedThreshold = settings.dynamicSpeedThreshold.first()
        if (location.speed > speedThreshold) return true

        return false
    }
}
