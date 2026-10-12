package tech.dm.conso.data

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

interface AltitudeSource {
    val altitudeM: Double?
    fun start() = Unit
    fun stop() = Unit
}

class DeviceAltitudeSource(context: Context) : AltitudeSource, SensorEventListener, LocationListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)
    private val barometer = sensors.getDefaultSensor(Sensor.TYPE_PRESSURE)

    @Volatile private var baroAltitude: Double? = null
    @Volatile private var gpsAltitude: Double? = null

    val usesBarometer get() = barometer != null

    override val altitudeM: Double? get() = baroAltitude ?: gpsAltitude

    @SuppressLint("MissingPermission")
    override fun start() {
        if (barometer != null) {
            sensors.registerListener(this, barometer, SensorManager.SENSOR_DELAY_UI)
        } else {
            runCatching {
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            }
        }
    }

    override fun stop() {
        sensors.unregisterListener(this)
        runCatching { locations.removeUpdates(this) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]).toDouble()
        baroAltitude = baroAltitude?.let { it + (alt - it) * 0.1 } ?: alt
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onLocationChanged(location: Location) {
        if (location.hasAltitude()) gpsAltitude = location.altitude
    }
}

class GradeEstimator(private val windowM: Double = 60.0) {
    private val points = ArrayDeque<Pair<Double, Double>>()

    fun update(distanceM: Double, altitudeM: Double?): Double {
        if (altitudeM == null) return 0.0
        points.addLast(distanceM to altitudeM)
        while (points.size > 2 && distanceM - points[1].first >= windowM) points.removeFirst()
        val (d0, a0) = points.first()
        val span = distanceM - d0
        return if (span < windowM / 2) 0.0 else ((altitudeM - a0) / span).coerceIn(-0.2, 0.2)
    }
}
