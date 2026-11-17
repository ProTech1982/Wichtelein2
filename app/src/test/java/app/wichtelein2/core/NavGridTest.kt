package app.wichtelein2.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGridTest {
    private fun plan(view: ViewMode = ViewMode.SIDE, objs: List<SceneObject> = emptyList()): Plan =
        Plan.default(1600, 1000).copy(view = view, objects = objs, nextId = 100)

    private fun samples(pts: List<Pt>): List<Pt> {
        val out = ArrayList<Pt>()
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val n = maxOf(1, (a.dist(b) / 5f).toInt())
            for (k in 0..n) out.add(Pt(a.x + (b.x - a.x) * k / n, a.y + (b.y - a.y) * k / n))
        }
        return out
    }

    @Test
    fun straightPathWithoutObstacles() {
        val g = NavGrid(plan())
        val r = g.findPath(Pt(200f, 700f), Pt(900f, 800f))
        assertTrue(r.ok)
        val direct = Pt(200f, 700f).dist(Pt(900f, 800f))
        assertEquals(direct, r.length, direct * 0.02f)
    }

    @Test
    fun pathGoesAroundHouse() {
        val house = SceneObject(1, ObjType.HOUSE, 700f, 650f, 300f, 400f)
        val g = NavGrid(plan(objs = listOf(house)))
        val r = g.findPath(Pt(300f, 760f), Pt(1100f, 760f))
        assertTrue(r.ok)
        for (s in samples(r.points)) {
            assertTrue("Punkt $s liegt im Haus", house.distToFootprint(s, ViewMode.SIDE) > 0f)
        }
        assertTrue(r.length > 800f)
    }

    @Test
    fun pathAvoidsTreeTrunkFromAbove() {
        val tree = SceneObject(1, ObjType.TREE, 600f, 500f, 400f, 400f)
        val g = NavGrid(plan(ViewMode.TOP, listOf(tree)))
        val r = g.findPath(Pt(100f, 500f), Pt(1100f, 500f))
        assertTrue(r.ok)
        for (s in samples(r.points)) assertTrue(tree.distToFootprint(s, ViewMode.TOP) > 0f)
    }

    @Test
    fun goalInsideBlockedAreaStillEndsAtGoal() {
        val box = SceneObject(1, ObjType.BOX, 800f, 760f, 200f, 200f)
        val g = NavGrid(plan(objs = listOf(box)))
        val goal = Pt(800f, 780f)
        val r = g.findPath(Pt(300f, 800f), goal)
        assertEquals(goal.x, r.points.last().x, 0.01f)
        assertEquals(goal.y, r.points.last().y, 0.01f)
    }

    @Test
    fun sideViewNeverAboveHorizon() {
        val p = plan()
        val g = NavGrid(p)
        val r = g.findPath(Pt(300f, 700f), Pt(1200f, p.horizon - 100f))
        for (s in samples(r.points)) assertTrue(s.y >= p.horizon - 1f)
    }

    @Test
    fun grassIsAvoidedWhenDetourIsCheap() {
        val grass = SceneObject(1, ObjType.GRASS, 800f, 760f, 200f, 120f)
        val g = NavGrid(plan(objs = listOf(grass)))
        val r = g.findPath(Pt(300f, 760f), Pt(1300f, 760f))
        assertTrue(r.ok)
        var inGrass = 0
        // Zellgröße als Spielraum am Rand: nur der innere Teil der Fläche zählt
        val core = grass.copy(w = grass.w * 0.7f, h = grass.h * 0.7f)
        for (s in samples(r.points)) if (core.contains(s)) inGrass++
        assertEquals(0, inGrass)
    }

    @Test
    fun wallAcrossEverythingGivesNoPath() {
        val wall = SceneObject(1, ObjType.FENCE, 800f, 236f, 1700f, 1300f)
        val g = NavGrid(plan(objs = listOf(wall)))
        val r = g.findPath(Pt(300f, 900f), Pt(1300f, 500f))
        assertFalse(r.ok)
    }

    @Test
    fun doorBaseIsAlwaysFree() {
        val p = plan()
        val big = SceneObject(1, ObjType.HOUSE, p.door.x, p.door.y - 100f, 600f, 500f)
        val g = NavGrid(p.copy(objects = listOf(big)))
        assertTrue(g.isFree(Pt(p.door.x, p.door.y + 20f)))
    }
}
