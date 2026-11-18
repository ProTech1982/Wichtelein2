package app.wichtelein2.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Punkt im Foto (Pixel). */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
    fun len() = sqrt(x * x + y * y)
    fun dist(o: Pt) = (this - o).len()
}

/** Blickwinkel des Fotos: SIDE = seitliche Ansicht (Wichtel von der Seite), TOP = Foto von oben. */
enum class ViewMode(val key: String, val label: String) {
    SIDE("side", "Seitlich"),
    TOP("top", "Von oben"),
}

enum class DoorShape(val key: String, val label: String, val aspect: Float) {
    RECT("rect", "Rechteck", 0.62f),
    ARCH("arch", "Rundbogen", 0.62f),
    ROUND("round", "Rund", 1.0f),
}

enum class Hinge(val label: String) { LEFT("Angeln links"), RIGHT("Angeln rechts") }
enum class OpenDir(val label: String) { OUT("öffnet nach außen"), IN("öffnet nach innen") }

enum class InteriorKind(val label: String) {
    BLACK("Schwarz"),
    STUBE("Stube"),
    HOEHLE("Höhle"),
    CUSTOM("Eigenes Bild"),
}

/** Form der Fläche, mit der ein Gegenstand das Foto-Stück beim Verdecken des Wichtels überdeckt. */
enum class ShapeKind(val label: String) { RECT("Rechteck"), OVAL("Oval") }

/**
 * Gegenstände aus dem echten Foto (Bett, Baum ...). Sie werden nicht gezeichnet, sondern nur markiert,
 * damit der Wichtel sie bei der Wegfindung beachtet und sich dahinter verstecken kann.
 *
 * Standfläche (Blocker) als Bruchteil des markierten Kastens:
 *  - SIDE: unten angelegt (Breite fx, Tiefe fy der Kastenhöhe); TOP: mittig (fx, fy).
 *  - 0 = nicht blockierend. cost = Wegkosten-Faktor (Gras bremst). hide*: Versteck möglich.
 */
enum class ObjType(
    val key: String,
    val label: String,
    val defW: Float,
    val defH: Float,
    val sideFx: Float, val sideFy: Float,
    val topFx: Float, val topFy: Float,
    val cost: Float,
    val hideSide: Boolean,
    val hideTop: Boolean,
    val shape: ShapeKind,
) {
    BED("bed", "Bett", 0.30f, 0.18f, 1.0f, 0.55f, 1.0f, 1.0f, 1f, true, true, ShapeKind.RECT),
    TREE("tree", "Baum", 0.22f, 0.45f, 0.22f, 0.10f, 0.20f, 0.20f, 1f, true, true, ShapeKind.OVAL),
    HOUSE("house", "Haus", 0.30f, 0.35f, 1.0f, 0.35f, 1.0f, 1.0f, 1f, true, false, ShapeKind.RECT),
    LADDER("ladder", "Leiter", 0.08f, 0.35f, 0f, 0f, 0f, 0f, 1f, false, false, ShapeKind.RECT),
    STAIRS("stairs", "Treppe", 0.22f, 0.22f, 0f, 0f, 0f, 0f, 1f, false, false, ShapeKind.RECT),
    GRASS("grass", "Gras", 0.25f, 0.10f, 0f, 0f, 0f, 0f, 2.4f, true, true, ShapeKind.OVAL),
    BUSH("bush", "Busch", 0.16f, 0.12f, 0.8f, 0.35f, 0.8f, 0.8f, 1f, true, true, ShapeKind.OVAL),
    STONE("stone", "Stein", 0.10f, 0.08f, 1.0f, 0.55f, 0.9f, 0.9f, 1f, true, false, ShapeKind.OVAL),
    FENCE("fence", "Zaun", 0.35f, 0.12f, 1.0f, 0.22f, 1.0f, 1.0f, 1f, true, false, ShapeKind.RECT),
    TABLE("table", "Tisch", 0.22f, 0.14f, 0.9f, 0.35f, 0.2f, 0.2f, 1f, true, true, ShapeKind.RECT),
    BOX("box", "Kiste", 0.12f, 0.10f, 1.0f, 0.55f, 1.0f, 1.0f, 1f, true, false, ShapeKind.RECT);

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: BOX
    }
}

/** Tür des Wichtels. (x, y) = Mitte der Unterkante im Foto, w/h = Größe in Pixeln, rot = Drehung in Grad. */
data class Door(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rot: Float = 0f,
    val shape: DoorShape = DoorShape.ARCH,
    val hinge: Hinge = Hinge.LEFT,
    val openDir: OpenDir = OpenDir.OUT,
    val interior: InteriorKind = InteriorKind.STUBE,
) {
    val base get() = Pt(x, y)
}

/** Markierter Gegenstand: (x, y) = Mitte des Kastens, w/h Größe in Pixeln, rot Grad, shape Verdeck-Form. */
data class SceneObject(
    val id: Int,
    val type: ObjType,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rot: Float = 0f,
    val shape: ShapeKind = type.shape,
) {
    val center get() = Pt(x, y)
    val baseY get() = y + h / 2f

    /** In das Koordinatensystem des Gegenstands (Mitte = 0, ohne Drehung). */
    fun toLocal(p: Pt): Pt {
        val a = -rot.toDouble() * PI / 180.0
        val dx = p.x - x
        val dy = p.y - y
        return Pt((dx * cos(a) - dy * sin(a)).toFloat(), (dx * sin(a) + dy * cos(a)).toFloat())
    }

    fun toWorld(l: Pt): Pt {
        val a = rot.toDouble() * PI / 180.0
        return Pt(
            (x + l.x * cos(a) - l.y * sin(a)).toFloat(),
            (y + l.x * sin(a) + l.y * cos(a)).toFloat(),
        )
    }

    /** Liegt p im markierten Bereich (Kasten bzw. Oval)? */
    fun contains(p: Pt): Boolean {
        val l = toLocal(p)
        return when (shape) {
            ShapeKind.RECT -> abs(l.x) <= w / 2f && abs(l.y) <= h / 2f
            ShapeKind.OVAL -> {
                val a = w / 2f
                val b = h / 2f
                a > 0f && b > 0f && (l.x / a) * (l.x / a) + (l.y / b) * (l.y / b) <= 1f
            }
        }
    }

    /** Standfläche (Blocker) im lokalen System: links, oben, rechts, unten; null = blockiert nicht. */
    fun footprintLocal(view: ViewMode): FloatArray? {
        val fx = if (view == ViewMode.SIDE) type.sideFx else type.topFx
        val fy = if (view == ViewMode.SIDE) type.sideFy else type.topFy
        if (fx <= 0f || fy <= 0f) return null
        val hw = fx * w / 2f
        return if (view == ViewMode.SIDE) {
            floatArrayOf(-hw, h / 2f - fy * h, hw, h / 2f)
        } else {
            val hh = fy * h / 2f
            floatArrayOf(-hw, -hh, hw, hh)
        }
    }

    /** Abstand von p zur Standfläche (0 = darin); große Zahl, wenn der Gegenstand nicht blockiert. */
    fun distToFootprint(p: Pt, view: ViewMode): Float {
        val f = footprintLocal(view) ?: return Float.MAX_VALUE
        val l = toLocal(p)
        val dx = max(max(f[0] - l.x, 0f), l.x - f[2])
        val dy = max(max(f[1] - l.y, 0f), l.y - f[3])
        return sqrt(dx * dx + dy * dy)
    }

    /** Oberkante der Standfläche in Foto-Koordinaten (Mitte oben), für „dahinter“ in der Seitenansicht. */
    fun footprintTopCenter(view: ViewMode): Pt {
        val f = footprintLocal(view) ?: return toWorld(Pt(0f, -h / 2f))
        return toWorld(Pt((f[0] + f[2]) / 2f, f[1]))
    }

    fun canHide(view: ViewMode) = if (view == ViewMode.SIDE) type.hideSide else type.hideTop
}

enum class ActionType(val key: String, val label: String) {
    SWEEP("sweep", "Fegen"),
    HIDE("hide", "Verstecken"),
    SCARE("scare", "Erschrecken"),
    LADDER("ladder", "Leiter"),
    STAIRS("stairs", "Treppe");

    /** Leiter und Treppe: der Wichtel geht einen einstellbaren Pfad hinauf oder hinunter. */
    val isClimb get() = this == LADDER || this == STAIRS

    /** Welcher Gegenstandstyp zu dieser Aktion gehört (nur Leiter/Treppe). */
    val climbType get() = if (this == LADDER) ObjType.LADDER else ObjType.STAIRS

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: SWEEP
    }
}

/** Wohin der Wichtel verschwindet (Verstecken, Flucht nach dem Erschrecken). */
enum class Retreat(val key: String, val label: String) {
    DOOR("door", "In die Tür"),
    OBJECT("object", "Hinter Gegenstand");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: DOOR
    }
}

/**
 * Ein Schritt des Ablaufs. at = Ort (Fegen: Mitte der Fegefläche, Erschrecken: Ort des Schrecks), null = automatisch.
 * retreat/objectId = Ziel beim Verstecken bzw. bei der Flucht; seconds = Fegedauer bzw. Zeit im Versteck.
 *
 * Leiter/Treppe: at = unteres Ende des Pfads, to = oberes Ende (null = automatisch aus dem Gegenstand objectId),
 * up = true: von unten nach oben, false: von oben nach unten; seconds = Zeit oben, bevor es weitergeht.
 */
data class Step(
    val action: ActionType,
    val at: Pt? = null,
    val retreat: Retreat = Retreat.DOOR,
    val objectId: Int = -1,
    val seconds: Float = 6f,
    val to: Pt? = null,
    val up: Boolean = true,
)

data class Plan(
    val width: Int,
    val height: Int,
    val view: ViewMode = ViewMode.SIDE,
    val horizon: Float,
    val door: Door,
    val objects: List<SceneObject> = emptyList(),
    val steps: List<Step> = listOf(Step(ActionType.SWEEP)),
    val wichtelRatio: Float = 0.85f,
    val nextId: Int = 1,
) {
    fun objectById(id: Int) = objects.firstOrNull { it.id == id }

    companion object {
        /** Startplan für ein Foto: Tür rechts unten, Horizont bei einem Drittel der Höhe. */
        fun default(width: Int, height: Int): Plan {
            val m = min(width, height).toFloat()
            val dh = 0.22f * m
            val shape = DoorShape.ARCH
            return Plan(
                width = width,
                height = height,
                horizon = 0.35f * height,
                door = Door(x = 0.78f * width, y = 0.72f * height, w = dh * shape.aspect, h = dh, shape = shape),
            )
        }
    }
}

/** Wichtelhöhe in Pixeln je nach Fußpunkt (Tiefe in der Seitenansicht, konstant von oben). */
class Scaler(private val plan: Plan) {
    private val ref = max(1f, plan.door.h * plan.wichtelRatio)

    fun heightAt(y: Float): Float {
        if (plan.view == ViewMode.TOP) return ref
        val doorDepth = plan.door.y - plan.horizon
        if (doorDepth < 8f) return ref
        val f = (y - plan.horizon) / doorDepth
        return ref * f.coerceIn(0.25f, 3.5f)
    }
}

fun headingToDir(dx: Float, dy: Float): Int {
    val deg = atan2(dx.toDouble(), dy.toDouble()) * 180.0 / PI
    var k = Math.round(deg / 45.0).toInt()
    k = ((k % 8) + 8) % 8
    return k
}
