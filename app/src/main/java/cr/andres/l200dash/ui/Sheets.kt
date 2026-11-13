package cr.andres.l200dash.ui

import android.appwidget.AppWidgetProviderInfo
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.andres.l200dash.data.AppEntry
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.HomeItem
import cr.andres.l200dash.data.HomeRepo
import cr.andres.l200dash.data.ItemKind
import cr.andres.l200dash.data.WidgetHost

/** Contenedor común de las hojas emergentes. */
@Composable
fun Sheet(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(l.radius)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            modifier
                .clip(shape).background(l.panelSolid).border(l.border, l.accent, shape)
                .pointerInput(Unit) { detectTapGestures() } // los toques dentro no cierran la hoja
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = l.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Cerrar", tint = l.accent, modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose).padding(8.dp))
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val l = LocalLook.current
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, l.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = l.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = l.dim, fontSize = 15.sp)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = l.text, fontSize = 15.sp), cursorBrush = SolidColor(l.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Hoja "Agregar": widgets propios, apps y widgets de Android. */
@Composable
fun AddSheet(page: Int, onClose: () -> Unit, onAddAndroidWidget: (AppWidgetProviderInfo) -> Unit) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    fun added(ok: Boolean) {
        if (ok) onClose() else Toast.makeText(ctx, "No hay espacio libre en esta página. Prueba en otra o achica piezas.", Toast.LENGTH_LONG).show()
    }

    Sheet("Agregar a la página ${page + 1}", onClose, Modifier.size(width = 820.dp, height = 600.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Widgets L200", "Apps", "Widgets de Android", "Carpeta").forEachIndexed { i, t ->
                OutlineButton(t, filled = tab == i) { tab = i }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> LazyVerticalGrid(columns = GridCells.Adaptive(240.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ItemKind.values().filter { it.builtin }) { k ->
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, l.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable { added(HomeRepo.add(page, k)) }.padding(14.dp)
                    ) {
                        Text(k.label, color = l.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tamaño inicial ${k.defW}×${k.defH} · mínimo ${k.minW}×${k.minH}", color = l.dim, fontSize = 12.sp)
                    }
                }
            }
            1 -> AppPicker { app -> added(HomeRepo.add(page, ItemKind.APP, pkg = app.pkg)) }
            2 -> {
                val groups = remember { WidgetHost.providers(ctx) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { (appName, list) ->
                        item {
                            Text(appName, color = l.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(list) { info ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x14FFFFFF))
                                    .clickable { onAddAndroidWidget(info); onClose() }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Widgets, null, tint = l.accent)
                                Spacer(Modifier.width(10.dp))
                                Text(WidgetHost.label(ctx, info), color = l.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                val (w, h) = WidgetHost.cellsFor(ctx, info, 12, 7)
                                Text("${w}×${h}", color = l.dim, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            3 -> Column {
                Text("Crea una carpeta vacía y luego toca el lápiz para elegir sus apps.", color = l.dim, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlineButton("Crear carpeta", filled = true) { added(HomeRepo.add(page, ItemKind.FOLDER, label = "Carpeta")) }
            }
        }
    }
}

/** Lista de apps con buscador; [onPick] recibe la app tocada. */
@Composable
fun AppPicker(onPick: (AppEntry) -> Unit) {
    val l = LocalLook.current
    val apps by AppsRepo.apps.collectAsState()
    var q by remember { mutableStateOf("") }
    Column {
        SearchField(q, { q = it }, "Buscar app", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        val filtered = apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
        LazyVerticalGrid(columns = GridCells.Adaptive(110.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.pkg + "/" + it.activity }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPick(app) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val ic = app.icon
                    if (ic != null) Image(ic, null, Modifier.size(52.dp)) else Box(Modifier.size(52.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(app.label, color = l.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Carpeta abierta: sus apps en grande. */
@Composable
fun FolderPopup(item: HomeItem, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Sheet(item.label ?: "Carpeta", onClose, Modifier.width(560.dp)) {
        LazyVerticalGrid(columns = GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(320.dp)) {
            items(item.apps) { pkg ->
                AppTile(pkg, Modifier.height(100.dp)) {
                    onClose()
                    AppsRepo.launch(ctx, pkg)
                }
            }
        }
    }
}

/** Editar carpeta: nombre y apps. */
@Composable
fun FolderEditor(page: Int, item: HomeItem, onClose: () -> Unit) {
    val l = LocalLook.current
    val apps by AppsRepo.apps.collectAsState()
    var name by remember { mutableStateOf(item.label ?: "Carpeta") }
    var chosen by remember { mutableStateOf(item.apps) }
    Sheet("Editar carpeta", {
        HomeRepo.update(page, item.copy(label = name.ifBlank { "Carpeta" }, apps = chosen))
        onClose()
    }, Modifier.size(width = 620.dp, height = 600.dp)) {
        SearchField(name, { name = it.take(20) }, "Nombre de la carpeta", Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text("Toca las apps para agregarlas o quitarlas. Se guarda al cerrar.", color = l.dim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxHeight()) {
            items(apps, key = { it.pkg + "/" + it.activity }) { app ->
                val sel = app.pkg in chosen
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { chosen = if (sel) chosen - app.pkg else chosen + app.pkg }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (sel) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null, tint = l.accent)
                    Spacer(Modifier.width(10.dp))
                    val ic = app.icon
                    if (ic != null) Image(ic, null, Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, color = l.text, fontSize = 14.sp)
                }
            }
        }
    }
}
