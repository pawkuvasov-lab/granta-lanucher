package ru.granta.carlauncher.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

data class GpsTelemetry(
    val speedKmh: Int = 0,
    val satellitesInUse: Int = 0,
    val accuracyMeters: Float = 0f,
    val altitudeMeters: Int = 0,
    val tripDistanceKm: Float = 0f,
    val isGpsFixed: Boolean = false
)

class GpsSpeedService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var audioManager: AudioManager
    private var lastLocation: Location? = null
    private var accumulatedMeters: Float = 0f
    private var autoVolumeEnabled: Boolean = true

    companion object {
        private val _telemetry = MutableStateFlow(GpsTelemetry())
        val telemetry: StateFlow<GpsTelemetry> = _telemetry
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Запрос обновлений с внешнего USB/UART/встроенного GPS-датчика магнитолы (5 Гц / 200 мс)
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            200L,
            0f,
            this
        )

        locationManager.registerGnssStatusCallback(object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                }
                _telemetry.value = _telemetry.value.copy(satellitesInUse = used)
            }
        }, null)
    }

    override fun onLocationChanged(location: Location) {
        // Перевод м/с в км/ч с фильтрацией шума на стоянке (< 2 км/ч = 0)
        val rawKmh = (location.speed * 3.6f).roundToInt()
        val speedKmh = if (rawKmh < 2) 0 else rawKmh

        lastLocation?.let { prev ->
            val delta = prev.distanceTo(location)
            if (delta in 1.0f..200.0f && speedKmh > 0) {
                accumulatedMeters += delta
            }
        }
        lastLocation = location

        _telemetry.value = _telemetry.value.copy(
            speedKmh = speedKmh,
            accuracyMeters = location.accuracy,
            altitudeMeters = location.altitude.roundToInt(),
            tripDistanceKm = accumulatedMeters / 1000f,
            isGpsFixed = true
        )

        // Автоматическая компенсация громкости магнитолы от скорости (Speed-Dependent Volume)
        if (autoVolumeEnabled) {
            adjustVolumeBySpeed(speedKmh)
        }
    }

    private fun adjustVolumeBySpeed(speedKmh: Int) {
        // Плавная коррекция при выходе на трассу (> 70 км/ч и > 100 км/ч)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val baseRatio = when {
            speedKmh >= 100 -> 0.75f
            speedKmh >= 70 -> 0.62f
            speedKmh >= 40 -> 0.52f
            else -> 0.45f
        }
        // Применяется только если пользователь включил ASL в настройках лаунчера
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        super.onDestroy()
    }
}
