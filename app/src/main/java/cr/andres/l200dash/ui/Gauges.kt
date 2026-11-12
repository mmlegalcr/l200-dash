package cr.andres.l200dash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Trío de medidores: brújula, velocidad (GPS) y altímetro. */
@Composable
fun GaugeCluster(speedKmh: Float?, bearing: Float?, altitudeM: Double?, gpsOk: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 38.dp)) {
            CompassGauge(bearing, 116.dp)
            GaugeLabel("Brújula")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SpeedGauge(speedKmh, 176.dp)
            GaugeLabel(if (gpsOk) "Velocidad (GPS)" else "Buscando GPS…")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 38.dp)) {
            RoundGauge(116.dp) {
                Text(altitudeM?.let { "%.0f".format(it) } ?: "--", color = LocalLook.current.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("m s.n.m.", color = LocalLook.current.dim, fontSize = 11.sp)
            }
            GaugeLabel("Altímetro")
        }
    }
}

@Composable
private fun GaugeLabel(text: String) {
    val l = LocalLook.current
    Spacer(Modifier.height(6.dp))
    Text(
        text,
        color = l.text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(l.panel)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun RoundGauge(size: Dp, content: @Composable () -> Unit) {
    val l = LocalLook.current
    Box(
        Modifier
            .size(size)
            .glow(l.accent, 22.dp, size / 2, l.glow)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2A3136), l.panelSolid)))
            .border(l.border + 0.5.dp, l.accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
fun SpeedGauge(speedKmh: Float?, size: Dp, maxKmh: Float = 160f) {
    val l = LocalLook.current
    val frac = ((speedKmh ?: 0f) / maxKmh).coerceIn(0f, 1f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        RoundGauge(size) {}
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val sweep = 280f
            val start = 130f
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Color(0x22FFFFFF), start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0f) drawArc(l.accent, start, sweep * frac, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                speedKmh?.let { "%.0f".format(it) } ?: "--",
                color = if (speedKmh != null) l.text else l.dim,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp
            )
            Text("km/h", color = l.dim, fontSize = 12.sp)
        }
    }
}

@Composable
fun CompassGauge(bearing: Float?, size: Dp) {
    val l = LocalLook.current
    Box(contentAlignment = Alignment.Center) {
        RoundGauge(size) {
            Text(bearing?.let { cardinal(it) } ?: "--", color = l.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(bearing?.let { "%.0f°".format(it) } ?: "detenido", color = l.dim, fontSize = 11.sp)
        }
        // Aguja: apunta al rumbo del carro.
        if (bearing != null) {
            Canvas(Modifier.size(size).rotate(bearing)) {
                val c = Offset(this.size.width / 2, this.size.height / 2)
                val r = this.size.width / 2 - 6.dp.toPx()
                val p = Path().apply {
                    moveTo(c.x, c.y - r)
                    lineTo(c.x - 6.dp.toPx(), c.y - r + 12.dp.toPx())
                    lineTo(c.x + 6.dp.toPx(), c.y - r + 12.dp.toPx())
                    close()
                }
                drawPath(p, l.accent)
            }
        }
    }
}
