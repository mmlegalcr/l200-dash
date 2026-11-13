package cr.andres.l200dash

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.DayNightMode
import cr.andres.l200dash.data.HomeRepo
import cr.andres.l200dash.data.ItemKind
import cr.andres.l200dash.data.LocationRepo
import cr.andres.l200dash.data.MediaRepo
import cr.andres.l200dash.data.StyleRepo
import cr.andres.l200dash.data.WeatherRepo
import cr.andres.l200dash.data.WidgetHost
import cr.andres.l200dash.ui.DashTheme
import cr.andres.l200dash.ui.HomeScreen
import cr.andres.l200dash.ui.lookOf
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    /** Se usa si todavía no hay ninguna ubicación (San José). */
    private val fallbackLat = 9.9281
    private val fallbackLon = -84.0907

    // Widget de Android que se está agregando (permiso y configuración van en pasos).
    private var pendingWidgetId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null
    private var pendingPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestMissingPermissions()
        setContent { DashRoot() }
    }

    override fun onStart() {
        super.onStart()
        WidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        WidgetHost.stopListening()
    }

    override fun onResume() {
        super.onResume()
        LocationRepo.start(this)
        MediaRepo.start(this)
        AppsRepo.refresh()
    }

    private fun requestMissingPermissions() {
        val needed = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 10)
    }

    // ------------------------------------------------------------------ Widgets de Android

    private fun addAndroidWidget(info: AppWidgetProviderInfo, page: Int) {
        val id = WidgetHost.host.allocateAppWidgetId()
        pendingWidgetId = id
        pendingInfo = info
        pendingPage = page
        if (WidgetHost.manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            afterBind()
        } else {
            // Android pide permiso para que este launcher muestre widgets.
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_BIND)
        }
    }

    private fun afterBind() {
        val info = pendingInfo ?: return
        if (info.configure != null) {
            try {
                WidgetHost.host.startAppWidgetConfigureActivityForResult(this, pendingWidgetId, 0, REQ_CONFIGURE, null)
                return
            } catch (e: Exception) {
                // Si la configuración no abre, se agrega igual.
            }
        }
        finishAdd()
    }

    private fun finishAdd() {
        val info = pendingInfo ?: return
        val layout = HomeRepo.layout.value
        val (w, h) = WidgetHost.cellsFor(this, info, layout.cols, layout.rows)
        val ok = HomeRepo.add(pendingPage, ItemKind.WIDGET, w = w, h = h, widgetId = pendingWidgetId)
        if (!ok) {
            WidgetHost.delete(pendingWidgetId)
            Toast.makeText(this, "No hay espacio libre en esa página", Toast.LENGTH_LONG).show()
        }
        pendingInfo = null
        pendingWidgetId = -1
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) afterBind() else cancelPending()
            REQ_CONFIGURE -> if (resultCode == Activity.RESULT_OK) finishAdd() else cancelPending()
        }
    }

    private fun cancelPending() {
        if (pendingWidgetId != -1) WidgetHost.delete(pendingWidgetId)
        pendingWidgetId = -1
        pendingInfo = null
    }

    // ------------------------------------------------------------------ UI

    @Composable
    private fun DashRoot() {
        var now by remember { mutableStateOf(LocalDateTime.now()) }
        val weather by WeatherRepo.weather.collectAsState()
        val fix by LocationRepo.fix.collectAsState()
        val mode by StyleRepo.mode.collectAsState()
        val dayStyle by StyleRepo.day.collectAsState()
        val nightStyle by StyleRepo.night.collectAsState()

        LaunchedEffect(Unit) {
            while (true) {
                now = LocalDateTime.now()
                delay(1000L - System.currentTimeMillis() % 1000L)
            }
        }

        LaunchedEffect(fix?.lat, fix?.lon, now.minute) {
            val f = fix
            if (f != null) WeatherRepo.maybeRefresh(f.lat, f.lon)
            else WeatherRepo.maybeRefresh(fallbackLat, fallbackLon)
        }

        // Noche automática = el próximo evento solar es el amanecer. Sin clima: 18:00–6:00.
        val autoNight = run {
            val w = weather
            if (w != null) {
                val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dayMs = 24 * 3600_000L
                val sr = Math.floorMod(w.sunriseMs - nowMs, dayMs)
                val ss = Math.floorMod(w.sunsetMs - nowMs, dayMs)
                ss > sr
            } else {
                now.hour < 6 || now.hour >= 18
            }
        }
        val night = when (mode) {
            DayNightMode.AUTO -> autoNight
            DayNightMode.DAY -> false
            DayNightMode.NIGHT -> true
        }

        DashTheme(lookOf(if (night) nightStyle else dayStyle, night)) {
            HomeScreen(now = now, onAddAndroidWidget = { info, page -> addAndroidWidget(info, page) })
        }
    }

    companion object {
        private const val REQ_BIND = 4101
        private const val REQ_CONFIGURE = 4102
    }
}
