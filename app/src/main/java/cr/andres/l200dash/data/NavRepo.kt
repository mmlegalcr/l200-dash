package cr.andres.l200dash.data

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Indicaciones de Waze leídas de su notificación de navegación.
 * No hay API oficial: se muestra lo que Waze ponga en título, texto e ícono grande.
 */
data class NavInfo(
    val title: String,
    val text: String,
    val sub: String,
    val icon: Bitmap?,
    val postedAt: Long
)

object NavRepo {
    const val WAZE = "com.waze"

    private val _nav = MutableStateFlow<NavInfo?>(null)
    val nav: StateFlow<NavInfo?> = _nav

    /** Último volcado crudo de la notificación (para diagnosticar qué trae Waze). */
    private val _raw = MutableStateFlow("")
    val raw: StateFlow<String> = _raw

    fun onPosted(sbn: StatusBarNotification, ctx: android.content.Context) {
        if (sbn.packageName != WAZE) return
        val n = sbn.notification ?: return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val sub = (ex.getCharSequence(Notification.EXTRA_SUB_TEXT) ?: ex.getCharSequence(Notification.EXTRA_INFO_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val icon: Bitmap? = try {
            val large: Icon? = n.getLargeIcon()
            (large?.loadDrawable(ctx) as? BitmapDrawable)?.bitmap
        } catch (_: Exception) {
            null
        }
        _nav.value = NavInfo(title, text, sub, icon, sbn.postTime)
        _raw.value = buildString {
            append("título=").append(title).append(" | texto=").append(text).append(" | sub=").append(sub)
            append(" | keys=").append(ex.keySet().joinToString())
        }
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == WAZE) _nav.value = null
    }
}
