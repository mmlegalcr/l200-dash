package cr.andres.l200dash.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ajustes persistentes del launcher. */
object Prefs {
    private lateinit var sp: SharedPreferences

    /** Id Camera2 que muestra el widget de cámara. En esta pantalla, "1" es la de reversa. */
    private val _cameraId = MutableStateFlow("1")
    val cameraId: StateFlow<String> = _cameraId

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("dash", Context.MODE_PRIVATE)
        _cameraId.value = sp.getString("camera_id", "1") ?: "1"
    }

    fun setCameraId(id: String) {
        _cameraId.value = id
        sp.edit().putString("camera_id", id).apply()
    }
}
