package com.opentak.tracker.service

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.opentak.tracker.data.LocationData
import com.opentak.tracker.data.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManagerWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: LogRepository
) : SensorEventListener {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _location = MutableStateFlow(LocationData())
    val location: StateFlow<LocationData> = _location.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var magneticHeading: Float = 0f
    private var smoothedHeading: Float = 0f
    private var headingInitialized = false
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Low-pass filter weight: lower = smoother but laggier (0.05–0.15 is good for compass)
    companion object {
        private const val ALPHA = 0.08f
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(intervalSeconds: Long, minDisplacementMeters: Float) {
        logRepository.info("Location", "Starting location updates (${intervalSeconds}s, ${minDisplacementMeters}m)")

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalSeconds * 1000)
            .setMinUpdateDistanceMeters(minDisplacementMeters)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { updateLocation(it) }
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())

        // Start heading sensor
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopLocationUpdates() {
        logRepository.info("Location", "Stopping location updates")
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        sensorManager.unregisterListener(this)
        headingInitialized = false
    }

    private fun updateLocation(location: Location) {
        _location.value = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = location.speed.coerceAtLeast(0f),
            bearing = location.bearing,
            accuracy = location.accuracy,
            magneticHeading = magneticHeading,
            trueHeading = location.bearing, // GPS bearing as true heading approximation
            timestamp = location.time,
            isValid = true
        )
    }

    // SensorEventListener for compass heading
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
            }
        }

        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val rawDeg = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360) % 360

            if (!headingInitialized) {
                smoothedHeading = rawDeg
                headingInitialized = true
            } else {
                // Angular difference that handles 359->1 wraparound
                var delta = rawDeg - smoothedHeading
                if (delta > 180) delta -= 360
                if (delta < -180) delta += 360
                smoothedHeading = (smoothedHeading + ALPHA * delta + 360) % 360
            }

            magneticHeading = smoothedHeading

            // Update location data with new heading
            val current = _location.value
            if (current.isValid) {
                _location.value = current.copy(magneticHeading = magneticHeading)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
