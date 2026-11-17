package app.wichtelein2.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Was im Editor gerade bearbeitet wird. */
sealed interface Sel {
    object None : Sel
    object Door : Sel
    object Horizon : Sel
    data class Obj(val id: Int) : Sel
    data class StepAt(val index: Int) : Sel
}

/** Bearbeitungsschritte am Plan (Platzieren mit Pfeilen, Größe, Drehen, Anfassen im Foto) – rein rechnerisch, ohne Android. */
object EditOps {
    private const val MIN_SIZE = 14f

    private fun clampX(p: Plan, x: Float) = x.coerceIn(0f, p.width.toFloat())
    private fun clampY(p: Plan, y: Float) = y.coerceIn(0f, p.height.toFloat())
    private fun clampSize(p: Plan, v: Float) = v.coerceIn(MIN_SIZE, 1.6f * max(p.width, p.height))

    /** Schrittweite der Pfeiltasten: 1 % der kürzeren Fotoseite. */
    fun nudge(p: Plan) = 0.01f * min(p.width, p.height)

    fun move(p: Plan, s: Sel, dx: Float, dy: Float): Plan = when (s) {
        Sel.None -> p
        Sel.Door -> p.copy(door = p.door.copy(x = clampX(p, p.door.x + dx), y = clampY(p, p.door.y + dy)))
        Sel.Horizon -> p.copy(horizon = (p.horizon + dy).coerceIn(0.05f * p.height, 0.9f * p.height))
        is Sel.Obj -> p.copy(objects = p.objects.map {
            if (it.id == s.id) it.copy(x = clampX(p, it.x + dx), y = clampY(p, it.y + dy)) else it
        })
        is Sel.StepAt -> p.copy(steps = p.steps.mapIndexed { i, st ->
            if (i == s.index) st.copy(at = Pt(clampX(p, (st.at ?: defaultAt(p)).x + dx), clampY(p, (st.at ?: defaultAt(p)).y + dy))) else st
        })
    }

    /** Startpunkt, wenn ein Schritt noch keinen eigenen Ort hat und einer gesetzt wird. */
    fun defaultAt(p: Plan): Pt {
        val y = if (p.view == ViewMode.SIDE) p.horizon + (p.height - p.horizon) * 0.65f else p.height * 0.55f
        return Pt(p.width * 0.5f, y)
    }

    /** Größe von Tür oder Gegenstand mit Faktoren für Breite und Höhe ändern (Seitenverhältnis frei). */
    fun stretch(p: Plan, s: Sel, fw: Float, fh: Float): Plan = when (s) {
        Sel.Door -> p.copy(door = p.door.copy(w = clampSize(p, p.door.w * fw), h = clampSize(p, p.door.h * fh)))
        is Sel.Obj -> p.copy(objects = p.objects.map {
            if (it.id == s.id) it.copy(w = clampSize(p, it.w * fw), h = clampSize(p, it.h * fh)) else it
        })
        else -> p
    }

    fun scale(p: Plan, s: Sel, f: Float) = stretch(p, s, f, f)

    fun rotate(p: Plan, s: Sel, deg: Float): Plan = when (s) {
        Sel.Door -> p.copy(door = p.door.copy(rot = norm(p.door.rot + deg)))
        is Sel.Obj -> p.copy(objects = p.objects.map { if (it.id == s.id) it.copy(rot = norm(it.rot + deg)) else it })
        else -> p
    }

    private fun norm(a: Float): Float {
        var r = a % 360f
        if (r > 180f) r -= 360f
        if (r < -180f) r += 360f
        return r
    }

    fun addObject(p: Plan, type: ObjType): Plan {
        val y = if (p.view == ViewMode.SIDE) p.horizon + (p.height - p.horizon) * 0.55f else p.height * 0.5f
        val o = SceneObject(
            id = p.nextId, type = type, x = p.width * 0.5f, y = y,
            w = type.defW * p.width, h = type.defH * p.height,
        )
        return p.copy(objects = p.objects + o, nextId = p.nextId + 1)
    }

    fun removeObject(p: Plan, id: Int): Plan = p.copy(
        objects = p.objects.filter { it.id != id },
        steps = p.steps.map { if (it.objectId == id) it.copy(objectId = -1) else it },
    )

    fun setObjectShape(p: Plan, id: Int, shape: ShapeKind) =
        p.copy(objects = p.objects.map { if (it.id == id) it.copy(shape = shape) else it })

    // ------------------------------------------------------------------ Schritte
    fun addStep(p: Plan, a: ActionType): Plan {
        val firstHide = p.objects.firstOrNull { it.canHide(p.view) }
        val st = when (a) {
            ActionType.SWEEP -> Step(a, seconds = 6f)
            ActionType.HIDE -> Step(a, retreat = if (firstHide != null) Retreat.OBJECT else Retreat.DOOR, objectId = firstHide?.id ?: -1, seconds = 4f)
            ActionType.SCARE -> Step(a, retreat = Retreat.DOOR, objectId = firstHide?.id ?: -1, seconds = 4f)
        }
        return p.copy(steps = p.steps + st)
    }

    fun updateStep(p: Plan, i: Int, f: (Step) -> Step): Plan =
        p.copy(steps = p.steps.mapIndexed { k, s -> if (k == i) f(s) else s })

    fun removeStep(p: Plan, i: Int): Plan =
        if (p.steps.size <= 1) p else p.copy(steps = p.steps.filterIndexed { k, _ -> k != i })

    fun moveStep(p: Plan, i: Int, delta: Int): Plan {
        val j = i + delta
        if (i !in p.steps.indices || j !in p.steps.indices) return p
        val l = p.steps.toMutableList()
        val t = l[i]
        l[i] = l[j]
        l[j] = t
        return p.copy(steps = l)
    }

    // ------------------------------------------------------------------ Anfassen im Foto
    private fun rotLocal(p: Pt, deg: Float): Pt {
        val a = deg.toDouble() * PI / 180.0
        return Pt((p.x * cos(a) - p.y * sin(a)).toFloat(), (p.x * sin(a) + p.y * cos(a)).toFloat())
    }

    private fun doorLocal(d: Door, w: Pt): Pt = rotLocal(w - d.base, -d.rot)

    fun doorContains(d: Door, w: Pt): Boolean {
        val l = doorLocal(d, w)
        return abs(l.x) <= d.w / 2f && l.y <= 0f && l.y >= -d.h
    }

    /** Position des Größen-Griffs der Auswahl im Foto, null wenn es keinen gibt. */
    fun handle(p: Plan, s: Sel): Pt? = when (s) {
        Sel.Door -> p.door.base + rotLocal(Pt(p.door.w / 2f, -p.door.h), p.door.rot)
        is Sel.Obj -> p.objectById(s.id)?.let { it.toWorld(Pt(it.w / 2f, it.h / 2f)) }
        else -> null
    }

    /** Größe so setzen, dass der Griff bei w liegt. */
    fun dragHandle(p: Plan, s: Sel, w: Pt): Plan = when (s) {
        Sel.Door -> {
            val l = doorLocal(p.door, w)
            p.copy(door = p.door.copy(w = clampSize(p, 2f * abs(l.x)), h = clampSize(p, -l.y)))
        }
        is Sel.Obj -> p.copy(objects = p.objects.map {
            if (it.id == s.id) {
                val l = it.toLocal(w)
                it.copy(w = clampSize(p, 2f * abs(l.x)), h = clampSize(p, 2f * abs(l.y)))
            } else it
        })
        else -> p
    }

    /**
     * Was liegt bei w? Reihenfolge: Schrittmarken, Gegenstände (oberster zuerst), Tür, Horizont (Seitenansicht).
     * tol = Fangradius in Foto-Pixeln für Marken und Linien.
     */
    fun hit(p: Plan, w: Pt, tol: Float): Sel {
        p.steps.forEachIndexed { i, st ->
            val at = st.at
            if (at != null && at.dist(w) <= tol) return Sel.StepAt(i)
        }
        for (o in p.objects.asReversed()) if (o.contains(w)) return Sel.Obj(o.id)
        if (doorContains(p.door, w)) return Sel.Door
        if (p.view == ViewMode.SIDE && abs(w.y - p.horizon) <= tol) return Sel.Horizon
        return Sel.None
    }
}
