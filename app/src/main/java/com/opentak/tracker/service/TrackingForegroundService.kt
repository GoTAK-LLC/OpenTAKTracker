package com.opentak.tracker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.opentak.tracker.R
import com.opentak.tracker.data.LogRepository
import com.opentak.tracker.data.SettingsRepository
import com.opentak.tracker.transport.ConnectionManager
import com.opentak.tracker.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class TrackingForegroundService : Service() {

    @Inject lateinit var trackerEngine: TrackerEngine
    @Inject lateinit var connectionManager: ConnectionManager
    @Inject lateinit var locationManager: LocationManagerWrapper
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var logRepository: LogRepository

    private val binder = TrackingBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    inner class TrackingBinder : Binder() {
        val service: TrackingForegroundService get() = this@TrackingForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        logRepository.info("Service", "TrackingForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // If locked, open app for PIN entry instead of stopping
                scope.launch {
                    val locked = settings.isLocked.first()
                    if (locked) {
                        val launchIntent = Intent(
                            this@TrackingForegroundService,
                            com.opentak.tracker.ui.MainActivity::class.java
                        ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(launchIntent)
                    } else {
                        stopTracking()
                    }
                }
                return START_STICKY
            }
        }

        startForeground(
            Constants.TRACKING_NOTIFICATION_ID,
            buildNotification("Starting...", "Initializing"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        scope.launch {
            startTracking()
        }

        return START_STICKY
    }

    private suspend fun startTracking() {
        logRepository.info("Service", "Starting tracking")

        val interval = settings.broadcastInterval.first()
        locationManager.startLocationUpdates(interval, 0f)

        connectionManager.startNetworkMonitoring()
        connectionManager.connectAll()

        trackerEngine.start()

        // Update notification periodically
        scope.launch {
            while (isActive) {
                delay(5000)
                updateNotification()
            }
        }
    }

    private fun stopTracking() {
        logRepository.info("Service", "Stopping tracking")
        trackerEngine.stop()
        locationManager.stopLocationUpdates()
        connectionManager.disconnect()
        connectionManager.stopNetworkMonitoring()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun updateNotification() {
        val callsign = settings.callsign.first()
        val summary = connectionManager.connectionSummary.value
        val lastTx = connectionManager.lastTransmitTime.value
        val timeStr = if (lastTx > 0) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastTx))
        } else "—"

        val notification = buildNotification(callsign, "$summary | $timeStr")
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(Constants.TRACKING_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, status: String): Notification {
        val stopIntent = Intent(this, TrackingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val sosIntent = Intent(this, com.opentak.tracker.ui.MainActivity::class.java).apply {
            action = com.opentak.tracker.ui.MainActivity.ACTION_SOS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sosPending = PendingIntent.getActivity(
            this, 1, sosIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val launchPending = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setContentTitle(title)
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(launchPending)
            .addAction(android.R.drawable.ic_dialog_alert, "SOS", sosPending)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        logRepository.info("Service", "TrackingForegroundService destroyed")
    }

    companion object {
        const val ACTION_STOP = "com.opentak.tracker.STOP_TRACKING"
    }
}
