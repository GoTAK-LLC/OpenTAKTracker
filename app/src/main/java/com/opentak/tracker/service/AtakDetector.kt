package com.opentak.tracker.service

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.opentak.tracker.data.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AtakDetector(
    private val context: Context,
    private val trackerEngine: TrackerEngine,
    private val logRepository: LogRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val POLL_INTERVAL_MS = 5000L
        private val ATAK_PACKAGES = setOf(
            "com.atakmap.app.civ",
            "com.atakmap.app"
        )
    }

    private var pollJob: Job? = null
    private var wasAtakActive = false

    fun start() {
        if (pollJob != null) return

        if (!hasUsageAccess()) {
            logRepository.warn("ATAK", "Usage access not granted, ATAK detection disabled")
            return
        }

        logRepository.info("ATAK", "ATAK detector started")
        pollJob = scope.launch {
            while (isActive) {
                val atakActive = isAtakInForeground()

                if (atakActive != wasAtakActive) {
                    wasAtakActive = atakActive
                    trackerEngine.isExternallyPaused = atakActive
                    if (atakActive) {
                        logRepository.info("ATAK", "ATAK detected in foreground, pausing transmission")
                    } else {
                        logRepository.info("ATAK", "ATAK no longer in foreground, resuming transmission")
                    }
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        trackerEngine.isExternallyPaused = false
        wasAtakActive = false
        logRepository.info("ATAK", "ATAK detector stopped")
    }

    private fun isAtakInForeground(): Boolean {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - POLL_INTERVAL_MS,
                now
            )
            val recentApp = stats.maxByOrNull { it.lastTimeUsed }
            recentApp?.packageName in ATAK_PACKAGES
        } catch (e: Exception) {
            false
        }
    }

    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
}
