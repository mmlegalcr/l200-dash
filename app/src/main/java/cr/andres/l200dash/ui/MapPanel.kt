package cr.andres.l200dash.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.LocationRepo
import cr.andres.l200dash.data.NavRepo
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Mapa en vivo (OpenStreetMap) + indicaciones de Waze + botón para abrir Waze. */
@Composable
fun MapPanel(modifier: Modifier = Modifier) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val fix by LocationRepo.fix.collectAsState()
    val nav by NavRepo.nav.collectAsState()
    var topo by remember { mutableStateOf(true) }
    var follow by remember { mutableStateOf(true) }

    val arrow = remember(l.accent) { arrowDrawable(ctx, l.accent.toArgb()) }
    val mapState = remember { mutableStateOf<MapView?>(null) }
    val marker = remember { mutableStateOf<Marker?>(null) }

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

    GlowPanel(
        title = "Navegación",
        icon = Icons.Filled.Map,
        modifier = modifier,
        tag = if (topo) "Relieve" else "Calles",
        onTag = { topo = !topo },
        footer = {
            Text("© OpenStreetMap", color = l.dim, fontSize = 10.sp)
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
            AndroidView(
                factory = { c ->
                    MapView(c).apply {
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(15.5)
                        controller.setCenter(GeoPoint(9.9281, -84.0907))
                        isTilesScaledToDpi = true
                        val m = Marker(this).apply {
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setInfoWindow(null)
                            icon = arrow
                        }
                        overlays.add(m)
                        marker.value = m
                        // Al arrastrar el mapa se deja de seguir al carro.
                        setOnTouchListener { _, ev ->
                            if (ev.action == MotionEvent.ACTION_MOVE) follow = false
                            false
                        }
                        mapState.value = this
                    }
                },
                update = { mv ->
                    val src = if (topo) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK
                    if (mv.tileProvider.tileSource.name() != src.name()) mv.setTileSource(src)
                    mv.overlayManager.tilesOverlay.setColorFilter(if (l.night) nightFilter() else null)
                    marker.value?.icon = arrow
                    fix?.let { f ->
                        val p = GeoPoint(f.lat, f.lon)
                        marker.value?.position = p
                        marker.value?.rotation = -(f.bearing ?: 0f)
                        if (follow) mv.controller.animateTo(p)
                    }
                    mv.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Indicación de Waze (si está navegando)
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

            // Recentrar
            if (!follow) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = if (n != null) 86.dp else 10.dp, end = 10.dp)
                        .size(42.dp).clip(CircleShape).background(l.panelSolid)
                        .clickable { follow = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.MyLocation, null, tint = l.accent) }
            }

            // Barra inferior: datos de Waze + abrir Waze
            Inset(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            n?.sub?.ifBlank { null } ?: if (n != null) "Navegando con Waze" else "Sin ruta activa",
                            color = l.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(if (n != null) "Toca para volver a Waze" else "Abre Waze para navegar", color = l.dim, fontSize = 11.sp)
                    }
                    OutlineButton("Abrir Waze") { AppsRepo.launch(ctx, NavRepo.WAZE) }
                }
            }
        }
    }
}

/** Oscurece y enfría los mosaicos del mapa para el modo noche. */
private fun nightFilter(): ColorMatrixColorFilter {
    val m = ColorMatrix().apply { setScale(0.45f, 0.48f, 0.55f, 1f) }
    return ColorMatrixColorFilter(m)
}

/** Flecha de posición dibujada con el color de acento. */
private fun arrowDrawable(ctx: android.content.Context, color: Int): BitmapDrawable {
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
    return BitmapDrawable(ctx.resources, bmp)
}
