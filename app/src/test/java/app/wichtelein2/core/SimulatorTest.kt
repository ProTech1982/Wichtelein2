package app.wichtelein2.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatorTest {
    private fun base(view: ViewMode = ViewMode.SIDE, steps: List<Step>, objs: List<SceneObject> = emptyList()) =
        Plan.default(1600, 1000).copy(view = view, steps = steps, objects = objs, nextId = 100)

    private fun house() = SceneObject(1, ObjType.HOUSE, 700f, 560f, 300f, 360f)
    private fun tree() = SceneObject(2, ObjType.TREE, 350f, 560f, 260f, 420f)
    private fun bed() = SceneObject(3, ObjType.BED, 1000f, 800f, 300f, 160f)
    private fun grass() = SceneObject(4, ObjType.GRASS, 500f, 820f, 300f, 120f)

    private fun allSteps() = listOf(
        Step(ActionType.SWEEP, seconds = 3f),
        Step(ActionType.HIDE, retreat = Retreat.OBJECT, objectId = 2, seconds = 2f),
        Step(ActionType.SCARE, retreat = Retreat.OBJECT, objectId = 3, seconds = 2f),
        Step(ActionType.HIDE, retreat = Retreat.DOOR, seconds = 1f),
        Step(ActionType.SCARE, retreat = Retreat.DOOR),
    )

    private fun plans(): List<Plan> = listOf(
        base(ViewMode.SIDE, allSteps(), listOf(house(), tree(), bed(), grass())),
        base(ViewMode.TOP, allSteps(), listOf(house(), tree(), bed(), grass())),
        base(ViewMode.SIDE, listOf(Step(ActionType.SWEEP))),
    )

    @Test
    fun startsAndEndsInsideWithClosedDoor() {
        for (p in plans()) {
            val s = Simulator(p).run()
            assertTrue(s.frames.size > 60)
            assertEquals(1f, s.frames.first().houseT, 0.001f)
            assertEquals(1f, s.frames.last().houseT, 0.001f)
            assertEquals(0f, s.frames.first().doorOpen, 0.001f)
            assertEquals(0f, s.frames.last().doorOpen, 0.01f)
            assertTrue(s.frames.maxOf { it.doorOpen } > 0.99f)
        }
    }

    @Test
    fun everyStepAppears() {
        for (p in plans()) {
            val s = Simulator(p).run()
            for (i in p.steps.indices) assertTrue("Schritt $i fehlt", s.frames.any { it.step == i })
        }
    }

    @Test
    fun walkingNeverInsideFootprints() {
        for (p in plans()) {
            val s = Simulator(p).run()
            for (f in s.frames) {
                if (f.houseT > 0.01f) continue
                val moving = f.anim == Anim.WALK || f.anim == Anim.RUN || f.anim == Anim.SNEAK || f.anim == Anim.SWEEP
                if (!moving) continue
                for (o in p.objects) {
                    val d = o.distToFootprint(Pt(f.x, f.y), p.view)
                    // Zielpunkte (Versteck) dürfen direkt an der Standfläche liegen, nie darin
                    assertTrue("Frame bei t=${f.t} läuft in ${o.type} (${f.x},${f.y})", d > 0f)
                }
            }
        }
    }

    @Test
    fun valuesAreInRange() {
        for (p in plans()) {
            val s = Simulator(p).run()
            for (f in s.frames) {
                assertTrue(f.dir in 0..7)
                assertTrue(f.frame in 0 until f.anim.frames)
                assertTrue(f.houseT in -0.001f..1.001f)
                assertTrue(f.doorOpen in -0.001f..1.001f)
                assertTrue(f.x in 0f..p.width.toFloat() && f.y in 0f..p.height.toFloat())
            }
        }
    }

    @Test
    fun hideBehindObjectDucksAtTheSpot() {
        val p = base(ViewMode.SIDE, listOf(Step(ActionType.HIDE, retreat = Retreat.OBJECT, objectId = 2, seconds = 2f)), listOf(tree()))
        val sim = Simulator(p)
        val spot = sim.hideSpot(p.objects[0])
        val s = sim.run()
        val ducked = s.frames.filter { it.anim == Anim.DUCK && it.frame == 3 }
        assertTrue(ducked.size >= 50)
        for (f in ducked) {
            assertEquals(spot.x, f.x, 1f)
            assertEquals(spot.y, f.y, 1f)
        }
        assertTrue(s.frames.any { it.anim == Anim.SNEAK })
        assertTrue(spot.y < p.objects[0].baseY)
    }

    @Test
    fun scareShowsAllPosesThenRunsToDoor() {
        val p = base(ViewMode.SIDE, listOf(Step(ActionType.SCARE, retreat = Retreat.DOOR)))
        val s = Simulator(p).run()
        val scareFrames = s.frames.filter { it.anim == Anim.SCARE }.map { it.frame }.toSet()
        assertEquals(setOf(0, 1, 2, 3, 4, 5), scareFrames)
        val lastScare = s.frames.indexOfLast { it.anim == Anim.SCARE }
        val firstRun = s.frames.indexOfFirst { it.anim == Anim.RUN }
        assertTrue(firstRun > lastScare)
        assertEquals(1f, s.frames.last().houseT, 0.001f)
    }

    @Test
    fun scareToObjectHidesBehindIt() {
        val p = base(ViewMode.TOP, listOf(Step(ActionType.SCARE, retreat = Retreat.OBJECT, objectId = 2, seconds = 2f)), listOf(tree()))
        val s = Simulator(p).run()
        assertTrue(s.frames.any { it.anim == Anim.RUN })
        assertTrue(s.frames.any { it.anim == Anim.DUCK && it.frame == 3 })
    }

    @Test
    fun hideInDoorStaysInsideForWhile() {
        val p = base(steps = listOf(Step(ActionType.SWEEP, seconds = 2f), Step(ActionType.HIDE, retreat = Retreat.DOOR, seconds = 2f)))
        val s = Simulator(p).run()
        assertTrue(s.frames.any { it.anim == Anim.SNEAK })
        val insideFrames = s.frames.count { it.houseT > 0.99f && it.step == 1 }
        assertTrue(insideFrames >= 2 * 30)
    }

    @Test
    fun hidingWhileAlreadyInsideDoesNotPeekOut() {
        // Erster Schritt „im Haus verstecken“: der Wichtel war schon drin, also bleibt er unsichtbar und die Tür zu
        val p = base(steps = listOf(Step(ActionType.HIDE, retreat = Retreat.DOOR, seconds = 2f)))
        val s = Simulator(p).run()
        for (f in s.frames) {
            assertEquals("Wichtel guckt heraus bei t=${f.t}", 1f, f.houseT, 0.001f)
            assertEquals("Tür zuckt bei t=${f.t}", 0f, f.doorOpen, 0.01f)
        }
        assertTrue(s.duration >= 2f)
    }

    @Test
    fun hidingAfterSweepEntersOnceAndStaysGone() {
        val p = base(steps = listOf(Step(ActionType.SWEEP, seconds = 2f), Step(ActionType.HIDE, retreat = Retreat.DOOR, seconds = 2f)))
        val s = Simulator(p).run()
        val enterEnd = s.frames.indexOfFirst { it.step == 1 && it.houseT > 0.99f }
        assertTrue(enterEnd > 0)
        // ab dem Hineingehen nie wieder sichtbar, solange der Schritt läuft; die Tür geht nur noch zu
        var lastOpen = 2f
        for (i in enterEnd until s.frames.size) {
            val f = s.frames[i]
            assertTrue("Wichtel taucht wieder auf bei t=${f.t}", f.houseT >= 0.99f)
            assertTrue("Tür öffnet sich erneut bei t=${f.t}", f.doorOpen <= lastOpen + 0.001f)
            lastOpen = f.doorOpen
        }
    }

    @Test
    fun noObjectGivesWarningAndFallbackToDoor() {
        val p = base(steps = listOf(Step(ActionType.HIDE, retreat = Retreat.OBJECT, objectId = 99)))
        val s = Simulator(p).run()
        assertTrue(s.warnings.isNotEmpty())
        assertEquals(1f, s.frames.last().houseT, 0.001f)
    }

    @Test
    fun speedIsReasonable() {
        for (p in plans()) {
            val s = Simulator(p).run()
            val sc = Scaler(p)
            for (i in 1 until s.frames.size) {
                val a = s.frames[i - 1]
                val b = s.frames[i]
                val d = Pt(a.x, a.y).dist(Pt(b.x, b.y))
                val limit = 2.5f * sc.heightAt(b.y) / 30f + 1.5f
                assertTrue("Sprung $d bei t=${b.t}", d <= limit)
            }
        }
    }

    @Test
    fun deterministic() {
        val p = plans()[0]
        val a = Simulator(p).run()
        val b = Simulator(p).run()
        assertEquals(a.frames.size, b.frames.size)
        for (i in a.frames.indices) assertEquals(a.frames[i], b.frames[i])
    }

    @Test
    fun sweepStaysNearItsPoint() {
        val at = Pt(800f, 800f)
        val p = base(steps = listOf(Step(ActionType.SWEEP, at = at, seconds = 4f)))
        val s = Simulator(p).run()
        val sw = s.frames.filter { it.anim == Anim.SWEEP }
        assertTrue(sw.size >= 100)
        val h = Scaler(p).heightAt(at.y)
        for (f in sw) assertTrue(Pt(f.x, f.y).dist(at) <= 1.0f * h)
    }

    @Test
    fun automaticSpotsAvoidHidingUnderObjects() {
        // Baum genau dort, wo der automatische Punkt sonst läge
        for (view in ViewMode.entries) {
            val p0 = Plan.default(1600, 1000).copy(view = view)
            val y = if (view == ViewMode.SIDE) p0.horizon + (1000f - p0.horizon) * 0.65f else 550f
            val tree = SceneObject(1, ObjType.TREE, 560f, y - 60f, 300f, 400f)
            val p = p0.copy(objects = listOf(tree), nextId = 2, steps = listOf(Step(ActionType.SWEEP, seconds = 3f), Step(ActionType.SCARE)))
            val s = Simulator(p).run()
            val sweep = s.frames.first { it.anim == Anim.SWEEP }
            val scare = s.frames.first { it.anim == Anim.SCARE }
            assertTrue("Fegeort im Baum ($view)", !tree.contains(Pt(sweep.x, sweep.y)))
            assertTrue("Schreckort im Baum ($view)", !tree.contains(Pt(scare.x, scare.y)))
        }
    }

    @Test
    fun directionsFollowMovement() {
        assertEquals(0, headingToDir(0f, 1f))
        assertEquals(2, headingToDir(1f, 0f))
        assertEquals(4, headingToDir(0f, -1f))
        assertEquals(6, headingToDir(-1f, 0f))
        assertEquals(1, headingToDir(1f, 1f))
        assertEquals(7, headingToDir(-1f, 1f))
    }

    @Test
    fun scalerShrinksTowardHorizonOnlyInSideView() {
        val p = Plan.default(1600, 1000)
        val side = Scaler(p)
        assertTrue(side.heightAt(p.horizon + 50f) < side.heightAt(p.door.y))
        assertTrue(side.heightAt(900f) > side.heightAt(p.door.y))
        val top = Scaler(p.copy(view = ViewMode.TOP))
        assertEquals(top.heightAt(100f), top.heightAt(900f), 0.001f)
    }
}
