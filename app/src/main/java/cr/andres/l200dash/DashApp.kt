package cr.andres.l200dash

import android.app.Application
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.Prefs
import cr.andres.l200dash.data.StyleRepo
import org.osmdroid.config.Configuration
import java.io.File

class DashApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        StyleRepo.init(this)
        AppsRepo.init(this)

        // osmdroid: identificación ante los servidores de mapas y caché de mosaicos en la app.
        Configuration.getInstance().apply {
            load(this@DashApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osm")
            osmdroidTileCache = File(cacheDir, "osm/tiles")
        }
    }
}
