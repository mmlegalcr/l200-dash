package cr.andres.l200dash.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cr.andres.l200dash.data.Dashcam
import kotlinx.coroutines.delay
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Video en vivo de la dashcam (la librería dibuja con OpenGL ES 2 en esta superficie). */
@Composable
fun DashcamVideo(modifier: Modifier = Modifier) {
    val l = LocalLook.current
    val st by Dashcam.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current
    val view = remember { mutableStateOf<GLSurfaceView?>(null) }

    DisposableEffect(lifecycle) {
        Dashcam.attachViewer()
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> view.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> view.value?.onPause()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose {
            lifecycle.lifecycle.removeObserver(obs)
            view.value?.onPause()
            Dashcam.detachViewer()
        }
    }
    // ~25 cuadros por segundo, igual que la app original.
    LaunchedEffect(Unit) {
        while (true) {
            view.value?.requestRender()
            delay(40)
        }
    }

    Box(modifier.background(Color.Black)) {
        if (st.connected) {
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(object : GLSurfaceView.Renderer {
                            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) = Dashcam.glInit()
                            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = Dashcam.glResize(width, height)
                            override fun onDrawFrame(gl: GL10?) = Dashcam.glDraw()
                        })
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        view.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (st.recording) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(5.dp))
                        .background(l.rec.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(5.dp))
                    Text("REC", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.VideocamOff, null, tint = l.dim, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Text(st.message, color = l.text, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Pantalla completa de la dashcam con todos los controles. */
@Composable
fun DashcamScreen(onClose: () -> Unit) {
    val l = LocalLook.current
    val st by Dashcam.state.collectAsState()
    val log by Dashcam.log.collectAsState()
    var showLog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        DashcamVideo(Modifier.fillMaxSize())

        // Barra superior
        Row(
            Modifier.fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ArrowBack, "Volver", tint = l.accent,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).padding(8.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Dashcam", color = l.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(18.dp))
            StatusPill(if (st.recording) "● Grabando" else "En pausa", if (st.recording) l.rec else l.dim)
            Spacer(Modifier.width(8.dp))
            StatusPill(if (st.sdPresent) "SD lista" else "Sin SD", if (st.sdPresent) l.ok else l.rec, Icons.Filled.SdCard)
            Spacer(Modifier.width(8.dp))
            if (st.locked) StatusPill("Clip protegido", l.accent, Icons.Filled.Lock)
            Spacer(Modifier.weight(1f))
            Text(
                if (st.commandsReady) "Controles listos" else "Controles no disponibles",
                color = if (st.commandsReady) l.ok else l.dim, fontSize = 13.sp
            )
        }

        // Controles
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
                .glow(l.accent, 24.dp, 18.dp, l.glow)
                .clip(RoundedCornerShape(18.dp)).background(l.panel).border(l.border, l.accent, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BigControl(if (st.recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord, if (st.recording) "Detener" else "Grabar", st.commandsReady, highlight = st.recording) { Dashcam.toggleRecording() }
            BigControl(Icons.Filled.Lock, if (st.locked) "Protegido" else "Guardar clip", st.commandsReady, highlight = st.locked) { Dashcam.toggleLock() }
            BigControl(Icons.Filled.PhotoCamera, "Foto", st.commandsReady) { Dashcam.takePhoto() }
            BigControl(if (st.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (st.muted) "Mic apagado" else "Mic", st.commandsReady) { Dashcam.toggleMute() }
            BigControl(Icons.Filled.Schedule, "Poner hora", st.commandsReady) { Dashcam.syncTime() }
            BigControl(Icons.Filled.Refresh, "Reconectar", true) { Dashcam.reconnect() }
            BigControl(Icons.Filled.Terminal, "Registro", true, highlight = showLog) { showLog = !showLog }
        }

        if (showLog) {
            val listState = rememberLazyListState()
            LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.scrollToItem(log.size - 1) }
            LazyColumn(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).padding(top = 70.dp, bottom = 120.dp, end = 16.dp)
                    .width(460.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xE6101418)).padding(10.dp)
            ) {
                items(log) { line ->
                    Text(line, color = Color(0xFFC9D4DF), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BigControl(icon: ImageVector, label: String, enabled: Boolean, highlight: Boolean = false, onClick: () -> Unit) {
    val l = LocalLook.current
    val c = if (enabled) l.accent else l.dim
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.size(width = 92.dp, height = 70.dp).clip(shape)
            .background(if (highlight) c.copy(alpha = 0.22f) else c.copy(alpha = 0.06f))
            .border(1.dp, c.copy(alpha = if (highlight) 1f else 0.5f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = c, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (enabled) l.text else l.dim, fontSize = 11.sp, maxLines = 1)
    }
}
