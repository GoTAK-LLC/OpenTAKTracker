package com.opentak.tracker.service

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.opentak.tracker.data.EmergencyType
import com.opentak.tracker.data.LogRepository
import com.opentak.tracker.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HardwareSOSDetector(
    private val context: Context,
    private val settings: SettingsRepository,
    private val logRepository: LogRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val REQUIRED_PRESSES = 5
        private const val WINDOW_MS = 3000L
        private const val COOLDOWN_MS = 10000L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastKnownVolume: Int = -1
    private val pressTimestamps = mutableListOf<Long>()
    private var lastTriggerTime: Long = 0
    private var running = false

    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            onSystemSettingChanged()
        }
    }

    fun start() {
        if (running) return
        running = true
        lastKnownVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        pressTimestamps.clear()
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver
        )
        logRepository.info("SOS", "Hardware SOS detector started")
    }

    fun stop() {
        if (!running) return
        running = false
        context.contentResolver.unregisterContentObserver(volumeObserver)
        pressTimestamps.clear()
        logRepository.info("SOS", "Hardware SOS detector stopped")
    }

    private fun onSystemSettingChanged() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVolume == lastKnownVolume) return
        lastKnownVolume = currentVolume

        val now = System.currentTimeMillis()

        // Cooldown after a trigger
        if (now - lastTriggerTime < COOLDOWN_MS) return

        pressTimestamps.add(now)

        // Evict timestamps outside the detection window
        pressTimestamps.removeAll { now - it > WINDOW_MS }

        if (pressTimestamps.size >= REQUIRED_PRESSES) {
            pressTimestamps.clear()
            lastTriggerTime = now
            onSOSTriggered()
        }
    }

    private fun onSOSTriggered() {
        scope.launch {
            val alreadyActive = settings.emergencyActive.first()
            if (alreadyActive) {
                logRepository.info("SOS", "Hardware SOS pressed but emergency already active")
                return@launch
            }

            logRepository.info("SOS", "Hardware SOS triggered! Activating 911 emergency")
            settings.setEmergencyActive(true)
            settings.setEmergencyType(EmergencyType.NineOneOne.name)

            vibrateConfirmation()
        }
    }

    private fun vibrateConfirmation() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 200, 100, 200, 100, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) {
            logRepository.error("SOS", "Could not vibrate: ${e.message}")
        }
    }
}
