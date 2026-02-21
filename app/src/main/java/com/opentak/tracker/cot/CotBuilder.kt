package com.opentak.tracker.cot

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.opentak.tracker.BuildConfig
import com.opentak.tracker.data.EmergencyType
import com.opentak.tracker.data.LocationData
import com.opentak.tracker.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CotBuilder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val timeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    fun buildPLI(
        location: LocationData,
        uid: String,
        callsign: String,
        team: String,
        role: String,
        staleTimeMinutes: Long
    ): String {
        val now = Instant.now()
        val time = timeFormatter.format(now)
        val stale = timeFormatter.format(now.plusSeconds(staleTimeMinutes * 60))

        return buildString {
            append("<event version=\"2.0\"")
            append(" uid=\"").append(escapeXml(uid)).append("\"")
            append(" type=\"").append(Constants.DEFAULT_COT_TYPE).append("\"")
            append(" how=\"").append(Constants.DEFAULT_COT_HOW).append("\"")
            append(" time=\"").append(time).append("\"")
            append(" start=\"").append(time).append("\"")
            append(" stale=\"").append(stale).append("\">")

            appendPoint(location)
            append("<detail>")
            append("<__group name=\"").append(escapeXml(team)).append("\" role=\"").append(escapeXml(role)).append("\" />")
            append("<contact callsign=\"").append(escapeXml(callsign)).append("\" />")
            append("<uid Droid=\"").append(escapeXml(callsign)).append("\" />")
            append("<precisionlocation altsrc=\"GPS\" geopointsrc=\"GPS\" />")
            append("<status battery=\"").append(getBatteryLevel()).append("\" />")
            append("<track speed=\"").append("%.1f".format(location.speed)).append("\" course=\"").append("%.1f".format(location.bearing)).append("\" />")
            append("<takv device=\"").append(escapeXml(Build.MODEL)).append("\"")
            append(" platform=\"").append(Constants.TAK_PLATFORM).append("\"")
            append(" os=\"Android ").append(Build.VERSION.RELEASE).append("\"")
            append(" version=\"").append(BuildConfig.VERSION_NAME).append("\" />")
            append("</detail>")
            append("</event>")
        }
    }

    fun buildEmergency(
        location: LocationData,
        uid: String,
        callsign: String,
        emergencyType: EmergencyType,
        cancel: Boolean
    ): String {
        val now = Instant.now()
        val time = timeFormatter.format(now)
        val stale = timeFormatter.format(now.plusSeconds(60))

        val cotType = if (cancel) emergencyType.cotType else emergencyType.cotType

        return buildString {
            append("<event version=\"2.0\"")
            append(" uid=\"").append(escapeXml(uid)).append("\"")
            append(" type=\"").append(cotType).append("\"")
            append(" how=\"").append(Constants.DEFAULT_COT_HOW).append("\"")
            append(" time=\"").append(time).append("\"")
            append(" start=\"").append(time).append("\"")
            append(" stale=\"").append(stale).append("\">")

            appendPoint(location)
            append("<detail>")
            append("<contact callsign=\"").append(escapeXml(callsign)).append("\" />")
            append("<emergency type=\"").append(escapeXml(emergencyType.displayName)).append("\"")
            append(" cancel=\"").append(cancel).append("\">")
            append(escapeXml(callsign))
            append("</emergency>")
            append("</detail>")
            append("</event>")
        }
    }

    private fun StringBuilder.appendPoint(location: LocationData) {
        append("<point")
        append(" lat=\"").append("%.6f".format(location.latitude)).append("\"")
        append(" lon=\"").append("%.6f".format(location.longitude)).append("\"")
        append(" hae=\"").append("%.1f".format(location.altitude)).append("\"")
        append(" ce=\"").append("%.1f".format(location.accuracy)).append("\"")
        append(" le=\"9999999\" />")
    }

    private fun getBatteryLevel(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0) (level * 100 / scale) else 0
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
