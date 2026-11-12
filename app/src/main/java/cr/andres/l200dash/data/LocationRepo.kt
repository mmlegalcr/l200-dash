package cr.andres.l200dash.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GpsFix(
    val lat: Double,
    val lon: Double,
    /** km/h, o null si el GPS no reporta velocidad. */
    val speedKmh: Float?,
    val accuracyM: Float,
    /** SystemClock.elapsedRealtime() del momento del fix. */
    val at: Long,
    val fromGps: Boolean,
    /** Rumbo en grados (0 = norte), o null si está detenido o no hay dato. */
    val bearing: Float?,
    /** Altitud en metros sobre el nivel del mar, o null. */
    val altitudeM: Double?
)

/** GPS de la pantalla: posición (para clima) y velocidad (sin necesidad de OBD). */
object LocationRepo {
    private val _fix = MutableStateFlow<GpsFix?>(null)
    val fix: StateFlow<GpsFix?> = _fix

    private var started = false

    // En Android 10 los métodos onStatusChanged/onProviderEnabled/Disabled no tienen
    // implementación por defecto: hay que sobrescribirlos todos o el sistema puede fallar.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = publish(location)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (started) return
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val last = listOfNotNull(
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ).maxByOrNull { it.time }
            if (last != null) publish(last, stale = true)

            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            }
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 50f, listener, Looper.getMainLooper())
            }
            started = true
        } catch (_: Exception) {
        }
    }

    private fun publish(l: Location, stale: Boolean = false) {
        val isGps = l.provider == LocationManager.GPS_PROVIDER
        // Un fix de red no debe pisar uno de GPS reciente (es menos preciso y no trae velocidad).
        val current = _fix.value
        if (!isGps && current != null && current.fromGps && SystemClock.elapsedRealtime() - current.at < 10_000) return
        _fix.value = GpsFix(
            lat = l.latitude,
            lon = l.longitude,
            speedKmh = if (l.hasSpeed() && !stale) l.speed * 3.6f else null,
            accuracyM = l.accuracy,
            at = if (stale) 0L else SystemClock.elapsedRealtime(),
            fromGps = isGps,
            // El rumbo del GPS no sirve a baja velocidad: se conserva el último bueno.
            bearing = if (l.hasBearing() && l.hasSpeed() && l.speed > 1.5f) l.bearing else current?.bearing,
            altitudeM = if (l.hasAltitude()) l.altitude else current?.altitudeM
        )
    }
}
