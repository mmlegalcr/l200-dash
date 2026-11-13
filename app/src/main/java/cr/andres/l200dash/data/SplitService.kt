package cr.andres.l200dash.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Servicio de accesibilidad mínimo: solo sirve para pedirle a Android que ponga la app
 * actual en pantalla dividida (acción global del sistema). No lee el contenido de la pantalla.
 */
class SplitService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: SplitService? = null
            private set

        val enabled: Boolean get() = instance != null

        /**
         * Abre [pkg] y, cuando ya está al frente, lo manda a pantalla dividida.
         * En Android 10 el otro lado de la pantalla queda para el launcher (este tablero).
         */
        fun openSplit(ctx: Context, pkg: String) {
            val svc = instance
            if (svc == null) {
                Toast.makeText(ctx, "Activa \"L200 Dash · pantalla dividida\" en Accesibilidad", Toast.LENGTH_LONG).show()
                try {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
                return
            }
            AppsRepo.launch(ctx, pkg)
            Handler(Looper.getMainLooper()).postDelayed({
                val ok = svc.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                if (!ok) Toast.makeText(ctx, "Esta pantalla no permitió la pantalla dividida", Toast.LENGTH_LONG).show()
            }, 1500)
        }
    }
}
