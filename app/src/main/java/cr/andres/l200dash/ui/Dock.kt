package cr.andres.l200dash.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.HomeRepo
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "CR")

/** Íconos de línea para apps conocidas; las demás usan su ícono real. */
private val KNOWN_ICONS: Map<String, Pair<String, ImageVector>> = mapOf(
    "com.waze" to ("Waze" to Icons.Filled.Navigation),
    "com.spotify.music" to ("Spotify" to Icons.Filled.MusicNote),
    "com.fvision.camera" to ("Dashcam" to Icons.Filled.Videocam),
    "com.tw.bt" to ("Teléfono" to Icons.Filled.Phone),
    "com.tw.radio" to ("Radio" to Icons.Filled.Radio),
    "com.dofun.carsetting" to ("Ajustes" to Icons.Filled.Settings),
    HomeRepo.APPS_BUTTON to ("Apps" to Icons.Filled.Apps)
)

@Composable
fun Dock(
    now: LocalDateTime,
    editing: Boolean,
    onOpenDrawer: () -> Unit,
    onCustomize: () -> Unit,
    onAddToDock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    val layout by HomeRepo.layout.collectAsState()
    val shape = RoundedCornerShape(l.radius)
    Row(
        modifier
            .glow(l.accent, 26.dp, l.radius, l.glow)
            .clip(shape)
            .background(l.panel)
            .border(l.border, l.accent, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.width(200.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(now.format(DateTimeFormatter.ofPattern("H:mm")), color = l.text, fontSize = 30.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(10.dp))
            Text(now.format(DateTimeFormatter.ofPattern("EEE d MMM", ES)), color = l.dim, fontSize = 12.sp)
        }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            layout.dock.forEach { pkg ->
                Box {
                    DockButton(pkg) {
                        when {
                            editing -> Unit
                            pkg == HomeRepo.APPS_BUTTON -> onOpenDrawer()
                            pkg == ctx.packageName -> onOpenDrawer()
                            else -> AppsRepo.launch(ctx, pkg)
                        }
                    }
                    if (editing && pkg != HomeRepo.APPS_BUTTON) {
                        Box(
                            Modifier.align(Alignment.TopStart).size(24.dp).clip(CircleShape).background(l.rec)
                                .clickable { HomeRepo.dockRemove(pkg) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Close, "Quitar", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
            if (editing) {
                Box(
                    Modifier.size(width = 58.dp, height = 58.dp).clip(RoundedCornerShape(10.dp))
                        .border(1.dp, l.accent, RoundedCornerShape(10.dp)).clickable(onClick = onAddToDock),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Add, "Agregar al dock", tint = l.accent) }
            }
        }
        Row(Modifier.width(220.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            VolumeSlider(Modifier.width(150.dp))
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Tune, contentDescription = "Personalizar", tint = l.accent,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onCustomize).padding(8.dp)
            )
        }
    }
}

@Composable
private fun DockButton(pkg: String, onClick: () -> Unit) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(10.dp)
    val known = KNOWN_ICONS[pkg]
    val appIcon = remember(pkg) { if (known == null) AppsRepo.loadIcon(pkg) else null }
    val label = known?.first ?: remember(pkg) { AppsRepo.labelOf(pkg) }
    Column(
        Modifier
            .size(width = 76.dp, height = 58.dp)
            .clip(shape)
            .background(l.accent.copy(alpha = 0.04f))
            .border(1.dp, l.accent.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            known != null -> Icon(known.second, null, tint = l.accent, modifier = Modifier.size(22.dp))
            appIcon != null -> Image(appIcon, null, Modifier.size(24.dp))
            else -> Box(Modifier.size(22.dp))
        }
        Spacer(Modifier.size(3.dp))
        Text(label, color = l.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Volumen multimedia real del sistema. */
@Composable
private fun VolumeSlider(modifier: Modifier) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var v by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.VolumeUp, null, tint = l.accent, modifier = Modifier.size(20.dp))
        Slider(
            value = v,
            onValueChange = {
                v = it
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0)
            },
            colors = SliderDefaults.colors(thumbColor = l.accent, activeTrackColor = l.accent, inactiveTrackColor = l.line),
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}
