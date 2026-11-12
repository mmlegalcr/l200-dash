package cr.andres.l200dash.ui

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cr.andres.l200dash.data.AppsRepo
import cr.andres.l200dash.data.CameraFeed
import cr.andres.l200dash.data.FeedState
import cr.andres.l200dash.data.Prefs

/** Etiquetas de las entradas de cámara de esta pantalla (según el diagnóstico). */
fun cameraLabel(id: String): String = when (id) {
    "1" -> "Reversa"
    "0" -> "AHD 0"
    "2" -> "AHD 2"
    else -> "Cámara $id"
}

@Composable
fun CameraPanel(modifier: Modifier = Modifier) {
    val l = LocalLook.current
    val ctx = LocalContext.current
    val cameraId by Prefs.cameraId.collectAsState()
    var state by remember { mutableStateOf(FeedState.CONNECTING) }

    GlowPanel(
        title = "Cámara",
        icon = Icons.Filled.Videocam,
        modifier = modifier,
        tag = cameraLabel(cameraId) + " ▸",
        onTag = {
            val ids = listOf("1", "0", "2")
            Prefs.setCameraId(ids[(ids.indexOf(cameraId) + 1) % ids.size])
        },
        footer = {
            Text(
                when (state) {
                    FeedState.LIVE -> "Señal OK"
                    FeedState.NO_SIGNAL -> "Sin señal"
                    FeedState.BUSY -> "En uso por el sistema"
                    else -> "Conectando…"
                },
                color = l.dim, fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Abrir dashcam ›",
                color = l.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickableCompat({ AppsRepo.launch(ctx, "com.fvision.camera") }, null).padding(4.dp)
            )
        }
    ) {
        key(cameraId) {
            CameraView(
                cameraId = cameraId,
                onState = { state = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            )
        }
    }
}

@Composable
private fun CameraView(cameraId: String, onState: (FeedState) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val l = LocalLook.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var feedState by remember { mutableStateOf(FeedState.CONNECTING) }
    var message by remember { mutableStateOf("Conectando…") }

    val feed = remember(cameraId) {
        CameraFeed(ctx, cameraId) { s, msg ->
            feedState = s
            message = msg
            onState(s)
        }
    }

    DisposableEffect(lifecycleOwner, feed) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> feed.resume()
                Lifecycle.Event.ON_PAUSE -> feed.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            feed.pause()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { c -> TextureView(c).also { feed.attach(it) } },
            update = { feed.attach(it) },
            modifier = Modifier.fillMaxSize()
        )
        if (feedState != FeedState.LIVE) {
            val hint = when (feedState) {
                FeedState.NO_SIGNAL -> if (cameraId == "1")
                    "La cámara de reversa solo recibe corriente con reversa puesta"
                else "No hay cámara conectada en esta entrada"
                FeedState.BUSY -> "La está usando el sistema; se reconecta sola"
                else -> ""
            }
            Column(
                Modifier.fillMaxSize().background(Color(0xD90B0E12)).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.VideocamOff, null, tint = l.dim, modifier = Modifier.size(34.dp))
                Spacer(Modifier.size(8.dp))
                Text(message, color = l.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                if (hint.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    Text(hint, color = l.dim, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xA6000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(l.ok))
                Spacer(Modifier.width(5.dp))
                Text("EN VIVO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
