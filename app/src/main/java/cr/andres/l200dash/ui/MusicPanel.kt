package cr.andres.l200dash.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.MediaRepo
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MusicPanel(modifier: Modifier = Modifier) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    val np by MediaRepo.now.collectAsState()
    val access by MediaRepo.hasAccess.collectAsState()
    val playing = np?.playing == true

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        while (playing) {
            tick = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    val appName = when (np?.pkg) {
        "com.spotify.music" -> "Spotify"
        null -> "Música"
        else -> AppsRepo.apps.value.firstOrNull { it.pkg == np?.pkg }?.label ?: "Música"
    }

    GlowPanel(
        title = "Música",
        icon = Icons.Filled.MusicNote,
        modifier = modifier,
        tag = appName,
        onTag = { AppsRepo.launch(ctx, np?.pkg ?: "com.spotify.music") }
    ) {
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val art = remember(np?.art) { np?.art?.asImageBitmap() }
                Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).background(l.accentSoft).border(1.dp, l.line, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Filled.MusicNote, null, tint = l.accent, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(np?.title?.ifBlank { null } ?: "Nada sonando", color = l.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(np?.artist?.ifBlank { null } ?: if (access) "Abre Spotify para empezar" else "Sin acceso a la música", color = l.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val p = np
                    if (p != null && p.durationMs > 0) {
                        val elapsed = if (p.playing) ((tick.takeIf { it > 0 } ?: SystemClock.elapsedRealtime()) - p.positionAt) * p.speed else 0f
                        val pos = (p.positionMs + elapsed.toLong()).coerceIn(0L, p.durationMs)
                        Text("${fmt(pos)} / ${fmt(p.durationMs)}", color = l.dim, fontSize = 12.sp)
                    } else if (!access) {
                        Text(
                            "Permitir acceso ›", color = l.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { openNotificationAccess(ctx) }
                        )
                    }
                }
            }

            Equalizer(playing, Modifier.fillMaxWidth().height(30.dp))
            val sessions by MediaRepo.sessions.collectAsState()
            Text(
                if (access) "Acceso: sí · sesiones activas: ${sessions.coerceAtLeast(0)}" else "Acceso: no · usando avisos de las apps",
                color = l.dim, fontSize = 10.sp
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                SquareButton(Icons.Filled.SkipPrevious, 42.dp) { MediaRepo.previous(ctx) }
                SquareButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 54.dp, filled = true) { MediaRepo.playPause(ctx) }
                SquareButton(Icons.Filled.SkipNext, 42.dp) { MediaRepo.next(ctx) }
            }
        }
    }
}

/** Abre la pantalla de "Acceso a notificaciones" probando varias rutas (cada fabricante la esconde distinto). */
fun openNotificationAccess(ctx: android.content.Context) {
    val attempts = listOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS"),
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$NotificationAccessSettingsActivity"),
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$AppAndNotificationDashboardActivity"),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (i in attempts) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (i.action == Settings.ACTION_SETTINGS) {
                Toast.makeText(ctx, "Busca: Apps y notificaciones → Acceso especial → Acceso a notificaciones → L200 Dash", Toast.LENGTH_LONG).show()
            }
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    Toast.makeText(ctx, "Esta pantalla no tiene ese ajuste", Toast.LENGTH_LONG).show()
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Ecualizador decorativo: se anima mientras suena algo. */
@Composable
private fun Equalizer(playing: Boolean, modifier: Modifier) {
    val l = LocalLook.current
    val bars = 36
    var levels by remember { mutableStateOf(FloatArray(bars) { 0.15f }) }
    LaunchedEffect(playing) {
        while (playing) {
            levels = FloatArray(bars) { i ->
                val prev = levels.getOrElse(i) { 0.3f }
                (prev * 0.45f + Random.nextFloat() * 0.55f).coerceIn(0.12f, 1f)
            }
            delay(110)
        }
        levels = FloatArray(bars) { 0.12f }
    }
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        val brush = Brush.verticalGradient(listOf(l.accent.copy(alpha = 0.95f), l.accent.copy(alpha = 0.55f)))
        levels.forEachIndexed { i, v ->
            val h = size.height * v
            drawRoundRect(brush, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(1.5f, 1.5f))
        }
    }
}

@Composable
private fun SquareButton(icon: ImageVector, size: Dp, filled: Boolean = false, onClick: () -> Unit) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(if (filled) l.accent else Color.Transparent)
            .border(1.dp, l.accent.copy(alpha = if (filled) 1f else 0.55f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (filled) Color(0xFF16191B) else l.accent, modifier = Modifier.size(size * 0.55f))
    }
}
