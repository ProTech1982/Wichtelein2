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
}

/**
 * Ein Bild des Ablaufs. (x, y) = Fußpunkt im Foto, dir = Blickrichtung (0 zum Betrachter, im Uhrzeigersinn je 45°),
 * houseT = 0 draußen .. 1 im Haus (unsichtbar), doorOpen = 0 Tür zu .. 1 ganz offen, step = Index des Schritts (-1 Ein-/Ausgang).
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
        val frame: Int, val houseT: Float, val step: Int,
    )

    private val raws = ArrayList<Raw>()
    private val doorKeys = ArrayList<Pair<Float, Float>>()
    private val warnings = ArrayList<String>()

    private var t = 0f
    private var pos = plan.door.base
    private var heading = 0f          // Grad, 0 = zum Betrachter, wie headingToDir
    private var inside = true
    private var phase = 0f
    private var curStep = -1

    fun run(): Simulation {
        hold(0.6f, Anim.IDLE, 1f)
        plan.steps.forEachIndexed { i, s ->
            curStep = i
            when (s.action) {
                ActionType.SWEEP -> doSweep(s)
                ActionType.HIDE -> doHide(s)
                ActionType.SCARE -> doScare(s)
            }
        }
        curStep = -1
        ensureInside()
        hold(0.8f, Anim.IDLE, 1f)
        val frames = raws.map { r ->
            Frame(r.t, r.x, r.y, r.dir, r.anim, r.frame, r.houseT, doorAt(r.t), r.step)
        }
        return Simulation(frames, fps, warnings)
    }

    // ------------------------------------------------------------------ Grundbausteine
    private fun dirIdx() = (((Math.round(heading / 45f) % 8) + 8) % 8)

    private fun emit(anim: Anim, frame: Int, houseT: Float) {
        raws.add(Raw(t, pos.x, pos.y, dirIdx(), anim, frame.coerceIn(0, anim.frames - 1), houseT, curStep))
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
        val r = nav.findPath(pos, target)
        if (!r.ok) warnings.add("Kein Weg gefunden (Schritt $curStep)")
        move(r.points, anim, speedH, strideH, onTick)
    }

    // ------------------------------------------------------------------ Tür
    private fun emerge() {
        val d = plan.door.base
        val out = nav.nearestFree(Pt(d.x, d.y + 0.55f * heightAt(d)))
        pos = d
        heading = 0f
        setDoor(t, 1f)
        hold(0.85f, Anim.IDLE, 1f)
        val n = max(1, Math.round(0.9f * fps))
        val dirAng = angleOf(out - d)
        for (i in 1..n) {
            val u = i.toFloat() / n
            pos = Pt(d.x + (out.x - d.x) * u, d.y + (out.y - d.y) * u)
            turnToward(dirAng, 900f * dt)
            val hpx = heightAt(pos)
            phase += (out.dist(d) / n) / (0.45f * hpx)
            emit(Anim.WALK, floor((phase - floor(phase)) * Anim.WALK.frames).toInt(), 1f - smooth(u))
        }
        pos = out
        setDoor(t + 0.1f, 0f)
        inside = false
    }

    private fun ensureOutside() {
        if (inside) emerge()
    }

    private fun enterHouse(anim: Anim, speedH: Float, strideH: Float) {
        val d = plan.door.base
        var opened = false
        var fullOpen = 0f
        val trigger = 1.3f * heightAt(d)
        walkTo(d, anim, speedH, strideH) { rem ->
            if (!opened && rem <= trigger) {
                fullOpen = setDoor(t, 1f)
                opened = true
            }
        }
        if (!opened) fullOpen = setDoor(t, 1f)
        pos = d
        // Warten, bis die Tür ganz offen ist, mit Blick zur Tür
        heading = 180f
        while (t < fullOpen) emit(Anim.IDLE, 0, 0f)
        val n = max(1, Math.round(0.9f * fps))
        for (i in 1..n) {
            val u = i.toFloat() / n
            val hpx = heightAt(pos)
            phase += (0.35f * hpx / n) / (strideH * hpx)
            emit(anim, floor((phase - floor(phase)) * anim.frames).toInt(), smooth(u))
        }
        setDoor(t + 0.05f, 0f)
        hold(0.9f, Anim.IDLE, 1f)
        inside = true
    }

    private fun ensureInside() {
        if (!inside) enterHouse(Anim.WALK, 0.9f, 0.45f)
    }

    // ------------------------------------------------------------------ Ziele
    private fun floorPoint(fx: Float): Pt {
        val y = if (plan.view == ViewMode.SIDE) plan.horizon + (plan.height - plan.horizon) * 0.65f else plan.height * 0.55f
        return nav.nearestFree(Pt(plan.width * fx, y))
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
        ensureOutside()
        val target = nav.nearestFree(s.at ?: autoSweepPoint())
        walkTo(target)
        hold(0.2f, Anim.IDLE, 0f)
        val hpx = heightAt(pos)
        var a = 0.9f * hpx
        while (a > 0.2f * hpx && (!nav.isFree(Pt(pos.x - a, pos.y)) || !nav.isFree(Pt(pos.x + a, pos.y)))) a *= 0.8f
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
        ensureOutside()
        val o = if (s.retreat == Retreat.OBJECT) chooseHideObject(s) else null
        if (o != null) {
            hideBehind(o, Anim.SNEAK, 0.45f, 0.30f, 0.7f, 0.6f, s.seconds)
        } else {
            if (s.retreat == Retreat.OBJECT) warnings.add("Kein Gegenstand zum Verstecken, Wichtel geht in die Tür")
            enterHouse(Anim.SNEAK, 0.45f, 0.30f)
            hold(max(0.5f, s.seconds), Anim.IDLE, 1f)
        }
    }

    private fun doScare(s: Step) {
        ensureOutside()
        walkTo(nav.nearestFree(s.at ?: autoScarePoint()))
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
}
