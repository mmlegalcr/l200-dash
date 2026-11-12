package cr.andres.l200dash.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Servicio vacío: solo existe para que Android nos deje leer las sesiones multimedia
 * (Spotify, etc.). El usuario lo habilita en "Acceso a notificaciones".
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaRepo.start(applicationContext)
        // Si Waze ya estaba navegando al conectar, leer su notificación actual.
        try {
            activeNotifications?.forEach { NavRepo.onPosted(it, applicationContext) }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { NavRepo.onPosted(it, applicationContext) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { NavRepo.onRemoved(it) }
    }
}

data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val playing: Boolean,
    val pkg: String,
    val durationMs: Long,
    val positionMs: Long,
    /** elapsedRealtime cuando se leyó positionMs, para extrapolar el avance. */
    val positionAt: Long,
    val speed: Float
)

object MediaRepo {
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess

    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var listening = false
    private lateinit var appCtx: Context

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list ?: emptyList())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            refreshSessions()
        }
    }

    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        val granted = NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
        _hasAccess.value = granted
        if (!granted) return
        val manager = appCtx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        msm = manager
        val cn = ComponentName(appCtx, MediaListener::class.java)
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
                listening = true
            }
            pick(manager.getActiveSessions(cn))
        } catch (_: SecurityException) {
            _hasAccess.value = false
        }
    }

    private fun refreshSessions() {
        val manager = msm ?: return
        try {
            pick(manager.getActiveSessions(ComponentName(appCtx, MediaListener::class.java)))
        } catch (_: SecurityException) {
        }
    }

    /** Prefiere la sesión que está sonando; si ninguna, Spotify; si no, la primera. */
    private fun pick(list: List<MediaController>) {
        val chosen = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.packageName == "com.spotify.music" }
            ?: list.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = chosen
            chosen?.registerCallback(controllerCallback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _now.value = null
            return
        }
        val md = c.metadata
        val st = c.playbackState
        _now.value = NowPlaying(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = st?.state == PlaybackState.STATE_PLAYING,
            pkg = c.packageName,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = st?.position ?: 0L,
            positionAt = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed ?: 1f
        )
    }

    // -------------------------------------------------------------- Controles
    // Con acceso: se usan los controles de la sesión. Sin acceso: teclas multimedia
    // del sistema, que funcionan con la app que esté sonando aunque no tengamos permiso.

    fun playPause(ctx: Context) {
        val c = controller
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        } else {
            mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun next(ctx: Context) {
        controller?.transportControls?.skipToNext() ?: mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(ctx: Context) {
        controller?.transportControls?.skipToPrevious() ?: mediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun mediaKey(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
