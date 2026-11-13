package cr.andres.l200dash.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.andres.l200dash.data.DashStyle
import cr.andres.l200dash.data.DayNightMode
import cr.andres.l200dash.data.StyleRepo
import cr.andres.l200dash.data.HomeRepo
import java.io.File

/**
 * Panel lateral de personalización. Edita el estilo del modo elegido (Día / Noche)
 * y los cambios se ven al instante en el tablero de atrás.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(currentlyNight: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    var editNight by remember { mutableStateOf(currentlyNight) }
    val day by StyleRepo.day.collectAsState()
    val night by StyleRepo.night.collectAsState()
    val mode by StyleRepo.mode.collectAsState()
    val saved by StyleRepo.saved.collectAsState()
    val s: DashStyle = if (editNight) night else day
    fun edit(change: (DashStyle) -> DashStyle) = StyleRepo.update(editNight, change)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val dest = File(ctx.filesDir, if (editNight) "bg_night.img" else "bg_day.img")
            ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            // Ruta con marca de tiempo para que el fondo se recargue aunque el nombre sea igual.
            edit { it.copy(image = dest.path + "?" + System.currentTimeMillis()) }
        } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo usar esa imagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val shape = RoundedCornerShape(l.radius)
    Column(
        modifier
            .width(440.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(l.panelSolid)
            .border(l.border, l.accent, shape)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Personalizar", color = l.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Close, "Cerrar", tint = l.accent, modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose).padding(8.dp))
        }

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Section("Cambio día / noche")
            Segmented(
                listOf("Automático" to (mode == DayNightMode.AUTO), "Siempre día" to (mode == DayNightMode.DAY), "Siempre noche" to (mode == DayNightMode.NIGHT))
            ) { i -> StyleRepo.setMode(DayNightMode.values()[i]) }

            Section("Estás editando")
            Segmented(listOf("☀ Día" to !editNight, "☾ Noche" to editNight)) { i -> editNight = i == 1 }

            Section("Temas rápidos")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleRepo.PRESETS.forEach { (name, d, n) ->
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Color(d), RoundedCornerShape(8.dp))
                            .clickable { StyleRepo.applyPreset(d, n) }.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(d)))
                        Spacer(Modifier.width(6.dp))
                        Text(name, color = l.text, fontSize = 13.sp)
                    }
                }
            }

            Section("Color de acento (bordes, medidores, íconos)")
            Swatches(StyleRepo.ACCENT_SWATCHES, s.accent) { c -> edit { it.copy(accent = c) } }
            HueSlider(s.accent) { c -> edit { it.copy(accent = c) } }

            Section("Fondo de los paneles")
            Swatches(StyleRepo.PANEL_SWATCHES, s.panel) { c -> edit { it.copy(panel = c) } }
            LabeledSlider("Opacidad", s.panelAlpha, 0.35f..1f, "%.0f%%".format(s.panelAlpha * 100)) { v -> edit { it.copy(panelAlpha = v) } }

            Section("Borde")
            LabeledSlider("Brillo del borde", s.glow, 0f..1f, if (s.glow < 0.02f) "apagado" else "%.0f%%".format(s.glow * 100)) { v -> edit { it.copy(glow = v) } }
            LabeledSlider("Grosor", s.borderDp, 0f..4f, "%.1f".format(s.borderDp)) { v -> edit { it.copy(borderDp = v) } }
            LabeledSlider("Esquinas", s.radiusDp, 0f..28f, "%.0f".format(s.radiusDp)) { v -> edit { it.copy(radiusDp = v) } }

            Section("Texto")
            LabeledSlider("Tamaño", s.textScale, 0.85f..1.3f, "%.0f%%".format(s.textScale * 100)) { v -> edit { it.copy(textScale = v) } }

            Section("Imagen central")
            LabeledSlider("Brillo de la imagen", s.imageBrightness, 0.2f..1.1f, "%.0f%%".format(s.imageBrightness * 100)) { v -> edit { it.copy(imageBrightness = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Elegir imagen…", Modifier.weight(1f)) { picker.launch("image/*") }
                OutlineButton("Usar la L200", Modifier.weight(1f), enabled = s.image != null) { edit { it.copy(image = null) } }
            }

            Section("Cuadrícula del inicio")
            val home by HomeRepo.layout.collectAsState()
            LabeledSlider("Columnas", home.cols.toFloat(), 8f..16f, "${home.cols}") { v -> HomeRepo.setGrid(v.toInt(), home.rows) }
            LabeledSlider("Filas", home.rows.toFloat(), 5f..10f, "${home.rows}") { v -> HomeRepo.setGrid(home.cols, v.toInt()) }
            Text("Para mover o agregar piezas: mantén presionado cualquier lugar del inicio.", color = l.dim, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            OutlineButton("Restablecer distribución original", Modifier.fillMaxWidth()) { HomeRepo.resetLayout() }

            Section("Permisos")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Acceso a la música", Modifier.weight(1f)) { openNotificationAccess(ctx) }
                OutlineButton("Pantalla dividida", Modifier.weight(1f)) {
                    try {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
            }

            Section("Mis temas")
            var name by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(1.dp, l.line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    if (name.isEmpty()) Text("Nombre del tema", color = l.dim, fontSize = 14.sp)
                    BasicTextField(
                        value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                        textStyle = TextStyle(color = l.text, fontSize = 14.sp), cursorBrush = SolidColor(l.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlineButton("Guardar", filled = true, enabled = name.isNotBlank()) {
                    StyleRepo.saveTheme(name.trim())
                    name = ""
                }
            }
            Spacer(Modifier.height(8.dp))
            saved.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x14FFFFFF))
                        .clickable { StyleRepo.applyTheme(t) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(t.day.accent)))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(t.night.accent)))
                    Spacer(Modifier.width(10.dp))
                    Text(t.name, color = l.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Delete, "Borrar", tint = l.dim, modifier = Modifier.size(32.dp).clip(CircleShape).clickable { StyleRepo.deleteTheme(t) }.padding(6.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlineButton("Restaurar valores de ${if (editNight) "noche" else "día"}", Modifier.fillMaxWidth()) { StyleRepo.reset(editNight) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    val l = LocalLook.current
    Spacer(Modifier.height(16.dp))
    Text(title.uppercase(), color = l.dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Segmented(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, l.accent.copy(alpha = 0.5f), shape)) {
        options.forEachIndexed { i, (label, selected) ->
            Box(
                Modifier.weight(1f).background(if (selected) l.accent else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Color(0xFF16191B) else l.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    val l = LocalLook.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            val isSel = (c and 0xFFFFFFFFL) == (selected and 0xFFFFFFFFL)
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                    .border(if (isSel) 2.dp else 1.dp, if (isSel) l.text else Color(0x33FFFFFF), CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center
            ) {
                if (isSel) Icon(Icons.Filled.Check, null, tint = if (Color(c).luminanceSimple() > 0.6f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun Color.luminanceSimple(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Selector libre de tono (conserva saturación y brillo altos). */
@Composable
private fun HueSlider(current: Long, onPick: (Long) -> Unit) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(current.toInt(), hsv)
    LabeledSlider("Tono libre", hsv[0] / 360f, 0f..1f, "%.0f°".format(hsv[0])) { v ->
        val sat = if (hsv[1] < 0.15f) 0.75f else hsv[1]
        val value = if (hsv[2] < 0.5f) 0.9f else hsv[2]
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(v * 360f, sat, value))
        onPick(argb.toLong() and 0xFFFFFFFFL)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    val l = LocalLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = l.text, fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = l.accent, activeTrackColor = l.accent, inactiveTrackColor = Color(0x33FFFFFF)),
            modifier = Modifier.weight(1f)
        )
        Text(display, color = l.dim, fontSize = 12.sp, modifier = Modifier.width(64.dp).padding(start = 6.dp))
    }
}
