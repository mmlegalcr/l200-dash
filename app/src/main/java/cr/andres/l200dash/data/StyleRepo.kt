package cr.andres.l200dash.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Apariencia de un modo (día o noche). Colores en ARGB. */
data class DashStyle(
    val accent: Long = 0xFFE3A83BL,
    val panel: Long = 0xFF121619L,
    val panelAlpha: Float = 0.86f,
    /** 0 = sin brillo, 1 = brillo máximo. */
    val glow: Float = 0.45f,
    val borderDp: Float = 1.5f,
    val radiusDp: Float = 14f,
    val textScale: Float = 1f,
    /** Ruta de una imagen propia, o null para la ilustración incluida. */
    val image: String? = null,
    /** Brillo de la imagen central (1 = original). */
    val imageBrightness: Float = 1f
) {
    fun toJson(): JSONObject = JSONObject()
        .put("accent", accent).put("panel", panel).put("panelAlpha", panelAlpha.toDouble())
        .put("glow", glow.toDouble()).put("borderDp", borderDp.toDouble()).put("radiusDp", radiusDp.toDouble())
        .put("textScale", textScale.toDouble()).put("image", image ?: "").put("imageBrightness", imageBrightness.toDouble())

    companion object {
        fun fromJson(o: JSONObject, def: DashStyle): DashStyle = DashStyle(
            accent = o.optLong("accent", def.accent),
            panel = o.optLong("panel", def.panel),
            panelAlpha = o.optDouble("panelAlpha", def.panelAlpha.toDouble()).toFloat(),
            glow = o.optDouble("glow", def.glow.toDouble()).toFloat(),
            borderDp = o.optDouble("borderDp", def.borderDp.toDouble()).toFloat(),
            radiusDp = o.optDouble("radiusDp", def.radiusDp.toDouble()).toFloat(),
            textScale = o.optDouble("textScale", def.textScale.toDouble()).toFloat(),
            image = o.optString("image", "").ifBlank { null },
            imageBrightness = o.optDouble("imageBrightness", def.imageBrightness.toDouble()).toFloat()
        )
    }
}

enum class DayNightMode { AUTO, DAY, NIGHT }

data class SavedTheme(val name: String, val day: DashStyle, val night: DashStyle)

/** Guarda y publica la apariencia del launcher. Los cambios se ven al instante. */
object StyleRepo {
    val DEFAULT_DAY = DashStyle()
    val DEFAULT_NIGHT = DashStyle(accent = 0xFFE3A83BL, glow = 0.35f, imageBrightness = 0.42f)

    /** Temas listos: (nombre, acento día, acento noche). */
    val PRESETS = listOf(
        Triple("Ámbar", 0xFFE3A83BL, 0xFFE3A83BL),
        Triple("Cian", 0xFF35C8E6L, 0xFF2BA3BDL),
        Triple("Rojo", 0xFFE5484DL, 0xFFC23B40L),
        Triple("Verde", 0xFF5FD38DL, 0xFF47A86DL),
        Triple("Blanco", 0xFFE9EEF2L, 0xFFB9C2CAL),
        Triple("Violeta", 0xFFA78BFAL, 0xFF8B6FE0L)
    )

    val ACCENT_SWATCHES = listOf(
        0xFFE3A83BL, 0xFFF59E0BL, 0xFFF97316L, 0xFFE5484DL, 0xFFEC4899L, 0xFFA78BFAL,
        0xFF6366F1L, 0xFF3B82F6L, 0xFF35C8E6L, 0xFF14B8A6L, 0xFF5FD38DL, 0xFFE9EEF2L
    )

    val PANEL_SWATCHES = listOf(
        0xFF121619L, 0xFF0B0E12L, 0xFF1B2228L, 0xFF14202BL, 0xFF1F1A14L, 0xFF1A1426L, 0xFF0F1D17L, 0xFF262626L
    )

    private lateinit var sp: SharedPreferences

    private val _day = MutableStateFlow(DEFAULT_DAY)
    val day: StateFlow<DashStyle> = _day
    private val _night = MutableStateFlow(DEFAULT_NIGHT)
    val night: StateFlow<DashStyle> = _night
    private val _mode = MutableStateFlow(DayNightMode.AUTO)
    val mode: StateFlow<DayNightMode> = _mode
    private val _saved = MutableStateFlow<List<SavedTheme>>(emptyList())
    val saved: StateFlow<List<SavedTheme>> = _saved

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("style", Context.MODE_PRIVATE)
        _day.value = load("day", DEFAULT_DAY)
        _night.value = load("night", DEFAULT_NIGHT)
        _mode.value = try {
            DayNightMode.valueOf(sp.getString("mode", "AUTO") ?: "AUTO")
        } catch (_: Exception) {
            DayNightMode.AUTO
        }
        _saved.value = try {
            val arr = JSONArray(sp.getString("saved", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedTheme(
                    o.getString("name"),
                    DashStyle.fromJson(o.getJSONObject("day"), DEFAULT_DAY),
                    DashStyle.fromJson(o.getJSONObject("night"), DEFAULT_NIGHT)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun load(key: String, def: DashStyle): DashStyle = try {
        sp.getString(key, null)?.let { DashStyle.fromJson(JSONObject(it), def) } ?: def
    } catch (_: Exception) {
        def
    }

    fun update(night: Boolean, change: (DashStyle) -> DashStyle) {
        if (night) {
            _night.value = change(_night.value)
            sp.edit().putString("night", _night.value.toJson().toString()).apply()
        } else {
            _day.value = change(_day.value)
            sp.edit().putString("day", _day.value.toJson().toString()).apply()
        }
    }

    fun setMode(m: DayNightMode) {
        _mode.value = m
        sp.edit().putString("mode", m.name).apply()
    }

    fun applyPreset(dayAccent: Long, nightAccent: Long) {
        update(false) { it.copy(accent = dayAccent) }
        update(true) { it.copy(accent = nightAccent) }
    }

    fun reset(night: Boolean) {
        update(night) { cur ->
            val def = if (night) DEFAULT_NIGHT else DEFAULT_DAY
            def.copy(image = cur.image) // la imagen se restaura aparte
        }
    }

    fun saveTheme(name: String) {
        val list = _saved.value.filterNot { it.name == name } + SavedTheme(name, _day.value, _night.value)
        persistSaved(list)
    }

    fun applyTheme(t: SavedTheme) {
        update(false) { t.day }
        update(true) { t.night }
    }

    fun deleteTheme(t: SavedTheme) = persistSaved(_saved.value.filterNot { it.name == t.name })

    private fun persistSaved(list: List<SavedTheme>) {
        _saved.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("day", it.day.toJson()).put("night", it.night.toJson())) }
        sp.edit().putString("saved", arr.toString()).apply()
    }
}
