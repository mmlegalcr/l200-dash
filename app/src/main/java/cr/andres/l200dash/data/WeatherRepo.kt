package cr.andres.l200dash.data

import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

data class DayForecast(val date: LocalDate, val code: Int, val maxC: Double, val minC: Double, val rainPct: Int)

data class Weather(
    val tempC: Double,
    val code: Int,
    val isDay: Boolean,
    val sunriseMs: Long,
    val sunsetMs: Long,
    val days: List<DayForecast>,
    val fetchedAt: Long
)

/** Clima de Open-Meteo (gratis, sin API key). */
object WeatherRepo {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    private var lastLat = 0.0
    private var lastLon = 0.0
    @Volatile private var loading = false

    /** Pide clima si no hay, si tiene más de 20 min, o si te moviste más de 10 km. */
    fun maybeRefresh(lat: Double, lon: Double) {
        val w = _weather.value
        val moved = FloatArray(1).also { Location.distanceBetween(lastLat, lastLon, lat, lon, it) }[0]
        val old = w == null || System.currentTimeMillis() - w.fetchedAt > 20 * 60_000
        if (loading || (!old && moved < 10_000)) return
        loading = true
        scope.launch {
            try {
                _weather.value = fetch(lat, lon)
                lastLat = lat
                lastLon = lon
            } catch (_: Exception) {
                // Sin internet: se mantiene el último dato y se reintenta en la próxima llamada.
            } finally {
                loading = false
            }
        }
    }

    private fun fetch(lat: Double, lon: Double): Weather {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
                    "&timezone=auto&forecast_days=4",
                lat, lon
            )
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val body = try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
        val json = JSONObject(body)
        val cur = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val zone = ZoneId.systemDefault()
        fun toMs(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

        val dates = daily.getJSONArray("time")
        val days = (0 until dates.length()).map { i ->
            DayForecast(
                date = LocalDate.parse(dates.getString(i)),
                code = daily.getJSONArray("weather_code").getInt(i),
                maxC = daily.getJSONArray("temperature_2m_max").getDouble(i),
                minC = daily.getJSONArray("temperature_2m_min").getDouble(i),
                rainPct = daily.getJSONArray("precipitation_probability_max").optInt(i, 0)
            )
        }
        return Weather(
            tempC = cur.getDouble("temperature_2m"),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            sunriseMs = toMs(daily.getJSONArray("sunrise").getString(0)),
            sunsetMs = toMs(daily.getJSONArray("sunset").getString(0)),
            days = days,
            fetchedAt = System.currentTimeMillis()
        )
    }

    /** Código WMO → (texto en español, emoji). */
    fun describe(code: Int, isDay: Boolean = true): Pair<String, String> = when (code) {
        0 -> "Despejado" to if (isDay) "☀️" else "🌙"
        1 -> "Mayormente despejado" to if (isDay) "🌤️" else "🌙"
        2 -> "Parcialmente nublado" to "⛅"
        3 -> "Nublado" to "☁️"
        45, 48 -> "Neblina" to "🌫️"
        51, 53, 55 -> "Llovizna" to "🌦️"
        56, 57 -> "Llovizna helada" to "🌧️"
        61 -> "Lluvia ligera" to "🌦️"
        63 -> "Lluvia" to "🌧️"
        65 -> "Lluvia fuerte" to "🌧️"
        66, 67 -> "Lluvia helada" to "🌧️"
        71, 73, 75, 77 -> "Nieve" to "❄️"
        80 -> "Aguaceros ligeros" to "🌦️"
        81 -> "Aguaceros" to "🌧️"
        82 -> "Aguaceros fuertes" to "⛈️"
        85, 86 -> "Nieve" to "❄️"
        95 -> "Tormenta" to "⛈️"
        96, 99 -> "Tormenta con granizo" to "⛈️"
        else -> "—" to "🌡️"
    }
}
