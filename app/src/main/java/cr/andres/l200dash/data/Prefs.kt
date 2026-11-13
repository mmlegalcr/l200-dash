package cr.andres.l200dash.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ajustes persistentes del launcher. */
object Prefs {
    private lateinit var sp: SharedPreferences

    /** Fuente del widget de cámara: "dash" = dashcam USB; si no, id Camera2 ("1" = reversa). */
    private val _cameraId = MutableStateFlow("dash")
    val cameraId: StateFlow<String> = _cameraId

    /** Proveedor del mapa: google, carto_dark, carto_light, topo. */
    private val _mapProvider = MutableStateFlow("google")
    val mapProvider: StateFlow<String> = _mapProvider

    fun setMapProvider(p: String) {
        _mapProvider.value = p
        sp.edit().putString("map_provider", p).apply()
    }

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("dash", Context.MODE_PRIVATE)
        _cameraId.value = sp.getString("camera_id", "dash") ?: "dash"
        _mapProvider.value = sp.getString("map_provider", "google") ?: "google"
    }

    fun setCameraId(id: String) {
        _cameraId.value = id
        sp.edit().putString("camera_id", id).apply()
    }
}
