package cr.andres.l200dash.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.HomeItem
import cr.andres.l200dash.data.HomeLayout
import cr.andres.l200dash.data.HomeRepo
import cr.andres.l200dash.data.ItemKind
import cr.andres.l200dash.data.WidgetHost
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Acciones que las piezas necesitan del resto de la app. */
class HomeActions(
    val openDashcam: () -> Unit,
    val openFolder: (HomeItem) -> Unit,
    val editFolder: (Int, HomeItem) -> Unit,
    val enterEdit: () -> Unit,
    val wazeSplit: () -> Unit,
    val dashcamFullscreen: Boolean
)

/**
 * Detecta una presión larga sin "robarle" los toques normales a lo que hay debajo
 * (mapa, botones, widgets de Android). Si el dedo se mueve, no cuenta.
 */
fun Modifier.longPressPassThrough(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var moved = 0f
        val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull() ?: break
                if (!ch.pressed) break
                moved += ch.positionChange().getDistance()
                if (moved > viewConfiguration.touchSlop) break
            }
            false
        } == null
        if (longPressed) {
            onLongPress()
            // Se consume el resto del gesto para que no se dispare un toque al soltar.
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                ev.changes.forEach { it.consume() }
                if (ev.changes.none { it.pressed }) break
            }
        }
    }
}

@Composable
fun HomeGrid(
    pageIndex: Int,
    layout: HomeLayout,
    editing: Boolean,
    now: LocalDateTime,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val l = LocalLook.current
    val page = layout.pages.getOrNull(pageIndex) ?: return
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier.pointerInput(editing) { detectTapGestures(onLongPress = { if (!editing) actions.enterEdit() }) }
    ) {
        val cellW = maxWidth / layout.cols
        val cellH = maxHeight / layout.rows
        val gap = 14.dp

        // Cuadrícula visible solo al editar
        if (editing) {
            for (y in 0 until layout.rows) for (x in 0 until layout.cols) {
                Box(
                    Modifier.offset(cellW * x + gap / 2, cellH * y + gap / 2).size(cellW - gap, cellH - gap)
                        .clip(RoundedCornerShape(8.dp)).background(l.accent.copy(alpha = 0.05f))
                        .border(1.dp, l.accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                )
            }
        }

        page.items.forEach { item ->
          key(item.id) {
            var drag by remember(item.id, item.x, item.y) { mutableStateOf(Offset.Zero) }
            var grow by remember(item.id, item.w, item.h) { mutableStateOf(Offset.Zero) }
            val cellWpx = with(density) { cellW.toPx() }
            val cellHpx = with(density) { cellH.toPx() }

            Box(
                Modifier
                    .offset(cellW * item.x + gap / 2, cellH * item.y + gap / 2)
                    .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                    .size(cellW * item.w - gap + with(density) { grow.x.toDp() }, cellH * item.h - gap + with(density) { grow.y.toDp() })
                    .graphicsLayer { if (drag != Offset.Zero) { scaleX = 1.03f; scaleY = 1.03f; alpha = 0.9f } }
                    .then(if (!editing) Modifier.longPressPassThrough { actions.enterEdit() } else Modifier)
            ) {
                ItemContent(item, pageIndex, layout, now, actions, Modifier.fillMaxSize())

                if (editing) {
                    // Capa de edición: arrastrar para mover
                    Box(
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33000000))
                            .border(2.dp, l.accent, RoundedCornerShape(12.dp))
                            .pointerInput(item.id, item.x, item.y) {
                                detectDragGestures(
                                    onDrag = { ch, d -> ch.consume(); drag += d },
                                    onDragEnd = {
                                        val nx = item.x + (drag.x / cellWpx).roundToInt()
                                        val ny = item.y + (drag.y / cellHpx).roundToInt()
                                        if (!HomeRepo.move(pageIndex, item.id, nx, ny)) drag = Offset.Zero
                                    },
                                    onDragCancel = { drag = Offset.Zero }
                                )
                            }
                            .then(
                                if (item.kind == ItemKind.FOLDER) Modifier.clickable { actions.editFolder(pageIndex, item) } else Modifier
                            )
                    )
                    // Borrar
                    Box(
                        Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp).size(30.dp).clip(CircleShape)
                            .background(l.rec).clickable {
                                HomeRepo.remove(pageIndex, item.id)
                                if (item.kind == ItemKind.WIDGET) WidgetHost.delete(item.widgetId)
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Close, "Quitar", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    // Editar carpeta
                    if (item.kind == ItemKind.FOLDER) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(8.dp, (-8).dp).size(30.dp).clip(CircleShape).background(l.accent)
                                .clickable { actions.editFolder(pageIndex, item) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Edit, "Editar", tint = Color(0xFF16191B), modifier = Modifier.size(16.dp)) }
                    }
                    // Cambiar tamaño
                    if (item.kind != ItemKind.APP && item.kind != ItemKind.FOLDER) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).offset(8.dp, 8.dp).size(34.dp).clip(CircleShape).background(l.accent)
                                .pointerInput(item.id, item.w, item.h) {
                                    detectDragGestures(
                                        onDrag = { ch, d -> ch.consume(); grow += d },
                                        onDragEnd = {
                                            val nw = item.w + (grow.x / cellWpx).roundToInt()
                                            val nh = item.h + (grow.y / cellHpx).roundToInt()
                                            if (!HomeRepo.resize(pageIndex, item.id, nw, nh)) grow = Offset.Zero
                                        },
                                        onDragCancel = { grow = Offset.Zero }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.OpenInFull, "Tamaño", tint = Color(0xFF16191B), modifier = Modifier.size(18.dp)) }
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun ItemContent(item: HomeItem, pageIndex: Int, layout: HomeLayout, now: LocalDateTime, actions: HomeActions, modifier: Modifier) {
    when (item.kind) {
        ItemKind.APP -> AppTile(item.pkg ?: "", modifier)
        ItemKind.FOLDER -> FolderTile(item, modifier) { actions.openFolder(item) }
        ItemKind.WIDGET -> AndroidWidget(item.widgetId, modifier)
        ItemKind.MAP -> MapWidget(modifier)
        ItemKind.CAMERA -> CameraPanel(modifier, onOpenDashcam = actions.openDashcam, videoAllowed = !actions.dashcamFullscreen)
        ItemKind.MUSIC -> MusicPanel(modifier)
        ItemKind.GAUGES -> GaugesWidget(modifier)
        ItemKind.SPEED -> SpeedWidget(modifier)
        ItemKind.CLOCK -> ClockWidget(now, modifier)
        ItemKind.WEATHER -> WeatherWidget(modifier)
        ItemKind.NAV -> NavWidget(modifier, actions.wazeSplit)
    }
}

/** Ícono de app con nombre. */
@Composable
fun AppTile(pkg: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    val icon = remember(pkg) { AppsRepo.loadIcon(pkg) }
    val label = remember(pkg) { AppsRepo.labelOf(pkg) }
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick?.invoke() ?: AppsRepo.launch(ctx, pkg) }.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) Image(icon, null, Modifier.size(56.dp))
        else Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(l.line))
        Spacer(Modifier.height(4.dp))
        TileLabel(label)
    }
}

@Composable
private fun TileLabel(text: String) {
    val l = LocalLook.current
    Text(
        text, color = l.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(l.panel).padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

@Composable
fun FolderTile(item: HomeItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val l = LocalLook.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(l.panel).border(1.dp, l.accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(5.dp)
        ) {
            val apps = item.apps.take(4)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                apps.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { pkg ->
                            val ic = remember(pkg) { AppsRepo.loadIcon(pkg) }
                            if (ic != null) Image(ic, null, Modifier.size(20.dp)) else Box(Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        TileLabel(item.label ?: "Carpeta")
    }
}

/** Widget de Android alojado por el launcher. */
@Composable
fun AndroidWidget(widgetId: Int, modifier: Modifier = Modifier) {
    val l = LocalLook.current
    val density = LocalDensity.current
    val info = remember(widgetId) { try { WidgetHost.manager.getAppWidgetInfo(widgetId) } catch (_: Exception) { null } }
    if (info == null) {
        Box(modifier.clip(RoundedCornerShape(12.dp)).background(l.panel), contentAlignment = Alignment.Center) {
            Text("Widget no disponible", color = l.dim, fontSize = 12.sp)
        }
        return
    }
    var lastSize by remember { mutableStateOf(0 to 0) }
    AndroidView(
        factory = { ctx ->
            WidgetHost.host.createView(ctx.applicationContext, widgetId, info)
        },
        modifier = modifier.onSizeChanged { sz ->
            val w = with(density) { sz.width.toDp().value.toInt() }
            val h = with(density) { sz.height.toDp().value.toInt() }
            if (abs(w - lastSize.first) > 4 || abs(h - lastSize.second) > 4) {
                lastSize = w to h
                WidgetHost.updateSize(widgetId, w, h)
            }
        }
    )
}
