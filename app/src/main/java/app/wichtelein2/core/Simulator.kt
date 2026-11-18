package app.wichtelein2.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Bewegungen des Wichtels; Bildanzahl muss zu assets/sprites/sprites.json passen (Test prüft das). */
enum class Anim(val key: String, val frames: Int) {
    IDLE("idle", 1),
    WALK("walk", 8),
    SNEAK("sneak", 8),
    RUN("run", 8),
    SWEEP("sweep", 8),
    SCARE("scare", 6),
    DUCK("duck", 4),
    CLIMB("climb", 8),
    STAIRS("stairs", 8),
}

/**
 * Ein Bild des Ablaufs. (x, y) = Fußpunkt im Foto, dir = Blickrichtung (0 zum Betrachter, im Uhrzeigersinn je 45°),
 * houseT = 0 draußen .. 1 im Haus (unsichtbar), doorOpen = 0 Tür zu .. 1 ganz offen, step = Index des Schritts (-1 Ein-/Ausgang).
 * refY = Foto-Höhe, nach der die Wichtelgröße berechnet wird (auf Leiter/Treppe fest, sonst = y);
 * ignore = -1: normal; sonst steht er auf Leiter/Treppe (Gegenstand-Nr. oder -2 ohne Gegenstand), dann deckt ihn nichts zu.
 */
data class Frame(
    val t: Float,
    val x: Float,
    val y: Float,
    val dir: Int,
    val anim: Anim,
    val frame: Int,
    val houseT: Float,
    val doorOpen: Float,
    val step: Int,
    val refY: Float = y,
    val ignore: Int = -1,
)

class Simulation(val frames: List<Frame>, val fps: Int, val warnings: List<String>) {
    val duration: Float get() = frames.size.toFloat() / fps
    fun at(t: Float): Frame = frames[(t * fps).toInt().coerceIn(0, frames.size - 1)]
}

/** Rechnet aus dem Plan (Tür, Gegenstände, Schritte) den ganzen Ablauf Bild für Bild aus. */
class Simulator(private val plan: Plan, private val fps: Int = 30) {
    private val nav = NavGrid(plan)
    private val scaler = Scaler(plan)
    private val dt = 1f / fps

    private class Raw(
        val t: Float, val x: Float, val y: Float, val dir: Int, val anim: Anim,
        val frame: Int, val houseT: Float, val step: Int, val refY: Float, val ignore: Int,
    )

    /** Der Wichtel ist auf einer Leiter/Treppe (Pfad a = unten, b = oben); solange gilt eine feste Größe und keine Verdeckung durch sie. */
    private class Elev(val a: Pt, val b: Pt, val action: ActionType, val objId: Int, val refY: Float)

    private val raws = ArrayList<Raw>()
    private val doorKeys = ArrayList<Pair<Float, Float>>()
    private val warnings = ArrayList<String>()

    private var t = 0f
    private var pos = plan.door.base
    private var heading = 0f          // Grad, 0 = zum Betrachter, wie headingToDir
    private var inside = true
    private var phase = 0f
    private var curStep = -1
    private var elev: Elev? = null

    fun run(): Simulation {
        hold(0.6f, Anim.IDLE, 1f)
        plan.steps.forEachIndexed { i, s ->
            curStep = i
            when (s.action) {
                ActionType.SWEEP -> doSweep(s)
                ActionType.HIDE -> doHide(s)
                ActionType.SCARE -> doScare(s)
                ActionType.LADDER, ActionType.STAIRS -> doClimb(s)
            }
        }
        curStep = -1
        ensureInside()
        hold(0.8f, Anim.IDLE, 1f)
        val frames = raws.map { r ->
            Frame(r.t, r.x, r.y, r.dir, r.anim, r.frame, r.houseT, doorAt(r.t), r.step, r.refY, r.ignore)
        }
        return Simulation(frames, fps, warnings)
    }

    // ------------------------------------------------------------------ Grundbausteine
    private fun dirIdx() = (((Math.round(heading / 45f) % 8) + 8) % 8)

    private fun emit(anim: Anim, frame: Int, houseT: Float) {
        val e = elev
        raws.add(Raw(t, pos.x, pos.y, dirIdx(), anim, frame.coerceIn(0, anim.frames - 1), houseT, curStep, e?.refY ?: pos.y, e?.objId ?: -1))
        t += dt
    }

    private fun normDeg(a: Float): Float {
        var r = a % 360f
        if (r < 0f) r += 360f
        return r
    }

    private fun turnToward(target: Float, maxDeg: Float) {
        var d = normDeg(target) - normDeg(heading)
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        val s = d.coerceIn(-maxDeg, maxDeg)
        heading = normDeg(heading + s)
    }

    private fun angleOf(v: Pt): Float = normDeg((atan2(v.x.toDouble(), v.y.toDouble()) * 180.0 / PI).toFloat())

    private fun hold(secs: Float, anim: Anim, houseT: Float, frame: Int = 0) {
        val n = max(1, Math.round(secs * fps))
        repeat(n) { emit(anim, frame, houseT) }
    }

    /** Tür ab `time` auf Ziel (1 offen, 0 zu) fahren; spätere Pläne werden ersetzt. Liefert die Zeit, zu der das Ziel erreicht ist. */
    private fun setDoor(time: Float, target: Float): Float {
        val v = doorAt(time)
        doorKeys.removeAll { it.first >= time }
        doorKeys.add(Pair(time, v))
        val end = time + 0.8f * abs(target - v)
        doorKeys.add(Pair(end, target))
        return end
    }

    private fun smooth(u: Float): Float {
        val c = u.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun doorAt(time: Float): Float {
        if (doorKeys.isEmpty()) return 0f
        val ks = doorKeys.sortedBy { it.first }
        if (time <= ks.first().first) return ks.first().second
        for (i in 1 until ks.size) {
            if (time <= ks[i].first) {
                val a = ks[i - 1]
                val b = ks[i]
                val span = b.first - a.first
                if (span <= 0f) return b.second
                return a.second + (b.second - a.second) * smooth((time - a.first) / span)
            }
        }
        return ks.last().second
    }

    private fun heightAt(p: Pt) = scaler.heightAt(p.y)

    /**
     * Läuft den Linienzug ab (Geschwindigkeit und Schrittlänge in Wichtelhöhen), Bild für Bild.
     * onTick bekommt die Restlänge in Pixeln.
     */
    private fun move(path: List<Pt>, anim: Anim, speedH: Float, strideH: Float, onTick: ((Float) -> Unit)? = null) {
        if (path.size < 2) return
        var total = 0f
        for (i in 1 until path.size) total += path[i - 1].dist(path[i])
        var traveled = 0f
        var seg = 0
        var segPos = 0f
        pos = path[0]
        while (seg < path.size - 1) {
            val hpx = heightAt(pos)
            val step = speedH * hpx * dt
            traveled += step
            phase += step / (strideH * hpx)
            var rem = step
            while (rem > 0f && seg < path.size - 1) {
                val a = path[seg]
                val b = path[seg + 1]
                val len = a.dist(b)
                if (len < 0.001f) {
                    seg++
                    segPos = 0f
                    continue
                }
                val left = len - segPos
                if (rem >= left) {
                    rem -= left
                    segPos = 0f
                    seg++
                    pos = b
                } else {
                    segPos += rem
                    rem = 0f
                    pos = Pt(a.x + (b.x - a.x) * (segPos / len), a.y + (b.y - a.y) * (segPos / len))
                }
            }
            val hs = min(seg, path.size - 2)
            turnToward(angleOf(path[hs + 1] - path[hs]), 900f * dt)
            val f = floor((phase - floor(phase)) * anim.frames).toInt()
            emit(anim, f, 0f)
            onTick?.invoke(max(0f, total - traveled))
        }
    }

    private fun walkTo(target: Pt, anim: Anim = Anim.WALK, speedH: Float = 0.9f, strideH: Float = 0.45f,
                       onTick: ((Float) -> Unit)? = null) {
        descend()   // wer noch auf einer Leiter/Treppe steht, steigt zuerst hinunter
        val r = nav.findPath(pos, target)
        if (!r.ok) warnings.add("Kein Weg gefunden (Schritt $curStep)")
        move(r.points, anim, speedH, strideH, onTick)
    }

    // ------------------------------------------------------------------ Tür
    /** Stelle vor der Tür, an der der Wichtel aus der Tür kommt bzw. die Tür betritt. */
    private fun doorFront(): Pt {
        val d = plan.door.base
        return nav.nearestFree(Pt(d.x, d.y + 0.55f * heightAt(d)))
    }

    /**
     * Herauskommen: Tür auf, der Wichtel steht noch im Haus (unsichtbar) und tritt in die Türöffnung; erst wenn er halb
     * sichtbar in der Öffnung steht, geht er heraus. Die Tür schließt sich, wenn er draußen ist.
     */
    private fun emerge() {
        val d = plan.door.base
        val out = doorFront()
        pos = d
        heading = 0f
        // Ist die Tür gerade erst zugegangen, erst ganz zufahren lassen und kurz warten, statt sie sofort wieder aufzureißen
        if (doorKeys.isNotEmpty()) {
            while (doorAt(t) > 0.02f) emit(Anim.IDLE, 0, 1f)
            hold(0.5f, Anim.IDLE, 1f)
        }
        setDoor(t, 1f)
        hold(0.85f, Anim.IDLE, 1f)
        val n = max(1, Math.round(1.2f * fps))
        val dirAng = angleOf(out - d)
        for (i in 1..n) {
            val u = i.toFloat() / n
            val moved = smooth(max(0f, (u - 0.5f) * 2f))            // erst im Türrahmen auftauchen, dann herausgehen
            val before = pos
            pos = Pt(d.x + (out.x - d.x) * moved, d.y + (out.y - d.y) * moved)
            turnToward(dirAng, 900f * dt)
            val hpx = heightAt(pos)
            phase += before.dist(pos) / (0.45f * hpx) + 0.6f * dt
            emit(Anim.WALK, floor((phase - floor(phase)) * Anim.WALK.frames).toInt(), 1f - smooth(u))
        }
        pos = out
        setDoor(t + 0.1f, 0f)
        inside = false
    }

    private fun ensureOutside() {
        if (inside) emerge()
    }

    /**
     * Hineingehen (Gegenstück zum Herauskommen): zur Stelle vor der Tür gehen, Tür öffnet sich rechtzeitig, der Wichtel
     * geht in die Türöffnung und verschwindet dort; danach schließt sich die Tür genau einmal.
     */
    private fun enterHouse(anim: Anim, speedH: Float, strideH: Float) {
        val d = plan.door.base
        val out = doorFront()
        var opened = false
        var fullOpen = 0f
        val trigger = 1.0f * heightAt(out)
        walkTo(out, anim, speedH, strideH) { rem ->
            if (!opened && rem <= trigger) {
                fullOpen = setDoor(t, 1f)
                opened = true
            }
        }
        if (!opened) fullOpen = setDoor(t, 1f)
        pos = out
        // Mit Blick zur Tür warten, bis sie ganz offen ist
        val toDoor = angleOf(d - out)
        while (t < fullOpen) {
            turnToward(toDoor, 600f * dt)
            emit(Anim.IDLE, 0, 0f)
        }
        val n = max(1, Math.round(1.6f * fps))
        for (i in 1..n) {
            val u = i.toFloat() / n
            val moved = smooth(min(1f, u * 2f))                      // erst zur Türöffnung gehen, dann tiefer in den Raum
            val before = pos
            pos = Pt(out.x + (d.x - out.x) * moved, out.y + (d.y - out.y) * moved)
            turnToward(toDoor, 900f * dt)
            val hpx = heightAt(pos)
            phase += before.dist(pos) / (strideH * hpx) + 0.6f * dt
            emit(anim, floor((phase - floor(phase)) * anim.frames).toInt(), smooth(u))
        }
        pos = d
        hold(0.6f, Anim.IDLE, 1f)    // Tür bleibt offen, bis er ganz im Raum verschwunden ist
        setDoor(t, 0f)
        hold(0.9f, Anim.IDLE, 1f)
        inside = true
    }

    private fun ensureInside() {
        if (!inside) enterHouse(Anim.WALK, 0.9f, 0.45f)
    }

    // ------------------------------------------------------------------ Ziele
    /** Vorgeschlagene Stelle am Boden: frei begehbar und nicht von einem markierten Gegenstand verdeckt, damit man den Wichtel sieht. */
    private fun floorPoint(fx: Float): Pt {
        val ys = if (plan.view == ViewMode.SIDE) {
            listOf(0.65f, 0.8f, 0.5f, 0.92f, 0.35f).map { plan.horizon + (plan.height - plan.horizon) * it }
        } else {
            listOf(0.55f, 0.75f, 0.35f, 0.9f, 0.15f).map { plan.height * it }
        }
        val xs = listOf(fx, 0.5f, 0.35f, 0.65f, 0.2f, 0.8f)
        for (y in ys) {
            for (x in xs) {
                val p = Pt(plan.width * x, y)
                if (nav.isFree(p) && plan.objects.none { it.contains(p) }) return p
            }
        }
        return nav.nearestFree(Pt(plan.width * fx, ys[0]))
    }

    private fun autoSweepPoint() = floorPoint(if (plan.door.x > plan.width / 2f) 0.35f else 0.65f)
    private fun autoScarePoint() = floorPoint(0.5f)

    /** Stelle, an der der Wichtel hinter/unter dem Gegenstand verschwindet. */
    fun hideSpot(o: SceneObject): Pt {
        val h = heightAt(o.center)
        val cands = ArrayList<Pt>()
        val f = o.footprintLocal(plan.view)
        if (f == null) {
            cands.add(o.center)
        } else if (plan.view == ViewMode.SIDE) {
            val top = o.footprintTopCenter(ViewMode.SIDE)
            cands.add(Pt(top.x, top.y - 0.12f * h))
            cands.add(o.toWorld(Pt(f[0] - 0.25f * h, f[1] - 0.05f * h)))
            cands.add(o.toWorld(Pt(f[2] + 0.25f * h, f[1] - 0.05f * h)))
        } else {
            cands.add(o.center)
        }
        for (c in cands) if (nav.isFree(c)) return c
        return nav.nearestFree(o.center)
    }

    private fun chooseHideObject(s: Step): SceneObject? {
        val o = plan.objectById(s.objectId)
        if (o != null && o.canHide(plan.view)) return o
        return plan.objects.filter { it.canHide(plan.view) }.minByOrNull { it.center.dist(pos) }
    }

    // ------------------------------------------------------------------ Aktionen
    private fun duck(down: Boolean, secs: Float) {
        val n = max(1, Math.round(secs * fps))
        for (i in 1..n) {
            val u = i.toFloat() / n
            val f = if (down) floor(u * (Anim.DUCK.frames - 1) + 0.5f).toInt() else floor((1f - u) * (Anim.DUCK.frames - 1) + 0.5f).toInt()
            emit(Anim.DUCK, f, 0f)
        }
    }

    private fun doSweep(s: Step) {
        // Steht er oben auf Leiter/Treppe, fegt er dort oben (der Ort des Schritts wird dann nicht gebraucht)
        val stay = elev != null
        if (!stay) {
            ensureOutside()
            val target = nav.nearestFree(s.at ?: autoSweepPoint())
            walkTo(target)
        }
        hold(0.2f, Anim.IDLE, 0f)
        val hpx = heightAt(pos)
        var a = 0.9f * hpx
        while (a > 0.2f * hpx && (!nav.isFree(Pt(pos.x - a, pos.y)) || !nav.isFree(Pt(pos.x + a, pos.y)))) a *= 0.8f
        if (stay) a = 0f
        val centerP = pos
        var dirSign = 1f
        val n = max(1, Math.round(max(1f, s.seconds) * fps))
        for (i in 0 until n) {
            val sp = if (a > 0.2f * hpx) 0.22f * heightAt(pos) * dt else 0f
            var nx = pos.x + dirSign * sp
            if (nx > centerP.x + a) { nx = centerP.x + a; dirSign = -1f }
            if (nx < centerP.x - a) { nx = centerP.x - a; dirSign = 1f }
            pos = Pt(nx, pos.y)
            turnToward(if (dirSign > 0f) 45f else 315f, 600f * dt)
            val f = floor(((i * dt) % 1.0f) * Anim.SWEEP.frames).toInt()
            emit(Anim.SWEEP, f, 0f)
        }
        hold(0.4f, Anim.IDLE, 0f)
    }

    private fun hideBehind(o: SceneObject, anim: Anim, speedH: Float, strideH: Float, downSecs: Float, upSecs: Float, secs: Float) {
        val spot = hideSpot(o)
        walkTo(spot, anim, speedH, strideH)
        heading = 0f
        duck(true, downSecs)
        hold(max(0.5f, secs), Anim.DUCK, 0f, Anim.DUCK.frames - 1)
        duck(false, upSecs)
        hold(0.3f, Anim.IDLE, 0f)
    }

    private fun doHide(s: Step) {
        val o = if (s.retreat == Retreat.OBJECT) chooseHideObject(s) else null
        if (o != null) {
            ensureOutside()
            hideBehind(o, Anim.SNEAK, 0.45f, 0.30f, 0.7f, 0.6f, s.seconds)
        } else {
            if (s.retreat == Retreat.OBJECT) warnings.add("Kein Gegenstand zum Verstecken, Wichtel geht in die Tür")
            // Schon im Haus: einfach drinnen bleiben. Erst herauszukommen und gleich wieder hineinzugehen sähe aus wie ein
            // kurzes Herausgucken mit zuckender Tür.
            if (!inside) enterHouse(Anim.SNEAK, 0.45f, 0.30f)
            hold(max(0.5f, s.seconds), Anim.IDLE, 1f)
        }
    }

    private fun doScare(s: Step) {
        if (elev == null) {
            ensureOutside()
            walkTo(nav.nearestFree(s.at ?: autoScarePoint()))
        }
        heading = 0f
        hold(0.5f, Anim.IDLE, 0f)
        // Schreck: Zusammenzucken, Hände hoch, Beben
        hold(0.35f, Anim.SCARE, 0f, 0)
        hold(0.18f, Anim.SCARE, 0f, 1)
        val shakes = max(1, Math.round(1.0f * fps))
        for (i in 0 until shakes) emit(Anim.SCARE, 2 + (i * 6 / fps) % 4, 0f)
        val o = if (s.retreat == Retreat.OBJECT) chooseHideObject(s) else null
        if (o != null) {
            hideBehind(o, Anim.RUN, 1.9f, 0.75f, 0.5f, 0.6f, s.seconds)
        } else {
            if (s.retreat == Retreat.OBJECT) warnings.add("Kein Gegenstand zur Flucht, Wichtel läuft in die Tür")
            enterHouse(Anim.RUN, 1.9f, 0.75f)
        }
    }

    // ------------------------------------------------------------------ Leiter und Treppe
    /** Blickrichtung (Grad) auf der Leiter/Treppe: seitlich zur Leiter hin bzw. entlang der Treppe, von oben in Bewegungsrichtung. */
    private fun climbHeading(from: Pt, to: Pt, action: ActionType): Float = when {
        plan.view == ViewMode.TOP -> angleOf(to - from)
        action == ActionType.LADDER -> 180f
        to.x >= from.x -> 90f
        else -> 270f
    }

    /** Läuft den Pfad von `from` nach `to` auf der Leiter/Treppe e (feste Größe, keine Verdeckung durch sie). */
    private fun climbAlong(from: Pt, to: Pt, e: Elev) {
        elev = e
        val ladder = e.action == ActionType.LADDER
        val anim = if (ladder) Anim.CLIMB else Anim.STAIRS
        val speedH = if (ladder) 0.30f else 0.45f
        val strideH = if (ladder) 0.26f else 0.32f
        val hpx = max(1f, scaler.heightAt(e.refY))
        val len = from.dist(to)
        val n = max(2, Math.ceil((len / (speedH * hpx * dt)).toDouble()).toInt())
        val face = climbHeading(from, to, e.action)
        heading = face
        for (i in 1..n) {
            val u = i.toFloat() / n
            pos = Pt(from.x + (to.x - from.x) * u, from.y + (to.y - from.y) * u)
            phase += (len / n) / (strideH * hpx)
            emit(anim, floor((phase - floor(phase)) * anim.frames).toInt(), 0f)
        }
        pos = to
    }

    /** Wer auf einer Leiter/Treppe steht, steigt (Pfad rückwärts) wieder hinunter. */
    private fun descend() {
        val e = elev ?: return
        hold(0.25f, Anim.IDLE, 0f)
        climbAlong(e.b, e.a, e)
        elev = null
        hold(0.2f, Anim.IDLE, 0f)
    }

    private fun samePath(e: Elev, a: Pt, b: Pt) = e.a.dist(a) < 1f && e.b.dist(b) < 1f

    private fun doClimb(s: Step) {
        val e0 = elev
        // Steht der Wichtel schon auf der Leiter/Treppe dieses Schritts (z. B. „hinunter“ nach „hinauf“), gilt deren Pfad.
        val path = if (e0 != null && e0.action == s.action && (!s.up || (s.at == null && s.to == null))) {
            Pair(e0.a, e0.b)
        } else {
            EditOps.climbPath(plan, s, pos)
        }
        if (path == null) {
            warnings.add("Keine ${s.action.label} markiert (Schritt ${curStep + 1}): im Tab „Objekte“ eine anlegen oder den Pfad im Foto setzen")
            return
        }
        val (a, b) = path
        val obj = EditOps.climbObject(plan, s, pos)
        if (obj != null && obj.type != s.action.climbType) {
            warnings.add("Keine ${s.action.label} markiert (Schritt ${curStep + 1}): ${obj.type.label} wird stattdessen benutzt")
        }
        val objId = obj?.id ?: e0?.objId ?: -2   // -2: auf Leiter/Treppe ohne Gegenstand
        val cur = elev
        if (cur != null && !samePath(cur, a, b)) descend()
        val e = elev ?: Elev(a, b, s.action, objId, a.y)
        if (elev == null) {
            ensureOutside()
            walkTo(a)
            hold(0.25f, Anim.IDLE, 0f)
            if (pos.dist(a) > 2f) climbAlong(pos, a, e)   // liegt A nicht auf begehbarem Boden (z. B. über dem Horizont), erst auf A steigen
            climbAlong(a, b, e)               // hinauf (bei „hinunter“ steigt er erst hinauf, damit er oben ankommt)
            hold(if (s.up) max(0.3f, s.seconds) else 0.5f, Anim.IDLE, 0f)
        } else if (s.up) {
            hold(max(0.3f, s.seconds), Anim.IDLE, 0f)
        }
        if (!s.up) {
            climbAlong(b, a, e)
            elev = null
            hold(0.3f, Anim.IDLE, 0f)
        }
    }
}
