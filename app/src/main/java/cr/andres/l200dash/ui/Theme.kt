package cr.andres.l200dash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cr.andres.l200dash.data.DashStyle

/** Estilo ya resuelto a tipos de Compose. */
@Immutable
data class Look(
    val night: Boolean,
    val style: DashStyle,
    val accent: Color,
    val accentSoft: Color,
    val panel: Color,
    val panelSolid: Color,
    val text: Color = Color(0xFFEEF1EE),
    val dim: Color = Color(0xFF9AA59F),
    val line: Color = Color(0x14FFFFFF),
    val ok: Color = Color(0xFF5BD69B),
    val rec: Color = Color(0xFFC8413A),
    val border: Dp,
    val radius: Dp,
    val glow: Float
)

fun lookOf(style: DashStyle, night: Boolean): Look {
    val accent = Color(style.accent)
    val panelBase = Color(style.panel)
    return Look(
        night = night,
        style = style,
        accent = accent,
        accentSoft = accent.copy(alpha = 0.14f),
        panel = panelBase.copy(alpha = style.panelAlpha),
        panelSolid = panelBase.copy(alpha = 0.95f),
        border = style.borderDp.dp,
        radius = style.radiusDp.dp,
        glow = style.glow
    )
}

val LocalLook = staticCompositionLocalOf { lookOf(DashStyle(), false) }

@Composable
fun DashTheme(look: Look, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalLook provides look,
        LocalDensity provides Density(d.density, d.fontScale * look.style.textScale),
        content = content
    )
}
