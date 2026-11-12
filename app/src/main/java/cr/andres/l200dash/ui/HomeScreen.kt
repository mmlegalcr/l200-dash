package cr.andres.l200dash.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.andres.l200dash.data.DayNightMode
import cr.andres.l200dash.data.LocationRepo
import cr.andres.l200dash.data.StyleRepo
import cr.andres.l200dash.data.WeatherRepo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "CR")

@Composable
fun HomeScreen(
    now: LocalDateTime,
    drawerOpen: Boolean,
    settingsOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit
) {
    val l = LocalLook.current
    val hero = rememberHero(l.style.image)
    val fix by LocationRepo.fix.collectAsState()
    val weather by WeatherRepo.weather.collectAsState()
    val mode by StyleRepo.mode.collectAsState()

    // "now" cambia cada segundo, así que esto se reevalúa solo.
    val fresh = fix?.let { it.at > 0 && SystemClock.elapsedRealtime() - it.at < 4000 } == true
    val gpsOk = fresh && fix?.fromGps == true

    Box(Modifier.fillMaxSize()) {
        DashBackground(hero, l.style.imageBrightness)

        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            // Encabezado
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(onClick = onOpenSettings, onLongClick = onOpenSettings) {
                    Text("L200", color = l.accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
                Chip {
                    Text(
                        now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() },
                        color = l.text, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.weight(1f))
                weather?.let { w ->
                    val (desc, emoji) = WeatherRepo.describe(w.code, w.isDay)
                    Chip { Text("$emoji ${"%.0f".format(w.tempC)}°  ·  $desc", color = l.text, fontSize = 14.sp) }
                }
                Chip(onClick = {
                    val next = when (mode) {
                        DayNightMode.AUTO -> DayNightMode.DAY
                        DayNightMode.DAY -> DayNightMode.NIGHT
                        DayNightMode.NIGHT -> DayNightMode.AUTO
                    }
                    StyleRepo.setMode(next)
                }) {
                    val icon = if (l.night) "☾" else "☀"
                    val label = when (mode) {
                        DayNightMode.AUTO -> if (l.night) "Noche · auto" else "Día · auto"
                        DayNightMode.DAY -> "Día"
                        DayNightMode.NIGHT -> "Noche"
                    }
                    Text("$icon $label", color = l.text, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MapPanel(Modifier.weight(0.27f).fillMaxHeight())
                Box(Modifier.weight(0.46f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    val f = fix
                    GaugeCluster(
                        speedKmh = if (fresh) f?.speedKmh else null,
                        bearing = f?.bearing,
                        altitudeM = f?.altitudeM,
                        gpsOk = gpsOk
                    )
                }
                Column(Modifier.weight(0.27f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CameraPanel(Modifier.fillMaxWidth().weight(0.48f))
                    MusicPanel(Modifier.fillMaxWidth().weight(0.52f))
                }
            }

            Spacer(Modifier.height(12.dp))
            Dock(now, onOpenDrawer, onOpenSettings, Modifier.fillMaxWidth().height(78.dp))
        }

        AnimatedVisibility(visible = drawerOpen, enter = fadeIn(), exit = fadeOut()) {
            AppDrawer(onClose = onCloseDrawer)
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SettingsSheet(currentlyNight = l.night, onClose = onCloseSettings, modifier = Modifier.padding(12.dp))
        }
    }
}
