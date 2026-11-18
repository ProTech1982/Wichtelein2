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

    // ------------------------------------------------------------------ Tür: ein Durchgang = einmal öffnen und schließen
    @Test
    fun everyDoorPassOpensOnceAndWichtelOnlyCrossesWhileOpen() {
        for (p in plans() + base(steps = listOf(Step(ActionType.HIDE, retreat = Retreat.DOOR), Step(ActionType.SWEEP, seconds = 1f), Step(ActionType.HIDE, retreat = Retreat.DOOR, seconds = 1f)))) {
            val s = Simulator(p).run()
            var opens = 0
            var crossings = 0
            for (i in 1 until s.frames.size) {
                val a = s.frames[i - 1]
                val b = s.frames[i]
                if (a.doorOpen < 0.5f && b.doorOpen >= 0.5f) opens++
                if ((a.houseT <= 0.5f) != (b.houseT <= 0.5f)) crossings++
                if (b.houseT > 0.02f && b.houseT < 0.98f) assertTrue("Wichtel geht durch geschlossene Tür bei t=${b.t}", b.doorOpen > 0.9f)
            }
            assertEquals("Türöffnungen und Durchgänge passen nicht zusammen", crossings, opens)
        }
    }

    @Test
    fun enteringWalksToTheDoorwayInsteadOfShrinkingAtTheDoorstep() {
        val p = base(steps = listOf(Step(ActionType.SCARE, retreat = Retreat.DOOR)))
        val s = Simulator(p).run()
        val door = p.door.base
        val half = s.frames.last { it.houseT < 0.5f }
        val start = s.frames.first { it.step == 0 && it.houseT > 0.01f && it.anim != Anim.IDLE && it.t > 5f }
        // Beim Verschwinden steht er in der Türöffnung (Füße an der Türkante), davor kam er von vorn auf die Tür zu
        assertEquals(door.x, half.x, 2f)
        assertEquals(door.y, half.y, 2f)
        assertTrue("Er geht beim Hineingehen noch ein Stück", start.y > door.y + 5f)
    }

    // ------------------------------------------------------------------ Leiter und Treppe
    private fun ladder() = SceneObject(5, ObjType.LADDER, 700f, 560f, 120f, 350f)
    private fun stairs() = SceneObject(6, ObjType.STAIRS, 1000f, 700f, 330f, 220f)

    @Test
    fun ladderClimbsUpStaysThenComesDownBeforeTheNextStep() {
        val p = base(steps = listOf(Step(ActionType.LADDER, objectId = 5, seconds = 1f), Step(ActionType.HIDE, seconds = 1f)), objs = listOf(ladder()))
        val s = Simulator(p).run()
        val path = EditOps.climbPath(p, p.steps[0])!!
        val climb = s.frames.filter { it.anim == Anim.CLIMB }
        assertTrue(climb.size > 30)
        // oben angekommen; beim Klettern feste Größe und kein Verdecken durch die Leiter
        assertEquals(path.second.y, climb.minOf { it.y }, 1f)
        for (f in climb) {
            assertEquals(path.first.y, f.refY, 1f)
            assertEquals(5, f.ignore)
            assertEquals(path.first.x, f.x, 1f)
        }
        // senkrecht, Blick zur Leiter (weg vom Betrachter)
        assertTrue(climb.all { it.dir == 4 })
        // Verstecken in der Tür: erst hinuntersteigen, dann zur Tür
        val lastClimb = s.frames.indexOfLast { it.anim == Anim.CLIMB }
        val enter = s.frames.indexOfFirst { it.step == 1 && it.houseT > 0.01f }
        assertTrue(lastClimb < enter)
        assertEquals(path.first.y, s.frames[lastClimb].y, 2f)
        assertEquals(-1, s.frames[enter].ignore)
        assertEquals(1f, s.frames.last().houseT, 0.001f)
    }

    @Test
    fun stairsDownFromTheGroundGoesUpFirstThenDown() {
        val p = base(steps = listOf(Step(ActionType.STAIRS, objectId = 6, up = false)), objs = listOf(stairs()))
        val s = Simulator(p).run()
        val path = EditOps.climbPath(p, p.steps[0])!!
        val st = s.frames.filter { it.anim == Anim.STAIRS }
        assertEquals(path.first.y, st.first().y, 6f)
        assertEquals(path.second.y, st.minOf { it.y }, 1f)
        assertEquals(path.first.y, st.last().y, 1f)
        val topIdx = st.indexOfFirst { it.y == st.minOf { f -> f.y } }
        assertTrue(topIdx > 10 && topIdx < st.size - 10)
        // seitlich: Blick nach rechts hinauf, nach links hinunter
        assertTrue(st.take(st.size / 2).drop(5).all { it.dir == 2 })
        assertTrue(st.drop(st.size / 2 + 5).all { it.dir == 6 })
    }

    @Test
    fun stairsUpThenDownContinuesOnTheSamePathWithoutWalkingBack() {
        val p = base(steps = listOf(Step(ActionType.STAIRS, objectId = 6, up = true, seconds = 1f), Step(ActionType.STAIRS, up = false)), objs = listOf(stairs()))
        val s = Simulator(p).run()
        assertTrue(s.frames.filter { it.step == 1 }.none { it.anim == Anim.WALK })
        assertTrue(s.frames.filter { it.step == 1 }.any { it.anim == Anim.STAIRS })
        assertEquals(1f, s.frames.last().houseT, 0.001f)
    }

    @Test
    fun climbingWorksInTopViewAndCustomPathIsUsed() {
        val a = Pt(300f, 800f)
        val b = Pt(300f, 500f)
        val p = base(ViewMode.TOP, listOf(Step(ActionType.LADDER, at = a, to = b, up = true, seconds = 1f)))
        val s = Simulator(p).run()
        val climb = s.frames.filter { it.anim == Anim.CLIMB }
        assertTrue(climb.isNotEmpty())
        assertEquals(b.y, climb.minOf { it.y }, 1f)
        assertTrue(s.warnings.isEmpty())
    }

    @Test
    fun climbWithoutAnyObjectUsesTheStepPointsAndEndsInside() {
        val p = base(steps = listOf(Step(ActionType.LADDER), Step(ActionType.SWEEP, seconds = 1f)))
        val s = Simulator(p).run()
        assertTrue(s.warnings.none { it.contains("Keine") })
        assertTrue(s.frames.any { it.anim == Anim.CLIMB })
        assertEquals(1f, s.frames.last().houseT, 0.001f)
    }

    @Test
    fun climbEndingTheWholeSequenceStillGetsDownAndHome() {
        val p = base(steps = listOf(Step(ActionType.LADDER, objectId = 5, up = true, seconds = 1f)), objs = listOf(ladder()))
        val s = Simulator(p).run()
        assertEquals(1f, s.frames.last().houseT, 0.001f)
        assertEquals(0f, s.frames.last().doorOpen, 0.01f)
        assertTrue(s.frames.filter { it.anim == Anim.CLIMB }.size > 60)
    }

    // ------------------------------------------------------------------ Meldung des Nutzers: Fegen, Erschrecken, Treppe, Fegen
    private fun userPlan(objs: List<SceneObject>) = base(
        ViewMode.SIDE,
        listOf(Step(ActionType.SWEEP, seconds = 3f), Step(ActionType.SCARE), Step(ActionType.STAIRS, seconds = 2f), Step(ActionType.SWEEP, seconds = 3f)),
        objs,
    )

    @Test
    fun stairsStepWithOnlyALadderUsesTheLadderInsteadOfDoingNothing() {
        val sim = Simulator(userPlan(listOf(house(), ladder()))).run()
        assertTrue("Hinweis auf Ersatz", sim.warnings.any { it.contains("Treppe") && it.contains("Leiter") })
        assertTrue("Wichtel steigt", sim.frames.any { it.step == 2 && it.anim == Anim.STAIRS })
        // Zweites Fegen oben: auf der Leiter/Treppe, ohne vorher aus der Tür zu kommen
        val top = sim.frames.filter { it.step == 3 && it.anim == Anim.SWEEP }
        assertTrue("fegt", top.size > 30)
        assertTrue("fegt oben", top.all { it.ignore != -1 })
    }

    @Test
    fun sweepAndScareOnTopStayThereUntilTheNextWalk() {
        val sim = Simulator(userPlan(listOf(house(), stairs()))).run()
        assertTrue(sim.warnings.none { it.contains("Keine") })
        val top = sim.frames.filter { it.step == 3 }
        val first = top.first()
        assertTrue("Fegen beginnt oben", first.ignore != -1)
        assertTrue("danach geht er hinunter und heim", sim.frames.last().houseT > 0.99f)
    }

    @Test
    fun doorStaysClosedForAMomentBetweenLeavingAndComingBack() {
        val sim = Simulator(userPlan(listOf(house(), stairs()))).run()
        var opens = 0
        var closedSince = -1f
        var minGap = 99f
        var wasOpen = false
        for (f in sim.frames) {
            if (f.doorOpen < 0.02f) {
                if (closedSince < 0f) closedSince = f.t
                if (wasOpen) wasOpen = false
            } else {
                if (closedSince >= 0f && opens > 0) minGap = minOf(minGap, f.t - closedSince)
                closedSince = -1f
                if (!wasOpen && f.doorOpen > 0.5f) { opens++; wasOpen = true }
            }
        }
        assertEquals("Türdurchgänge: heraus, hinein, heraus, hinein", 4, opens)
        assertTrue("Tür bleibt zwischen zwei Durchgängen mindestens 0,4 s zu (war $minGap)", minGap >= 0.4f)
    }

    @Test
    fun sweepingAndScaringOnTopStayOnTheUpperPointEvenWithAGroundPointSet() {
        val a = Pt(400f, 800f)
        val b = Pt(900f, 450f)
        val steps = listOf(
            Step(ActionType.STAIRS, at = a, to = b, up = true, seconds = 1f),
            Step(ActionType.SWEEP, at = Pt(300f, 900f), seconds = 2f),
            Step(ActionType.STAIRS, at = Pt(1f, 1f), to = Pt(2f, 2f), up = false),
        )
        val s = Simulator(base(steps = steps)).run()
        val sweep = s.frames.filter { it.step == 1 && it.anim == Anim.SWEEP }
        assertTrue(sweep.size > 30)
        for (f in sweep) {
            assertEquals(b.x, f.x, 1f)
            assertEquals(b.y, f.y, 1f)
            assertTrue(f.ignore != -1)
        }
        // „Hinunter“ nimmt den Pfad, auf dem er steht, nicht die eigenen Punkte des Schritts
        val down = s.frames.filter { it.step == 2 && it.anim == Anim.STAIRS }
        assertTrue(down.isNotEmpty())
        assertEquals(a.x, down.last().x, 6f)
    }

    @Test
    fun climbStartsExactlyAtPointAEvenAboveTheHorizon() {
        val p = base(steps = listOf(Step(ActionType.STAIRS, at = Pt(500f, 300f), to = Pt(900f, 150f), seconds = 1f)))
        val s = Simulator(p).run()
        val climb = s.frames.filter { it.anim == Anim.STAIRS }
        assertTrue(climb.isNotEmpty())
        // kein Sprung: aufeinanderfolgende Bilder liegen nahe beieinander
        for (i in 1 until climb.size) assertTrue(Math.hypot((climb[i].x - climb[i - 1].x).toDouble(), (climb[i].y - climb[i - 1].y).toDouble()) < 25.0)
        assertEquals(900f, climb.maxOf { it.x }, 1f)
    }
}
