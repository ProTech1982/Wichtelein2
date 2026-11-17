package app.wichtelein2.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Was die AR-Sitzung in einem Bild weiß (vom GL-Thread kopiert). Alle Vektoren sind Weltkoordinaten (Meter).
 * [viewProjection] ist Projektion × Ansicht, spaltenweise 4 × 4 (wie OpenGL).
 */
class ArSnapshot(
    val viewProjection: FloatArray,
    /** Fußpunkt der Tür (Anker) oder null, solange noch nichts platziert ist */
    val anchor: FloatArray?,
    /** Boden-Achsen beim Platzieren: nach rechts, zur Kamera hin, nach oben */
    val right: FloatArray,
    val toward: FloatArray,
    val up: FloatArray,
    val camera: FloatArray,
    val viewW: Int,
    val viewH: Int,
    val tracking: Boolean,
    val planeCount: Int,
    /** Wie oft der Nutzer einen Platz gewählt hat (die Aufführung startet dann von vorn) */
    val placement: Int,
)

/**
 * Bildet den Ablauf (Plan-Einheiten, Tür = Ursprung) auf den Boden im Raum ab und projiziert auf den Bildschirm.
 * x geht nach rechts, y nach vorn (zur Kamera beim Platzieren). [metersPerUnit]: Meter je Plan-Einheit.
 */
class ArView(private val s: ArSnapshot, private val originX: Float, private val originY: Float, private val metersPerUnit: Float) {
    fun world(x: Float, y: Float): FloatArray? {
        val a = s.anchor ?: return null
        val dx = (x - originX) * metersPerUnit
        val dz = (y - originY) * metersPerUnit
        return floatArrayOf(
            a[0] + s.right[0] * dx + s.toward[0] * dz,
            a[1] + s.right[1] * dx + s.toward[1] * dz,
            a[2] + s.right[2] * dx + s.toward[2] * dz,
        )
    }

    fun project(p: FloatArray): Pt? {
        val m = s.viewProjection
        val cw = m[3] * p[0] + m[7] * p[1] + m[11] * p[2] + m[15]
        if (cw <= 0.01f) return null
        val cx = m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12]
        val cy = m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13]
        return Pt((cx / cw * 0.5f + 0.5f) * s.viewW, (1f - (cy / cw * 0.5f + 0.5f)) * s.viewH)
    }

    /** Fußpunkt auf dem Bildschirm */
    fun foot(x: Float, y: Float): Pt? = world(x, y)?.let { project(it) }

    /** Höhe eines Gegenstands von [meters] Metern in Bildschirm-Pixeln, der bei (x, y) steht */
    fun heightPx(x: Float, y: Float, meters: Float): Float {
        val w = world(x, y) ?: return 0f
        val top = floatArrayOf(w[0] + s.up[0] * meters, w[1] + s.up[1] * meters, w[2] + s.up[2] * meters)
        val a = project(w) ?: return 0f
        val b = project(top) ?: return 0f
        return abs(a.y - b.y)
    }

    private fun toCamera(x: Float, y: Float): FloatArray? {
        val w = world(x, y) ?: return null
        return floatArrayOf(s.camera[0] - w[0], s.camera[1] - w[1], s.camera[2] - w[2])
    }

    /** Blickwinkel der Kamera auf den Punkt über dem Boden in Grad (0 = waagerecht, 90 = senkrecht von oben). */
    fun elevationDeg(x: Float, y: Float): Float {
        val v = toCamera(x, y) ?: return 0f
        val h = v[0] * s.up[0] + v[1] * s.up[1] + v[2] * s.up[2]
        val hx = v[0] - s.up[0] * h
        val hy = v[1] - s.up[1] * h
        val hz = v[2] - s.up[2] * h
        val d = sqrt(hx * hx + hy * hy + hz * hz)
        return (atan2(h.toDouble(), d.toDouble()) * 180.0 / PI).toFloat()
    }

    /** Wo steht die Kamera um den Punkt herum (0° = auf der Seite, von der aus platziert wurde, +90° = rechts davon). */
    fun azimuthDeg(x: Float, y: Float): Float {
        val v = toCamera(x, y) ?: return 0f
        val cr = v[0] * s.right[0] + v[1] * s.right[1] + v[2] * s.right[2]
        val ct = v[0] * s.toward[0] + v[1] * s.toward[1] + v[2] * s.toward[2]
        return (atan2(cr.toDouble(), ct.toDouble()) * 180.0 / PI).toFloat()
    }
}

object ArMath {
    /** Von oben (Sprites der Draufsicht) erst ab diesem Blickwinkel, sonst die seitlichen Bilder. */
    const val TOP_FROM_DEG = 40f

    fun viewFor(elevationDeg: Float): ViewMode = if (elevationDeg >= TOP_FROM_DEG) ViewMode.TOP else ViewMode.SIDE

    /** Blickrichtung im Plan (0..7) → Bildrichtung für den Betrachter, der um [azimuthDeg] herumgegangen ist. */
    fun cameraDir(dir: Int, azimuthDeg: Float): Int {
        val k = Math.round(azimuthDeg / 45f)
        return (((dir - k) % 8) + 8) % 8
    }
}
