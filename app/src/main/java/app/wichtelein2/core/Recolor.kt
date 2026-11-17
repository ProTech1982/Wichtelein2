package app.wichtelein2.core

import kotlin.math.max
import kotlin.math.min

/** Wunschfarben des Wichtels (ARGB); null = Originalfarbe. */
data class WichtelColors(
    val hat: Int? = null,
    val jacket: Int? = null,
    val pants: Int? = null,
    val shoes: Int? = null,
) {
    val isOriginal get() = hat == null && jacket == null && pants == null && shoes == null
}

/**
 * Färbt die Bereiche Mütze, Jacke, Hose und Schuhe eines Sprites um. Die Farbmaske kommt aus Blender
 * (rot = Mütze, grün = Jacke, blau = Hose, gelb = Schuhe). Die Helligkeit des Originals (Falten, Schatten,
 * Muster) bleibt erhalten, nur der Farbton wechselt.
 */
object Recolor {
    /** Mittlere Helligkeit der Originalbereiche (Mütze, Jacke, Hose, Schuhe); sprites.json liefert genauere Werte. */
    val DEFAULT_REF = floatArrayOf(0.35f, 0.39f, 0.33f, 0.50f)

    /** px und mask: ARGB ohne vormultiplizierten Alpha-Kanal, gleiche Länge; px wird verändert. */
    fun apply(px: IntArray, mask: IntArray, colors: WichtelColors, ref: FloatArray = DEFAULT_REF) {
        if (colors.isOriginal) return
        val targets = arrayOf(colors.hat, colors.jacket, colors.pants, colors.shoes)
        val tr = FloatArray(4)
        val tg = FloatArray(4)
        val tb = FloatArray(4)
        for (i in 0 until 4) {
            val c = targets[i] ?: continue
            tr[i] = ((c shr 16) and 255) / 255f
            tg[i] = ((c shr 8) and 255) / 255f
            tb[i] = (c and 255) / 255f
        }
        val w = FloatArray(4)
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            val m = mask[i]
            val mr = ((m shr 16) and 255) / 255f
            val mg = ((m shr 8) and 255) / 255f
            val mb = (m and 255) / 255f
            val sh = min(mr, mg)
            w[0] = max(0f, mr - sh)
            w[1] = max(0f, mg - sh)
            w[2] = mb
            w[3] = sh
            var any = false
            for (k in 0 until 4) if (targets[k] != null && w[k] > 0.01f) any = true
            if (!any) continue
            val r0 = ((p shr 16) and 255) / 255f
            val g0 = ((p shr 8) and 255) / 255f
            val b0 = (p and 255) / 255f
            val y = 0.2126f * r0 + 0.7152f * g0 + 0.0722f * b0
            var wsum = 0f
            for (k in 0 until 4) if (targets[k] != null) wsum += w[k]
            if (wsum > 1f) for (k in 0 until 4) w[k] /= wsum
            var r = r0
            var g = g0
            var b = b0
            for (k in 0 until 4) {
                if (targets[k] == null || w[k] <= 0.01f) continue
                val f = (y / ref[k]).coerceIn(0.15f, 2.0f)
                val nr = (tr[k] * f).coerceIn(0f, 1f)
                val ng = (tg[k] * f).coerceIn(0f, 1f)
                val nb = (tb[k] * f).coerceIn(0f, 1f)
                r += (nr - r0) * w[k]
                g += (ng - g0) * w[k]
                b += (nb - b0) * w[k]
            }
            px[i] = (a shl 24) or
                ((r * 255f + 0.5f).toInt().coerceIn(0, 255) shl 16) or
                ((g * 255f + 0.5f).toInt().coerceIn(0, 255) shl 8) or
                (b * 255f + 0.5f).toInt().coerceIn(0, 255)
        }
    }
}
