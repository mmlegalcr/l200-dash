package cr.andres.l200dash.data

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.huiying.cameramjpeg.UvcCamera
import com.serenegiant.usb.IEventCallback
import com.serenegiant.usb.IFrameCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Estado de la dashcam leído del cuadro de estado (bytes 0-1, little-endian). */
data class DashcamState(
    val connected: Boolean = false,
    val previewing: Boolean = false,
    val commandsReady: Boolean = false,
    val recording: Boolean = false,
    val locked: Boolean = false,
    val muted: Boolean = false,
    val sdPresent: Boolean = false,
    val playback: Boolean = false,
    val message: String = "Buscando la dashcam…"
)

/**
 * Maneja la dashcam "USB CAR DVR" con su librería nativa (libuvccamera-lib.so).
 *
 *  - Video: archivo especial VSFILE en la raíz del disco USB de la cámara.
 *  - Comandos: <disco>/Android/data/com.fvision.camera/HWC (por eso la app usa ese paquete).
 *  - Estado: la librería entrega cuadros de estado por callback.
 *
 * Todo el trabajo con la librería corre en un hilo propio; la UI solo lee [state].
 */
object Dashcam {
    // Comandos (sacados del análisis de la app original)
    private const val CMD_REC = 1
    private const val CMD_LOCK = 2
    private const val CMD_MUTE = 6
    private const val CMD_PHOTO = 7
    private const val CMD_TIME = 11

    private const val BIT_PLAYBACK = 0x8000
    private const val BIT_REC = 0x4000
    private const val BIT_LOCK = 0x2000
    private const val BIT_MUTE = 0x0400
    private const val BIT_SD = 0x0200

    private val _state = MutableStateFlow(DashcamState())
    val state: StateFlow<DashcamState> = _state

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private lateinit var appCtx: Context
    private val thread = HandlerThread("dashcam").apply { start() }
    private val worker = Handler(thread.looper)

    private var cam: UvcCamera? = null
    private var libError: String? = null
    private var videoFd = -1
    private var cmdFd = -1
    private var started = false
    @Volatile private var lastStateAt = 0L
    @Volatile private var flags = 0

    /** Cuántas vistas de video están mostrando la cámara (la vista pide/cede el video). */
    @Volatile var viewers = 0
        private set

    fun init(ctx: Context) {
        if (started) return
        started = true
        appCtx = ctx.applicationContext
        // Crea Android/data/com.fvision.camera en cada volumen (incluido el disco de la cámara):
        // así Android le da a esta app la propiedad de la carpeta donde está HWC.
        try {
            appCtx.getExternalFilesDirs(null)
        } catch (_: Exception) {
        }
        worker.post(watchdog)
    }

    // ------------------------------------------------------------------ Conexión

    private val watchdog = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (t: Throwable) {
                log("✗ ${t.javaClass.simpleName}: ${t.message}")
            }
            worker.postDelayed(this, 3000)
        }
    }

    private fun tick() {
        val s = _state.value
        if (!s.connected) {
            connect()
            return
        }
        // Si la cámara dejó de mandar estados por más de 8 s, se asume desconectada.
        if (lastStateAt > 0 && SystemClock.elapsedRealtime() - lastStateAt > 8000) {
            log("Sin estados de la cámara: reconectando")
            disconnect()
        } else if (!s.previewing) {
            // El video se mantiene siempre activo (como la app original): así también llegan
            // los cuadros de estado aunque no haya ninguna vista abierta.
            startPreview()
        }
    }

    private fun findDevPath(): String? {
        val roots = linkedSetOf<String>()
        try {
            File("/proc/mounts").readLines().forEach { l ->
                val p = l.split(" ").getOrNull(1) ?: return@forEach
                if ((p.contains("usb", true) || p.contains("udisk", true)) && p.startsWith("/storage")) roots += p
            }
        } catch (_: Exception) {
        }
        roots += "/storage/usbdisk0"
        for (r in roots) {
            val f = File(r, "VSFILE")
            if (f.exists()) return f.path
        }
        return null
    }

    private fun connect() {
        val c = cam ?: try {
            UvcCamera().also { cam = it }
        } catch (t: Throwable) {
            if (libError == null) {
                libError = "${t.javaClass.simpleName}: ${t.message}"
                log("✗ No cargó la librería de la dashcam: $libError")
            }
            _state.value = DashcamState(message = "Librería de la dashcam no disponible")
            return
        }

        val dev = findDevPath()
        if (dev == null) {
            _state.value = DashcamState(message = "Dashcam no conectada")
            return
        }

        log("Dashcam encontrada: $dev")
        try {
            c.nativeEventCallback(IEventCallback { ev -> log("evento $ev") })
            c.nativeStateFrameCallback(IFrameCallback { frame -> onStateFrame(frame) })
        } catch (t: Throwable) {
            log("✗ callbacks: ${t.message}")
        }

        val r = try {
            c.initUvcCamera(dev)
        } catch (t: Throwable) {
            "-1_${t.message}"
        }
        videoFd = r.substringBefore('_').toIntOrNull() ?: -1
        log("initUvcCamera → \"$r\"")
        if (videoFd <= 0) {
            _state.value = DashcamState(message = "No se pudo abrir el video de la dashcam")
            return
        }

        val cmdPath = dev.removeSuffix("VSFILE") + "Android/data/${appCtx.packageName}/HWC"
        val rc = try {
            c.initUvcCmd(cmdPath)
        } catch (t: Throwable) {
            "-1_${t.message}"
        }
        cmdFd = rc.substringBefore('_').toIntOrNull() ?: -1
        log("initUvcCmd($cmdPath) → \"$rc\"")

        lastStateAt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(
            connected = true,
            commandsReady = cmdFd > 0,
            message = if (cmdFd > 0) "Conectada" else "Conectada (sin comandos)"
        )
        try {
            c.nativeSetMainRuning(true)
        } catch (_: Throwable) {
        }
        if (cmdFd > 0) syncTime()
        startPreview()
    }

    private fun startPreview() {
        val c = cam ?: return
        if (videoFd <= 0) return
        try {
            c.stopPreview(videoFd)
            SystemClock.sleep(400)
            val r = c.startPreview(videoFd)
            log("startPreview → $r")
            _state.value = _state.value.copy(previewing = r >= 0)
        } catch (t: Throwable) {
            log("✗ startPreview: ${t.message}")
        }
    }

    private fun stopPreview() {
        val c = cam ?: return
        if (videoFd <= 0) return
        try {
            c.stopPreview(videoFd)
        } catch (_: Throwable) {
        }
        _state.value = _state.value.copy(previewing = false)
    }

    private fun disconnect() {
        val c = cam
        if (c != null && videoFd > 0) {
            try {
                c.stopPreview(videoFd)
                c.releaseUvcCamera(videoFd)
            } catch (_: Throwable) {
            }
        }
        videoFd = -1
        cmdFd = -1
        lastStateAt = 0L
        _state.value = DashcamState(message = "Reconectando…")
    }

    private fun onStateFrame(frame: ByteArray?) {
        if (frame == null || frame.size < 8) return
        // La app original descarta cuadros con los primeros 9 bytes en cero.
        if ((0 until 9).all { frame[it].toInt() == 0 }) return
        lastStateAt = SystemClock.elapsedRealtime()
        val f = (frame[0].toInt() and 0xff) or ((frame[1].toInt() and 0xff) shl 8)
        if (f == flags && _state.value.connected) return
        flags = f
        _state.value = _state.value.copy(
            recording = f and BIT_REC != 0,
            locked = f and BIT_LOCK != 0,
            muted = f and BIT_MUTE != 0,
            sdPresent = f and BIT_SD != 0,
            playback = f and BIT_PLAYBACK != 0
        )
    }

    // ------------------------------------------------------------------ Vistas

    /** Una vista de video se muestra en pantalla (widget o pantalla completa). */
    fun attachViewer() {
        viewers++
        worker.post { if (_state.value.connected && !_state.value.previewing) startPreview() }
    }

    fun detachViewer() {
        viewers = (viewers - 1).coerceAtLeast(0)
    }

    // Llamados desde el hilo de OpenGL de la vista activa.
    fun glInit() = safeGl { it.initGles(1280, 720) }
    fun glResize(w: Int, h: Int) = safeGl { it.changeESLayout(w, h) }
    fun glDraw() = safeGl { it.drawESFrame() }

    private inline fun safeGl(block: (UvcCamera) -> Unit) {
        val c = cam ?: return
        try {
            block(c)
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------ Comandos

    private fun send(cmd: Int, data: ByteArray, label: String) {
        worker.post {
            val c = cam
            if (c == null || cmdFd <= 0) {
                log("✗ $label: sin archivo de comandos")
                return@post
            }
            val r = try {
                c.nativeSendCmd(cmdFd, -1, cmd, -1, -1, data.size, data)
            } catch (t: Throwable) {
                log("✗ $label: ${t.message}")
                -1
            }
            log("$label → $r")
        }
    }

    fun toggleRecording() = send(CMD_REC, byteArrayOf(if (_state.value.recording) 0 else 1), "grabar")
    fun toggleLock() = send(CMD_LOCK, byteArrayOf(if (_state.value.locked) 0 else 1), "guardar clip")
    fun toggleMute() = send(CMD_MUTE, byteArrayOf(if (_state.value.muted) 0 else 1), "micrófono")
    fun takePhoto() = send(CMD_PHOTO, byteArrayOf(1), "foto")

    fun syncTime() {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        send(
            CMD_TIME,
            byteArrayOf(
                (y and 0xff).toByte(), (y shr 8 and 0xff).toByte(),
                (cal.get(Calendar.MONTH) + 1).toByte(), cal.get(Calendar.DAY_OF_MONTH).toByte(),
                cal.get(Calendar.HOUR_OF_DAY).toByte(), cal.get(Calendar.MINUTE).toByte(), cal.get(Calendar.SECOND).toByte()
            ),
            "hora"
        )
    }

    fun reconnect() = worker.post { disconnect(); connect() }

    private fun log(s: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.value = (_log.value + "[$ts] $s").takeLast(200)
    }
}
