package com.opentak.tracker.util

import com.opentak.tracker.data.SpeedUnit
import kotlin.math.abs
import kotlin.math.floor

object CoordinateConverter {

    // --- DMS Conversion ---
    fun latToDMS(latitude: Double): String {
        val direction = if (latitude >= 0) "N" else "S"
        return "$direction  ${toUnsignedDMS(latitude)}"
    }

    fun lonToDMS(longitude: Double): String {
        val direction = if (longitude >= 0) "E" else "W"
        return "$direction  ${toUnsignedDMS(longitude)}"
    }

    private fun toUnsignedDMS(value: Double): String {
        val abs = abs(value)
        val degrees = floor(abs)
        val minutes = floor(60 * (abs - degrees))
        val seconds = 3600 * (abs - degrees) - 60 * minutes
        return "%02.0f° %02.0f' %06.3f\"".format(degrees, minutes, seconds)
    }

    // --- Decimal ---
    fun latToDecimal(latitude: Double): String = "%.6f".format(latitude)
    fun lonToDecimal(longitude: Double): String = "%.6f".format(longitude)

    // --- MGRS (simplified - full MGRS requires a library) ---
    fun toMGRS(latitude: Double, longitude: Double): String {
        // Simplified UTM/MGRS conversion
        // For production, use mil-java or similar library
        val zone = ((longitude + 180) / 6).toInt() + 1
        val bandLetters = "CDEFGHJKLMNPQRSTUVWX"
        val bandIndex = ((latitude + 80) / 8).toInt().coerceIn(0, bandLetters.length - 1)
        val band = bandLetters[bandIndex]

        // Simplified easting/northing (approximate)
        val lonRef = (zone - 1) * 6 - 180 + 3.0
        val easting = ((longitude - lonRef) * 111320 * kotlin.math.cos(Math.toRadians(latitude)) + 500000).toInt()
        val northing = (latitude * 110574).toInt().let { if (it < 0) it + 10000000 else it }

        return "%02d%c %05d %05d".format(zone, band, easting % 100000, northing % 100000)
    }

    // --- Speed Conversion ---
    fun convertSpeed(metersPerSecond: Float, unit: SpeedUnit): String {
        val value = when (unit) {
            SpeedUnit.MPS -> metersPerSecond.toDouble()
            SpeedUnit.KPH -> metersPerSecond * 3.6
            SpeedUnit.FPS -> metersPerSecond * 3.28084
            SpeedUnit.MPH -> metersPerSecond * 2.23694
        }
        return if (value < 0) "0" else "%.0f".format(value)
    }

    // --- Format helpers ---
    fun formatHeading(degrees: Float): String = "%.0f°".format(degrees)
}
