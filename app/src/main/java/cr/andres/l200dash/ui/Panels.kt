package cr.andres.l200dash.ui

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Halo de color alrededor de una forma redondeada (el "brillo del borde"). */
fun Modifier.glow(color: Color, radius: Dp, corner: Dp, strength: Float): Modifier =
    if (strength <= 0.01f) this else drawBehind {
        val blur = radius.toPx() * strength
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(blur, 0f, 0f, color.copy(alpha = (0.25f + 0.55f * strength).coerceAtMost(1f)).toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner.toPx(), corner.toPx(), paint)
        }
    }

/** Panel oscuro con borde de acento, encabezado y pie opcional. */
@Composable
fun GlowPanel(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tag: String? = null,
    onTag: (() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(l.radius)
    // El contenido va recortado con un radio interior (radio - grosor) y separado del borde
    // por el grosor del borde, para que las esquinas no se vean dobles.
    val inner = RoundedCornerShape((l.radius - l.border).coerceAtLeast(0.dp))
    Box(
        modifier
            .glow(l.accent, 26.dp, l.radius, l.glow)
            .clip(shape)
            .background(l.panel)
            .border(l.border, l.accent, shape)
            .padding(l.border)
            .clip(inner)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(l.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = Color(0xFF16191B), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text(title, color = l.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (tag != null) Tag(tag, onTag)
            }
            Column(Modifier.fillMaxWidth().weight(1f), content = content)
            if (footer != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(l.line))
                Row(
                    Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer
                )
            }
        }
    }
}

@Composable
fun Tag(text: String, onClick: (() -> Unit)? = null) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(6.dp)
    Text(
        text,
        color = l.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, l.accent, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

/** Píldora oscura con borde (encabezado superior). */
@Composable
fun Chip(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .clip(shape)
            .background(l.panel)
            .border(1.dp, l.accent.copy(alpha = 0.6f), shape)
            .then(
                if (onClick != null || onLongClick != null)
                    Modifier.combinedClickableCompat(onClick ?: {}, onLongClick)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Botón de contorno con color de acento. */
@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(8.dp)
    val c = if (enabled) l.accent else l.dim
    Box(
        modifier
            .clip(shape)
            .background(if (filled) c else c.copy(alpha = 0.08f))
            .border(1.dp, c.copy(alpha = if (filled) 1f else 0.55f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) Color(0xFF16191B) else c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Contenedor que solo agrega fondo con forma; útil para tarjetas internas. */
@Composable
fun Inset(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val l = LocalLook.current
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.clip(shape).background(l.panelSolid).border(1.dp, l.line, shape), content = content)
}
