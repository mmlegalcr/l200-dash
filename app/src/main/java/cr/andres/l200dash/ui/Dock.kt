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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "CR")

/** Accesos del dock. pkg == null => acción interna. */
data class DockItem(val label: String, val icon: ImageVector, val pkg: String?)

val DOCK_ITEMS = listOf(
    DockItem("Waze", Icons.Filled.Navigation, "com.waze"),
    DockItem("Spotify", Icons.Filled.MusicNote, "com.spotify.music"),
    DockItem("Dashcam", Icons.Filled.Videocam, "com.fvision.camera"),
    DockItem("Teléfono", Icons.Filled.Phone, "com.tw.bt"),
    DockItem("Radio", Icons.Filled.Radio, "com.tw.radio"),
    DockItem("Ajustes", Icons.Filled.Settings, "com.dofun.carsetting"),
    DockItem("Apps", Icons.Filled.Apps, null)
)

@Composable
fun Dock(now: LocalDateTime, onOpenDrawer: () -> Unit, onCustomize: () -> Unit, modifier: Modifier = Modifier) {
    val l = LocalLook.current
    val ctx = LocalContext.current
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
        Row(Modifier.width(220.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(now.format(DateTimeFormatter.ofPattern("H:mm")), color = l.text, fontSize = 30.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(10.dp))
            Text(now.format(DateTimeFormatter.ofPattern("EEE d MMM", ES)), color = l.dim, fontSize = 12.sp)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            DOCK_ITEMS.forEach { item ->
                DockButton(item) { if (item.pkg == null) onOpenDrawer() else AppsRepo.launch(ctx, item.pkg) }
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
private fun DockButton(item: DockItem, onClick: () -> Unit) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(10.dp)
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
        Icon(item.icon, null, tint = l.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(3.dp))
        Text(item.label, color = l.text, fontSize = 11.sp, maxLines = 1)
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
