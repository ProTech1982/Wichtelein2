package app.wichtelein2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.wichtelein2.core.BgDesign
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** Schalter für Tests: Animationen und Musik der Startseite lassen sich abschalten (sonst wartet die Test-Uhr ewig auf Ruhe). */
object HomeFx {
    @Volatile var animate = true
    @Volatile var music = true
}

private fun hash(i: Int, salt: Int): Float {
    var h = i * 374761393 + salt * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777215f
}

/**
 * Startbild mit sanfter Bewegung: fallender Schnee, flackernde Leuchten (Feuer, Kerzen, Laternen) und funkelnde Lichter.
 * Eigene Bilder ([design] = null) bekommen nur leichten Schneefall. Darüber liegt ein dunkler Schleier, damit Schrift lesbar bleibt.
 */
@Composable
fun HomeBackdrop(bitmap: Bitmap?, design: BgDesign?, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    if (HomeFx.animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { time = (it - start) / 1_000_000_000f }
            }
        }
    }
    val snow = design?.snow ?: 0.5f
    Box(modifier.fillMaxSize().background(Color(0xFF101B2E))) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = time
            // Abbildung der Bildkoordinaten auf die Zeichenfläche (Bild wird mit Crop eingepasst)
            val bw = bitmap?.width?.toFloat() ?: size.width
            val bh = bitmap?.height?.toFloat() ?: size.height
            val k = max(size.width / bw, size.height / bh)
            val ox = (size.width - bw * k) / 2f
            val oy = (size.height - bh * k) / 2f
            if (design != null) {
                for (g in design.glows) {
                    val c = Offset(ox + g.x * bw * k, oy + g.y * bh * k)
                    val r = g.r * bw * k
                    val f = 1f - g.flicker + g.flicker * (0.5f + 0.25f * sin(t * g.speed * 5.3f) + 0.15f * sin(t * g.speed * 11.7f + 1.3f) + 0.1f * sin(t * g.speed * 2.1f + 4f))
                    val a = (0.55f * f).coerceIn(0f, 1f)
                    drawCircle(
                        brush = Brush.radialGradient(listOf(g.color.toColor(a), g.color.toColor(0f)), center = c, radius = r),
                        radius = r, center = c,
                    )
                }
                for (tw in design.twinkles) {
                    val c = Offset(ox + tw.x * bw * k, oy + tw.y * bh * k)
                    val r = tw.r * bw * k * 2.2f
                    val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t * 0.45f + tw.phase)))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(tw.color.toColor(0.85f * a), tw.color.toColor(0f)), center = c, radius = r),
                        radius = r, center = c,
                    )
                    drawCircle(Color.White.copy(alpha = 0.8f * a), radius = r * 0.16f, center = c)
                }
            }
            // Schneefall
            val n = (snow * 90f).toInt()
            for (i in 0 until n) {
                val size0 = (1.2f + 3.2f * hash(i, 1)) * (size.width / 1080f)
                val speed = (28f + 70f * hash(i, 2)) * (size.height / 1920f) * (0.6f + size0 / 3f)
                val x0 = hash(i, 3) * size.width
                val y0 = hash(i, 4) * (size.height + 40f)
                val sway = (10f + 26f * hash(i, 5)) * (size.width / 1080f)
                val y = (y0 + t * speed) % (size.height + 40f) - 20f
                val x = x0 + sway * sin(t * (0.4f + hash(i, 6)) + hash(i, 7) * 6.28f)
                drawCircle(Color.White.copy(alpha = 0.35f + 0.5f * hash(i, 8)), radius = size0, center = Offset(x, y))
            }
            // dunkler Schleier: oben leicht, unten kräftiger, damit Titel und Knöpfe gut lesbar sind
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.30f),
                    0.35f to Color.Black.copy(alpha = 0.08f),
                    0.65f to Color.Black.copy(alpha = 0.22f),
                    1f to Color.Black.copy(alpha = 0.62f),
                ),
            )
        }
    }
}

private fun Int.toColor(alpha: Float): Color = Color(this).copy(alpha = alpha)
