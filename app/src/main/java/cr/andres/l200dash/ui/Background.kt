package cr.andres.l200dash.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import cr.andres.l200dash.R
import java.io.File

/** Imagen central + colores de relleno tomados de sus bordes. */
class Hero(val image: ImageBitmap, val edgeColors: List<Color>)

fun loadHero(ctx: Context, path: String?): Hero? {
    val bmp: Bitmap = (try {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val f = path?.substringBefore('?')?.let { File(it) }?.takeIf { it.exists() }
        if (f != null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 2200) sample *= 2
            opts.inSampleSize = sample
            BitmapFactory.decodeFile(f.path, opts)
        } else {
            BitmapFactory.decodeResource(ctx.resources, R.drawable.hero_l200, opts)
        }
    } catch (_: Throwable) {
        null
    }) ?: return null

    // Color promedio de una franja del borde izquierdo y derecho a distintas alturas.
    fun avg(yFrac: Float): Color {
        val y = (bmp.height * yFrac).toInt().coerceIn(0, bmp.height - 1)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (x in listOf(2, 6, 10, bmp.width - 3, bmp.width - 7, bmp.width - 11)) {
            for (dy in -4..4) {
                val p = bmp.getPixel(x.coerceIn(0, bmp.width - 1), (y + dy).coerceIn(0, bmp.height - 1))
                r += (p shr 16) and 0xff; g += (p shr 8) and 0xff; b += p and 0xff; n++
            }
        }
        return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
    return Hero(bmp.asImageBitmap(), listOf(avg(0.03f), avg(0.30f), avg(0.50f), avg(0.75f), avg(0.97f)))
}

/**
 * Fondo: degradado con los colores de los bordes de la imagen y, encima, la imagen
 * centrada con los costados desvanecidos para que se funda con el degradado.
 */
@Composable
fun DashBackground(hero: Hero?, brightness: Float, modifier: Modifier = Modifier) {
    val dim = brightness.coerceIn(0.15f, 1.2f)
    val filter = remember(dim) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(dim, dim, (dim * 1.08f).coerceAtMost(1.2f), 1f) })
    }
    fun shade(c: Color) = Color(
        (c.red * dim).coerceIn(0f, 1f),
        (c.green * dim).coerceIn(0f, 1f),
        ((c.blue * dim * 1.08f)).coerceIn(0f, 1f)
    )
    val stops = hero?.edgeColors?.map(::shade) ?: listOf(Color(0xFF0B0E12), Color(0xFF141A21))

    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(stops))) {
        if (hero != null) {
            val w = maxWidth * 0.62f
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Image(
                    bitmap = hero.image,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    colorFilter = filter,
                    modifier = Modifier
                        .width(w)
                        .offset(y = maxHeight * 0.26f)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.horizontalGradient(
                                    0f to Color.Transparent, 0.13f to Color.Black,
                                    0.87f to Color.Black, 1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Transparent, 0.18f to Color.Black,
                                    0.92f to Color.Black, 1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )
            }
        }
    }
}

@Composable
fun rememberHero(path: String?): Hero? {
    val ctx = LocalContext.current
    return remember(path) { loadHero(ctx, path) }
}
