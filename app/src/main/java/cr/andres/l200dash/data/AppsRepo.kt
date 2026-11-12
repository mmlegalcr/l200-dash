package cr.andres.l200dash.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppEntry(
    val label: String,
    val pkg: String,
    val activity: String,
    val icon: ImageBitmap?
)

/** Lista de apps instaladas con ícono, y utilidades para abrirlas. */
object AppsRepo {
    private lateinit var appCtx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val iconCache = HashMap<String, ImageBitmap?>()

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        refresh()
    }

    fun refresh() {
        scope.launch {
            val pm = appCtx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != appCtx.packageName }
                .map {
                    AppEntry(
                        label = it.loadLabel(pm).toString(),
                        pkg = it.activityInfo.packageName,
                        activity = it.activityInfo.name,
                        icon = loadIcon(it.activityInfo.packageName)
                    )
                }
                .sortedBy { it.label.lowercase() }
            _apps.value = list
        }
    }

    /** Ícono de una app como ImageBitmap (con caché). Puede llamarse desde cualquier hilo. */
    @Synchronized
    fun loadIcon(pkg: String): ImageBitmap? {
        if (iconCache.containsKey(pkg)) return iconCache[pkg]
        val bmp = try {
            appCtx.packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap()
        } catch (_: Exception) {
            null
        }
        iconCache[pkg] = bmp
        return bmp
    }

    fun isInstalled(pkg: String): Boolean = try {
        appCtx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun launch(ctx: Context, pkg: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(ctx, "App no instalada: $pkg", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo abrir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
