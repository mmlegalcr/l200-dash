package cr.andres.l200dash.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle

/** Anfitrión de widgets de Android (los de Google Maps, Spotify, calendario, etc.). */
object WidgetHost {
    private const val HOST_ID = 2009

    lateinit var host: AppWidgetHost
        private set
    lateinit var manager: AppWidgetManager
        private set

    fun init(ctx: Context) {
        manager = AppWidgetManager.getInstance(ctx)
        host = AppWidgetHost(ctx.applicationContext, HOST_ID)
    }

    fun startListening() = try {
        host.startListening()
    } catch (_: Exception) {
    }

    fun stopListening() = try {
        host.stopListening()
    } catch (_: Exception) {
    }

    /** Todos los widgets instalados, agrupados por app. */
    fun providers(ctx: Context): List<Pair<String, List<AppWidgetProviderInfo>>> {
        val pm = ctx.packageManager
        return manager.installedProviders
            .groupBy { it.provider.packageName }
            .map { (pkg, list) ->
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    pkg
                }
                appName to list
            }
            .sortedBy { it.first.lowercase() }
    }

    fun label(ctx: Context, info: AppWidgetProviderInfo): String = try {
        info.loadLabel(ctx.packageManager)
    } catch (_: Exception) {
        info.provider.shortClassName
    }

    /** Tamaño inicial en celdas según el mínimo que pide el widget (~100 dp por celda). */
    fun cellsFor(ctx: Context, info: AppWidgetProviderInfo, cols: Int, rows: Int): Pair<Int, Int> {
        val d = ctx.resources.displayMetrics.density
        val wDp = info.minWidth / d
        val hDp = info.minHeight / d
        val w = kotlin.math.ceil(wDp / 100f).toInt().coerceIn(1, cols)
        val h = kotlin.math.ceil(hDp / 100f).toInt().coerceIn(1, rows)
        return w to h
    }

    /** Le dice al widget cuánto espacio tiene, para que se acomode. */
    fun updateSize(id: Int, widthDp: Int, heightDp: Int) {
        try {
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            manager.updateAppWidgetOptions(id, opts)
        } catch (_: Exception) {
        }
    }

    fun delete(id: Int) = try {
        host.deleteAppWidgetId(id)
    } catch (_: Exception) {
    }
}
