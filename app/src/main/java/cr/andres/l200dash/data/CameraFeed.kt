package cr.andres.l200dash.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView

enum class FeedState { CONNECTING, LIVE, NO_SIGNAL, BUSY, ERROR, NO_PERMISSION }

/**
 * Maneja una cámara Camera2 dentro de un TextureView, pensada para un widget:
 * - Se reconecta sola si el sistema le quita la cámara (p. ej. al meter reversa).
 * - Distingue "sin señal" (la sesión corre pero no llegan cuadros) de "ocupada".
 */
class CameraFeed(
    private val ctx: Context,
    private val cameraId: String,
    private val onState: (FeedState, String) -> Unit
) : TextureView.SurfaceTextureListener {

    private val main = Handler(Looper.getMainLooper())
    private val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var texture: TextureView? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null

    private var active = false
    private var opening = false
    private var sessionStartedAt = 0L
    private var lastFrameAt = 0L
    private var lastAttemptAt = 0L
    private var state = FeedState.CONNECTING

    private val watchdog = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            if (session != null) {
                when {
                    now - lastFrameAt < 1500 -> set(FeedState.LIVE, "")
                    now - sessionStartedAt > 2000 -> set(FeedState.NO_SIGNAL, "Sin señal de video")
                }
            } else if (device == null && !opening && now - lastAttemptAt > 3000) {
                tryOpen()
            }
            main.postDelayed(this, 1000)
        }
    }

    private val availability = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(id: String) {
            if (id == cameraId && active && device == null && !opening) main.postDelayed({ tryOpen() }, 300)
        }
    }

    fun attach(view: TextureView) {
        if (texture === view) return
        texture = view
        view.surfaceTextureListener = this
        if (view.isAvailable && active) tryOpen()
    }

    fun resume() {
        if (active) return
        active = true
        try {
            manager.registerAvailabilityCallback(availability, main)
        } catch (_: Exception) {
        }
        tryOpen()
        main.post(watchdog)
    }

    fun pause() {
        active = false
        main.removeCallbacks(watchdog)
        try {
            manager.unregisterAvailabilityCallback(availability)
        } catch (_: Exception) {
        }
        close()
    }

    // ------------------------------------------------------------------ SurfaceTexture

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        if (active) tryOpen()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        close()
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
        lastFrameAt = SystemClock.elapsedRealtime()
    }

    // ------------------------------------------------------------------ Cámara

    @SuppressLint("MissingPermission")
    private fun tryOpen() {
        val st = texture?.takeIf { it.isAvailable }?.surfaceTexture ?: return
        if (!active || device != null || opening) return
        lastAttemptAt = SystemClock.elapsedRealtime()

        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            set(FeedState.NO_PERMISSION, "Falta permiso de cámara")
            return
        }
        val size = try {
            manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.filter { it.width <= 1280 && it.height <= 720 }
                ?.maxByOrNull { it.width * it.height }
        } catch (e: Exception) {
            set(FeedState.ERROR, "Cámara $cameraId no existe")
            return
        }
        size?.let { st.setDefaultBufferSize(it.width, it.height) }

        opening = true
        set(FeedState.CONNECTING, "Conectando…")
        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    opening = false
                    if (!active) {
                        cam.close()
                        return
                    }
                    device = cam
                    startSession(cam, st)
                }

                override fun onDisconnected(cam: CameraDevice) {
                    opening = false
                    set(FeedState.BUSY, "En uso por el sistema")
                    closeQuietly(cam)
                }

                override fun onError(cam: CameraDevice, error: Int) {
                    opening = false
                    val busy = error == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE ||
                        error == CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE
                    set(if (busy) FeedState.BUSY else FeedState.ERROR, if (busy) "En uso por otra app" else "Error de cámara ($error)")
                    closeQuietly(cam)
                }
            }, main)
        } catch (e: Exception) {
            opening = false
            set(FeedState.ERROR, "No abre: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun startSession(cam: CameraDevice, st: SurfaceTexture) {
        try {
            val s = Surface(st)
            surface = s
            val request = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(s) }
            @Suppress("DEPRECATION")
            cam.createCaptureSession(listOf(s), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cs: CameraCaptureSession) {
                    if (!active) {
                        cs.close()
                        return
                    }
                    session = cs
                    try {
                        cs.setRepeatingRequest(request.build(), null, main)
                        sessionStartedAt = SystemClock.elapsedRealtime()
                        lastFrameAt = 0L
                    } catch (e: Exception) {
                        set(FeedState.ERROR, "No inicia el video")
                        close()
                    }
                }

                override fun onConfigureFailed(cs: CameraCaptureSession) {
                    set(FeedState.ERROR, "Falló la configuración")
                    close()
                }
            }, main)
        } catch (e: Exception) {
            set(FeedState.ERROR, "Error de sesión")
            close()
        }
    }

    private fun closeQuietly(cam: CameraDevice) {
        try { cam.close() } catch (_: Exception) {}
        if (device === cam) device = null
        session = null
        surface?.release()
        surface = null
    }

    private fun close() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { device?.close() } catch (_: Exception) {}
        device = null
        surface?.release()
        surface = null
        opening = false
    }

    private fun set(s: FeedState, msg: String) {
        if (s == state && s == FeedState.LIVE) return
        state = s
        onState(s, msg)
    }
}
