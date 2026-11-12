package cr.andres.l200dash.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** Punto cardinal en español para un rumbo en grados. */
fun cardinal(deg: Float): String {
    val names = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    val i = (((deg % 360f) + 360f) % 360f / 45f + 0.5f).toInt() % 8
    return names[i]
}
