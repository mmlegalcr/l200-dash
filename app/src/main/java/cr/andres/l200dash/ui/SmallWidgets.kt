package cr.andres.l200dash.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.LocationRepo
import cr.andres.l200dash.data.NavRepo
import cr.andres.l200dash.data.WeatherRepo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ES = Locale("es", "CR")

/** Datos de GPS "frescos" (menos de 4 s). */
@Composable
fun rememberGps(): Triple<Float?, Float?, Double?> {
    val fix by LocationRepo.fix.collectAsState()
    val f = fix
    val fresh = f != null && f.at > 0 && SystemClock.elapsedRealtime() - f.at < 4000
    return Triple(if (fresh) f?.speedKmh else null, f?.bearing, f?.altitudeM)
}

/** Medidores escalados al espacio que tengan. */
@Composable
fun GaugesWidget(modifier: Modifier = Modifier) {
    val (speed, bearing, alt) = rememberGps()
    val fix by LocationRepo.fix.collectAsState()
    val gpsOk = speed != null && fix?.fromGps == true
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val natW = 116f + 176f + 116f + 44f
        val natH = 230f
        val s = minOf(maxWidth.value / natW, maxHeight.value / natH, 1.4f)
        Box(Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true).graphicsLayer(scaleX = s, scaleY = s, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f))) {
            GaugeCluster(speed, bearing, alt, gpsOk)
        }
    }
}

@Composable
fun SpeedWidget(modifier: Modifier = Modifier) {
    val (speed, _, _) = rememberGps()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        SpeedGauge(speed, side * 0.92f)
    }
}

@Composable
fun ClockWidget(now: LocalDateTime, modifier: Modifier = Modifier) {
    val l = LocalLook.current
    GlowPanel("Reloj", Icons.Filled.Schedule, modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
            Text(now.format(DateTimeFormatter.ofPattern("H:mm")), color = l.text, fontSize = 52.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() },
                color = l.dim, fontSize = 14.sp
            )
        }
    }
}

@Composable
fun WeatherWidget(modifier: Modifier = Modifier) {
    val l = LocalLook.current
    val w by WeatherRepo.weather.collectAsState()
    GlowPanel("Clima", Icons.Filled.Cloud, modifier) {
        val weather = w
        if (weather == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Cargando…", color = l.dim, fontSize = 14.sp) }
            return@GlowPanel
        }
        val (desc, emoji) = WeatherRepo.describe(weather.code, weather.isDay)
        val zone = ZoneId.systemDefault()
        val hm = DateTimeFormatter.ofPattern("H:mm")
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("%.0f°".format(weather.tempC), color = l.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(desc, color = l.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("☀ " + Instant.ofEpochMilli(weather.sunriseMs).atZone(zone).format(hm), color = l.dim, fontSize = 11.sp)
                    Text("☾ " + Instant.ofEpochMilli(weather.sunsetMs).atZone(zone).format(hm), color = l.dim, fontSize = 11.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weather.days.drop(1).take(3).forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).replaceFirstChar { it.uppercase() }, color = l.dim, fontSize = 11.sp)
                        Text(WeatherRepo.describe(d.code).second, fontSize = 16.sp)
                        Text("%.0f°/%.0f°".format(d.maxC, d.minC), color = l.text, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** Indicación de Waze (leída de su notificación) como widget independiente. */
@Composable
fun NavWidget(modifier: Modifier = Modifier, onSplit: () -> Unit) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    val nav by NavRepo.nav.collectAsState()
    GlowPanel("Waze", Icons.Filled.Navigation, modifier, tag = "Dividido ▸", onTag = onSplit) {
        val n = nav
        Row(
            Modifier.fillMaxSize().clickable { AppsRepo.launch(ctx, NavRepo.WAZE) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ic = remember(n?.icon) { n?.icon?.asImageBitmap() }
            if (ic != null) Image(ic, null, Modifier.size(44.dp))
            else Icon(Icons.Filled.TurnRight, null, tint = l.accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n?.title ?: "Sin ruta activa", color = l.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    n?.text?.ifBlank { null } ?: if (n == null) "Toca para abrir Waze" else "",
                    color = l.dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
