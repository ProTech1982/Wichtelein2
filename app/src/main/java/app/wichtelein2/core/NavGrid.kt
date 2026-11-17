package app.wichtelein2.core

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Begehbares Raster über dem Foto (A*-Wegsuche). Gegenstände mit Standfläche sperren Zellen
 * (um den Fußradius des Wichtels vergrößert), Gras kostet mehr, in der Seitenansicht ist nur der
 * Boden unterhalb des Horizonts begehbar.
 */
class NavGrid(private val plan: Plan) {
    private val scaler = Scaler(plan)
    val cell: Float = max(plan.width, plan.height) / 110f
    val cols = (plan.width / cell).toInt() + 1
    val rows = (plan.height / cell).toInt() + 1
    private val blocked = BooleanArray(cols * rows)
    private val cost = FloatArray(cols * rows) { 1f }

    init {
        val door = plan.door
        val doorRadius = 0.5f * scaler.heightAt(door.y)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val p = center(c, r)
                var b = !inside(p)
                var k = 1f
                val rad = footRadius(p.y)
                for (o in plan.objects) {
                    if (o.distToFootprint(p, plan.view) < rad) b = true
                    if (o.type.cost > 1f && o.contains(p)) k = max(k, o.type.cost)
                }
                // Vor der Tür muss immer Platz sein
                if (abs(p.x - door.x) < doorRadius && p.y >= door.y - 0.2f * doorRadius && p.y <= door.y + 1.4f * doorRadius) b = false
                blocked[r * cols + c] = b
                cost[r * cols + c] = k
            }
        }
    }

    private fun footRadius(y: Float) = 0.10f * scaler.heightAt(y)

    /** Im Foto und (Seitenansicht) unter dem Horizont? */
    fun inside(p: Pt): Boolean {
        val m = 0.01f * plan.height
        val minY = if (plan.view == ViewMode.SIDE) plan.horizon + m else m
        return p.x >= m && p.x <= plan.width - m && p.y >= minY && p.y <= plan.height - m
    }

    private fun center(c: Int, r: Int) = Pt((c + 0.5f) * cell, (r + 0.5f) * cell)
    private fun colOf(x: Float) = (x / cell).toInt().coerceIn(0, cols - 1)
    private fun rowOf(y: Float) = (y / cell).toInt().coerceIn(0, rows - 1)

    /** Ist an dieser Stelle freier Boden (Zelle nicht gesperrt)? */
    fun isFree(p: Pt): Boolean = inside(p) && !blocked[rowOf(p.y) * cols + colOf(p.x)]

    /** Nächster freier Punkt (Suche in wachsenden Ringen); p selbst, wenn schon frei. */
    fun nearestFree(p: Pt): Pt {
        if (isFree(p)) return p
        val c0 = colOf(p.x)
        val r0 = rowOf(p.y)
        var best: Pt? = null
        var bestD = Float.MAX_VALUE
        val maxRing = max(cols, rows)
        for (ring in 1..maxRing) {
            for (r in (r0 - ring)..(r0 + ring)) {
                for (c in (c0 - ring)..(c0 + ring)) {
                    if (max(abs(r - r0), abs(c - c0)) != ring) continue
                    if (c !in 0 until cols || r !in 0 until rows) continue
                    if (blocked[r * cols + c]) continue
                    val q = center(c, r)
                    if (!inside(q)) continue
                    val d = q.dist(p)
                    if (d < bestD) {
                        bestD = d
                        best = q
                    }
                }
            }
            if (best != null) break
        }
        return best ?: p
    }

    /** Gerade frei und nicht teurer (Gras) als der gefundene Weg an dieser Stelle? */
    private fun lineOk(a: Pt, b: Pt, maxCost: Float): Boolean {
        val d = a.dist(b)
        val n = max(1, (d / (cell * 0.5f)).toInt())
        for (i in 0..n) {
            val t = i.toFloat() / n
            val p = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            if (!isFree(p)) return false
            if (cost[rowOf(p.y) * cols + colOf(p.x)] > maxCost + 0.001f) return false
        }
        return true
    }

    /**
     * Weg von `from` nach `to` als Linienzug (Start und Ziel exakt). Liegt ein Ende im gesperrten Bereich,
     * wird bis zum nächsten freien Punkt gesucht und der Rest geradlinig angehängt.
     * Gibt es keinen Weg, kommt die gerade Linie zurück (ok = false).
     */
    fun findPath(from: Pt, to: Pt): PathResult {
        val s = nearestFree(from)
        val g = nearestFree(to)
        val sc = colOf(s.x)
        val sr = rowOf(s.y)
        val gc = colOf(g.x)
        val gr = rowOf(g.y)
        val n = cols * rows
        val gScore = FloatArray(n) { Float.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val closed = BooleanArray(n)
        val open = PriorityQueue<Pair<Float, Int>>(compareBy { it.first })
        val si = sr * cols + sc
        val gi = gr * cols + gc
        gScore[si] = 0f
        open.add(Pair(h(sc, sr, gc, gr), si))
        var found = false
        while (open.isNotEmpty()) {
            val (_, cur) = open.poll()!!
            if (closed[cur]) continue
            closed[cur] = true
            if (cur == gi) {
                found = true
                break
            }
            val cc = cur % cols
            val cr = cur / cols
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nc = cc + dc
                    val nr = cr + dr
                    if (nc !in 0 until cols || nr !in 0 until rows) continue
                    val ni = nr * cols + nc
                    if (blocked[ni] || closed[ni]) continue
                    // Diagonale nur, wenn beide Nachbarn frei sind (nicht um Ecken schneiden)
                    if (dr != 0 && dc != 0 && (blocked[cr * cols + nc] || blocked[nr * cols + cc])) continue
                    val step = if (dr != 0 && dc != 0) 1.4142135f else 1f
                    val ng = gScore[cur] + step * 0.5f * (cost[cur] + cost[ni])
                    if (ng < gScore[ni]) {
                        gScore[ni] = ng
                        prev[ni] = cur
                        open.add(Pair(ng + h(nc, nr, gc, gr), ni))
                    }
                }
            }
        }
        if (!found) return PathResult(listOf(from, to), false)
        val cells = ArrayList<Pt>()
        val cellCost = ArrayList<Float>()
        var cur = gi
        while (cur != -1) {
            cells.add(center(cur % cols, cur / cols))
            cellCost.add(cost[cur])
            cur = prev[cur]
        }
        cells.reverse()
        cellCost.reverse()
        // Linienzug glätten: von jedem Punkt aus so weit wie möglich geradeaus über freie Zellen
        val pts = ArrayList<Pt>()
        pts.add(s)
        var idx = 0
        var a = s
        while (idx < cells.size - 1) {
            var k = cells.size - 1
            while (k > idx + 1 && !lineOk(a, cells[k], cellCost.subList(idx, k + 1).max())) k--
            a = cells[k]
            pts.add(a)
            idx = k
        }
        if (pts.size == 1) pts.add(g) else pts[pts.size - 1] = g
        val full = ArrayList<Pt>()
        if (from != s) full.add(from)
        full.addAll(pts)
        if (to != g && inside(to)) full.add(to)
        return PathResult(dedupe(full), true)
    }

    private fun dedupe(l: List<Pt>): List<Pt> {
        val out = ArrayList<Pt>()
        for (p in l) if (out.isEmpty() || out.last().dist(p) > 0.01f) out.add(p)
        if (out.size == 1) out.add(out[0])
        return out
    }

    private fun h(c: Int, r: Int, gc: Int, gr: Int): Float {
        val dx = (c - gc).toFloat()
        val dy = (r - gr).toFloat()
        return sqrt(dx * dx + dy * dy)
    }
}

class PathResult(val points: List<Pt>, val ok: Boolean) {
    val length: Float
        get() {
            var s = 0f
            for (i in 1 until points.size) s += points[i - 1].dist(points[i])
            return s
        }
}
