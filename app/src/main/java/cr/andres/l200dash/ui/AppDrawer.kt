package cr.andres.l200dash.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.andres.l200dash.data.AppEntry
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.HomeRepo
import cr.andres.l200dash.data.ItemKind

/**
 * Cajón con todas las apps: buscador, apps ocultas, y menú con presión larga
 * (agregar al inicio, agregar al dock, ocultar).
 */
@Composable
fun AppDrawer(currentPage: Int, onClose: () -> Unit) {
    val c = LocalLook.current
    val ctx = LocalContext.current
    val apps by AppsRepo.apps.collectAsState()
    val hidden by HomeRepo.hidden.collectAsState()
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }

    val list = apps.filter { a ->
        (showHidden || a.pkg !in hidden) && (query.isBlank() || a.label.contains(query, ignoreCase = true))
    }

    Box(Modifier.fillMaxSize().background(c.panelSolid)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Apps", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                SearchField(query, { query = it }, "Buscar app", Modifier.width(420.dp))
                Spacer(Modifier.width(12.dp))
                OutlineButton(if (showHidden) "Ocultar las ocultas" else "Ver ocultas (${hidden.size})", filled = showHidden) { showHidden = !showHidden }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(c.accentSoft).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = c.accent) }
            }
            Text("Mantén presionada una app para agregarla al inicio, al dock u ocultarla.", color = c.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(list, key = { it.pkg + "/" + it.activity }) { app ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .alpha(if (app.pkg in hidden) 0.45f else 1f)
                            .combinedClickableCompat(
                                onClick = { onClose(); AppsRepo.launch(ctx, app.pkg) },
                                onLongClick = { menuFor = app }
                            )
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val icon = app.icon
                        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(64.dp))
                        else Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(c.line))
                        Spacer(Modifier.height(8.dp))
                        Text(app.label, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        val app = menuFor
        if (app != null) {
            Sheet(app.label, { menuFor = null }, Modifier.width(380.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Agregar al inicio (página ${currentPage + 1})", Modifier.fillMaxWidth()) {
                        val ok = HomeRepo.add(currentPage, ItemKind.APP, pkg = app.pkg)
                        Toast.makeText(ctx, if (ok) "Agregada al inicio" else "No hay espacio en esa página", Toast.LENGTH_SHORT).show()
                        menuFor = null
                    }
                    OutlineButton("Agregar al dock", Modifier.fillMaxWidth()) {
                        HomeRepo.dockAdd(app.pkg)
                        menuFor = null
                    }
                    val isHidden = app.pkg in hidden
                    OutlineButton(if (isHidden) "Mostrar en el cajón" else "Ocultar del cajón", Modifier.fillMaxWidth()) {
                        HomeRepo.setHidden(app.pkg, !isHidden)
                        menuFor = null
                    }
                }
            }
        }
    }
}

