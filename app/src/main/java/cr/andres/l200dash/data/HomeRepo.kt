package cr.andres.l200dash.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Tipos de pieza que se pueden poner en el inicio. */
enum class ItemKind(val label: String, val minW: Int, val minH: Int, val defW: Int, val defH: Int, val builtin: Boolean) {
    APP("App", 1, 1, 1, 1, false),
    FOLDER("Carpeta", 1, 1, 1, 1, false),
    WIDGET("Widget de Android", 1, 1, 2, 2, false),
    MAP("Mapa", 2, 2, 3, 5, true),
    CAMERA("Cámara / Dashcam", 2, 3, 3, 4, true),
    MUSIC("Música", 2, 3, 3, 3, true),
    GAUGES("Medidores", 3, 2, 6, 3, true),
    SPEED("Velocímetro", 2, 2, 2, 2, true),
    CLOCK("Reloj", 2, 1, 3, 2, true),
    WEATHER("Clima", 2, 2, 3, 2, true),
    NAV("Indicaciones de Waze", 2, 1, 3, 2, true)
}

data class HomeItem(
    val id: Long,
    val kind: ItemKind,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    /** APP: paquete. FOLDER: no aplica. */
    val pkg: String? = null,
    /** FOLDER: nombre. */
    val label: String? = null,
    /** WIDGET: id del widget de Android. */
    val widgetId: Int = -1,
    /** FOLDER: paquetes de las apps. */
    val apps: List<String> = emptyList()
) {
    fun overlaps(o: HomeItem) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.name).put("x", x).put("y", y).put("w", w).put("h", h)
        .put("pkg", pkg ?: "").put("label", label ?: "").put("widgetId", widgetId)
        .put("apps", JSONArray(apps))

    companion object {
        fun fromJson(o: JSONObject): HomeItem? = try {
            val arr = o.optJSONArray("apps")
            HomeItem(
                id = o.getLong("id"),
                kind = ItemKind.valueOf(o.getString("kind")),
                x = o.getInt("x"), y = o.getInt("y"), w = o.getInt("w"), h = o.getInt("h"),
                pkg = o.optString("pkg").ifBlank { null },
                label = o.optString("label").ifBlank { null },
                widgetId = o.optInt("widgetId", -1),
                apps = if (arr == null) emptyList() else (0 until arr.length()).map { arr.getString(it) }
            )
        } catch (_: Exception) {
            null
        }
    }
}

data class HomePage(val items: List<HomeItem>)

data class HomeLayout(
    val cols: Int,
    val rows: Int,
    val pages: List<HomePage>,
    /** Paquetes del dock. "@apps" = botón del cajón de apps. */
    val dock: List<String>
)

/** Distribución del inicio: páginas, piezas y dock. Se guarda en el teléfono como JSON. */
object HomeRepo {
    const val APPS_BUTTON = "@apps"

    private lateinit var sp: SharedPreferences
    private val _layout = MutableStateFlow(defaultLayout())
    val layout: StateFlow<HomeLayout> = _layout

    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    val hidden: StateFlow<Set<String>> = _hidden

    private var nextId = System.currentTimeMillis()
    private fun newId() = ++nextId

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
        _layout.value = load() ?: defaultLayout().let { d ->
            // En la distribución inicial solo se ponen las apps que estén instaladas.
            val pm = ctx.packageManager
            fun ok(p: String) = try { pm.getPackageInfo(p, 0); true } catch (_: Exception) { false }
            d.copy(
                pages = d.pages.map { pg ->
                    HomePage(pg.items.filter { it.kind != ItemKind.APP || ok(it.pkg ?: "") }
                        .map { if (it.kind == ItemKind.FOLDER) it.copy(apps = it.apps.filter(::ok)) else it })
                },
                dock = d.dock.filter { it == APPS_BUTTON || ok(it) }
            )
        }
        _hidden.value = sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()
    }

    private fun defaultLayout(): HomeLayout {
        val p1 = listOf(
            HomeItem(1, ItemKind.MAP, 0, 0, 3, 7),
            HomeItem(2, ItemKind.GAUGES, 3, 0, 6, 3),
            HomeItem(3, ItemKind.CAMERA, 9, 0, 3, 4),
            HomeItem(4, ItemKind.MUSIC, 9, 4, 3, 3)
        )
        val p2 = listOf(
            HomeItem(10, ItemKind.CLOCK, 0, 0, 3, 2),
            HomeItem(11, ItemKind.WEATHER, 3, 0, 3, 2),
            HomeItem(12, ItemKind.NAV, 6, 0, 3, 2),
            HomeItem(13, ItemKind.APP, 0, 3, 1, 1, pkg = "com.waze"),
            HomeItem(14, ItemKind.APP, 1, 3, 1, 1, pkg = "com.google.android.apps.maps"),
            HomeItem(15, ItemKind.APP, 2, 3, 1, 1, pkg = "com.spotify.music"),
            HomeItem(16, ItemKind.APP, 3, 3, 1, 1, pkg = "com.google.android.youtube"),
            HomeItem(17, ItemKind.APP, 4, 3, 1, 1, pkg = "com.whatsapp"),
            HomeItem(18, ItemKind.APP, 5, 3, 1, 1, pkg = "com.android.chrome"),
            HomeItem(19, ItemKind.FOLDER, 6, 3, 1, 1, label = "Carro", apps = listOf("com.tw.bt", "com.tw.radio", "com.tw.eq", "com.dofun.carsetting", "com.tw.music"))
        )
        return HomeLayout(
            cols = 12, rows = 7,
            pages = listOf(HomePage(p1), HomePage(p2)),
            dock = listOf("com.waze", "com.spotify.music", "com.tw.bt", "com.tw.radio", "com.dofun.carsetting", APPS_BUTTON)
        )
    }

    private fun load(): HomeLayout? = try {
        sp.getString("layout", null)?.let { s ->
            val o = JSONObject(s)
            val pagesArr = o.getJSONArray("pages")
            val pages = (0 until pagesArr.length()).map { i ->
                val items = pagesArr.getJSONArray(i)
                HomePage((0 until items.length()).mapNotNull { HomeItem.fromJson(items.getJSONObject(it)) })
            }
            val dockArr = o.getJSONArray("dock")
            HomeLayout(
                cols = o.getInt("cols"), rows = o.getInt("rows"),
                pages = pages.ifEmpty { listOf(HomePage(emptyList())) },
                dock = (0 until dockArr.length()).map { dockArr.getString(it) }
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun save(l: HomeLayout) {
        _layout.value = l
        val pages = JSONArray()
        l.pages.forEach { p -> pages.put(JSONArray().also { a -> p.items.forEach { a.put(it.toJson()) } }) }
        val o = JSONObject().put("cols", l.cols).put("rows", l.rows).put("pages", pages).put("dock", JSONArray(l.dock))
        sp.edit().putString("layout", o.toString()).apply()
    }

    private fun editPage(page: Int, change: (List<HomeItem>) -> List<HomeItem>) {
        val l = _layout.value
        if (page !in l.pages.indices) return
        save(l.copy(pages = l.pages.mapIndexed { i, p -> if (i == page) HomePage(change(p.items)) else p }))
    }

    /** ¿Cabe la pieza en esa posición sin salirse ni chocar con otras? */
    fun fits(page: Int, cand: HomeItem): Boolean {
        val l = _layout.value
        if (cand.x < 0 || cand.y < 0 || cand.x + cand.w > l.cols || cand.y + cand.h > l.rows) return false
        val items = l.pages.getOrNull(page)?.items ?: return false
        return items.none { it.id != cand.id && it.overlaps(cand) }
    }

    fun move(page: Int, id: Long, x: Int, y: Int): Boolean {
        val item = _layout.value.pages.getOrNull(page)?.items?.firstOrNull { it.id == id } ?: return false
        val cand = item.copy(x = x, y = y)
        if (!fits(page, cand)) return false
        editPage(page) { list -> list.map { if (it.id == id) cand else it } }
        return true
    }

    fun resize(page: Int, id: Long, w: Int, h: Int): Boolean {
        val item = _layout.value.pages.getOrNull(page)?.items?.firstOrNull { it.id == id } ?: return false
        val cand = item.copy(w = w.coerceAtLeast(item.kind.minW), h = h.coerceAtLeast(item.kind.minH))
        if (!fits(page, cand)) return false
        editPage(page) { list -> list.map { if (it.id == id) cand else it } }
        return true
    }

    fun remove(page: Int, id: Long): HomeItem? {
        val item = _layout.value.pages.getOrNull(page)?.items?.firstOrNull { it.id == id }
        editPage(page) { list -> list.filterNot { it.id == id } }
        return item
    }

    fun update(page: Int, item: HomeItem) = editPage(page) { list -> list.map { if (it.id == item.id) item else it } }

    /** Busca el primer hueco libre (de arriba a abajo, izquierda a derecha). */
    private fun freeSpot(page: Int, w: Int, h: Int): Pair<Int, Int>? {
        val l = _layout.value
        for (y in 0..(l.rows - h)) for (x in 0..(l.cols - w)) {
            if (fits(page, HomeItem(-1, ItemKind.APP, x, y, w, h))) return x to y
        }
        return null
    }

    /** Agrega una pieza en el primer hueco; si no cabe con su tamaño ideal prueba con el mínimo. */
    fun add(page: Int, kind: ItemKind, w: Int = kind.defW, h: Int = kind.defH, pkg: String? = null, label: String? = null, widgetId: Int = -1, apps: List<String> = emptyList()): Boolean {
        val l = _layout.value
        val ww = w.coerceIn(kind.minW, l.cols)
        val hh = h.coerceIn(kind.minH, l.rows)
        val spot = freeSpot(page, ww, hh)?.let { Triple(it.first, it.second, ww to hh) }
            ?: freeSpot(page, kind.minW, kind.minH)?.let { Triple(it.first, it.second, kind.minW to kind.minH) }
            ?: return false
        val item = HomeItem(newId(), kind, spot.first, spot.second, spot.third.first, spot.third.second, pkg, label, widgetId, apps)
        editPage(page) { it + item }
        return true
    }

    fun addPage(): Int {
        val l = _layout.value
        save(l.copy(pages = l.pages + HomePage(emptyList())))
        return l.pages.size
    }

    fun removePage(page: Int) {
        val l = _layout.value
        if (l.pages.size <= 1 || page !in l.pages.indices) return
        save(l.copy(pages = l.pages.filterIndexed { i, _ -> i != page }))
    }

    fun setGrid(cols: Int, rows: Int) {
        val l = _layout.value
        // Las piezas que no quepan se encogen o se acomodan dentro de los nuevos límites.
        val pages = l.pages.map { p ->
            HomePage(p.items.map { it ->
                val w = it.w.coerceAtMost(cols)
                val h = it.h.coerceAtMost(rows)
                it.copy(w = w, h = h, x = it.x.coerceAtMost(cols - w), y = it.y.coerceAtMost(rows - h))
            })
        }
        save(l.copy(cols = cols, rows = rows, pages = pages))
    }

    fun resetLayout() = save(defaultLayout())

    // ---- Dock
    fun dockAdd(pkg: String) {
        val l = _layout.value
        if (pkg in l.dock) return
        val withoutApps = l.dock.filterNot { it == APPS_BUTTON }
        save(l.copy(dock = withoutApps + pkg + APPS_BUTTON))
    }

    fun dockRemove(pkg: String) {
        val l = _layout.value
        save(l.copy(dock = l.dock.filterNot { it == pkg }))
    }

    // ---- Apps ocultas en el cajón
    fun setHidden(pkg: String, hide: Boolean) {
        val s = if (hide) _hidden.value + pkg else _hidden.value - pkg
        _hidden.value = s
        sp.edit().putStringSet("hidden", s).apply()
    }
}
