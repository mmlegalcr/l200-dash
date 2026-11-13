package cr.andres.l200dash

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.DayNightMode
import cr.andres.l200dash.data.LocationRepo
import cr.andres.l200dash.data.MediaRepo
import cr.andres.l200dash.data.StyleRepo
import cr.andres.l200dash.data.WeatherRepo
import cr.andres.l200dash.ui.DashTheme
import cr.andres.l200dash.ui.HomeScreen
import cr.andres.l200dash.ui.lookOf
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    /** Se usa si todavía no hay ninguna ubicación (San José). */
    private val fallbackLat = 9.9281
    private val fallbackLon = -84.0907

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestMissingPermissions()
        setContent { DashRoot() }
    }

    override fun onResume() {
        super.onResume()
        LocationRepo.start(this)
        MediaRepo.start(this)
        AppsRepo.refresh()
    }

    private fun requestMissingPermissions() {
        val needed = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 10)
    }

    // No hace falta onRequestPermissionsResult: al cerrar el diálogo de permisos la
    // actividad vuelve a onResume(), que arranca el GPS.

    @Composable
    private fun DashRoot() {
        var now by remember { mutableStateOf(LocalDateTime.now()) }
        var drawerOpen by remember { mutableStateOf(false) }
        var settingsOpen by remember { mutableStateOf(false) }
        val weather by WeatherRepo.weather.collectAsState()
        val fix by LocationRepo.fix.collectAsState()
        val mode by StyleRepo.mode.collectAsState()
        val dayStyle by StyleRepo.day.collectAsState()
        val nightStyle by StyleRepo.night.collectAsState()

        LaunchedEffect(Unit) {
            while (true) {
                now = LocalDateTime.now()
                delay(1000L - System.currentTimeMillis() % 1000L)
            }
        }

        // Clima: con la ubicación del GPS si la hay; si no, San José como aproximación.
        LaunchedEffect(fix?.lat, fix?.lon, now.minute) {
            val f = fix
            if (f != null) WeatherRepo.maybeRefresh(f.lat, f.lon)
            else WeatherRepo.maybeRefresh(fallbackLat, fallbackLon)
        }

        // Noche automática = el próximo evento solar es el amanecer. Sin clima: 18:00–6:00.
        val autoNight = run {
            val w = weather
            if (w != null) {
                val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dayMs = 24 * 3600_000L
                val sr = Math.floorMod(w.sunriseMs - nowMs, dayMs)
                val ss = Math.floorMod(w.sunsetMs - nowMs, dayMs)
                ss > sr
            } else {
                now.hour < 6 || now.hour >= 18
            }
        }
        val night = when (mode) {
            DayNightMode.AUTO -> autoNight
            DayNightMode.DAY -> false
            DayNightMode.NIGHT -> true
        }

        BackHandler(enabled = true) {
            // En el launcher "atrás" solo cierra paneles; nunca sale de la app.
            when {
                settingsOpen -> settingsOpen = false
                drawerOpen -> drawerOpen = false
            }
        }

        DashTheme(lookOf(if (night) nightStyle else dayStyle, night)) {
            HomeScreen(
                now = now,
                drawerOpen = drawerOpen,
                settingsOpen = settingsOpen,
                onOpenDrawer = { drawerOpen = true },
                onCloseDrawer = { drawerOpen = false },
                onOpenSettings = { settingsOpen = true },
                onCloseSettings = { settingsOpen = false }
            )
        }
    }
}

