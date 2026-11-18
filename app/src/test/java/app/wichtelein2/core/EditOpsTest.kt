package app.wichtelein2.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditOpsTest {
    private val p0 = Plan.default(1600, 1000)

    @Test
    fun arrowsMoveDoorAndStayInPhoto() {
        val n = EditOps.nudge(p0)
        val up = EditOps.move(p0, Sel.Door, 0f, -n)
        assertEquals(p0.door.y - n, up.door.y, 0.001f)
        val left = EditOps.move(p0, Sel.Door, -n, 0f)
        assertEquals(p0.door.x - n, left.door.x, 0.001f)
        val far = EditOps.move(p0, Sel.Door, -99999f, 99999f)
        assertEquals(0f, far.door.x, 0.001f)
        assertEquals(1000f, far.door.y, 0.001f)
    }

    @Test
    fun rotateAndSizeDoor() {
        val r = EditOps.rotate(p0, Sel.Door, 5f)
        assertEquals(5f, r.door.rot, 0.001f)
        val r2 = EditOps.rotate(EditOps.rotate(p0, Sel.Door, 179f), Sel.Door, 3f)
        assertEquals(-178f, r2.door.rot, 0.001f)
        val big = EditOps.scale(p0, Sel.Door, 1.5f)
        assertEquals(p0.door.w * 1.5f, big.door.w, 0.01f)
        assertEquals(p0.door.h * 1.5f, big.door.h, 0.01f)
        val wide = EditOps.stretch(p0, Sel.Door, 2f, 1f)
        assertEquals(p0.door.w * 2f, wide.door.w, 0.01f)
        assertEquals(p0.door.h, wide.door.h, 0.01f)
        val tiny = EditOps.scale(p0, Sel.Door, 0.0001f)
        assertTrue(tiny.door.w >= 14f && tiny.door.h >= 14f)
    }

    @Test
    fun addAndRemoveObjectsKeepsStepsConsistent() {
        var p = EditOps.addObject(p0, ObjType.TREE)
        val id = p.objects.single().id
        p = EditOps.addStep(p, ActionType.HIDE)
        assertEquals(id, p.steps.last().objectId)
        assertEquals(Retreat.OBJECT, p.steps.last().retreat)
        p = EditOps.removeObject(p, id)
        assertTrue(p.objects.isEmpty())
        assertEquals(-1, p.steps.last().objectId)
        val ids = EditOps.addObject(EditOps.addObject(p0, ObjType.BED), ObjType.BOX).objects.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun objectGetsPlacedOnGroundInSideView() {
        val p = EditOps.addObject(p0, ObjType.BED)
        assertTrue(p.objects[0].y > p0.horizon)
        val top = EditOps.addObject(p0.copy(view = ViewMode.TOP), ObjType.BED)
        assertEquals(500f, top.objects[0].y, 0.01f)
    }

    @Test
    fun stepsCanBeAddedMovedRemoved() {
        var p = EditOps.addStep(EditOps.addStep(p0, ActionType.SCARE), ActionType.HIDE)
        assertEquals(listOf(ActionType.SWEEP, ActionType.SCARE, ActionType.HIDE), p.steps.map { it.action })
        p = EditOps.moveStep(p, 2, -1)
        assertEquals(listOf(ActionType.SWEEP, ActionType.HIDE, ActionType.SCARE), p.steps.map { it.action })
        p = EditOps.moveStep(p, 0, -1)
        assertEquals(ActionType.SWEEP, p.steps[0].action)
        p = EditOps.removeStep(p, 1)
        assertEquals(2, p.steps.size)
        val one = EditOps.removeStep(p0, 0)
        assertEquals(1, one.steps.size)
        val withAt = EditOps.move(p, Sel.StepAt(0), 10f, 0f)
        assertEquals(EditOps.defaultAt(p).x + 10f, withAt.steps[0].at!!.x, 0.01f)
    }

    @Test
    fun hitTestPicksTopmostThenDoorThenHorizon() {
        var p = EditOps.addObject(p0, ObjType.BED)
        val bed = p.objects[0]
        assertEquals(Sel.Obj(bed.id), EditOps.hit(p, bed.center, 20f))
        val doorMid = Pt(p.door.x, p.door.y - p.door.h / 2f)
        assertEquals(Sel.Door, EditOps.hit(p, doorMid, 20f))
        assertEquals(Sel.Horizon, EditOps.hit(p, Pt(10f, p.horizon + 5f), 20f))
        assertEquals(Sel.None, EditOps.hit(p, Pt(10f, 900f), 20f))
        p = p.copy(view = ViewMode.TOP)
        assertEquals(Sel.None, EditOps.hit(p, Pt(10f, p.horizon + 5f), 20f))
        p = EditOps.updateStep(p, 0) { it.copy(at = Pt(100f, 100f)) }
        assertEquals(Sel.StepAt(0), EditOps.hit(p, Pt(105f, 98f), 20f))
    }

    @Test
    fun handleResizesDoorAndObject() {
        val h = EditOps.handle(p0, Sel.Door)!!
        val bigger = EditOps.dragHandle(p0, Sel.Door, Pt(h.x + 40f, h.y - 30f))
        assertEquals(p0.door.w + 80f, bigger.door.w, 0.5f)
        assertEquals(p0.door.h + 30f, bigger.door.h, 0.5f)
        val p = EditOps.addObject(p0, ObjType.BOX)
        val o = p.objects[0]
        val oh = EditOps.handle(p, Sel.Obj(o.id))!!
        val q = EditOps.dragHandle(p, Sel.Obj(o.id), Pt(oh.x + 10f, oh.y + 10f))
        assertEquals(o.w + 20f, q.objects[0].w, 0.5f)
        assertEquals(o.h + 20f, q.objects[0].h, 0.5f)
        assertNotEquals(null, EditOps.handle(p0, Sel.Door))
        assertEquals(null, EditOps.handle(p0, Sel.Horizon))
    }

    @Test
    fun rotatedDoorHandleFollowsRotation() {
        val p = p0.copy(door = p0.door.copy(rot = 90f))
        val h = EditOps.handle(p, Sel.Door)!!
        // 90° gedreht: aus „rechts oben“ wird „rechts unten“ relativ zur Basis
        assertEquals(p.door.x + p.door.h, h.x, 0.01f)
        val same = EditOps.dragHandle(p, Sel.Door, h)
        assertEquals(p.door.w, same.door.w, 0.5f)
        assertEquals(p.door.h, same.door.h, 0.5f)
    }

    @Test
    fun horizonMovesOnlyVertically() {
        val p = EditOps.move(p0, Sel.Horizon, 50f, 30f)
        assertEquals(p0.horizon + 30f, p.horizon, 0.001f)
    }

    @Test
    fun climbStepsGetAutomaticPathAndCanBeMoved() {
        var p = EditOps.addObject(p0, ObjType.LADDER)
        p = EditOps.addStep(p, ActionType.LADDER)
        val idx = p.steps.size - 1
        val st = p.steps[idx]
        assertTrue("Punkte A und B sind gesetzt", st.at != null && st.to != null)
        assertTrue(st.up)
        val (a, b) = EditOps.climbPath(p, st)!!
        assertTrue("oben liegt über unten", b.y < a.y)
        // Verschieben des oberen Endes geht vom automatischen Pfad aus; das untere bleibt automatisch
        val q = EditOps.move(p, Sel.StepTo(idx), 10f, 0f)
        assertEquals(b.x + 10f, q.steps[idx].to!!.x, 0.01f)
        assertEquals(a.x, q.steps[idx].at!!.x, 0.01f)
        // beide Enden sind im Foto anfassbar, ein anderer Schritt/anderes Bauteil nicht
        assertEquals(Sel.StepAt(idx), EditOps.hit(q, a, 5f, Layer.STEPS))
        assertEquals(Sel.StepTo(idx), EditOps.hit(q, q.steps[idx].to!!, 5f, Layer.STEPS))
        assertEquals(Sel.None, EditOps.hit(q, a, 5f, Layer.DOOR))
    }

    @Test
    fun staircaseRunsFromBottomLeftToTopRight() {
        val o = SceneObject(1, ObjType.STAIRS, 800f, 600f, 300f, 200f)
        val (a, b) = EditOps.autoClimbPath(o, ActionType.STAIRS)
        assertTrue(a.x < o.x && b.x > o.x)
        assertTrue(a.y > o.y && b.y < o.y)
        val (la, lb) = EditOps.autoClimbPath(o, ActionType.LADDER)
        assertEquals(o.x, la.x, 0.01f)
        assertEquals(o.x, lb.x, 0.01f)
        assertTrue(lb.y < la.y)
    }

    @Test
    fun onlyTheActiveLayerCanBeHitAndOnlyTheSelectionCanBeDragged() {
        val d = p0.door
        var p = EditOps.addObject(p0, ObjType.BOX)
        val id = p.objects[0].id
        val w = Pt(d.x, d.y - d.h / 2f)
        p = EditOps.move(p, Sel.Obj(id), w.x - p.objects[0].x, w.y - p.objects[0].y)
        // Tür und Kiste liegen übereinander: je Ebene wird nur das Eigene getroffen
        assertEquals(Sel.Obj(id), EditOps.hit(p, w, 5f, Layer.OBJECTS))
        assertEquals(Sel.Door, EditOps.hit(p, w, 5f, Layer.DOOR))
        assertEquals(Sel.None, EditOps.hit(p, w, 5f, Layer.STEPS))
        // ziehen darf nur die Auswahl
        assertTrue(EditOps.hitsSelection(p, Sel.Door, w, 5f))
        assertTrue(EditOps.hitsSelection(p, Sel.Obj(id), w, 5f))
        assertFalse(EditOps.hitsSelection(p, Sel.Obj(id), Pt(5f, 5f), 5f))
        assertFalse(EditOps.hitsSelection(p, Sel.None, w, 5f))
        assertEquals(Layer.DOOR, Layer.ofTab(0))
        assertEquals(Layer.OBJECTS, EditOps.layerOf(Sel.Obj(id)))
        assertEquals(Layer.STEPS, EditOps.layerOf(Sel.StepTo(0)))
    }

    @Test
    fun movingAStepNeverTouchesOtherSteps() {
        var p = EditOps.addStep(EditOps.addStep(p0, ActionType.SCARE), ActionType.SWEEP)
        val gy = p0.horizon + 200f
        p = EditOps.updateStep(p, 0) { it.copy(at = Pt(100f, gy)) }
        p = EditOps.updateStep(p, 1) { it.copy(at = Pt(300f, 300f)) }
        p = EditOps.updateStep(p, 2) { it.copy(at = Pt(500f, 500f)) }
        val q = EditOps.move(p, Sel.StepAt(0), 20f, 0f)
        assertEquals(Pt(120f, gy), q.steps[0].at)
        assertEquals(Pt(300f, 300f), q.steps[1].at)
        assertEquals(Pt(500f, 500f), q.steps[2].at)
    }

    @Test
    fun stairsCanRiseLeftOrRight() {
        val o = SceneObject(1, ObjType.STAIRS, 500f, 700f, 300f, 200f)
        val p = Plan.default(1600, 1000).copy(objects = listOf(o), steps = listOf(Step(ActionType.STAIRS)))
        assertTrue("automatisch nach rechts", EditOps.stairsRiseRight(p, o))
        val (a, b) = EditOps.mirroredStairsPath(o)
        assertTrue(b.x < a.x && b.y < a.y)
        val q = p.copy(steps = listOf(Step(ActionType.STAIRS, at = a, to = b)))
        assertTrue("nach links", !EditOps.stairsRiseRight(q, o))
    }

    @Test
    fun groundPointsSnapToWalkableGroundButClimbPointsStayWhereTheyAre() {
        val p = Plan.default(1600, 1000).copy(steps = listOf(Step(ActionType.SWEEP), Step(ActionType.STAIRS)))
        // Fegen: ein Punkt über dem Horizont rutscht auf den Boden darunter
        val q = EditOps.placeStepPoint(p, 0, Pt(500f, p.horizon - 150f), false)
        assertTrue("liegt unter dem Horizont: ${q.steps[0].at}", q.steps[0].at!!.y >= p.horizon)
        // Treppe: A und B sind frei setzbar
        val r = EditOps.placeStepPoint(q, 1, Pt(500f, p.horizon - 150f), false)
        assertEquals(p.horizon - 150f, r.steps[1].at!!.y, 0.01f)
    }

    @Test
    fun stairsSlopeKeepsAAndMovesBToTheOtherSide() {
        var p = Plan.default(1600, 1000).copy(steps = listOf(Step(ActionType.STAIRS)))
        p = EditOps.addStep(p, ActionType.STAIRS)
        val a = p.steps[1].at!!
        val left = EditOps.setStairsSlope(p, 1, false)
        assertEquals(a, left.steps[1].at)
        assertTrue(left.steps[1].to!!.x < a.x)
        val right = EditOps.setStairsSlope(left, 1, true)
        assertTrue(right.steps[1].to!!.x > a.x)
    }

    @Test
    fun onTopAfterClimbingUpUntilDownHideOrScare() {
        val steps = listOf(Step(ActionType.SWEEP), Step(ActionType.STAIRS, up = true), Step(ActionType.SWEEP), Step(ActionType.SCARE), Step(ActionType.SWEEP))
        val p = Plan.default(1600, 1000).copy(steps = steps)
        assertEquals(listOf(false, false, true, true, false), (0 until 5).map { EditOps.onTopAt(p, it) })
    }
}
