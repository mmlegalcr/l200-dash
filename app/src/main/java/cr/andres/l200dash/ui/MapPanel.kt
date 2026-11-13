package cr.andres.l200dash.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView as GMapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker as GMarker
import com.google.android.gms.maps.model.MarkerOptions
import cr.andres.l200dash.BuildConfig
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.GpsFix
import cr.andres.l200dash.data.LocationRepo
import cr.andres.l200dash.data.NavRepo
import cr.andres.l200dash.data.Prefs
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val CARTO_DARK = XYTileSource(
    "CartoDark", 0, 19, 256, ".png",
    arrayOf("https://a.basemaps.cartocdn.com/dark_all/", "https://b.basemaps.cartocdn.com/dark_all/", "https://c.basemaps.cartocdn.com/dark_all/"),
    "© OpenStreetMap © CARTO"
)
private val CARTO_LIGHT = XYTileSource(
    "CartoVoyager", 0, 19, 256, ".png",
    arrayOf("https://a.basemaps.cartocdn.com/rastertiles/voyager/", "https://b.basemaps.cartocdn.com/rastertiles/voyager/", "https://c.basemaps.cartocdn.com/rastertiles/voyager/"),
    "© OpenStreetMap © CARTO"
)

private fun providers(): List<String> =
    (if (BuildConfig.HAS_MAPS_KEY) listOf("google") else emptyList()) + listOf("carto_dark", "carto_light", "topo")

private fun providerLabel(p: String) = when (p) {
    "google" -> "Google · tráfico"
    "carto_dark" -> "Oscuro"
    "carto_light" -> "Claro"
    else -> "Relieve"
}

/** Widget de mapa: Google Maps con tráfico (si hay key) o mapas libres, + indicaciones de Waze. */
@Composable
fun MapWidget(modifier: Modifier = Modifier) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    val fix by LocationRepo.fix.collectAsState()
    val nav by NavRepo.nav.collectAsState()
    val saved by Prefs.mapProvider.collectAsState()
    val provs = providers()
    val provider = if (saved in provs) saved else provs.first()
    val follow = remember { mutableStateOf(true) }

    GlowPanel(
        title = "Navegación",
        icon = Icons.Filled.Map,
        modifier = modifier,
        tag = providerLabel(provider) + " ▸",
        onTag = { Prefs.setMapProvider(provs[(provs.indexOf(provider) + 1) % provs.size]) },
        footer = {
            Text(if (provider == "google") "© Google" else "© OpenStreetMap", color = l.dim, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            val f = fix
            Text(
                buildString {
                    append(f?.bearing?.let { "↗ " + cardinal(it) } ?: "—")
                    f?.altitudeM?.let { append(" · %.0f m".format(it)) }
                },
                color = l.text, fontSize = 12.sp
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            key(provider) {
                if (provider == "google") GoogleMapContent(fix, l.night, l.accent.toArgb(), follow)
                else OsmMapContent(provider, fix, l.night, l.accent.toArgb(), follow)
            }

            val n = nav
            if (n != null) {
                Inset(Modifier.fillMaxWidth().padding(10.dp).clickable { AppsRepo.launch(ctx, NavRepo.WAZE) }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ic = remember(n.icon) { n.icon?.asImageBitmap() }
                        if (ic != null) Image(ic, null, Modifier.size(40.dp))
                        else Icon(Icons.Filled.TurnRight, null, tint = l.accent, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(n.title, color = l.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (n.text.isNotBlank()) Text(n.text, color = l.dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (!follow.value) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = if (n != null) 86.dp else 10.dp, end = 10.dp)
                        .size(42.dp).clip(CircleShape).background(l.panelSolid)
                        .clickable { follow.value = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.MyLocation, null, tint = l.accent) }
            }

            Inset(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            n?.sub?.ifBlank { null } ?: if (n != null) "Navegando con Waze" else "Sin ruta activa",
                            color = l.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(if (n != null) "Toca para volver a Waze" else "Abre Waze para navegar", color = l.dim, fontSize = 11.sp)
                    }
                    OutlineButton("Waze") { AppsRepo.launch(ctx, NavRepo.WAZE) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Google Maps

@Composable
private fun GoogleMapContent(fix: GpsFix?, night: Boolean, accent: Int, follow: MutableState<Boolean>) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val mapView = remember { GMapView(ctx).apply { onCreate(null) } }
    val gmap = remember { mutableStateOf<GoogleMap?>(null) }
    val marker = remember { mutableStateOf<GMarker?>(null) }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose {
            lifecycle.lifecycle.removeObserver(obs)
            try {
                mapView.onPause(); mapView.onStop(); mapView.onDestroy()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.isTrafficEnabled = true
            m.isBuildingsEnabled = true
            m.uiSettings.isZoomControlsEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isMapToolbarEnabled = false
            m.uiSettings.isMyLocationButtonEnabled = false
            m.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) follow.value = false
            }
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(9.9281, -84.0907), 15f))
            gmap.value = m
        }
    }

    // Estilo según día/noche
    LaunchedEffect(gmap.value, night) {
        gmap.value?.setMapStyle(if (night) MapStyleOptions(NIGHT_STYLE) else null)
    }

    // Marcador propio (flecha con el color de acento) y cámara que sigue al carro
    LaunchedEffect(gmap.value, fix, follow.value, accent) {
        val m = gmap.value ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        val pos = LatLng(f.lat, f.lon)
        val mk = marker.value
        if (mk == null) {
            marker.value = m.addMarker(
                MarkerOptions().position(pos).flat(true).anchor(0.5f, 0.5f)
                    .icon(BitmapDescriptorFactory.fromBitmap(arrowBitmap(ctx, accent)))
                    .rotation(f.bearing ?: 0f)
            )
        } else {
            mk.position = pos
            mk.rotation = f.bearing ?: mk.rotation
        }
        if (follow.value) {
            val cam = CameraPosition.Builder().target(pos).zoom(16.5f).tilt(45f).bearing(f.bearing ?: m.cameraPosition.bearing).build()
            m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 900, null)
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

// ------------------------------------------------------------------ Mapas libres (osmdroid)

@Composable
private fun OsmMapContent(provider: String, fix: GpsFix?, night: Boolean, accent: Int, follow: MutableState<Boolean>) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val arrow = remember(accent) { BitmapDrawable(ctx.resources, arrowBitmap(ctx, accent)) }
    val mapState = remember { mutableStateOf<MapView?>(null) }
    val marker = remember { mutableStateOf<Marker?>(null) }
    val src: ITileSource = when (provider) {
        "carto_dark" -> CARTO_DARK
        "carto_light" -> CARTO_LIGHT
        else -> TileSourceFactory.OpenTopo
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapState.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapState.value?.onPause()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose {
            lifecycle.lifecycle.removeObserver(obs)
            mapState.value?.onDetach()
        }
    }

    AndroidView(
        factory = { c ->
            MapView(c).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(9.9281, -84.0907))
                isTilesScaledToDpi = true
                setTileSource(src)
                val m = Marker(this).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    icon = arrow
                }
                overlays.add(m)
                marker.value = m
                setOnTouchListener { _, ev ->
                    if (ev.action == MotionEvent.ACTION_MOVE) follow.value = false
                    false
                }
                mapState.value = this
            }
        },
        update = { mv ->
            // El tema oscuro de CARTO ya es oscuro; el resto se oscurece de noche.
            mv.overlayManager.tilesOverlay.setColorFilter(if (night && provider != "carto_dark") nightFilter() else null)
            marker.value?.icon = arrow
            fix?.let { f ->
                val p = GeoPoint(f.lat, f.lon)
                marker.value?.position = p
                marker.value?.rotation = -(f.bearing ?: 0f)
                if (follow.value) mv.controller.animateTo(p)
            }
            mv.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun nightFilter(): ColorMatrixColorFilter =
    ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.45f, 0.48f, 0.55f, 1f) })

/** Flecha de posición con el color de acento. */
private fun arrowBitmap(ctx: android.content.Context, color: Int): Bitmap {
    val d = ctx.resources.displayMetrics.density
    val s = (40 * d).toInt()
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(s / 2f, s / 2f, s / 2.4f, p)
    p.color = color
    val path = Path().apply {
        moveTo(s / 2f, s * 0.18f)
        lineTo(s * 0.74f, s * 0.78f)
        lineTo(s / 2f, s * 0.64f)
        lineTo(s * 0.26f, s * 0.78f)
        close()
    }
    c.drawPath(path, p)
    return bmp
}

@Suppress("unused")
private fun hasLocationPermission(ctx: android.content.Context) =
    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Estilo nocturno para Google Maps (grafito con vías cálidas). */
private const val NIGHT_STYLE = """
[
  {"elementType":"geometry","stylers":[{"color":"#1d2328"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#9aa59f"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#12171a"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#3a4248"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#232a2f"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#1f2e24"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2e363c"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#1a1f23"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#3a4249"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#6b5428"}]},
  {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#3b2f18"}]},
  {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#e3c48a"}]},
  {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#262d33"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1a24"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4e6a80"}]}
]
"""
