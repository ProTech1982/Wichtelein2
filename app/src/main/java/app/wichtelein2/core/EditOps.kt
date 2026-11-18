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

    /** Oberes Ende des Pfads eines Leiter-/Treppen-Schritts (StepAt ist das untere Ende). */
    data class StepTo(val index: Int) : Sel
}

/** Bearbeitungsebene: Beim Bearbeiten ist immer nur eine aktiv (Reiter Tür, Objekte, Ablauf, Ansicht), alles andere ist gesperrt. */
enum class Layer { DOOR, OBJECTS, STEPS, HORIZON;

    companion object {
        /** Ebene zum Reiter im Editor (0 Tür, 1 Objekte, 2 Ablauf, 3 Ansicht). */
        fun ofTab(tab: Int): Layer = when (tab) {
            0 -> DOOR
            1 -> OBJECTS
            2 -> STEPS
            else -> HORIZON
        }
    }
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
            if (i == s.index) {
                val c = st.at ?: stepDefault(p, st, false)
                st.copy(at = Pt(clampX(p, c.x + dx), clampY(p, c.y + dy)))
            } else st
        })
        is Sel.StepTo -> p.copy(steps = p.steps.mapIndexed { i, st ->
            if (i == s.index) {
                val c = st.to ?: stepDefault(p, st, true)
                st.copy(to = Pt(clampX(p, c.x + dx), clampY(p, c.y + dy)))
            } else st
        })
    }

    /** Startpunkt eines Orts/Pfadendes, wenn der Schritt noch keinen eigenen hat: bei Leiter/Treppe der automatische Pfad. */
    private fun stepDefault(p: Plan, st: Step, top: Boolean): Pt {
        if (st.action.isClimb) {
            val path = climbPath(p, st)
            if (path != null) return if (top) path.second else path.first
        }
        return defaultAt(p)
    }

    // ------------------------------------------------------------------ Leiter und Treppe
    /** Gegenstand eines Leiter-/Treppen-Schritts: der gewählte (passender Typ), sonst der nächste passende zu `near`, sonst der erste. */
    fun climbObject(p: Plan, s: Step, near: Pt? = null): SceneObject? {
        if (!s.action.isClimb) return null
        val type = s.action.climbType
        p.objectById(s.objectId)?.let { if (it.type == type) return it }
        val c = p.objects.filter { it.type == type }
        return if (near != null) c.minByOrNull { it.center.dist(near) } else c.firstOrNull()
    }

    /** Automatischer Pfad am Gegenstand: Leiter von unten nach oben mittig, Treppe von unten links nach oben rechts. */
    fun autoClimbPath(o: SceneObject, action: ActionType): Pair<Pt, Pt> =
        if (action == ActionType.LADDER) {
            Pair(o.toWorld(Pt(0f, o.h * 0.46f)), o.toWorld(Pt(0f, -o.h * 0.40f)))
        } else {
            Pair(o.toWorld(Pt(-o.w * 0.40f, o.h * 0.46f)), o.toWorld(Pt(o.w * 0.40f, -o.h * 0.30f)))
        }

    /** Die im Foto gezeigten Punkte eines Schritts: (Ort bzw. Pfad unten, Pfad oben); bei Leiter/Treppe auch der automatische Pfad. */
    fun stepPoints(p: Plan, st: Step): Pair<Pt?, Pt?> {
        if (st.action.isClimb) {
            val path = climbPath(p, st)
            if (path != null) return Pair(path.first, path.second)
        }
        return Pair(st.at, st.to)
    }

    /** Pfad (unten, oben) eines Leiter-/Treppen-Schritts: eigene Punkte, fehlende aus dem Gegenstand; null, wenn nichts da ist. */
    fun climbPath(p: Plan, s: Step, near: Pt? = null): Pair<Pt, Pt>? {
        if (!s.action.isClimb) return null
        val a = s.at
        val b = s.to
        if (a != null && b != null) return Pair(a, b)
        val o = climbObject(p, s, near) ?: return null
        val auto = autoClimbPath(o, s.action)
        return Pair(a ?: auto.first, b ?: auto.second)
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
            ActionType.LADDER, ActionType.STAIRS ->
                Step(a, objectId = p.objects.firstOrNull { it.type == a.climbType }?.id ?: -1, seconds = 2f)
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
     * tol = Fangradius in Foto-Pixeln für Marken und Linien. only: nur diese Ebene ist aktiv (der Rest ist gesperrt), null = alles.
     */
    fun hit(p: Plan, w: Pt, tol: Float, only: Layer? = null): Sel {
        if (only == null || only == Layer.STEPS) {
            p.steps.forEachIndexed { i, st ->
                val (at, to) = stepPoints(p, st)
                if (at != null && at.dist(w) <= tol) return Sel.StepAt(i)
                if (to != null && to.dist(w) <= tol) return Sel.StepTo(i)
            }
        }
        if (only == null || only == Layer.OBJECTS) {
            for (o in p.objects.asReversed()) if (o.contains(w)) return Sel.Obj(o.id)
        }
        if ((only == null || only == Layer.DOOR) && doorContains(p.door, w)) return Sel.Door
        if ((only == null || only == Layer.HORIZON) && p.view == ViewMode.SIDE && abs(w.y - p.horizon) <= tol) return Sel.Horizon
        return Sel.None
    }

    /** Liegt w auf der aktuellen Auswahl? Nur diese darf beim Ziehen verschoben werden (alles andere ist gesperrt). */
    fun hitsSelection(p: Plan, s: Sel, w: Pt, tol: Float): Boolean = when (s) {
        Sel.None -> false
        Sel.Door -> doorContains(p.door, w) || p.door.base.dist(w) <= tol
        Sel.Horizon -> p.view == ViewMode.SIDE && abs(w.y - p.horizon) <= tol
        is Sel.Obj -> p.objectById(s.id)?.let { it.contains(w) || it.center.dist(w) <= tol } ?: false
        is Sel.StepAt -> p.steps.getOrNull(s.index)?.let { st -> stepPoints(p, st).first?.let { it.dist(w) <= tol } } ?: false
        is Sel.StepTo -> p.steps.getOrNull(s.index)?.let { st -> stepPoints(p, st).second?.let { it.dist(w) <= tol } } ?: false
    }

    /** Zu welcher Ebene gehört eine Auswahl? */
    fun layerOf(s: Sel): Layer? = when (s) {
        Sel.None -> null
        Sel.Door -> Layer.DOOR
        Sel.Horizon -> Layer.HORIZON
        is Sel.Obj -> Layer.OBJECTS
        is Sel.StepAt, is Sel.StepTo -> Layer.STEPS
    }
}
