package cr.andres.l200dash.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cr.andres.l200dash.data.DayNightMode
import cr.andres.l200dash.data.HomeItem
import cr.andres.l200dash.data.HomeRepo
import cr.andres.l200dash.data.SplitService
import cr.andres.l200dash.data.StyleRepo
import cr.andres.l200dash.data.WeatherRepo
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "CR")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(now: LocalDateTime, onAddAndroidWidget: (AppWidgetProviderInfo, Int) -> Unit) {
    val l = LocalLook.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hero = rememberHero(l.style.image)
    val layout by HomeRepo.layout.collectAsState()
    val weather by WeatherRepo.weather.collectAsState()
    val mode by StyleRepo.mode.collectAsState()
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var dockPicker by remember { mutableStateOf(false) }
    var folderOpen by remember { mutableStateOf<HomeItem?>(null) }
    var folderEdit by remember { mutableStateOf<Pair<Int, HomeItem>?>(null) }
    var dashcamFull by remember { mutableStateOf(false) }

    val pager = rememberPagerState(pageCount = { layout.pages.size })
    val currentPage = pager.currentPage.coerceIn(0, (layout.pages.size - 1).coerceAtLeast(0))

    val actions = HomeActions(
        openDashcam = { dashcamFull = true },
        openFolder = { folderOpen = it },
        editFolder = { p, item -> folderEdit = p to item },
        enterEdit = { editing = true },
        wazeSplit = { SplitService.openSplit(ctx, "com.waze") },
        dashcamFullscreen = dashcamFull
    )

    BackHandler(enabled = true) {
        when {
            dashcamFull -> dashcamFull = false
            folderEdit != null -> folderEdit = null
            folderOpen != null -> folderOpen = null
            addOpen -> addOpen = false
            dockPicker -> dockPicker = false
            settingsOpen -> settingsOpen = false
            drawerOpen -> drawerOpen = false
            editing -> editing = false
            else -> scope.launch { pager.animateScrollToPage(0) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        DashBackground(hero, l.style.imageBrightness)

        if (maxWidth < 900.dp) {
            // Pantalla dividida (Waze a un lado): versión compacta del tablero.
            CompactDash(now, actions)
        } else {
            Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 30.dp, top = 12.dp, bottom = 14.dp)) {
                if (editing) {
                    EditToolbar(
                        page = currentPage,
                        pages = layout.pages.size,
                        onPrev = { scope.launch { pager.animateScrollToPage((currentPage - 1).coerceAtLeast(0)) } },
                        onNext = { scope.launch { pager.animateScrollToPage((currentPage + 1).coerceAtMost(layout.pages.size - 1)) } },
                        onAdd = { addOpen = true },
                        onNewPage = {
                            val idx = HomeRepo.addPage()
                            scope.launch { pager.animateScrollToPage(idx) }
                        },
                        onDeletePage = { HomeRepo.removePage(currentPage) },
                        onDone = { editing = false }
                    )
                } else {
                    TopChips(now, weather?.let { WeatherRepo.describe(it.code, it.isDay) to it.tempC }, mode, onOpenSettings = { settingsOpen = true })
                }
                Spacer(Modifier.height(12.dp))

                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !editing,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { p ->
                    HomeGrid(p, layout, editing, now, actions, Modifier.fillMaxSize())
                }

                // Indicador de páginas
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(layout.pages.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 4.dp).size(if (i == currentPage) 9.dp else 7.dp).clip(CircleShape)
                                .background(if (i == currentPage) l.accent else l.text.copy(alpha = 0.35f))
                        )
                    }
                }

                Dock(
                    now, editing,
                    onOpenDrawer = { drawerOpen = true },
                    onCustomize = { settingsOpen = true },
                    onAddToDock = { dockPicker = true },
                    modifier = Modifier.fillMaxWidth().height(78.dp)
                )
            }
        }

        AnimatedVisibility(visible = drawerOpen, enter = fadeIn(), exit = fadeOut()) {
            AppDrawer(currentPage = currentPage, onClose = { drawerOpen = false })
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SettingsSheet(currentlyNight = l.night, onClose = { settingsOpen = false }, modifier = Modifier.padding(12.dp))
        }

        if (addOpen) {
            AddSheet(currentPage, onClose = { addOpen = false }, onAddAndroidWidget = { info -> onAddAndroidWidget(info, currentPage) })
        }
        if (dockPicker) {
            Sheet("Agregar al dock", { dockPicker = false }, Modifier.size(width = 760.dp, height = 560.dp)) {
                AppPicker { app -> HomeRepo.dockAdd(app.pkg); dockPicker = false }
            }
        }
        folderOpen?.let { f -> FolderPopup(f) { folderOpen = null } }
        folderEdit?.let { (p, f) -> FolderEditor(p, f) { folderEdit = null } }

        if (dashcamFull) DashcamScreen(onClose = { dashcamFull = false })
    }

    // Si se borra la página actual, volver a una que exista.
    LaunchedEffect(layout.pages.size) {
        if (pager.currentPage >= layout.pages.size) pager.scrollToPage((layout.pages.size - 1).coerceAtLeast(0))
    }
}

@Composable
private fun TopChips(now: LocalDateTime, weather: Pair<Pair<String, String>, Double>?, mode: DayNightMode, onOpenSettings: () -> Unit) {
    val l = LocalLook.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip(onClick = onOpenSettings, onLongClick = onOpenSettings) {
            Text("L200", color = l.accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Chip {
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() },
                color = l.text, fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.weight(1f))
        weather?.let { (dw, temp) ->
            val (desc, emoji) = dw
            Chip { Text("$emoji ${"%.0f".format(temp)}°  ·  $desc", color = l.text, fontSize = 14.sp) }
        }
        Chip(onClick = {
            StyleRepo.setMode(
                when (mode) {
                    DayNightMode.AUTO -> DayNightMode.DAY
                    DayNightMode.DAY -> DayNightMode.NIGHT
                    DayNightMode.NIGHT -> DayNightMode.AUTO
                }
            )
        }) {
            val icon = if (l.night) "☾" else "☀"
            val label = when (mode) {
                DayNightMode.AUTO -> if (l.night) "Noche · auto" else "Día · auto"
                DayNightMode.DAY -> "Día"
                DayNightMode.NIGHT -> "Noche"
            }
            Text("$icon $label", color = l.text, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EditToolbar(
    page: Int, pages: Int,
    onPrev: () -> Unit, onNext: () -> Unit, onAdd: () -> Unit,
    onNewPage: () -> Unit, onDeletePage: () -> Unit, onDone: () -> Unit
) {
    val l = LocalLook.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip { Text("Editando", color = l.accent, fontSize = 15.sp, fontWeight = FontWeight.Black) }
        OutlineButton("◀", enabled = page > 0, onClick = onPrev)
        Text("Página ${page + 1} de $pages", color = l.text, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).background(l.panel).padding(horizontal = 10.dp, vertical = 6.dp))
        OutlineButton("▶", enabled = page < pages - 1, onClick = onNext)
        Spacer(Modifier.size(8.dp))
        OutlineButton("+ Agregar", filled = true, onClick = onAdd)
        OutlineButton("Página nueva", onClick = onNewPage)
        OutlineButton("Borrar página", enabled = pages > 1, onClick = onDeletePage)
        Spacer(Modifier.weight(1f))
        Text("Arrastra para mover · círculo para tamaño · ✕ para quitar", color = l.dim, fontSize = 12.sp)
        Spacer(Modifier.size(8.dp))
        OutlineButton("Listo", filled = true, onClick = onDone)
    }
}

/** Versión angosta para cuando Waze ocupa la otra mitad de la pantalla. */
@Composable
private fun CompactDash(now: LocalDateTime, actions: HomeActions) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SpeedWidget(Modifier.fillMaxWidth().weight(0.26f))
        CameraPanel(Modifier.fillMaxWidth().weight(0.42f), onOpenDashcam = actions.openDashcam, videoAllowed = !actions.dashcamFullscreen)
        MusicPanel(Modifier.fillMaxWidth().weight(0.32f))
    }
}
